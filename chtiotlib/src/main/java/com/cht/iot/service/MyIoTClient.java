package com.cht.iot.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cht.iot.persistence.entity.api.IDevice;
import com.cht.iot.persistence.entity.api.ISensor;
import com.cht.iot.persistence.entity.data.Rawdata;
import com.cht.iot.service.api.OpenMqttClient;
import com.cht.iot.service.api.OpenMulticastClient;
import com.cht.iot.service.api.OpenRESTfulClient;
import com.cht.iot.util.JsonUtils;
import com.cht.util.FileBlockingQueue;

/*
 * OpenIoTClient 同時將 RESTful, MQTT 與 Multicast 三個 clients 包裝在一起，可因網路狀態，組合使用。
 * Author: YuCheng Wang
 * send/receive rawdata
 * 需求：
 * 1. 你可以寫一個 KarafutoClient 同時將 RESTful, MQTT 與 Multicast 三個 clients 包裝
 *    在一起，可因網路狀態，組合使用。
 * 2. 當 Internet 不通的時候，KarafutoClient.saveRawdata() 除了透過 multicast 將訊息廣
 *    播出去外，另外也需要暫存起來，等 Internet 恢復之後再一次用 RESTful 將 Rawdata 向上拋，
 *    這邊你會需要用到 file based queue，因為記憶體會爆炸，請參考此類別 FileBlockingQueue：
 *    https://github.com/YunYenWang/utils
 */

public class MyIoTClient {
	static final Logger LOG = LoggerFactory.getLogger(MyIoTClient.class);
	
	private OpenRESTfulClient restful;
	private OpenMqttClient mqtt;
	private OpenMulticastClient multicast;
	
	private String host = "iot.cht.com.tw";
	
	private String apiKey; // CHANGE TO YOUR PROJECT API KEY
	private String multicastAddress = "224.144.77.1";
	private int restfulPort = 80;
	private int mqttPort = 1883;
	private int multicastPort = 8883;
	
	final int keepAliveInterval = 10;
	
	List<String> topics = Collections.emptyList();
	
	final String pingHost = "211.20.181.194";
	final int pingPort = 80;
	
	static final DateFormat DF = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
	
	Listener listener = new ListenerAdapter();
	
	private Boolean isKeepTemporarilyData = false;  //is keep Temporarily data
	private FileBlockingQueue queue;  // using for caching the data of multicast service
	private File queuePath = null;
	final String queuePrefix = "chtiot";
	
	Thread networkObserver;
	Boolean isConnectedInternet;

	public MyIoTClient(){
		
	}
	
	public MyIoTClient(String host, int port, String apiKey) {
		this.host = host;
		this.restfulPort = port;
		this.apiKey = apiKey;
	}
	
	// Both RESTful and mqtt services
	public MyIoTClient(String host, int port, int mqttPort, String apiKey) {
		this.host = host;
		this.restfulPort = port;
		this.mqttPort = mqttPort;
		this.apiKey = apiKey;
	}
	
	// Both RESTful and multicast services
	public MyIoTClient(String host, int port, String multicastAddress, int multicastPort, String apiKey) {
		this.host = host;
		this.restfulPort = port;
		this.multicastAddress = multicastAddress;
		this.multicastPort = multicastPort;
		this.apiKey = apiKey;
	}
	
	public MyIoTClient(String host, int port, int mqttPort, String multicastAddress, int multicastPort, String apiKey) {
		this.host = host;
		this.restfulPort = port;
		this.mqttPort = mqttPort;
		this.multicastAddress = multicastAddress;
		this.multicastPort = multicastPort;
		this.apiKey = apiKey;
	}
	
	public void setMqttPort(int mqttPort) {
		this.mqttPort = mqttPort;
	}
	
