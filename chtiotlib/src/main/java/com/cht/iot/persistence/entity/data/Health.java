package com.cht.iot.persistence.entity.data;

public class Health {
	
	String time;
	String status;
	
	public Health(){
		
	}
	
	public Health(String time, String status) {
		this.time = time;
		this.status = status;
	}
	
	public void setTime(String time) {
		this.time = time;
	}
	
	public String getTime() {
		return time;
	}
	
	public void setStatus(String status) {
		this.status = status;
	}
	
	public String getStatus() {
		return status;
	}
}
