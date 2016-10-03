package com.cht.iot.persistence.entity.data;

public class Rawdata extends Material {
	private static final long serialVersionUID = 1L;
	
	String id;
	String deviceId;
	String time;
	Float lat;
	Float lon;
	String save;
	String[] value;

	public Rawdata() {
	}

	public Rawdata(String id, String deviceId, String time, String[] value) {
		this.id = id;
		this.deviceId = deviceId;
		this.time = time;
		this.value = value;
	}

	/**
	 * Sensor ID.
	 * 
	 * @return
	 */
	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}
	
	/**
	 * Device ID.
	 * 
	 * @return
	 */
	public String getDeviceId() {
		return deviceId;
	}
	
	public void setDeviceId(String deviceId) {
		this.deviceId = deviceId;
	}

	/**
	 * Timestamp (UTC). It's ISO-8601 format.
	 * 
	 * @return
	 */
	public String getTime() {
		return time;
	}

	public void setTime(String time) {
		this.time = time;
	}

	/**
	 * [optional] Latitude.
	 * 
	 * @return
	 */
	public Float getLat() {
		return lat;
	}

	public void setLat(Float lat) {
		this.lat = lat;
	}

	/**
	 * [optional] Longitude.
	 * 
	 * @return
	 */
	public Float getLon() {
		return lon;
	}

	public void setLon(Float lon) {
		this.lon = lon;
	}

	/**
	 * [optional] save.
	 *
	 * @return
	 */
	public String getSave() {
		return save;
	}

	public void setSave(String save) {
		this.save = save;
	}

	/**
	 * Rawdata value with string array expression.
	 * 
	 * @return
	 */
	public String[] getValue() {
		return value;
	}

	public void setValue(String[] value) {
		this.value = value;
	}
}
