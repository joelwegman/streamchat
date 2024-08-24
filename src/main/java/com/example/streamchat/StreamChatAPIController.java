package com.example.streamchat;

import com.fasterxml.uuid.Generators;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

@RestController
public class StreamChatAPIController {
	private BlockingQueue<String> q = new ArrayBlockingQueue<>(4096, true);
	private Flux<String> stream = Flux.create(sink -> {
		while (true) {
			try {
				sink.next("<div>" + q.take() + "</div>");
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
	}).share().map(x -> (String) x);

	@GetMapping("/add")
	public String add() throws InterruptedException {
		String uuid = Generators.timeBasedEpochGenerator().generate().toString();
		q.put(uuid);
		return uuid;
	}

	@GetMapping("/getStream")
	public Flux<String> getStream() {
		return Flux.interval(Duration.ofSeconds(0)).take(1).map(seq -> "").concatWith(stream);
	}
}
