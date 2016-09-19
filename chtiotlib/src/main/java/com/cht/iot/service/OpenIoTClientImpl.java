package com.cht.iot.service;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cht.iot.client.ControllerClient;
import com.cht.iot.client.ControllerClientBuilder;
import com.cht.iot.persistence.entity.data.HeartBeat;
import com.cht.iot.persistence.entity.data.Rawdata;
import com.cht.iot.persistence.entity.data.ServiceDevice;
import com.cht.iot.persistence.entity.data.Session;
import com.cht.iot.service.api.OpenMqttClient;
import com.cht.iot.service.api.OpenRESTfulClient;

/**
 * @author YuCheng Wang
 * Controller(Arduino) <--> Clinet(App) <--> IoT Platform
 *
 * Support: Local: UDP connection, internet: MQTT, RESTful
 *
 * - link for UDP connection
 * - subscribe for mqtt
 *
 */

public class OpenIoTClientImpl implements OpenIoTClient {

	static final Logger LOG = LoggerFactory.getLogger(OpenIoTClientImpl.class);
	
	OpenMqttClient mqtt;
	OpenRESTfulClient restful;
	ControllerClientBuilder builder;
	
	String host = "iot.cht.com.tw";
	int httpPort = 80;
	int httpsPort = 443;
	int mqttPort = 1883;
	
	// Local Network
	int controllerHost;
	int announcementPort = 10400; // UDP (broadcast)
	int controllerPort = 10600; // UDP

	InetAddress sourceAddress = null;
	
	int timeout = 2000;  //Millisecond
	long keepAliveInterval = 10000L;  //Millisecond
	String projectKey; // CHANGE TO YOUR PROJECT API KEY
	
	Map<String, ServiceDevice> serviceDevices;
	Map<String, Rawdata> cacheRawdata;
	
	Callback callback = new CallbackAdapter();
	Listener listener = new OpenIoTClientListener();
	
	// use 'enum' to present the local mode, remote mode or other mode ...
	public enum Mode {
	    LOCAL, REMOTE
	} 
	
	public OpenIoTClientImpl() {
		serviceDevices = Collections.synchronizedMap(new HashMap<String, ServiceDevice>());
		cacheRawdata = Collections.synchronizedMap(new HashMap<String, Rawdata>());
	}
	
	@Override
	public void setHost(String host) {
		this.host = host;
	}

	@Override
	public void setHttpPort(int port) {
		this.httpPort = port;
	}

	@Override
	public void setHttpsPort(int port) {
		this.httpsPort = port;
	}

	@Override
	public void setMqttPort(int port) {
		this.mqttPort = port;
	}

	@Override
	public void setProjectKey(String projectKey) {
		this.projectKey = projectKey;
	}
	
	@Override
	public void setAnnouncementPort(int port) {
		this.announcementPort = port;
	}

	@Override
	public void setControllerPort(int port) {
		this.controllerPort = port;
	}

	public void setSourceAddress (InetAddress sourceAddress) {
		this.sourceAddress = sourceAddress;
	}

	/**
	 * To IoT platform & control
	 */
	@Override
	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	/**
	 * This is an interval of 'Ping' command to controller
	 */
	@Override
	public void setKeepAliveInterval(long timeout) {
		this.keepAliveInterval = timeout;
	}
	
	public void initControllerClientBuilder() {
		builder = new ControllerClientBuilder();
		try {
			builder.start();
		} catch (IOException e) {
			LOG.error(e.getMessage(), e);
		} 
	}
	
	public void initOpenMqttClient() {
		if(!StringUtils.isEmpty(projectKey)) {
			mqtt = new OpenMqttClient(host, mqttPort, projectKey); // MQTT to listen the value changed
			mqtt.setKeepAliveInterval((int) keepAliveInterval);
			mqtt.start();
		}else{
			LOG.warn("Not found ProjectKey, can not create mqtt (initOpenMqttClient).");
		}
	}
	
	public void initOpenRESTfulClient() {
		if(!StringUtils.isEmpty(projectKey)) {
			// httpsPort -> https (secure transfer)
			restful = new OpenRESTfulClient(host, httpsPort, projectKey);
			restful.setSecureTransfer(true); //using https
		}else{
			LOG.warn("Not found ProjectKey, can not create restful (initOpenRESTfulClient).");
		}
	}
	
