package com.cht.iot.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.security.DigestException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cht.iot.client.protocol.Protocol;
import com.cht.iot.persistence.entity.data.Session;

public class ControllerClient {
	static final Logger LOG = LoggerFactory.getLogger(ControllerClient.class);
	
	public static final String FLAG_CONNECT = "connect";
	public static final String FLAG_CHALLENGE = "challenge";
	public static final String FLAG_INTRODUCE = "introduce";
	public static final String FLAG_PING = "ping";
	public static final String FLAG_READ = "read";
	public static final String FLAG_WRITE = "write";
	public static final String FLAG_REPLY = "reply";
	
	public static final String STATUS_CONNECT = "connect";
	public static final String STATUS_DISCONNECT = "disconnect";
	public static final String STATUS_SUCCESS = "success";
	public static final String STATUS_FAIL = "fail";
	
	final Session session;
	
	String apiKey;
	Listener listener = new EmptyListener();
	
	Thread thread;
	ScheduledExecutorService timer;
	
	Thread consumer;
	Thread producer;
	
	//Socket socket;
	DatagramSocket socket;
	OutputStream output = null;
	int bufSize = 10240;
	int timeout = 2000;
	long keepalive = 10000L;
	int sendingDelayInNanos = 1000; // 0.001 ms
	
	String cipher;
	String extra;
	
	int sendingQueueSize = 10000;
	
	private String requestFlag;
	Map<String, ClientMessage> requests = Collections.synchronizedMap(new HashMap<String, ClientMessage>());
	
	boolean authenticated = false;
	
