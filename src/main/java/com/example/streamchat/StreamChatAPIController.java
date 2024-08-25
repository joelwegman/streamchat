package com.example.streamchat;

import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
public class StreamChatAPIController {
	private MessageService messageService = new MessageService();

	@GetMapping("/add")
	public String add() throws InterruptedException {
		// TODO: actually get message from input
		messageService.addMessage(new Message("asdf"));
		return "this should be a form submission template?";
	}

	@GetMapping("/getPageBefore/{id}")
	public Flux<String> getPageBefore(@PathVariable("id") String id) {
		return messageService.getPageBefore(id).map(message -> "<div>" + message.getId() + "</div>");
	}

	// Stream rendered messages as they're received.  Inspired by
	// https://github.com/niutech/phooos and the articles it references.
	@GetMapping("/getStream")
	public Flux<String> getStream() {
		// Safari and Firefox appear to require a minimum number of bytes
		// before they start rendering, so add that to the beginning:
		var paddedHeader = Flux.just(
			new String(new char[1024]).replace("\0", " ") +
			"<div>header</div>"
		);

		var renderedMessages = messageService.getStream().map(message ->
			message.isEmpty() ? "" : "<div>" + message.getId() + "</div>"
		);

		return paddedHeader.concatWith(renderedMessages);
	}
}
