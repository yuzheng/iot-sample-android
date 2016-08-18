package com.cht.iot.persistence.entity.data;

public class Message {
	int from;
	String topic;
	String payload;
	
	public Message(){
		
	}
	
	public Message(int from, String topic, String payload) {
		this.from = from;
		this.topic = topic;
		this.payload = payload;
	}
	
	public int getFrom() {
		return from;
	}
	
	public void setFrom(int from) {
		this.from = from;
	}
	
	public String getTopic() {
		return topic;
	}
	
	public void setTopic(String topic) {
		this.topic = topic;
	}
	
	public String getPayload() {
		return payload;
	}
	
	public void setPayload(String payload) {
		this.payload = payload;
	}
}
