package com.cht.iot.client;

public interface ControllerListener {	
	
	/**
	 * Return the sensor's value.
	 * 
	 * @param deviceId
	 * @param sensorId
	 * @return
	 */
	String[] readValue(String deviceId, String sensorId);
	
	/**
	 * Update the sensor's value.
	 * 
	 * @param deviceId
	 * @param sensorId
	 * @param value
	 */	
	void writeValue(String deviceId, String sensorId, String[] value);
}