	public ControllerClient(Session session) {
		this.session = session;
	}
	
	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}
	
	public void setKeepalive(long keepalive) {
		this.keepalive = keepalive;
	}
	
	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}
	
	public void setListener(Listener listener) {
		this.listener = listener;
	}

	public String getCipher() {
		return cipher;
	}
	
	public String getExtra() {
		return extra;
	}

	public boolean isAuthenticated(){
		return authenticated;
	}
	
	public void start() throws IOException{
		// get a datagram socket 
        socket = new DatagramSocket();
        //socket.setSoTimeout(timeout);
        
		consumer = new Thread(new Runnable() {
			@Override
			public void run() {
				receiving();
			}
		}, "iot-consumer");
		consumer.start();
		
		producer = new Thread(new Runnable() {
			@Override
			public void run() {
				sending();
			}
		}, "iot-producer");
		producer.start();
		
		buildConnect();
		
		timer = Executors.newSingleThreadScheduledExecutor();
		timer.scheduleWithFixedDelay(new Runnable() { // send the keepalive every 10 seconds
			public void run() {
				doKeepalive();				
			}
		}, keepalive, keepalive, TimeUnit.MILLISECONDS);
	}	
	
	public void stop() throws IOException {
		LOG.info("ControllerClient stop");

		authenticated = false;
		
		timer.shutdown();
		
		thread = null;
		
		Thread thr;
		
		socket.close();
		
		// stop consumer
		if(consumer != null) {
			thr = consumer;
			consumer = null;
			thr.interrupt();
		}

		// stop producer
		if(producer != null) {
			thr = producer;
			producer = null;
			thr.interrupt();
		}
		
		requests.clear();
	}
	
	public void buildConnect() {
		try {
			ClientMessage message = new ClientMessage(Protocol.COMMAND_CONNECT_REQUEST ,Protocol.buildConnectReqPacket());
			requestFlag = FLAG_CONNECT;
			requests.put(requestFlag, message);
		} catch (IOException e) {
			LOG.error(e.getMessage(), e);
		}
	}
	
	// Ping 
	protected void doKeepalive() {
		try {
			if(authenticated){  // 有認證才允許 ping
				ClientMessage message = new ClientMessage(Protocol.COMMAND_PING_REQUEST ,Protocol.buildPingReqPacket());
				requestFlag = FLAG_PING;
				requests.put(requestFlag, message);
			}
		} catch (IOException e) {
			LOG.error(e.getMessage(), e);
		}
	}
	
	public void read(final String deviceId, final String sensorId) {
		timer.execute(new Runnable() {
			public void run() {
				try {
					ClientMessage message = null;
					if(StringUtils.isNotEmpty(cipher)){
						message = new ClientMessage(Protocol.COMMAND_READ_REQUEST, Protocol.buildReadReqPacket(deviceId, sensorId, cipher, apiKey));
					}else{
						message = new ClientMessage(Protocol.COMMAND_READ_REQUEST, Protocol.buildReadReqPacket(deviceId, sensorId));
					}
					requestFlag = FLAG_READ;
					requests.put(requestFlag, message);
					
				} catch (Exception e) {
					LOG.error(e.getMessage(), e);
				}
			}
		});
	}
	
	public void write(final String deviceId, final String sensorId, final String[] value) throws IOException {
		timer.execute(new Runnable() {
			public void run() {
				try {
					ClientMessage message = null;
					if(StringUtils.isNotEmpty(cipher)){
						message = new ClientMessage(Protocol.COMMAND_WRITE_REQUEST, Protocol.buildWriteReqPacket(deviceId, sensorId, value, cipher, apiKey));
					}else{
						message = new ClientMessage(Protocol.COMMAND_WRITE_REQUEST, Protocol.buildWriteReqPacket(deviceId, sensorId, value));
					}
					requestFlag = FLAG_WRITE;
					requests.put(requestFlag, message);
					
				} catch (Exception e) {
					LOG.error(e.getMessage(), e);
				}
			}
		});
	}
	
	protected void receiving() {
		byte[] buf = new byte[bufSize];
		while (consumer != null ) {
            // receive request
            try {
            	DatagramPacket packet = new DatagramPacket(buf, buf.length);
            	socket.receive(packet);
				
				ByteArrayInputStream in = new ByteArrayInputStream(packet.getData());
                byte[] body = Protocol.readPacketBody(in);		
                
                //debug
				//showByte("receive", body);

				ByteArrayInputStream bais = new ByteArrayInputStream(body);
				
				int cmd = bais.read();
				
				//LOG.info("client receiver command - {}", String.format("%02X", cmd));
				
				if (cmd == Protocol.COMMAND_CHALLENGE_REQUEST) {
					//LOG.info("COMMAND_CHALLENGE_REQUEST");
					
					ClientMessage message = requests.get(FLAG_CONNECT);
					if(message != null){
						message.setValue(bais);
						
						listener.onLinkStatusChanged(FLAG_CONNECT, STATUS_SUCCESS,"");
						
						String salt = Protocol.readString(bais);
						//LOG.info("COMMAND_CHALLENGE_REQUEST : SALT: {}, apikey:{}", salt, apiKey);
						
						try {
							ClientMessage sendMessage = new ClientMessage(Protocol.COMMAND_CHALLENGE_REPLY, Protocol.buildChallengeReplyPacket(salt, apiKey));
							requestFlag = FLAG_CHALLENGE;
							requests.put(requestFlag, sendMessage);
							
							//debug
							//showByte("challenge", sendMessage.getData());
							
						} catch (DigestException e) {
							LOG.error(e.getMessage(), e);
						}
					}
				} else if (cmd == Protocol.COMMAND_INTRODUCE_REQUEST) {
					//LOG.info("COMMAND_INTRODUCE_REQUEST");
				
					ClientMessage message = requests.get(FLAG_CHALLENGE);
					if(message != null){
						message.setValue(bais);
						
						listener.onLinkStatusChanged(FLAG_CHALLENGE, STATUS_CONNECT,"");
						
						cipher = Protocol.readString(bais);
						extra = Protocol.readString(bais);
						
						LOG.info("COMMAND_INTRODUCE_REQUEST: {}, {}", cipher, extra);
						
						synchronized (session) {
							authenticated = true;
							session.notifyAll();
						}
						
						ClientMessage sendMessage = new ClientMessage(Protocol.COMMAND_INTRODUCE_REPLY, Protocol.buildIntroduceReplyPacket());
						requestFlag = FLAG_INTRODUCE;
						requests.put(requestFlag, sendMessage);
					}
				} else if(cmd == Protocol.COMMAND_PING_REPLY) {
					LOG.info("COMMAND_PING_REPLY");
					
					ClientMessage message = requests.get(FLAG_PING);
					if(message != null){
						message.setValue(Protocol.COMMAND_PING_REPLY);
					}
				} else if(cmd == Protocol.COMMAND_READ_REPLY) {
					LOG.info("COMMAND_READ_REPLY");
					
					ClientMessage message = requests.get(FLAG_READ);
					if(message != null){
						message.setValue(Protocol.COMMAND_PING_REPLY);
					}
				} else if (cmd == Protocol.COMMAND_WRITE_REQUEST) {
					LOG.info("COMMAND_WRITE_REQUEST");
					
					if(StringUtils.isNotEmpty(cipher)){
						LOG.info("body length {} ",body.length);
						bais = new ByteArrayInputStream(Protocol.decrypt(bais, cipher, apiKey));
					}
					
					String deviceId = Protocol.readString(bais);
					String sensorId = Protocol.readString(bais);
					int s = Protocol.read(bais);
					String[] value = new String[s];
					
					LOG.info("write count: {}",s);

					for (int i = 0;i < s;i++) {

						value[i] = Protocol.readString(bais);
						LOG.info("write value: {} : {}",i,value[i]);
					}
					
					listener.onValueChanged(deviceId, sensorId, value);
					
					ClientMessage sendMessage = new ClientMessage(Protocol.COMMAND_WRITE_REPLY, Protocol.buildWriteReplyPacket());
					requestFlag = FLAG_REPLY;
					requests.put(requestFlag, sendMessage);
					
				} else if(cmd == Protocol.COMMAND_WRITE_REPLY) {
					LOG.info("COMMAND_WRITE_REPLY");

					ClientMessage message = requests.get(FLAG_WRITE);
					if(message != null){
						message.setValue(Protocol.COMMAND_PING_REPLY);
					}
				} else {
					LOG.warn("Undefined command {}!",String.format("%02X", cmd));
				}
			} catch (IOException e) {
				LOG.error(e.getMessage(), e);
			} catch (NoSuchAlgorithmException e) {
				LOG.error(e.getMessage(), e);
			} catch (InvalidKeyException e) {
				LOG.error(e.getMessage(), e);
			} catch (NoSuchPaddingException e) {
				LOG.error(e.getMessage(), e);
			} catch (IllegalBlockSizeException e) {
				LOG.error(e.getMessage(), e);
			} catch (BadPaddingException e) {
				LOG.error(e.getMessage(), e);
			}
		}
	}
	
	protected void sending() {
		
		while (producer != null) {
			if(StringUtils.isNotEmpty(requestFlag)){

				ClientMessage message = requests.get(requestFlag);
				if(message != null){
				byte[] buf = message.getData();

				//LOG.info("client send to: {}",buf, session.host);
				//showByte("send:"+session.host,buf);

				try {
					DatagramPacket packet = new DatagramPacket(buf, buf.length, InetAddress.getByName(session.host), session.port);

					socket.send(packet);
					
					if(requestFlag.equals(FLAG_INTRODUCE) || requestFlag.equals(FLAG_REPLY) ){
						message.setValue("done");
					}
					try{
						if(message.getValue(timeout)!=null){
							requests.remove(requestFlag);
						}
						
					} catch (TimeoutException e) {
						if(message.countReply < 3){
							message.countReply++;
							listener.onLinkStatusChanged(requestFlag, STATUS_FAIL, "TimeoutException! Reply #"+ message.countReply);
						}else{
							requests.remove(requestFlag);
							listener.onLinkStatusChanged(requestFlag, STATUS_DISCONNECT, "TimeoutException! Has exceeded the number of resending");
							stop(); // stop socket
						}
					}
					Thread.sleep(0L, sendingDelayInNanos); // TODO - flow control

				} catch (InterruptedException e) {
					LOG.error(e.getMessage(), e);
				} catch (IOException e) {
					LOG.error(e.getMessage(), e);
				} 
				}

			}
		}
	}
	
	@SuppressWarnings("unused")
	private static void showByte(String info, byte[] bs){
		//debug
		LOG.info("{} :",info);
		for(int i=0; i<bs.length; i++){
			LOG.info("{}", String.format("%02X", bs[i]));
		}
	}
	
	public static interface Listener {
		/**
		 * Controller sends the value changed message.
		 * 
		 * @param deviceId
		 * @param sensorId
		 * @param value
		 */
		void onValueChanged(String deviceId, String sensorId, String[] value);
		
		public void onLinkStatusChanged(String communication, String status, String message);
	}
	
	static class EmptyListener implements Listener {
		
		public EmptyListener() {
			
		}
		
		public void onValueChanged(String deviceId, String sensorId, String[] value) {
			
		}
		
		public void onLinkStatusChanged(String communication, String status, String message) {
			
		}
	}
	
	static class ClientMessage {
		byte[] data;
		int command;
		Object value;
		int countReply;
		long birthday;
		
		public ClientMessage(){
		}
		
		public ClientMessage(int command, byte[] data){
			this.command = command;
			this.data = data;
			countReply = 0;
			birthday = System.currentTimeMillis();
		}
		
		// ====
		
		public synchronized void reset() {
			value = null;
			countReply = 0;
		}
		
		public synchronized void setValue(Object value) {
			this.value = value;
			
			notifyAll();
		}
		
		public synchronized Object getValue(long timeout) throws InterruptedException, TimeoutException {
			if(value == null){
				wait(timeout);
			}
			if(value == null){
				throw new TimeoutException();
			}
			return value;
		}

		// ===
		
		public byte[] getData() {
			return data;
		}

		public void setData(byte[] data) {
			this.data = data;
		}

		public int getCommand() {
			return command;
		}

		public void setCommand(int command) {
			this.command = command;
		}

		public long getBirthday() {
			return birthday;
		}

		public void setBirthday(long birthday) {
			this.birthday = birthday;
		}
	}
}
