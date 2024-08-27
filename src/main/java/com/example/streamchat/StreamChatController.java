package com.example.streamchat;

import com.fasterxml.uuid.Generators;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import java.util.HashMap;
import java.util.TreeMap;

@RestController
public class StreamChatController {
	// These must be of Object until I find a way to cast from within the
	// .create() lambda.
	private FluxSink<Object> qSink;
	private Flux<Object> q = Flux.create(sink -> { sink.next(new Message()); qSink = sink; }).share();

	// These must be of String since they need to handle arbitrary HTML.
	private HashMap<String, FluxSink<String>> streamSinks = new HashMap<>();

	// temporary, before real DB
	private TreeMap<String, Message> db = new TreeMap<>((a, b) -> a == null ? 1 : a.compareTo(b) * -1);

	public StreamChatController() {
		// Ensure that qSink is set before anything needs it.
		q.blockFirst();

		// As Messages become available from q, add them to db.
		q.subscribe(m -> {
			var message = (Message)m;
			db.put(message.getId(), message);
		});
	}

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
						<iframe src="/stream"></iframe>
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

	@RequestMapping(
		value="/submit",
		method = RequestMethod.POST,
		consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
	public String postSubmit(@RequestBody MultiValueMap<String, String> formData) {
		var message = new Message(formData.get("message").getFirst());
		qSink.next(message);
		return submitTemplate;
	}

	@RequestMapping(value = "/pageBefore", produces = "image/svg")
	public String pageBefore(
		@RequestParam String streamId,
		@RequestParam String messageId,
		@RequestParam int flexOrder
	) {
		// exclude the given key from the results
		var newId = db.containsKey(messageId) ? db.higherKey(messageId) : messageId;

		var sink = streamSinks.get(streamId);
		var messages = db.tailMap(newId).sequencedValues().stream().limit(20).toList().reversed();
		if (!messages.isEmpty()) {
			var firstId = messages.getFirst().getId();
			sink.next(String.format("""
				<img src="/pageBefore?streamId=%s&messageId=%s&flexOrder=%d">
			""", streamId, firstId, flexOrder - 1));
		} else {
			sink.next(String.format("""
				<div class="page" style="order:%d;">(start)</div>
			""", flexOrder));
		}
		sink.next(String.format("""
			<div class="page" style="order:%d;">
		""", flexOrder));
		messages.forEach(message -> {
			sink.next("<div>" + message.getMessage() + "</div>");
		});
		sink.next("</div>");

		// TODO: valid SVG of negligible (ideally zero) visual impact while
		// still causing a GET
		return "<svg></svg>";
	}

	// NOTE: There's currently a resource leak here.  Elements should be
	// removed from streamSinks as the connection to the browser is severed.
	// Unfortunately, I haven't found a good way to detect when this happens.
	// Writing a non-empty string to a closed streamSink will cause it to fail,
	// and this can be set up to happen periodically by merging the return with
	// `Flux.interval(Duration.ofSeconds(10)).map(x -> "\n")`.  Unfortunately,
	// I'm not yet well versed enough in Reactive Spring Boot to know how to
	// catch that exception when it happens.
	// TODO: experiment with ways to at least invoke a periodic cleanup
	// e.g. merge with Flux.interval() that writes whitespace and updates a
	// counter, and if the cleanup task hasn't seen sufficient counter update
	// since last pass, it can remove the item from the map
	@GetMapping("/stream")
	public Flux<String> stream() {
		var uuid = Generators.timeBasedEpochRandomGenerator().generate().toString();
		return Flux.create(sink -> {
			streamSinks.put(uuid, sink);
			sink.next("""
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
						.messageContainer {
							display: flex;
							flex-direction: column;
						}
					</style>
				</head>
				<body>
					<div class="messageContainer">
			""" + String.format("""
				<img src="/pageBefore?streamId=%s&messageId=%s&flexOrder=-1">
			""", uuid, uuid));
			q.subscribe(x -> sink.next("<div>" + x.toString() + "</div>"));
		});
	}
}
