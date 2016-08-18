package com.cht.iot.service.api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cht.iot.persistence.entity.data.Message;
import com.cht.iot.persistence.entity.data.Rawdata;
import com.cht.iot.util.JsonUtils;

/*
 * Multicast (using UDP - wifi)
 * 在無internet環境時，採用此機制於內部網路控制設備(rawdata)
 * Author: YuCheng Wang
 */

public class OpenMulticastClient {
	static final Logger LOG = LoggerFactory.getLogger(OpenMulticastClient.class);
	
	// multicast
	String address = "224.144.77.1";
	int port = 8883;
	MulticastSocket socket;
	InetAddress group;	
	
	Listener listener = new ListenerAdapter();
	
	Thread consumer;
	Thread producer;
	
	int id = (int) (Math.random() * Integer.MAX_VALUE);
	
	int sendingPredictPacketSize = 64; // auto grown
	int sendingQueueSize = 10000;
	int sendingDelayInNanos = 1000; // 0.001 ms
	int receivingPacketSize = 64000; // 64 kilo bytes per packet
	
	BlockingQueue<Message> queue;
	
	Set<String> subscribeTopics;	// subscribe topics (List or Set?)

	public OpenMulticastClient() {
		
	}
	
	public OpenMulticastClient(String address, int port) {
		this.address = address;
		this.port = port;
	}
	
	public void setAddress(String address) {
		this.address = address;
	}
	
	public void setPort(int port) {
		this.port = port;
	}
	
	public void setSendingPredictPacketSize(int sendingPredictPacketSize) {
		this.sendingPredictPacketSize = sendingPredictPacketSize;
	}
	
	public void setSendingQueueSize(int sendingQueueSize) {
		this.sendingQueueSize = sendingQueueSize;
	}
	
	public void setSendingDelayInNanos(int sendingDelayInNanos) {
		this.sendingDelayInNanos = sendingDelayInNanos;
	}
	
	public void setReceivingPacketSize(int receivingPacketSize) {
		this.receivingPacketSize = receivingPacketSize;
	}
	
	public void setSubscribeTopics(Set<String> subscribeTopics) {
		this.subscribeTopics = subscribeTopics;
	}
	
	/**
	 * Set the listener to read the incoming events.
	 * 
	 * @param listener
	 */
	public void setListener(Listener listener) {
		this.listener = listener;
	}
	
	
	protected Rawdata toRawdata(String json) throws IOException {
		return JsonUtils.fromJson(json, Rawdata.class);
	}
	
	protected Message toMessage(String json) throws IOException {
		return JsonUtils.fromJson(json, Message.class);
	}
	
	/**
	 * Start the Multicast connection. 
	 * @throws IOException 
	 */
	public synchronized void start() throws IOException {
		group = InetAddress.getByName(address);
		socket = new MulticastSocket(port);
		socket.joinGroup(group);
		
		LOG.info(String.format("Join multicast group - %s:%d", address, port));		
		
		consumer = new Thread(new Runnable() {
			@Override
			public void run() {
				receiving();
			}
		}, "iot-consumer");
		consumer.start();
		
		queue = new ArrayBlockingQueue<Message>(sendingQueueSize);	
		
		producer = new Thread(new Runnable() {
			@Override
			public void run() {
				sending();
			}
		}, "iot-producer");
		producer.start();
	}
	
	public void stop() throws IOException {
		Thread thr;
		
		// stop consumer		
		thr = consumer;
		consumer = null;
		thr.interrupt();
		
		socket.leaveGroup(group);
		socket.close();
		
		// stop producer
		thr = producer;
		producer = null;
		thr.interrupt();		
	}
	
	public void saveRawdata(String deviceId, String sensorId, String time, Float lat, Float lon, String[] value) throws IOException , InterruptedException{
		String topic = String.format("/v1/device/%s/sensor/%s/rawdata", deviceId,sensorId);
		
		Rawdata rawdata = new Rawdata();
		rawdata.setId(sensorId);
		rawdata.setDeviceId(deviceId);
		rawdata.setTime(time);
		rawdata.setLat(lat);
		rawdata.setLon(lon);
		rawdata.setValue(value);
		
		String json = JsonUtils.toJson(rawdata);
		Message message = new Message(id, topic, json);
		queue.put(message);	
	}
	
	/**
	 * Insert one rawdata into the data store.
	 * 
	 * @param deviceId
	 * @param sensorId
	 * @param value
	 * @throws IOException
	 * @throws InterruptedException 
	 */
	public void saveRawdata(String deviceId, String sensorId, String value) throws IOException, InterruptedException {
		saveRawdata(deviceId, sensorId, null, null, null, new String[] { value });
	}
	
