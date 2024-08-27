package com.example.streamchat;

import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
public class StreamChatController {
	private MessageService messageService = new MessageService();

	// TODO: use a real templating engine
	@GetMapping("/")
	public String getIndex() {
		return """
			<!DOCTYPE html>
			<html>
			<head>
				<title>Stream Chat</title>
				<style>
					html, body {
						margin: 0;
						height: 100%;
						overflow: hidden;
					}
					main {
						display: grid;
						margin: .5em;
						height: calc(100% - 1em);
						grid-template-rows: auto min-content;
						iframe {
							width: 100%;
							height: 100%;
							border: none;
						}
						#channel {
							height: 100%;
						}
						#submit {
							height: 2.5em;
						}
					}
				</style>
			</head>
			<body>
				<main>
					<section id="channel">
						<iframe src="/getStream"></iframe>
					</section>
					<section id="submit">
						<iframe src="/submit"></iframe>
					</section>
				</main>
			</body>
			</html>
		""";
	}

	private String submitTemplate = """
		<!DOCTYPE html>
		<html>
		<head>
			<style>
				html, body {
					margin: 0;
					overflow: hidden;
				}
				form {
					margin: 0.5em;
					display: grid;
					grid-template-columns: auto min-content;
				}
			</style>
		</head>
		<body>
			<form action="/submit" method="post">
				<input type="textarea" name="message" autofocus></input>
				<input type="submit" value="Send"></input>
			</form>
		</body>
		</html>
	""";

	@GetMapping("/submit")
	public String getSubmit() {
		return submitTemplate;
	}

	@RequestMapping(value="/submit", method = RequestMethod.POST,
		consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
	public String postSubmit(@RequestBody MultiValueMap<String, String> formData) throws InterruptedException {
		var message = formData.get("message").getFirst();
		messageService.addMessage(new Message(message));
		return submitTemplate;
	}

	@GetMapping("/add")
	public String add() throws InterruptedException {
		// TODO: actually get message from input
		messageService.addMessage(new Message("asdf"));
		return "this should be a form submission template?";
	}

	private String renderMessage(Message message) {
		return message.isEmpty() ?
			"" :
			"<div>" + message.getId() + ": " + message.getMessage() + "</div>";
	}

	@GetMapping("/getPageBefore/{id}")
	public Flux<String> getPageBefore(@PathVariable("id") String id) {
		var messages = messageService.getPageBefore(id);
		var renderedMessages = messages.map(this::renderMessage);
		var oldest = messages.blockFirst();
		var prefix = Flux.just("""
			<!DOCTYPE html>
			<html>
			<head>
				<style>
					html, body, iframe {
						border: none;
						width: 100%;
						height: fit-content;
						//overflow: hidden;
						margin: 0;
					}
				</style>
			</head>
			<body>
		""");
		if (oldest != null)
			prefix = prefix.concatWith(Flux.just("<iframe load=\"lazy\" src=\"/getPageBefore/" + oldest.getId() + "\"></iframe>"));
		var postfix = Flux.just("</body></html>");
		return prefix.concatWith(renderedMessages).concatWith(postfix);
	}

	// Stream rendered messages as they're received.  Inspired by
	// https://github.com/niutech/phooos and the articles it references.
	@GetMapping("/getStream")
	public Flux<String> getStream() {
		var now = new Message();
		var previous = getPageBefore(now.getId());
		var prefix = Flux.just("""
			<!DOCTYPE html>
			<html>
			<head>
				<style>
					html, body, iframe {
						border: none;
						width: 100%;
						height: fit-content;
						margin: 0;
					}
				</style>
			</head>
			<body>
		""");
		var renderedStream = messageService.getStream().map(this::renderMessage);
		var postFix = Flux.just("</body></html>");
		return prefix.concatWith(previous).concatWith(renderedStream).concatWith(postFix);
	}
}