	public void setMulticastConfiguration(String multicastAddress, int multicastPort){
		this.multicastAddress = multicastAddress;
		this.multicastPort = multicastPort;
	}
	
	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}
	
	public void setIsKeepTemporarilyData(Boolean isKeepTemporarilyData) {
		this.isKeepTemporarilyData = isKeepTemporarilyData;
	}
	
	public void setQueuePath(File queuePath) {
		this.queuePath = queuePath;
	}
	
	public void init(){
		//restful part
		restful = new OpenRESTfulClient(host, restfulPort, apiKey); // save or query the value
		
		//mqtt part
		mqtt = new OpenMqttClient(host, mqttPort, apiKey); // MQTT to listen the value changed
		mqtt.setKeepAliveInterval(keepAliveInterval);
		
		//multicast part
		multicast = new OpenMulticastClient(multicastAddress, multicastPort);
		
		//fileBlockingQueue
		if(queuePath == null){
			queuePath = new File(System.getProperty("user.dir"));
		}
		try {
			queue = new FileBlockingQueue(queuePath, queuePrefix);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			LOG.warn("FileBlockingQueue Exception: "+e);
		}
	}
	
	public static final String getRawdataTopic(String deviceId, String sensorId) {
		return OpenMqttClient.getRawdataTopic(deviceId, sensorId);
	}
	
	public synchronized void setTopics(List<String> topics) {
		// mqtt part
		mqtt.setTopics(topics);
		
		// multicast part
		HashSet<String> multicastSet = new HashSet<String>(topics);

		multicast.setSubscribeTopics(multicastSet);
	}
	
	/**
	 * Set the listener to read the incoming events.
	 * 
	 * @param listener
	 */
	public void setListener(Listener listener) {
		this.listener = listener;
	}
	
	/**
	 * MQTT Registry Part : reconfigure, setDeviceId
	 * mqtt的username與password要設為null,故apiKey要為null
	 * @throws IOException
	 */
	public synchronized void mqttReconfigure() throws IOException {
		// set mqtt listener
		mqtt.setListener(new OpenMqttClient.ListenerAdapter() {			
			@Override
			public void onReconfigure(String topic, String apiKey) {
				LOG.info("OpenIoTClient MQTT onReconfigure:"+topic);
				listener.onReconfigure(topic, apiKey);		
			}
		});
		mqtt.start(); // wait for incoming message from IoT platform
	}
	
	public synchronized void mqttSetDeviceId() throws IOException {
		// set mqtt listener
		mqtt.setListener(new OpenMqttClient.ListenerAdapter() {			
			@Override
			public void onSetDeviceId(String topic, String apiKey, String deviceId) {
				LOG.info("OpenIoTClient MQTT onSetDeviceId:"+topic);
				listener.onSetDeviceId(topic, apiKey, deviceId);
			}
		});
		mqtt.start(); // wait for incoming message from IoT platform
	}
	
	/**
	 * Start the Multicast and Mqtt connection. 
	 * mqtt need set username and password by apikey
	 * @throws IOException 
	 */
	public synchronized void start() throws IOException {
		isConnectedInternet = hasInternet();
		
		//start networkObserver
		networkObserver = new Thread(new Runnable() {
			@Override
			public void run() {
				observing();
			}
		}, "iot-observer");
		networkObserver.start();
		
		// set mqtt listener
		mqtt.setListener(new OpenMqttClient.ListenerAdapter() {			
			@Override
			public void onRawdata(String topic, Rawdata rawdata) {
				LOG.info("OpenIoTClient MQTT onRawdata:"+topic);
				listener.onRawdata(topic, rawdata);		
			}
		});
		mqtt.start(); // wait for incoming message from IoT platform
		
		// set multicast listener
		multicast.setListener(new OpenMulticastClient.Listener() {			
			@Override
			public void onRawdata(String topic, Rawdata rawdata) {
				LOG.info("OpenIoTClient Multicast onRawdata:"+topic);
				listener.onRawdata(topic, rawdata);				
			}
		});		
		multicast.start();
	}
	
	public void stop() throws IOException {
		// stop mqtt
		mqtt.stop();
		
		// stop multicast
		multicast.stop();
		
		// stop networkObserver		
		Thread thr = networkObserver;
		networkObserver = null;
		thr.interrupt();
		
		// close FileBlockingQueue
		try {
			queue.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			LOG.warn("FileBlockingQueue close catach Exception:" + e);
		}
	}
	
	/**
	 * RESTful Part
	 */
	// device
	public IDevice saveDevice(IDevice dev) throws IOException {
		if(!hasInternet()){   //當 Internet 不通
			LOG.warn("Unable to connect internet!");
			throw new IOException("Unable to connect internet!");
		}else{
			return restful.saveDevice(dev);
		}
	}
	
	public <T> IDevice saveDevice(IDevice dev, Class<T> clazz) throws IOException {
		if(!hasInternet()){   //當 Internet 不通
			LOG.warn("Unable to connect internet!");
			throw new IOException("Unable to connect internet!");
		}else{
			return restful.saveDevice(dev, clazz);
		}
	}
	
	public IDevice modifyDevice(IDevice dev) throws IOException {
		if(!hasInternet()){   //當 Internet 不通
			LOG.warn("Unable to connect internet!");
			throw new IOException("Unable to connect internet!");
		}else{
			return restful.modifyDevice(dev);
		}
	}
	
	public IDevice getDevice(String deviceId) throws IOException {
		if(!hasInternet()){   //當 Internet 不通
			LOG.warn("Unable to connect internet!");
			throw new IOException("Unable to connect internet!");
		}else{
			return restful.getDevice(deviceId);
		}
	}
	
	public IDevice[] getDevices() throws IOException {
		if(!hasInternet()){   //當 Internet 不通
			LOG.warn("Unable to connect internet!");
			throw new IOException("Unable to connect internet!");
		}else{
			return restful.getDevices();
		}
	}
	
	public void deleteDevice(String deviceId) throws IOException {
		if(!hasInternet()){   //當 Internet 不通
			LOG.warn("Unable to connect internet!");
			throw new IOException("Unable to connect internet!");
		}else{
			restful.deleteDevice(deviceId);
		}
	}
	
	//sensor
	public ISensor saveSensor(String deviceId, ISensor sensor) throws IOException {
		if(!hasInternet()){   //當 Internet 不通
			LOG.warn("Unable to connect internet!");
			throw new IOException("Unable to connect internet!");
		}else{
			return restful.saveSensor(deviceId, sensor);
		}
	}
	
	public ISensor modifySensor(String deviceId, ISensor sensor) throws IOException {
		if(!hasInternet()){   //當 Internet 不通
			LOG.warn("Unable to connect internet!");
			throw new IOException("Unable to connect internet!");
		}else{
			return restful.modifySensor(deviceId, sensor);
		}
	}
	
	public ISensor getSensor(String deviceId, String sensorId) throws IOException {
		if(!hasInternet()){   //當 Internet 不通
			LOG.warn("Unable to connect internet!");
			throw new IOException("Unable to connect internet!");
		}else{
			return restful.getSensor(deviceId, sensorId);
		}
	}
	
	public ISensor[] getSensors(String deviceId) throws IOException {
		if(!hasInternet()){   //當 Internet 不通
			LOG.warn("Unable to connect internet!");
			throw new IOException("Unable to connect internet!");
		}else{
			return restful.getSensors(deviceId);
		}
	}
	
	public void deleteSensor(String deviceId, String sensorId) throws IOException {
		if(!hasInternet()){   //當 Internet 不通
			LOG.warn("Unable to connect internet!");
			throw new IOException("Unable to connect internet!");
		}else{
			restful.deleteSensor(deviceId, sensorId);
		}
	}
	
	// rawdata
	/**
	 * 
	 * @param deviceId
	 * @param sensorId
	 * @param time
	 * @param lat
	 * @param lon
	 * @param value
	 * @throws IOException
	 * @throws InterruptedException
	 */
	// 先從 saveRawdata 開始著手
	public void saveRawdata(String deviceId, String sensorId, String time, Float lat, Float lon, String[] value) throws IOException , InterruptedException {
		if(!hasInternet()){   //當 Internet 不通
			LOG.info("Unable to connect internet!");
			multicast.saveRawdata(deviceId, sensorId, time, lat, lon, value);
			// multicast handle to RESTful (/FileBlockingQueue)
			// to do (reference:https://github.com/YunYenWang/utils)
			if(isKeepTemporarilyData){
				putQueue(deviceId, sensorId, time, lat, lon, value);
			}
		}else{
			LOG.info("Internet connected!");
			restful.saveRawdata(deviceId, sensorId, time, lat, lon, value);
			if(isKeepTemporarilyData){
				takeQueue();
			}
		}
	}
	
	public void saveRawdata(String deviceId, String sensorId, String value) throws IOException, InterruptedException {
		saveRawdata(deviceId, sensorId, null, null, null, new String[] { value });
	}
	
	public void saveRawdata(String deviceId, String sensorId, String[] value) throws IOException, InterruptedException {
		saveRawdata(deviceId, sensorId, null, null, null, value);
	}
	
	public Rawdata getRawdata(String deviceId, String sensorId) throws IOException {
		if(!hasInternet()){   //當 Internet 不通
			LOG.warn("Unable to connect internet!");
			throw new IOException("Unable to connect internet!");
		}else{
			return restful.getRawdata(deviceId, sensorId);
		}
	}
	
	public Rawdata[] getRawdatas(String deviceId, String sensorId, String start, String end, Integer interval) throws IOException {
		if(!hasInternet()){   //當 Internet 不通
			LOG.warn("Unable to connect internet!");
			throw new IOException("Unable to connect internet!");
		}else{
			return restful.getRawdatas(deviceId, sensorId, start, end, interval);
		}
	}
	
	public void deleteRawdata(String deviceId, String sensorId, String start, String end) throws IOException {
		if(!hasInternet()){   //當 Internet 不通
			LOG.warn("Unable to connect internet!");
			throw new IOException("Unable to connect internet!");
		}else{
			restful.deleteRawdata(deviceId, sensorId, start, end);
		}
	}
	
	//snapshot
	public void saveSnapshot(String deviceId, String sensorId, String time, Float lat, Float lon, String[] value, String imageName, String imageType, InputStream imageBody) throws IOException {
		if(!hasInternet()){   //當 Internet 不通
			LOG.warn("Unable to connect internet!");
			throw new IOException("Unable to connect internet!");
		}else{
			restful.saveSnapshot(deviceId, sensorId, time, lat, lon, value, imageName, imageType, imageBody);
		}
	}
	
	public Rawdata getSnapshotMeta(String deviceId, String sensorId) throws IOException {
		if(!hasInternet()){   //當 Internet 不通
			LOG.warn("Unable to connect internet!");
			throw new IOException("Unable to connect internet!");
		}else{
			return restful.getSnapshotMeta(deviceId, sensorId);
		}
	}
	
	public Rawdata[] getSnapshotMetas(String deviceId, String sensorId, String start, String end) throws IOException {
		if(!hasInternet()){   //當 Internet 不通
			LOG.warn("Unable to connect internet!");
			throw new IOException("Unable to connect internet!");
		}else{
			return restful.getSnapshotMetas(deviceId, sensorId, start, end);
		}
	}
	
	public InputStream getSnapshotBody(String deviceId, String sensorId) throws IOException {
		if(!hasInternet()){   //當 Internet 不通
			LOG.warn("Unable to connect internet!");
			throw new IOException("Unable to connect internet!");
		}else{
			return restful.getSnapshotBody(deviceId, sensorId);
		}
	}
	
	public InputStream getSnapshotBody(String deviceId, String sensorId, String imageId) throws IOException {
		if(!hasInternet()){   //當 Internet 不通
			LOG.warn("Unable to connect internet!");
			throw new IOException("Unable to connect internet!");
		}else{
			return restful.getSnapshotBody(deviceId, sensorId, imageId);
		}
	}
	
	public void deleteSnapshot(String deviceId, String sensorId, String start, String end) throws IOException {
		if(!hasInternet()){   //當 Internet 不通
			LOG.warn("Unable to connect internet!");
			throw new IOException("Unable to connect internet!");
		}else{
			restful.deleteSnapshot(deviceId, sensorId, start, end);
		}
	}
	
	//registry
	public void reconfigure(String serialId, String digest) throws IOException {
		if(!hasInternet()){   //當 Internet 不通
			LOG.warn("Unable to connect internet!");
			throw new IOException("Unable to connect internet!");
		}else{
			restful.reconfigure(serialId, digest);
		}
	}
	
	public void setDeviceId(String serialId, String digest, String deviceId) throws IOException {
		if(!hasInternet()){   //當 Internet 不通
			LOG.warn("Unable to connect internet!");
			throw new IOException("Unable to connect internet!");
		}else{
			restful.setDeviceId(serialId, digest, deviceId);
		}
	}
	
	/*
	 * 處理 FileBlockingQueue
	 * 將rawdata儲存至 file queue中
	 */
	protected void putQueue(String deviceId, String sensorId, String time, Float lat, Float lon, String[] value){
		try {
			Rawdata rawdata = new Rawdata();
			rawdata.setId(sensorId);
			rawdata.setDeviceId(deviceId);
			if(time == null){
				time = setCurrentTime();
			}
			rawdata.setTime(time);
			rawdata.setLat(lat);
			rawdata.setLon(lon);
			rawdata.setValue(value);
			byte[] bytes = JsonUtils.toJson(rawdata).getBytes(StandardCharsets.UTF_8);
			queue.put(bytes);
			
		} catch  (IOException e) {
			// TODO Auto-generated catch block
			LOG.warn("FileBlockingQueue catach Exception:" + e);
		} 
	}
	
	/*
	 * 處理 FileBlockingQueue
	 * 從 file queue 中取出 rawdata (byte -> String)
	 * 再轉成rawdata後呼叫 RESTful saveRawdata
	 */
	protected void takeQueue(){
		try {
			//int count = 0;
			
			while(!queue.isEmpty()){
			//if(!queue.isEmpty()){
				byte[] bytes = queue.peek();   //takeLOG.info("FileBlockingQueue take:"+bytes.length);
				String data = new String(bytes, StandardCharsets.UTF_8);
				LOG.info("==> takeQueue:"+data);
				Rawdata rawdata = JsonUtils.fromJson(data, Rawdata.class);
				restful.saveRawdata(rawdata);
				
				queue.remove();	//delete
			}
			/*
			for (;;) {
				LOG.info("FileBlockingQueue take:"+queue.isEmpty());
				if(queue.isEmpty()){
					LOG.info("FileBlockingQueue is empty");
					break;
				}else{
					byte[] bytes = queue.take();   //take
					if (bytes == null) {
						LOG.info("FileBlockingQueue bytes is null");
						break;
					}
					LOG.info("FileBlockingQueue take:"+bytes.length);
					String data = new String(bytes, StandardCharsets.UTF_8);
					LOG.info("takeQueue:"+data);
					Rawdata rawdata = JsonUtils.fromJson(data, Rawdata.class);
					restful.saveRawdata(rawdata);
					
					queue.remove();	//delete
					
					count += 1;
				}
			}
			LOG.info(" ### update quere count: "+count);
			*/
		} catch  (IOException e) {
			// TODO Auto-generated catch block
			LOG.warn("FileBlockingQueue catach IOException:" + e);
		}/* catch (InterruptedException e) {
			// TODO Auto-generated catch block
			LOG.warn("FileBlockingQueue catach InterruptedException:" + e);
		}*/
	}
	
	protected String setCurrentTime(){
		//https://docs.oracle.com/javase/7/docs/api/java/text/SimpleDateFormat.html 
		return DF.format(new Date());
	}
	
	protected void observing() {
		
		while(networkObserver != null){
			if(!isConnectedInternet){
				//
				//LOG.info(".");
			}
		}
	}
	
	protected boolean hasInternet() {
		Boolean status = false;
		
		pingHost(pingHost, pingPort, 1500);
		/*
		try {
			OpenRESTfulClient healtClient = new OpenRESTfulClient(host, restfulPort, apiKey); // save or query the value
			healtClient.setTimeout(2000);
			Health health = healtClient.getHealth();
			if(health == null){
				LOG.warn("Can not connect to health");
			}else if(health.getStatus().equalsIgnoreCase("online")){
				status = true;
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		*/
		return status;
	}
	
	/*
	 *  ping for checking whether the internet connection
	 *  timeout is milliseconds
	 */
	protected boolean pingHost(String host, int port, int timeout) {
		LOG.info("pingHost:"+host);
		Boolean status = false;
		
		//http://www.java2s.com/Tutorial/Java/0320__Network/Pingahost.htm 
		try {
			InetAddress address = InetAddress.getByName(host);
			status = address.isReachable(timeout);
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		/*
		Socket socket = new Socket();
	    try {
	        socket.connect(new InetSocketAddress(host, port), timeout);
	        if(socket.isConnected()){
	        	status = true;
	        }
	    } catch (IOException e) {
	    	// Either timeout or unreachable or failed DNS lookup.
	    } finally {
	    	 try {
				socket.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	    }
	    */
	    return status;
	}
	
	// ...
	public static interface Listener {
		
		/**
		 * The value changed of the sensor.
		 * 
		 * @param topic
		 * @param rawdata
		 */
		public void onRawdata(String topic, Rawdata rawdata);
		
		/**
		 * The device/sensor reconfiguration event from server.
		 * 
		 * @param topic
		 * @param apiKey
		 */
		public void onReconfigure(String topic, String apiKey);
		
		/**
		 * The re-assigned device ID from server.
		 * 
		 * @param topic
		 * @param apiKey
		 * @param deviceId
		 */
		public void onSetDeviceId(String topic, String apiKey, String deviceId);
	}
	
	public static class ListenerAdapter implements Listener {
		@Override
		public void onRawdata(String topic, Rawdata rawdata) {
		}
		
		@Override
		public void onReconfigure(String topic, String apiKey) {
		}
		
		@Override
		public void onSetDeviceId(String topic, String apiKey, String deviceId) {
		}
	}
	
}