	/**
	 * Insert one rawdata into the data store.
	 * 
	 * @param deviceId
	 * @param sensorId
	 * @param value
	 * @throws IOException
	 */
	public void saveRawdata(String deviceId, String sensorId, String[] value) throws IOException, InterruptedException {
		saveRawdata(deviceId, sensorId, null, null, null, value);
	}
	
	protected void sending() {
		//MyByteArrayOutputStream baos = new MyByteArrayOutputStream(sendingPredictPacketSize);
		
		while (producer != null) {
			try {
				Message message = queue.take();
				/*
				 *  由於 C 語言部分目前無找到好的實作 DataOutputStream/DataInputStream 方法
				 *  故先不採用 DataOutputStream/DataInputStream 方法
				 */
				/*
				String topic = message.getTopic();
				String payload = message.getPayload();
				
				baos.reset(); // reuse it
				
				DataOutputStream dos = new DataOutputStream(baos);
				dos.writeInt(id);
				dos.writeUTF(topic);
				dos.writeUTF(payload);
				dos.flush();
				
				DatagramPacket pkt = new DatagramPacket(baos.array(), baos.size());
				//LOG.info("pkt size:"+baos.size());
				*/
				
				String sendString = JsonUtils.toJson(message);
				// convert keyboard input to bytes
				byte[] sendBytes = sendString.getBytes();
		        // populate the DatagramPacket
		        DatagramPacket pkt = new DatagramPacket(sendBytes, sendBytes.length);
				
				pkt.setAddress(group);
				pkt.setPort(port);
				
				socket.send(pkt);
				
				Thread.sleep(0L, sendingDelayInNanos); // TODO - flow control
				
			} catch (InterruptedException ie) {
				
			} catch (Exception ex) {
				LOG.error("Failed to send message", ex);
			}
		}
	}
	
	protected void receiving() {
		byte[] buf = new byte[receivingPacketSize];
		DatagramPacket pkt = new DatagramPacket(buf, buf.length);
		
		while (consumer != null) {
			try {				
				socket.receive(pkt);
				LOG.info("Received " + pkt.getLength() + " bytes from " + pkt.getAddress() + ": "  + new String(pkt.getData(),0,pkt.getLength()));
				String receiveString = new String(pkt.getData(),0,pkt.getLength());
				Message message = toMessage(receiveString);
				if(message.getFrom() != id){
					String topic =  message.getTopic();
					// OpenMulticastClient 增加訂閱指定 Topic
					LOG.info("Multicast Received topic: " + topic);
					if(subscribeTopics.size() > 0 && subscribeTopics.contains(topic)){
						LOG.info("in the subscribeTopics!");
						String payload = message.getPayload();
						Rawdata rawdata = toRawdata(payload);
						//Rawdata rawdata = message.getPayload();
						listener.onRawdata(topic, rawdata);
					}
				}
				
				/*
				ByteArrayInputStream bais = new ByteArrayInputStream(buf, pkt.getOffset(), pkt.getLength());
				DataInputStream dis = new DataInputStream(bais);
				int from = dis.readInt();
				if (id != from) { // TODO - filter myself packet to reduce the memory usage	
					
					String topic = dis.readUTF(); // topic
					System.out.println("receiving topic:"+topic);
					System.out.println("subscribeTopics size:"+subscribeTopics.size());
					
					// OpenMulticastClient 增加訂閱指定 Topic
					if(subscribeTopics.size() > 0 && subscribeTopics.contains(topic)){
						String payload = dis.readUTF();
						Rawdata rawdata = toRawdata(payload);
						listener.onRawdata(topic, rawdata);
					}else{
						System.out.println("not found topic");
					}
					
				}
				*/
			} catch (SocketException se) {
				LOG.warn("Socket is closed");
				
			} catch (Exception ex) {
				LOG.error("Unknown error", ex);
			}
		}
	}	
	
	// ======
	
	public static interface Listener {
		/**
		 * The value changed of the sensor.
		 * 
		 * @param topic
		 * @param rawdata
		 */
		public void onRawdata(String topic, Rawdata rawdata);
	}
	
	public static class ListenerAdapter implements Listener {
		@Override
		public void onRawdata(String topic, Rawdata rawdata) {
		}
	}
	
	// ======
	
	static class MyByteArrayOutputStream extends ByteArrayOutputStream {
		
		public MyByteArrayOutputStream(int size) {
			super(size);			
		}
		
		public byte[] array() {
			return buf;
		}
		
		public int size() {
			return count;
		}
	}
	
}
