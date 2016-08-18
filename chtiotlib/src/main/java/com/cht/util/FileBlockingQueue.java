package com.cht.util;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileBlockingQueue {
	static final Logger LOG = LoggerFactory.getLogger(FileBlockingQueue.class);
	
	static final byte[] MAGIC = new byte[] { 0x09, 0x08, 0x07, 0x06 };
	
	static final DateFormat DF = new SimpleDateFormat("yyyyMMddHHmmssSSS");	
	
	final File path;
	final String fileNamePrefix;
	
	final Indexer indexer;
	
	long maxFileSize = 10 * 1024 * 1024; // 10 MB
	
	File tailFile;
	RandomAccessFile tailFileWriter;
	
	File headFile;
	RandomAccessFile headFileReader;

	/**
	 * Persist data into the queue files. 
	 * 
	 * @param path		path of queue files
	 * @param prefix	prefix name of the queue file
	 * 
	 * @throws IOException
	 */
	public FileBlockingQueue(File path, String prefix) throws IOException {
		this.path = path;
		
		if (!path.isDirectory()) {
			if (!path.mkdirs()) {
				throw new IOException("failed to make the directory - " + path);
			}
		}		
		
		fileNamePrefix = String.format("%s.queue.", prefix);
		
		indexer = new Indexer(path, prefix); // index file could be locked by other program
	}
	
	/**
	 * Close the file queue.
	 * 
	 * @throws IOException
	 */
	public void close() throws IOException {
		closeWriter();
		closeReader();
		
		indexer.close();
	}
	
	/**
	 * Set the maximum size in bytes to split the queue files.
	 * 
	 * @param maxFileSize
	 */
	public void setMaxFileSize(long maxFileSize) {
		this.maxFileSize = maxFileSize;
	}
	
	/**
	 * Set the saving delay of the index file.
	 * 
	 * @param delay
	 */
	public void setSavingDelay(long delay) {
		indexer.setSavingDelay(delay);
	}	
	
	// ======
	
	protected static String newFileSuffix() {
		return DF.format(new Date());
	}
	
	protected static void checkMagicHead(DataInput di) throws IOException {
		for (byte b : MAGIC) {
			if (b != di.readByte()) {
				throw new IOException("File is broken");
			}
		}		
	}
	
	protected File newFile(String suffix) {
		return new File(path, fileNamePrefix + suffix);
	}
	
	// ======
	
	protected RandomAccessFile loadWriter() throws IOException {
		tailFile = newFile(indexer.getTailFileSuffix());
		tailFileWriter = new RandomAccessFile(tailFile, "rw");
		tailFileWriter.seek(indexer.getTailFilePointer()); // if we fail or lag to update the index file, we use the last tail index to skip the extra part.
		
		if (LOG.isDebugEnabled()) LOG.debug("load the queue file to write - {}", tailFile);
		
		return tailFileWriter;
	}
	
	protected RandomAccessFile newWriter() throws IOException {
		closeWriter();
		
		String suffix = newFileSuffix();
		
		tailFile = newFile(suffix);
		tailFileWriter = new RandomAccessFile(tailFile, "rw");
		
		indexer.setTailFile(suffix); // reset index
		
		if (LOG.isDebugEnabled()) LOG.debug("build a new queue file to write - {}", tailFile);
		
		return tailFileWriter;
	}
	
	protected void closeWriter()  throws IOException {
		if (tailFileWriter != null) {
			tailFileWriter.close();
			tailFileWriter = null;
		}
	}
	
	protected DataOutput getWriter() throws IOException {
		if (tailFile == null) { // open the existed tail file
			return loadWriter();
			
		} else if (indexer.getTailFilePointer() >= maxFileSize) {
			return newWriter();
		}		
		
		return tailFileWriter;
	}
	
	/**
	 * Put the data.
	 * 
	 * @param bytes
	 * @throws IOException
	 */
	public synchronized void put(byte[] bytes) throws IOException {
		DataOutput writer = getWriter();
		
		writer.write(MAGIC);
		writer.writeInt(bytes.length);
		writer.write(bytes);
		
		indexer.addTailFilePosition(MAGIC.length + 4 + bytes.length);
		
		notify(); // okay, you can take something from queue file
	}
	
	// ======
	
	protected void closeReader() throws IOException {
		if (headFileReader != null) {
			headFileReader.close();
			headFileReader = null;
		}
	}
	
	protected File getNextHeadFile() throws IOException {
		String[] fns = path.list(); // TODO - out of memory
		Arrays.sort(fns);
		
		for (String fn : fns) {
			if (fn.startsWith(fileNamePrefix)) {
				return new File(path, fn);
			}
		}
		
		return null;
	}
	
	protected DataInput getReader() throws IOException {
		if (headFile == null) {
			headFile = newFile(indexer.getHeadFileSuffix());
		}
		
		if (indexer.getHeadFilePointer() >= headFile.length()) { // we already consumed out of the head queue file 
			if (headFile.equals(tailFile)) {
				throw new EOFException("no more data from " + headFile);
			}
			
			closeReader();
			
			if (!headFile.delete()) { // delete the empty queue file
				throw new IOException("failed to delete queue file - " + headFile);
			}
			
			if (LOG.isDebugEnabled()) LOG.debug("deleted the queue file - " + headFile);
			
			File f = getNextHeadFile();
			if (f == null) {
				throw new EOFException("no more file from " + path);
			}
			
			String suffix = f.getName().substring(fileNamePrefix.length()); // get the suffix from file name
			indexer.setHeadFile(suffix);
			
			headFile = f;
		}

		if (headFileReader == null) {
			headFileReader = new RandomAccessFile(headFile, "r");
			headFileReader.seek(indexer.getHeadFilePointer());
			
			if (LOG.isDebugEnabled()) LOG.debug("load the queue file to read - {}", headFile);
		}
		
		return headFileReader;
	}
	
	/**
	 * Empty?
	 * 
	 * @return
	 */
	public boolean isEmpty() {
		return indexer.isEmpty();
	}
	
	protected byte[] read() throws IOException {
		DataInput reader = getReader();
		
		checkMagicHead(reader);
		
		int length = reader.readInt();
		byte[] bytes = new byte[length];
		reader.readFully(bytes);
		
		return bytes;
	}
	
	protected int skip() throws IOException {
		DataInput reader = getReader();
		
		checkMagicHead(reader);
		
		int length = reader.readInt(); // 4 bytes
		reader.skipBytes(length); // just skip without reading
		
		return length;
	}
	
	/**
	 * Peek a data without deleting.
	 * 
	 * @return
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public synchronized byte[] peek() throws IOException {
		if (!indexer.isEmpty()) {		
			try {
				byte[] bytes = read();

				headFileReader.seek(indexer.getHeadFilePointer()); // reset reading position
				
				return bytes;
				
			} catch (EOFException e) {
				LOG.warn("the queue file is broken at tail - " + headFile);
				
				indexer.setHeadFilePosition(headFile.length()); // getReader() will trace the next queue file
				
				return peek();
			}	
		}
		
		return null;
	}
	
	/**
	 * Take a data from queue file with blocking.
	 * 
	 * @return
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public synchronized byte[] take() throws IOException, InterruptedException {
		while (indexer.isEmpty()) {
			wait();
		}
		
		try {
			byte[] bytes = read();
			
			int size = MAGIC.length + 4 + bytes.length;
			
			indexer.addHeadFilePosition(size); // update index
			
			return bytes;
			
		} catch (EOFException e) {
			LOG.warn("the queue file is broken at tail - " + headFile);
			
			indexer.setHeadFilePosition(headFile.length()); // getReader() will trace the next queue file
			
			return take();
		}		
	}

	/**
	 * Remove a data from queue file.
	 * 
	 * @return
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public synchronized void remove() throws IOException {
		//System.out.println("FileBlockingQueue,remove");
		
		if (!indexer.isEmpty()) {
			try {	
				int length = skip(); // skip without reading
				System.out.println("length: "+length);
				int size = MAGIC.length + 4 + length;
				
				indexer.addHeadFilePosition(size); // update index
				
			} catch (EOFException e) {
				LOG.warn("the queue file is broken at tail - " + headFile);
				//System.out.println("the queue file is broken at tail - " + headFile);
				indexer.setHeadFilePosition(headFile.length()); // getReader() will trace the next queue file
				
				remove();
			}			
		}
	}	
	
	static class Indexer {
		String headFileSuffix = newFileSuffix();
		long headFilePointer = 0L;
		
		String tailFileSuffix = headFileSuffix;
		long tailFilePointer = headFilePointer;
		
		final RandomAccessFile file;
		final FileChannel channel;
		final FileLock fileLock;

		Thread thread;
		SignalLock lock = new SignalLock();
		long delay = 1000L; // saving delay of the index file in ms
		
		public Indexer(File path, String prefix) throws IOException {
			File f = new File(path, String.format("%s.index", prefix));			
			
			// open and lock the index file
			file = new RandomAccessFile(f, "rwd"); // Open for reading and writing, as with "rw", and also require that every update to the file's content be written synchronously to the underlying storage device.
			channel = file.getChannel();		
			fileLock = channel.tryLock(); // try to lock it, no blocking
			if (fileLock == null) {
				channel.close();
				file.close();
				
				throw new IOException("another program has locked this index file - " + f);
			}

			if (file.length() > 0) { // file is not empty
				load();
			}
			
			// fork a thread to save the index file changed in background
			thread = new Thread(new Runnable() {
				public void run() {
					saving();				
				}
			}, "Index File Saving");
			thread.start();
			
			if (LOG.isDebugEnabled()) LOG.debug("index - {}", toString());		
		}
		
		public void close() throws IOException {
			save(); // last job
			
			Thread t = thread;
			thread = null;
			t.interrupt(); // interrupt from sleeping
			
			fileLock.release();			
			channel.close();
			file.close();
		}
		
		protected void load() throws IOException {
			file.seek(0);
			
			checkMagicHead(file);			
			
			headFileSuffix = file.readUTF();
			headFilePointer = file.readLong();
			tailFileSuffix = file.readUTF();
			tailFilePointer = file.readLong();
			
			if (LOG.isDebugEnabled()) LOG.debug("load index file - {}", toString());
		}
		
		protected synchronized void save() throws IOException {
			if (LOG.isDebugEnabled()) LOG.debug("save index file - {}", toString());
			
			file.seek(0);
			
			file.write(MAGIC);
			file.writeUTF(headFileSuffix);
			file.writeLong(headFilePointer);
			file.writeUTF(tailFileSuffix);
			file.writeLong(tailFilePointer);			
		}
		
		public void setSavingDelay(long delay) {
			this.delay = delay;
		}
		
		// ======
		
		public String getHeadFileSuffix() {
			return headFileSuffix;
		}
		
		public long getHeadFilePointer() {
			return headFilePointer;
		}
		
		public String getTailFileSuffix() {
			return tailFileSuffix;
		}
		
		public long getTailFilePointer() {
			return tailFilePointer;
		}
		
		// ======
		
		public synchronized void setTailFile(String suffix) {
			tailFileSuffix = suffix; // reset tail index
			tailFilePointer = 0L;
			
			lock.wakeup();
		}
		
		public synchronized void addTailFilePosition(int size) {
			tailFilePointer += size;
			
			lock.wakeup();
		}
		
		public synchronized void setHeadFile(String suffix) {
			headFileSuffix = suffix; // reset head index
			headFilePointer = 0L;
			
			lock.wakeup();
		}
		
		public synchronized void setHeadFilePosition(long position) {
			headFilePointer = position;
			
			lock.wakeup();
		}
		
		public synchronized void addHeadFilePosition(int size) {
			headFilePointer += size;
			
			lock.wakeup();
		}
		
		public synchronized boolean isEmpty() {
			if (headFileSuffix.equals(tailFileSuffix)) {
				if (headFilePointer >= tailFilePointer) {
					return true;
				}
			}
			
			return false;
		}
		
		// ======

		protected void saving() {
			while (thread != null) {
				try {
					lock.sleep(0); // wait until somebody wake me up
					
					Thread.sleep(delay); // during sleeping, the data is still coming
					
					lock.reset();
					
					save();
					
				} catch (InterruptedException e) {
					
				} catch (Exception e) {
					LOG.error(e.getMessage(), e);
				}			
			}
			
			LOG.warn("index file saving process is stopped!");
		}
		
		// ======
		
		@Override
		public String toString() {			
			return String.format("head: %s [%d], tail: %s [%d]", headFileSuffix, headFilePointer, tailFileSuffix, tailFilePointer);
		}
	}
}
