package com.cht.iot.persistence.entity.data;

public class Session {
	final String vendor;
	final String model;
	final String series;
	final String name;

	final String id;
	final long birthday;

	public String host;
	public int port;

	public Session(String vendor, String model, String series, String name) {
		this.vendor = vendor;
		this.model = model;
		this.series = series;
		this.name = name;

		id = String.format("%s/%s/%s/%s", vendor, model, series, name);
		birthday = System.currentTimeMillis();
	}

	public String getVendor(){
		return vendor;
	}

	public String getModel(){
		return model;
	}

	public String getSeries(){
		return series;
	}

	public String getName(){
		return name;
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Session) {
			Session s = (Session) obj;
			return id.equals(s.id);
		}

		return false;
	}

	@Override
	public String toString() {
		return id;
	}
}
