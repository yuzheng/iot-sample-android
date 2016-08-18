package com.cht.iot.persistence.entity.data;

import com.cht.iot.client.ControllerClient;
//import com.cht.iot.service.api.OpenMqttClient;
//import com.cht.iot.service.api.OpenRESTfulClient;

import java.io.IOException;

public class ServiceDevice {

	private String deviceKey;
	private String deviceId;
	private String[] sensorIds;

	//private OpenRESTfulClient restful;
	//private OpenMqttClient mqtt;
	private ControllerClient client;

	public ServiceDevice() {

	}

	public ServiceDevice(String deviceKey, String deviceId, String[] sensorIds) {
		this.deviceKey = deviceKey;
		this.deviceId = deviceId;
		this.sensorIds = sensorIds;
	}

	public String getDeviceKey() {
		return deviceKey;
	}
	public void setDeviceKey(String deviceKey) {
		this.deviceKey = deviceKey;
	}
	public String getDeviceId() {
		return deviceId;
	}
	public void setDeviceId(String deviceId) {
		this.deviceId = deviceId;
	}
	public String[] getSensorIds() {
		return sensorIds;
	}
	public void setSensorIds(String[] sensorIds) {
		this.sensorIds = sensorIds;
	}
	/*
	public OpenRESTfulClient getRestful() {
		return restful;
	}
	public void setRestful(OpenRESTfulClient restful) {
		this.restful = restful;
	}
	public OpenMqttClient getMqtt() {
		return mqtt;
	}
	public void setMqtt(OpenMqttClient mqtt) {
		this.mqtt = mqtt;
	}
	*/
	public ControllerClient getClient() {
		return client;
	}
	public void setClient(ControllerClient client) {
		this.client = client;
	}

	public void stop() throws IOException {
		// stop mqtt
		//mqtt.stop();

		// stop ControllerClient
		client.stop();
	}
}