	@Override
	public void start() {
		// 1. ControllerClientBuilder, 建立 ControllerClientBuilder, 是否有收到 controller 的 announcement
		initControllerClientBuilder();
		
		// 2. OpenMqttClient, 建立 MQTT connection，用 project key 來登入(不是每面對一個 controller 就分別建立一個，所有 controller 內的 device 都屬於同一個 project)
		initOpenMqttClient();
		
		// 3. OpenRESTfulClient
		initOpenRESTfulClient();
	}

	@Override
	public void stop() {
		// stop ControllerClientBuilder
		if(builder != null){
			builder.stop();
		}
		
		// OpenMqttClient must be destory here.
		if(mqtt != null){
			mqtt.stop();
		}

		// synchronized ControllerClient must be stop
		synchronized (serviceDevices) {
			Iterator<ServiceDevice> it = serviceDevices.values().iterator();
			while(it.hasNext()){
				ServiceDevice serviceDevice = it.next();
				try {
					serviceDevice.stop();
				} catch (IOException e) {
					LOG.error(e.getMessage(), e);
				}
				it.remove();
			}
		}
		
		synchronized (cacheRawdata) {
			cacheRawdata.clear();
		}
	}
	
	/**
	 * Get controllers in LAN.
	 * @return Session includes vendor, model, series, name, cipher & extra for user to assign device key.
	 * (cipher & extra will be retrieved when controller's connection is ready)
	 * */
	@Override
	public List<Session> getSessions() {
		return builder.getSessions();
	}
	
	/**
	 * Set the callback to read the incoming events.
	 * @param callback
	 */
	public void setCallback(Callback callback) {
		this.callback = callback;
	}
	
	/**
	 * Set the listener to read the incoming events.
	 * @param listener
	 */
	public void setListener(Listener listener) {
		this.listener = listener;
	}
	
	protected String getMapKey(String deviceId, String sensorId) {
		return deviceId+"_"+sensorId;
	}

	/**
	 * Ask OpenIoTClient to establish the connection to controller in background.
	 * After connecting to controller, the background thread will send the 'Ping' to keep the udp connection.
	 *  @param session
	 *  @param deviceKey    	 device key
	 *  @param deviceId      keep the deviceId/sessionId -> Session, you'll know how to send the rawdata later.
	 *  @param sensorIds
	 */
	//不用去區分 local mode 或 remote mode，當使用者用 link() 關聯設備之後，預設就是透過 ControllerClient 發送 UDP 指令。可以從 serviceDevices 內是否找得到物件來判斷有沒有 link() 過。
	@Override
	public void link(final Session session, final String deviceKey, String deviceId, String[] sensorIds) {
		LOG.info("link session: "+session);
		boolean islinked = false;
		ServiceDevice serviceDevice = serviceDevices.get(deviceId);
		if( serviceDevice != null) {
			ControllerClient client = serviceDevice.getClient();
			if(client != null && client.isAuthenticated()) {
				islinked = true;
			}
		}else{
			serviceDevice = new ServiceDevice(deviceKey, deviceId, sensorIds);
		}

		if(!islinked) {
			ControllerClient client = null;
			try {
				client = builder.build(session);
				client.setApiKey(deviceKey);
				client.setKeepalive(keepAliveInterval);
				client.setTimeout(timeout);
				client.setListener(new ControllerClient.Listener() {
					public void onValueChanged(String deviceId, String sensorId, String[] value) {
						LOG.info("Client - device: {}, sensor: {}, value: {}", deviceId, sensorId, Arrays.toString(value));
						Rawdata rawdata = new Rawdata(sensorId, deviceId, null, value);
						// cache data
						cacheRawdata.put(getMapKey(rawdata.getDeviceId(),rawdata.getId()),rawdata);
						callback.onRawdata(rawdata);
					}

					public void onLinkStatusChanged(String communication, String status, String message) {
						// TODO Auto-generated method stub
						LOG.info("link status : " + communication + ":" + status + "[" + message +"]");
						if(status.equals(ControllerClient.STATUS_FAIL)){
							//listener.didNotConnectToController(communication);
						}else if(status.equals(ControllerClient.STATUS_DISCONNECT)){
							listener.didNotConnectToController(Mode.LOCAL, communication);
						}else if(status.equals(ControllerClient.STATUS_CONNECT)){
							listener.didConnectToController();
						}
					}
				});
				if(sourceAddress != null) {
					client.setSourceAddress(sourceAddress);
				}
				client.start();
				serviceDevice.setClient(client);
			} catch (IOException e) {
				LOG.error(e.getMessage(), e);
			}
		}else{
			LOG.info("Device {} already linked!", deviceId);
		}

		serviceDevices.put(deviceId, serviceDevice);
	}

