package com.cht.iot.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cht.iot.client.protocol.Protocol;

public class Controller {
	static final Logger LOG = LoggerFactory.getLogger(Controller.class);
	
	String apiKey = "aabbccdd";  //using deviceKey
	String salt = "";
	
	String vendor = "vendor";
	String model = "model";
	String series = "series";
	String name = "name";
	String cipher = "";
	String extra = "";
	
	int announcePort = 10400; // UDP
	long announceInterval = 5000L;
	DatagramSocket announcement;
	
	int listenPort = 10600; // UDP
	DatagramSocket server;
	
	//int listenPort = 10400; // TCP
	//ServerSocket server;	
	
	List<Session> sessions = Collections.synchronizedList(new ArrayList<Session>()); 

	ScheduledExecutorService timer;
	
	ControllerListener listener = new EmptyControllerListener();
	
	public Controller() {		
	}
	
	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}
	
	public void setVendor(String vendor) {
		this.vendor = vendor;
	}
	
	public void setModel(String model) {
		this.model = model;
	}
	
	public void setSeries(String series) {
		this.series = series;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public void setCipher(String cipher) {
		this.cipher = cipher;
	}
	
	public void setExtra(String extra) {
		this.extra = extra;
	}
	// ======
	
	public void setAnnouncePort(int announcePort) {
		this.announcePort = announcePort;
	}
	
	public void setAnnounceInterval(long announceInterval) {
		this.announceInterval = announceInterval;
	}
	
	public void setListenPort(int listenPort) {
		this.listenPort = listenPort;
	}
	
	// ======
	
	public void setListener(ControllerListener listener) {
		this.listener = listener;
	}
	
	// ======
	
	public void start() throws IOException {
		timer = Executors.newSingleThreadScheduledExecutor();
		
		// broadcast my self
		announcement = new DatagramSocket();
		announcement.setBroadcast(true);
		timer.scheduleWithFixedDelay(new Runnable() {
			public void run() {
				doAnnounce();				
			}
		}, 0, announceInterval, TimeUnit.MILLISECONDS);
		
		// wait for request
		//server = new ServerSocket(listenPort);
		server();
	}
	
	public void server() throws IOException {
		LOG.warn("build server.");
		server = new DatagramSocket(listenPort);
		Thread thread = new Thread(new Runnable() {
			public void run() {
				doListen();
			}
		});
		thread.start();
	}
	
	public void stop() throws IOException {
		timer.shutdown();
		
		server.close();
	}
	
	protected void doAnnounce() {
		try {
			LOG.info("doAnnounce");
			byte[] payload = Protocol.buildAnnouncePacket(vendor, model, series, name);
			DatagramPacket packet = new DatagramPacket(payload, payload.length);
			packet.setAddress(InetAddress.getByName("255.255.255.255"));
			packet.setPort(announcePort);
			
			announcement.send(packet);
			
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		}
	}
	
	protected void doListen() {
		try {
			for (;;) {
				byte[] buf = new byte[10240];
                // receive request
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                server.receive(packet);
                
                //Packet received
                LOG.info( getClass().getName() + " >>> Discovery packet received from: {}, {} " + packet.getAddress().getHostAddress(), packet.getPort() );
 
                ByteArrayInputStream in = new ByteArrayInputStream(packet.getData());
                byte[] body = Protocol.readPacketBody(in);
				ByteArrayInputStream bais = new ByteArrayInputStream(body);
				int cmd = bais.read();
				
				LOG.info("COMMAND: {}", String.format("%02X", cmd));			
				
				if (cmd == Protocol.COMMAND_CONNECT_REQUEST) {
					LOG.error("COMMAND_CONNECT_REQUEST from {}, {}", packet.getAddress().getHostAddress(),packet.getPort());	
					
					
					Session existSession = null;
					if(sessions.size() > 0){
						
						for (Session session : sessions) {
							LOG.info("session {} - {}:",session.host ,packet.getAddress().getHostAddress());
							if(session.host.equals(packet.getAddress().getHostAddress())){
								LOG.info("find connected session!");
								existSession = session;
								break;
							}
						}
						
					}
					
					
					if(existSession != null){
						//existSession.stop();
						existSession.update(server, packet.getAddress().getHostAddress(), packet.getPort());
						existSession.start();
					}else{
						Session session = new Session(server, packet.getAddress().getHostAddress(), packet.getPort());
						session.start();
						sessions.add(session);
					}
					
					LOG.info("session size: {}", sessions.size());
				}else{
					
					synchronized (sessions) {
						for (Session session : sessions) {
							LOG.info("session {} - {}:",session.host ,packet.getAddress().getHostAddress());
							if(session.host.equals(packet.getAddress().getHostAddress())){
								session.doReceive(body);	
							}
						}
					}
					
				}	
			}			
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		}
		
		LOG.warn("connection is lost");
		try {
			server();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/**
	 * Change the value
	 * 
	 * @param deviceId
	 * @param sensorId
	 * @param value
	 * @throws IOException 
	 */
	public void write(String deviceId, String sensorId, String[] value) throws IOException {
		byte[] packet = Protocol.buildWriteReqPacket(deviceId, sensorId, value);
		
		synchronized (sessions) {
			for (Session session : sessions) {
				session.send(packet);
			}
		}
	}
	
	public synchronized void send(byte[] payload, String host, int port) throws IOException {
		DatagramPacket packet = new DatagramPacket(payload, payload.length, InetAddress.getByName(host), port);
        server.send(packet);
	}
	
	@SuppressWarnings("resource")
	protected void handle(Session session, byte[] body) {
		try {		
			InputStream is = new ByteArrayInputStream(body);
			
			int cmd = is.read(); // the first byte is command code
			LOG.info("command - {}", String.format("%02X", cmd));			
			if (cmd == Protocol.COMMAND_CHALLENGE_REPLY) { // challenge reply
				LOG.info("cmd is COMMAND_CHALLENGE_REPLY ");
				String s = session.getSalt() + apiKey;
				
				byte[] digest = Protocol.digest(s.getBytes());
				
				LOG.info("server digest {}",digest);
				LOG.info("client digest {}",body);
				if (body.length == (1 + digest.length + 1)) {				
					if (Protocol.equals(digest, 0, body, 1, digest.length)) {
						LOG.info("authenticated");
						session.setAuthenticated(true);
						
						byte[] packet = Protocol.buildIntroduceReqPacket(cipher, extra);
						session.send(packet);						
						
					} else {
						LOG.error("Failed to authenticated.");
						session.stop(); // authentication is failed
					}
				}else{
					LOG.info("cmd is format error ");
				}
			} else if (session.isAuthenticated()) {
				
				 if (cmd == Protocol.COMMAND_INTRODUCE_REPLY) {
					 LOG.info("cmd is COMMAND_INTRODUCE_REPLY ");
				 }else if (cmd == Protocol.COMMAND_PING_REQUEST) { // ping
					 
					LOG.info("cmd is COMMAND_PING_REQUEST ");
					byte[] packet = Protocol.buildPingReplyPacket();
					session.send(packet);
					
				} else if (cmd == Protocol.COMMAND_READ_REQUEST) {
					LOG.info("cmd is COMMAND_READ_REQUEST ");
					
					if( StringUtils.isNotEmpty(cipher) ) {
						LOG.info(" {} ",body.length);
						is =  new ByteArrayInputStream(Protocol.decrypt(is, cipher, apiKey));
					}
					
					String deviceId = Protocol.readString(is);
					String sensorId = Protocol.readString(is);
					
					LOG.info("COMMAND_READ_REQUEST > {}, {}", deviceId, sensorId);
					
					byte[] pkt = Protocol.buildReadReplyPacket();
					session.send(pkt);
					
					String[] value = listener.readValue(deviceId, sensorId);
					
					value = new String[]{};
					if (value != null) { // we got this value
						LOG.info("package content:{}, {}, {}",deviceId, sensorId, value);
						byte[] packet = null;
						if(StringUtils.isNotEmpty(cipher)){
							packet = Protocol.buildWriteReqPacket(deviceId, sensorId, value, cipher, apiKey);
						}else{
							packet = Protocol.buildWriteReqPacket(deviceId, sensorId, value);
						}
						LOG.info("package length :{}",packet.length);
						session.send(packet);
					}
				} else if (cmd == Protocol.COMMAND_WRITE_REQUEST) {
					LOG.info("cmd is COMMAND_WRITE_REQUEST ");
					String deviceId = Protocol.readString(is);
					String sensorId = Protocol.readString(is);
					
					byte[] pkt = Protocol.buildWriteReplyPacket();
					session.send(pkt);
					
					int s = Protocol.read(is);
					String[] value = new String[s];
					for (int i = 0;i < s;i++) {
						value[i] = Protocol.readString(is);
					}
					
					listener.writeValue(deviceId, sensorId, value);
					
				} else if (cmd == Protocol.COMMAND_WRITE_REPLY) {
					LOG.info("cmd is COMMAND_WRITE_REPLY ");
				}else {
					LOG.error("Unknown command - {}", String.format("%02X", cmd));										
				}
			} else {
				LOG.info("Failed to authenticated.");
				LOG.error("Failed to authenticated.");
				session.stop(); // authentication is not ready
			}
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
			session = null;
		}
	}
	
	class Session {
		//final Socket socket;
		private DatagramSocket socket;
		
		private String connection;
		
		private String host;
		private int port;
		
		InputStream is;
		OutputStream os;
		
		String salt;
		boolean authenticated = false;
		
		public Session(DatagramSocket socket, String host, int port) throws IOException {
			this.socket = socket;
			this.host = host;
			this.port = port;
			
			connection = String.format("%s:%d", host, port);
			
			/*
			is = new BufferedInputStream(socket.getInputStream());
			os = new BufferedOutputStream(socket.getOutputStream());
			*/
			salt = String.format("%X", (long)(Math.random() * 10000000000000000L));
		}
		
		public void update(DatagramSocket socket, String host, int port) throws IOException {
			this.socket = socket;
			this.host = host;
			this.port = port;
			
			connection = String.format("%s:%d", host, port);
			
			salt = String.format("%X", (long)(Math.random() * 10000000000000000L));
		}
		
		public void setSocket(DatagramSocket socket){
			this.socket = socket;
		}
		
		public void start() throws IOException {
			Thread thread = new Thread(new Runnable() {
				public void run() {
					// send the challenge packet for authentication
					try {
						byte[] challenge = Protocol.buildChallengeReqPacket(salt);
						send(challenge);	
						LOG.info("Send ChallengeReqPacket");
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
				}
			});
			thread.start();
		}
		
		public void stop() throws IOException {
			socket.close();
			
		}
		
		public String getSalt() {
			return salt;
		}
		
		public boolean isAuthenticated() {
			return authenticated;
		}
		
		public void setAuthenticated(boolean authenticated) {
			this.authenticated = authenticated;
		}
		
		public synchronized void send(byte[] payload) throws IOException {
			//os.write(payload);
			//os.flush();
			DatagramPacket packet = new DatagramPacket(payload, payload.length, InetAddress.getByName(host), port);
	        socket.send(packet);
			
		}
		
		public void doReceive(byte[] packet) {
			handle(this, packet);		
		}
		/*
		protected void doReceive() {
			try {
				// send the challenge packet for authentication
				byte[] challenge = Protocol.buildChallengeReqPacket(salt);
				send(challenge);
				
				for (;;) {
					byte[] packet = Protocol.readPacketBody(is);
					
					handle(this, packet);									
				}				
			} catch (Exception e) {
				LOG.error(e.getMessage(), e);
			}
			
			LOG.warn("connection is lost");
						
			try {
				socket.close();
				
			} catch (Exception e) {
				LOG.error(e.getMessage(), e);
			}
			
			sessions.remove(this);
		}
		 */
		@Override
		public String toString() {			
			return connection;
		}
	}
	
	static class EmptyControllerListener implements ControllerListener {

		public EmptyControllerListener() {	
		}
		
		public String[] readValue(String deviceId, String sensorId) {		
			return new String[] { "0" };
		}
		
		public void writeValue(String deviceId, String sensorId, String[] value) {
			LOG.info("device: {}, sensor: {}, value: {}", deviceId, sensorId, value[0]);
		}
	}
}
