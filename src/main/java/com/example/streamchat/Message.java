package com.example.streamchat;

import com.fasterxml.uuid.Generators;
import java.sql.Timestamp;
import java.util.UUID;

public class Message {
	private String id;
	private String channelId;
	private String message;

	// NOTE: empty messages are filtered out
	// TODO: only allow empty message creation via a subclass for more safety?
	public Message() {
		UUID uuid = Generators.timeBasedEpochRandomGenerator().generate();
		this.id = uuid.toString();
		this.message = "";
		this.channelId = "";
	}

	public Message(String message, String channelId) {
		UUID uuid = Generators.timeBasedEpochRandomGenerator().generate();
		this.id = uuid.toString();
		this.message = message;
		this.channelId = channelId;
	}

	public Message(String message) {
		this(message, "");
	}

	public Boolean isEmpty() {
		return message.isEmpty();
	}

	public String getId() {
		return id;
	}

	public String getChannelId() {
		return channelId;
	}

	public String getMessage() {
		return message;
	}

	public Timestamp getTimestamp() {
		UUID id = UUID.fromString(this.id);
		return new Timestamp(id.getMostSignificantBits() >> (Long.SIZE - 48));
	}
}
