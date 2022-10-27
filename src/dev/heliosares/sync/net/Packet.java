package dev.heliosares.sync.net;

import org.json.JSONObject;

public class Packet {
	private final String channel;
	private final int packetid;
	private final JSONObject payload;
	private byte[] blob;
	private String forward;

	public Packet(String channel, int packetid, JSONObject payload) {
		this.channel = channel;
		this.packetid = packetid;
		this.payload = payload;
	}

	public Packet(String channel, int packetid, JSONObject payload, byte[] blob) {
		this(channel, packetid, payload);
		this.blob = blob;
	}

	Packet(JSONObject packet) {
		packetid = packet.getInt("pid");
		if (packet.has("ch")) {
			channel = packet.getString("ch");
		} else {
			channel = null;
		}
		if (packet.has("pl")) {
			payload = packet.getJSONObject("pl");
		} else {
			payload = null;
		}
		if (packet.has("fw")) {
			forward = packet.getString("fw");
		}
	}

	public String getChannel() {
		return channel;
	}

	public int getPacketId() {
		return packetid;
	}

	public JSONObject getPayload() {
		return payload;
	}

	@Override
	public String toString() {
		JSONObject json = new JSONObject();
		json.put("pid", packetid);
		if (channel != null) {
			json.put("ch", channel);
		}
		if (payload != null) {
			json.put("pl", payload);
		}
		if (forward != null) {
			json.put("fw", forward);
		}
		return json.toString();
	}

	public byte[] getBlob() {
		return blob;
	}

	public void setBlob(byte[] blob) {
		this.blob = blob;
	}

	public String getForward() {
		return forward;
	}

	public void setForward(String forward) {
		this.forward = forward;
	}
}
