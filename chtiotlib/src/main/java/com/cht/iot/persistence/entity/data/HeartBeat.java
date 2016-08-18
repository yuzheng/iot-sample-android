package com.cht.iot.persistence.entity.data;

public class HeartBeat extends Material {
	private static final long serialVersionUID = 1L;

	String deviceId;
	String pulse;
	String from;
	String last;
	String time;
	String type;
	
	public HeartBeat(){
		
	}
	
	public HeartBeat(String pulse, String from, String last, String time, String type){
		this.pulse = pulse;
		this.from = from;
		this.last = last;
		this.time = time;
		this.type = type;
	}
	
	public String getDeviceId() {
		return deviceId;
	}
	public void setDeviceId(String deviceId) {
		this.deviceId = deviceId;
	}
	public String getPulse() {
		return pulse;
	}
	public void setPulse(String pulse) {
		this.pulse = pulse;
	}
	public String getFrom() {
		return from;
	}
	public void setFrom(String from) {
		this.from = from;
	}
	public String getLast() {
		return last;
	}
	public void setLast(String last) {
		this.last = last;
	}
	public String getTime() {
		return time;
	}
	public void setTime(String time) {
		this.time = time;
	}
	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}
	public static long getSerialversionuid() {
		return serialVersionUID;
	}
	
}