	@Override
	public void saveRawdata(String deviceId, String sensorId, String[] value) {
		LOG.info("saveRawdata:");
		ServiceDevice serviceDevice = serviceDevices.get(deviceId);
		if(serviceDevice != null){  // just checj the returned instance from serviceDevices.get(deviceId) is null or not
			ControllerClient client = serviceDevice.getClient();
			if(client != null && client.isAuthenticated()) {
				try {
					client.write(deviceId, sensorId, value);
				} catch (IOException e) {
					LOG.error(e.getMessage(), e);
				}
			}else{
				mqtt.save(deviceId, sensorId, value); // change the rawdata
			}
		}else{
			mqtt.save(deviceId, sensorId, value); // change the rawdata
		}
	}

	//getRawdata() 有 3 種方式：1) 用 RESTful 向平台要，2) MQTT 通知的時候 cache 起來，3) controller 用 write 指令通知的時候 cache 起來。
	@Override
	public Rawdata getRawdata(String deviceId, String sensorId) {
		String cacheKey = getMapKey(deviceId, sensorId);
		Rawdata rawdata = cacheRawdata.get(cacheKey);  // get data from cache

		if(rawdata == null){	// check rawdata is null or not
			//using Restful
			try {
				if(!StringUtils.isEmpty(projectKey)){
					rawdata = restful.getRawdata(deviceId, sensorId);
					callback.onRawdata(rawdata);
				}else {
					LOG.warn("Not found ProjectKey, can not create mqtt (initOpenMqttClient).");
				}
			} catch (IOException e) {
				LOG.error(e.getMessage(), e);
			}
		}
		return rawdata;
	}

	//subscribe() 只有針對 MQTT 來處理，原本建立 ControllerClient 的時候就已經設定好 callback 了。
	@Override
	public void subscribe(String deviceId, String sensorId) {
		LOG.info("subscribe: {} {}",deviceId, sensorId);
		mqtt.subscribe(deviceId, sensorId);
		mqtt.setListener(new OpenMqttClient.ListenerAdapter() {
			@Override
			public void onRawdata(String topic, Rawdata rawdata) {
				System.out.printf("[MQTT] Rawdata - deviceId: %s, id: %s, time: %s, value: %s\n", rawdata.getDeviceId(), rawdata.getId(), rawdata.getTime(), rawdata.getValue()[0]);     
				String cacheKey = getMapKey(rawdata.getDeviceId(), rawdata.getId());
				cacheRawdata.put(cacheKey,rawdata);
				callback.onRawdata(rawdata);
	        }
	    });
	}
	
	public void subscribeHeartBeat(String deviceId) {
		LOG.info("subscribe HeartBeat: {}",deviceId);
		mqtt.subscribeHeartbeat(deviceId);
		mqtt.setListener(new OpenMqttClient.ListenerAdapter() {
			@Override
			public void onHeartBeat(String topic, HeartBeat heartbeat) {
				System.out.printf("HeartBeat - deviceId: %s, pulse: %s, from: %s, time: %s, type: %s\n", heartbeat.getDeviceId(), heartbeat.getPulse(), heartbeat.getFrom(), heartbeat.getTime(), heartbeat.getType());				     
				callback.onHeartBeat(heartbeat);
	        }
	    });
	}
	
	// --
	public static class CallbackAdapter implements Callback {
		@Override
		public void onRawdata(Rawdata rawdata) {
		}
		
		@Override
		public void onHeartBeat(HeartBeat heartbeat) {
		}
	}
	
	// --
	public static interface Listener {
		/**
		 * Controller connect or disconnect status
		 * 
		 */
		void didConnectToController();
		void didNotConnectToController(Mode mode, String info);
		
		
	}

	public static class OpenIoTClientListener implements Listener {
		
		public OpenIoTClientListener() {
			
		}
		
		public void didConnectToController() {
			
		}
		
		public void didNotConnectToController(Mode mode, String info) {
			
		}
	}
}
