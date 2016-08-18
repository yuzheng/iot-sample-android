package com.cht.iot.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cht.iot.client.protocol.Protocol;
import com.cht.iot.persistence.entity.data.Session;


public class ControllerClientBuilder {
	static final Logger LOG = LoggerFactory.getLogger(ControllerClientBuilder.class);
	
	DatagramSocket announcement;
	
	Thread thread;
	
	int announcePort = 10400; // UDP
	
	int connectPort = 10600; // UDP
	
	int bufSize = 10240;
	
	Set<Session> sessions = Collections.synchronizedSet(new HashSet<Session>()); 
	
	public ControllerClientBuilder() {		
	}
	
	public void setAnnouncePort(int announcePort) {
		this.announcePort = announcePort;
	}
	
	public void setConnectPort(int connectPort) {
		this.connectPort = connectPort;
	}
	
	public void start() throws IOException {
		announcement = new DatagramSocket(announcePort);
		
		thread = new Thread(new Runnable() {
			public void run() {
				doListen();
			}
		});
		thread.start();
	}
	
	public void stop() {
		if (thread != null) {
			Thread t = thread;
			thread = null;
			t.interrupt();
		}
		
		// synchronized
		synchronized (sessions) {
			sessions.clear();
		}
		
		announcement.close();
	}
	
	public List<Session> getSessions() {
		List<Session> ss = new ArrayList<Session>();		
		synchronized (sessions) {
			ss.addAll(sessions);
		}		
		return ss;
	}
	
	public ControllerClient build(Session session) {
		ControllerClient client = new ControllerClient(session);
		return client;		
	}
	
	protected void doListen() {
		try {
			byte[] packet = new byte[bufSize];
			while (thread != null) {  //for(;;){ 
					DatagramPacket p = new DatagramPacket(packet, packet.length);
					announcement.receive(p);
					
					try {
						InputStream is = new ByteArrayInputStream(p.getData(), 0, p.getLength());
						
						byte[] body = Protocol.readPacketBody(is);
						
						//LOG.info("{}",body);
						//for(int i=0; i<body.length; i++){
						//	LOG.info("{}", String.format("%02X", body[i]));
						//}
						
						is = new ByteArrayInputStream(body);
						int cmd = is.read();
						if (cmd == Protocol.COMMAND_ANNOUNCE) {						
							String vendor = Protocol.readString(is);
							String model = Protocol.readString(is);
							String series = Protocol.readString(is);
							String name = Protocol.readString(is);
							
							String host = p.getAddress().getHostAddress();
							
							Session session = new Session(vendor, model, series, name);
							session.host = host;
							session.port = connectPort;
							
							sessions.add(session); // FIXME - 'host:port' could be changed by the same controller when network changed.
							
							LOG.info("Discover - {}", session);
							
						} else {
							LOG.error("Receive the incorrect packet from {}", p.getAddress().getHostAddress());						
						}					
					} catch (Exception e) {
						LOG.error(e.getMessage(), e);
					}
			}			
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		}
	}
}
