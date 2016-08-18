package com.cht.util;

public class SignalLock {
	protected int notifications = 0;
	
	public SignalLock() {
	}
	
	public synchronized void reset() {
		this.notifications = 0;
	}
	
	/**
	 * Only support one thread to sleep.
	 * 
	 * @param timeout
	 * @throws InterruptedException
	 */
	public synchronized void sleep(long timeout) throws InterruptedException {
		if (this.notifications <= 0) {
			this.wait(timeout);			
		}
		
		this.notifications = 0;
	}
	
	/**
	 * Support many threads to wake me up
	 */
	public synchronized void wakeup() {
		this.notifications ++;
		
		this.notifyAll();
	}
}

