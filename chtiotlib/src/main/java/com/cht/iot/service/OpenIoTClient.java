package com.cht.iot.service;

import java.util.List;

import com.cht.iot.persistence.entity.data.HeartBeat;
import com.cht.iot.persistence.entity.data.Rawdata;
import com.cht.iot.persistence.entity.data.Session;

public interface OpenIoTClient {
	// ======
	   
    public void setHost(String host);
 
    public void setHttpPort(int port);
   
    public void setHttpsPort(int port);
   
    public void setMqttPort(int port);
   
    /**
    * This is a account key or project key
    *
     * @param apiKey
    */
    public void setProjectKey(String projectKey);
   
    /**
    * UDP/10400 to listen
    *
     * @param port
    */
    public void setAnnouncementPort(int port);
   
    /**
    * TCP/10400 to connect
    *
     * @param port
    */
    public void setControllerPort(int port);  
   
    /**
    * To IoT platform & controller.
    *
     * @param timeout
    */
    public void setTimeout(int timeout);
   
    /**
    * This is an interval of 'Ping' command to controller.
    *
     * @param timeout
    */
    public void setKeepAliveInterval(long timeout);
   
    // ======
   
    public void start();
   
    public void stop();
   
    // ======
   
    /**
    * Get controllers in LAN.
    *
     * @return Session includes vendor, model, series, name, cipher & extra for user to assign device key.
    *
     * (cipher & extra will be retrieved when controller's connection is ready)
    *
     *
     */
    public List<Session> getSessions();
   
    /**
    * Ask OpenIoTClient to establish the connection to controller in background.
    *
     * After connecting to controller, the background thread will send the 'Ping' to keep the tcp connection.
    *
     * @param session
    * @param apiKey     device key
    * @param deviceId       keep the deviceId/sessionId -> Session, you'll know how to send the rawdata later.
    * @param sensorIds
    */
    public void link(Session session, String apiKey, String deviceId, String[] sensorIds);
   
    // ======
   
    /*
    * By IoT platform only.
    *
     * saveDevice(), modifyDevice(), getDevice(), getDevices(), deleteDevice()
    * saveSensor(), modifySensor(), getSensor(), getSensors(), deleteSensor()
    *
     */
   
    /**
    * Save the rawdata to IoT platform and local controller.
    *
     * Based on deviceId/sessionId -> Session which defined by link(), you will select the ControllerClient to send the 'write' command.
    *
     * @param deviceId
    * @param sensorId
    * @param value
    */
    public void saveRawdata(String deviceId, String sensorId, String[] value);
   
    /**
    * Read the rawdata from local controller or IoT platform.
    *
     * Based on deviceId/sessionId -> Session which defined by link(), you will select the ControllerClient to send the 'read' command.
    *
     * @param deviceId
    * @param sensor
    * @return
    */
    public Rawdata getRawdata(String deviceId, String sensorId);
   
   
    /*
    * By IoT platform only.
    *
     * getRawdatas(), deleteRawdata()
    * saveSnapshot(), getSnapshotMeta(), getSnapshotMetas(), getSnapshotBody(), deleteSnapshot()
    *
     */
   
    /*
    * By IoT platform only.
     *
     * saveHeartbeat(), getHeartbeat()
    *
     */
   
   
    /**
    * Subscribe the rawdata changed.
    *
     * @param deviceId
    * @param sensorId
    */
    public void subscribe(String deviceId, String sensorId);
   
    /**
    * By default, OpenIoTClient will connect to MQTT broker to listen the rawdata changed message.
    *
     * @author rickwang
    *
    */
    public static interface Callback {
       void onRawdata(Rawdata rawdata);
       void onHeartBeat(HeartBeat heartbeat);
    }
}
