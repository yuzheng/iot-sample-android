package com.cht.iot.service.api;

import static org.junit.Assert.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.cht.iot.persistence.entity.data.Rawdata;

public class OpenMulticastClientTest {

	@Test
	public void test() throws Exception {
		int count = 1000;
		long timeout = 5000L;
		
		final CountDownLatch latch = new CountDownLatch(count);
		
		OpenMulticastClient a = new OpenMulticastClient();
		a.setListener(new OpenMulticastClient.Listener() {			
			@Override
			public void onRawdata(String topic, Rawdata rawdata) {
				System.out.println("topic:"+topic);
				latch.countDown();				
			}
		});		
		a.start();
		
		OpenMulticastClient b = new OpenMulticastClient();
		b.start();
		
		for (int i = 0; i < count; i++) {
			b.saveRawdata("MyDeviceId", "MySensorId", "MyValue");
		}
		
		boolean r = latch.await(timeout, TimeUnit.MILLISECONDS);
		
		assertTrue(r);
		
		b.stop();
		a.stop();		
	}
}
