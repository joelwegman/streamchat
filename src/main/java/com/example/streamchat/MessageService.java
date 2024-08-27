package com.example.streamchat;

import reactor.core.publisher.Flux;
import java.time.Duration;
import java.util.TreeMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

// NOTE: This is my first Spring Boot project, and my experience with reactive
// programming in general isn't terribly deep yet, so this is very likely not a
// very canonical way of doing this.
// TODO: fix that after reading more documentation and code out in the wild

// Unfortunately, I couldn't find much information about the kind of streaming
// I want to do using Spring Boot and/or Reactor Core.  Most looked like this:
// https://technicalsand.com/streaming-data-spring-boot-restful-web-service/.
// All the sample code I tried would either block until the input Flux was
// completed, complete it prematurely instead of accepting new messages, or
// set a polling loop on an interval instead of actually being reactive.

public class MessageService {
	// temporary, before real DB
	private TreeMap<String, Message> db = new TreeMap<>((a, b) -> a == null ? 1 : a.compareTo(b) * -1);

	public MessageService() {
		for (int i = 0; i < 100; i++) {
			try {
				var message = new Message("message" + i);
				addMessage(message);
				Thread.sleep(1);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
	}

	// This creates an infinite Flux<Message>, which sends new values as
	// they're added to a BlockingQueue.

	// TODO: find a more canonical way of doing this
	// (Mono.and, Mono.then, and ConnectableFlux seem like promising places to start)
	private BlockingQueue<Message> messageBuffer = new ArrayBlockingQueue<>(4096, true);
	private Flux<Message> stream = Flux.create(sink -> {
		while (true) {
			try {
				var message = messageBuffer.take();
				db.put(message.getId(), message);
				sink.next(message);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
	}).share()              // share all messages with all subscribers
	.map(x -> (Message) x); // undo cast to Flux<Object> caused by share()

	public Flux<Message> getStream() {
		// the only way I could find to return a lazy value that would start
		// evaluation as soon as it receives data was to use Flux.interval()
		// with a duration of zero, taking only the first one, and mapping that
		// to an empty message that can be filtered out by the controller
		// (also, when something else gets prepended, a non-zero duration is necessary)
		// TODO: find a cleaner way of doing this
		var dummy = Flux.interval(Duration.ofMillis(100)).take(1).map(_ -> new Message());
		return dummy.concatWith(stream);
	}

	public Message addMessage(Message message) throws InterruptedException {
		messageBuffer.put(message);
		return message;
	}


	// methods below here are temporary until a real db is ready


	public Message getMessage(String id) {
		return db.get(id);
	}

	public Flux<Message> getPageBefore(String id) {
		var newId = db.containsKey(id) ? db.higherKey(id) : id;
		return Flux.fromIterable(db.tailMap(newId).sequencedValues().stream().limit(20).toList().reversed());
	}
}
