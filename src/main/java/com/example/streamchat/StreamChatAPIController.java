package com.example.streamchat;

import com.fasterxml.uuid.Generators;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

@RestController
public class StreamChatAPIController {
	// TODO: find a more canonical way to do these next couple steps

	// This is a buffer for incoming messages.
	private BlockingQueue<String> messageBuffer = new ArrayBlockingQueue<>(4096, true);

	// This is an infinite Flux that pulls values from the input buffer.
	private Flux<String> stream = Flux.create(sink -> {
		while (true) {
			try {
				sink.next(messageBuffer.take());
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
	}).share()             // share all messages with all subscribers
	.map(x -> (String) x); // undo cast to Flux<Object> caused by share()

	@GetMapping("/add")
	public String add() throws InterruptedException {
		String uuid = Generators.timeBasedEpochGenerator().generate().toString();
		messageBuffer.put(uuid);
		return uuid;
	}

	@GetMapping("/getStream")
	public Flux<String> getStream() {
		// Safari and Firefox appear to require a minimum number of bytes
		// before they start rendering, so add that to the beginning:
		Mono<String> paddedHeader = Mono.just(
			new String(new char[1024]).replace("\0", " ")
		);

		// I spent far too much time trying to come up with a way to do this.
		// This seems to work, but I very much doubt it's a canonical approach.
		// The goal is to provide an infinite stream of new messages as they
		// are received.  The problem I ran into repeatedly was to find a way
		// of returning from this method immediately (i.e. so it outputs
		// immediately, which allows the browser to start rendering) while
		// also continuing to render the full stream.  See
		// https://github.com/niutech/phooos for Node and PHP versions of the
		// same idea.

		// create a time-based, infinite Flux that waits 0 seconds between elements
		Flux<String> dummy = Flux.interval(Duration.ofSeconds(0)).
			take(1). // we only want to do this once
			map(seq -> ""); // push nothing to the client

		return paddedHeader.concatWith(dummy).concatWith(stream.map(message ->
			"<div>" + message + "</div>"
		));
	}
}
