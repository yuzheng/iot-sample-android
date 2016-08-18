package com.cht.iot.service;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cht.iot.client.Controller;
import com.cht.iot.persistence.entity.data.Rawdata;
import com.cht.iot.persistence.entity.data.Session;

public class OpenIoTClientImplTest extends OpenIoTClientImpl {
	static final Logger LOG = LoggerFactory.getLogger(OpenIoTClientImplTest.class);
	@Test
	public void test() throws IOException, InterruptedException {
		String apiKey = "PK1G27KG0PUFFTGBX0";
		String deviceId = "388622157";
		String[] sensorIds = new String[]{"button"};
		//
		// String projectKey = "PK1G27KG0PUFFTGBX0";
		// String deviceId = "388622157";
		// String deviceKey = "DKAY1GHR4UB3REZT0G";
		// String sensorId = "button";
		//
		
		// start the controller side
		Controller controller = new Controller();
		//controller.setCipher("AES-128");
		controller.setApiKey(apiKey);		
		controller.start();

		// create app side
		OpenIoTClientImpl openIoTClient = new OpenIoTClientImpl();
		openIoTClient.setProjectKey(apiKey);
		openIoTClient.start();
		
		Thread.sleep(7000L); // wait for controller's announcement
		final CountDownLatch latch = new CountDownLatch(1); // latch
		LOG.info(" openIoTClient sessions: " + openIoTClient.getSessions().size());
		//LOG.info(" openIoTClient localMode: " + openIoTClient.getIsLocalMode());
		
		if( openIoTClient.getSessions().size() > 0){
			LOG.info("local mode ");
			for (Session session : openIoTClient.getSessions()) {
				openIoTClient.link(session, apiKey, deviceId, sensorIds);
				//openIoTClient.link(session, "asdasfasf", deviceId, sensorIds);
				openIoTClient.subscribe(deviceId, sensorIds[0]);
				openIoTClient.setCallback(new OpenIoTClientImpl.CallbackAdapter(){		
					@Override
					public void onRawdata(Rawdata rawdata) {
						LOG.info("=== onRawdata callback ===");
						LOG.info("{}",rawdata.getValue().toString());
					}
				});
				openIoTClient.setListener(new OpenIoTClientImpl.OpenIoTClientListener(){
					@Override
					public void didConnectToController() {
						LOG.info("didConnectToController");
					}
					@Override
					public void didNotConnectToController(Mode mode, String info) {
						LOG.info("didNotConnectToController: {}",info);
					}
				});
			}
		}else{
			//openIoTClient.setServiceDevice(apiKey, deviceId, sensorIds);
			openIoTClient.subscribe( deviceId, sensorIds[0]);
			openIoTClient.setCallback(new OpenIoTClientImpl.CallbackAdapter(){		
				@Override
				public void onRawdata(Rawdata rawdata) {
					LOG.info("=== mqtt onRawdata callback ===");
				}
			});
		}
		
		//openIoTClient.subscribe(apiKey, deviceId, "MySensor");
		Thread.sleep(5000L); // wait for controller's announcement
		
		LOG.info("------------ test --------------");
		
		
		openIoTClient.getRawdata(deviceId, sensorIds[0]);
		/*
		openIoTClient.stop();
		openIoTClient.start();
		
		//Thread.sleep(5000L);
		
		
		Thread.sleep(7000L); // wait for controller's announcement
		if( openIoTClient.getSessions().size() > 0){
			LOG.info("local mode ");
			for (Session session : openIoTClient.getSessions()) {
				openIoTClient.link(session, apiKey, deviceId, sensorIds);
				openIoTClient.subscribe(apiKey, deviceId, sensorIds[0]);
				openIoTClient.setCallback(new OpenIoTClientImpl.CallbackAdapter(){		
					@Override
					public void onRawdata(Rawdata rawdata) {
						LOG.info("=== onRawdata callback ===");
						LOG.info("{}",rawdata.getValue());
					}
				});
			}
		}
		*/
		/*
		Thread.sleep(5000L); // wait for controller's announcement
		openIoTClient = new OpenIoTClientImpl();
		openIoTClient.setProjectKey(apiKey);
		openIoTClient.start();
		*/
		/*
		if(client==null){
			LOG.error("client is null");
		}else{
			client.read("123", "MySensor");
		}
		*/
		//openIoTClient.subscribe(apiKey, "123", "MySensor");
		//openIoTClient.getRawdata(deviceId, sensorIds[0]);
		
		latch.await(); 
		
	}
	
	@Test
	public void testServerSide() throws IOException, InterruptedException {
		String apiKey = "PK1G27KG0PUFFTGBX0";
		
		// start the controller side
		Controller controller = new Controller();
		controller.setName("light");
		controller.setSeries("388622157");
		controller.setApiKey(apiKey);		
		
		final CountDownLatch latch = new CountDownLatch(1); // latch
		
		controller.start();
		
		latch.await(); 
		
	}
	
	@Test
	public void testInternet() throws IOException, InterruptedException {
		String apiKey = "XT2XGTMRFK3T9YXT";
		
		// create app side
		OpenIoTClientImpl openIoTClient = new OpenIoTClientImpl();
		openIoTClient.setProjectKey(apiKey);
		openIoTClient.start();
		
		Thread.sleep(2000L); // wait for controller's announcement
		final CountDownLatch latch = new CountDownLatch(1); // latch
		LOG.info(" openIoTClient sessions: " + openIoTClient.getSessions());
		String deviceId = "266148835";
		String[] sensorIds = new String[]{"ledred"};
		
			
		openIoTClient.link(null, apiKey, deviceId, sensorIds);
		openIoTClient.subscribe(deviceId, sensorIds[0]);
		openIoTClient.setCallback(new OpenIoTClientImpl.CallbackAdapter(){		
			@Override
			public void onRawdata(Rawdata rawdata) {
				LOG.info("=== mqtt onRawdata callback ===");
			}
		});
		
		
		//openIoTClient.subscribe(apiKey, deviceId, "MySensor");
		Thread.sleep(5000L); // wait for controller's announcement
		LOG.info("main is work");
		
		openIoTClient.saveRawdata(deviceId, sensorIds[0],new String[]{"0"});
		/*
		if(client==null){
			LOG.error("client is null");
		}else{
			client.read("123", "MySensor");
		}
		*/
		
		//openIoTClient.subscribe(apiKey, "123", "MySensor");
		//openIoTClient.getRawdata(deviceId, "MySensor");
		latch.await(); 
	}
}
