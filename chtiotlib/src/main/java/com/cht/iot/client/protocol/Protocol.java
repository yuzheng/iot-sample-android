package com.cht.iot.client.protocol;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.DigestException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

public class Protocol {
	public static final int MAGIC_HEAD_HI = 0x0A4;
	public static final int MAGIC_HEAD_LO = 0x0B2;

	public static final int COMMAND_ANNOUNCE = 0x00;
	public static final int COMMAND_CONNECT_REQUEST = 0x01;
	public static final int COMMAND_CHALLENGE_REQUEST = 0x01;
	public static final int COMMAND_CHALLENGE_REPLY = 0x081;
	public static final int COMMAND_INTRODUCE_REQUEST = 0x02;
	public static final int COMMAND_INTRODUCE_REPLY = 0x82;
	public static final int COMMAND_PING_REQUEST = 0x03;
	public static final int COMMAND_PING_REPLY = 0x083;
	public static final int COMMAND_READ_REQUEST = 0x0A;
	public static final int COMMAND_READ_REPLY = 0x8A;
	public static final int COMMAND_WRITE_REQUEST = 0x0B;	
	public static final int COMMAND_WRITE_REPLY = 0x8B;	
	
	public static final int MAXIMUM_VALUE = 255;


	public static final String CIPHER_AES = "AES";
	
	public static int checksum(byte[] body) {
		int checksum = 0;		
		for (byte b : body) {
			checksum = checksum + (b & 0x0FF);
		}		
		return checksum & 0x0FF;
	}
	
	// ======
	
	public static int read(InputStream is) throws IOException {
		
		int b = is.read();
		//System.out.println(b);
		if (b < 0) {
			throw new EOFException("EOF");
		}
		return b;
	}
	
	public static byte[] read(InputStream is, byte[] bytes) throws IOException {
		int i = 0;
		int s;
		while ((i < bytes.length) && ((s = is.read(bytes, i, bytes.length - i)) > 0)) {
			i += s;
		}
		
		if (i != bytes.length) {
			throw new EOFException("EOF");
		}
		
		return bytes;
	}
	
	public static byte[] readPacketBody(InputStream is) throws IOException {
		if ((read(is) != MAGIC_HEAD_HI) || (read(is) != MAGIC_HEAD_LO)) {
			throw new IOException("Magic Head is illegal.");
		}
		
		int length = (read(is) << 8) | read(is); // FIXME - avoid the illegal length (too huge)

		byte[] body = read(is, new byte[length]);
		
		int checksum = checksum(body);

		if (read(is) != checksum) {
			throw new IOException("Checksum is wrong.");
		}
		
		return body;
	}
	
	// ======
	
	public static byte[] buildPacket(byte[] body) {
		int checksum = checksum(body);
		
		byte[] packet = new byte[2 + 2 + body.length + 1]; // head + length + body + checksum
		
		packet[0] = (byte) MAGIC_HEAD_HI;
		packet[1] = (byte) MAGIC_HEAD_LO;
		packet[2] = (byte) ((body.length >> 8) & 0x0FF);
		packet[3] = (byte) (body.length & 0x0FF);
		System.arraycopy(body, 0, packet, 4, body.length);
		packet[packet.length - 1] = (byte) checksum;
		
		return packet;		
	}
	
	public static byte[] buildAnnouncePacket(String vendor, String model, String series, String name) throws IOException {
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		body.write(COMMAND_ANNOUNCE); // command
		
		body.write(vendor.getBytes());
		body.write(0); // zero tail
		
		body.write(model.getBytes());
		body.write(0); // zero tail
		
		body.write(series.getBytes());
		body.write(0); // zero tail
		
		body.write(name.getBytes());
		body.write(0); // zero tail
		
		return buildPacket(body.toByteArray());
	}
	
	public static String readString(InputStream is) throws IOException {
		
		StringBuilder sb = new StringBuilder();
		for (;;) {
			int c = read(is);
			if (c == 0x00) {
				break;
			}
			
			sb.append((char) c);
		}
		
		return sb.toString();
	}
	
	public static byte[] decrypt(InputStream is, String cipherMethod, String key) throws IOException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException {
		String encrpyStr = getStringFromInputStream(is);
		System.out.println("getStringFromInputStream:"+encrpyStr);
		byte[] body = parseHexStr2Byte(encrpyStr);  
		
		if(cipherMethod.equalsIgnoreCase(CIPHER_AES)){
			Cipher cipher = Cipher.getInstance("AES");
			cipher.init(Cipher.DECRYPT_MODE, aesKey(key));
			byte[] original = cipher.doFinal(body);
			return original;
		}
		return body;	
	}
	
	public static byte[] encrypt(byte[] content, String cipherMethod, String key) throws IOException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException {
		
		if(cipherMethod.equalsIgnoreCase(CIPHER_AES)){
			Cipher cipher = Cipher.getInstance("AES");
			cipher.init(Cipher.ENCRYPT_MODE, aesKey(key));
			byte[] encryptResult = cipher.doFinal(content);
			String encryptResultStr = new String(parseByte2HexStr(encryptResult));
			return encryptResultStr.getBytes();
		}
		return content;	
	}
	
	public static byte[] digest(byte[] bytes) throws NoSuchAlgorithmException, DigestException {
		return digest(bytes, 0, bytes.length);
	}
	
	public static byte[] digest(byte[] bytes, int offset, int length) throws NoSuchAlgorithmException, DigestException {
		MessageDigest md = MessageDigest.getInstance("MD5");
		md.update(bytes, offset, length);
		//md.digest(bytes, offset, length);
		return md.digest();
	}
	
	public static boolean equals(byte[] src, int srcOffset, byte[] dst, int dstOffset, int length) {
		for (int i = 0;i < length;i++) {
			//System.out.format("equals: %d %d \n", src[srcOffset + i], dst[dstOffset + i]);
			if (src[srcOffset + i] != dst[dstOffset + i]) {
				return false;
			}
		}
		return true;
	}
	
	// 2016.08.06 Yu-Cheng Wang
	public static byte[] buildConnectReqPacket() throws IOException {
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		body.write(COMMAND_CHALLENGE_REQUEST); // command
		
		return buildPacket(body.toByteArray());
	}
	
	public static byte[] buildChallengeReqPacket(String salt) throws IOException {
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		body.write(COMMAND_CHALLENGE_REQUEST); // command
		
		body.write(salt.getBytes());
		body.write(0); // zero tail
		
		return buildPacket(body.toByteArray());
	}
	
	public static byte[] buildChallengeReplyPacket(String salt, String apiKey) throws IOException, NoSuchAlgorithmException, DigestException {
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		body.write(COMMAND_CHALLENGE_REPLY); // command
		
		String s = salt + apiKey;
		
		byte[] md5 = digest(s.getBytes());
		
		body.write(md5);
		body.write(0); // zero tail
		
		return buildPacket(body.toByteArray());
	}
	
	public static byte[] buildIntroduceReqPacket(String cipher, String extra) throws IOException {
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		body.write(COMMAND_INTRODUCE_REQUEST); // command
		
		body.write(cipher.getBytes());
		body.write(0); // zero tail
		
		body.write(extra.getBytes());
		body.write(0);
		
		return buildPacket(body.toByteArray());
	}
	
	// 2016.08.06 Yu-Cheng Wang
	public static byte[] buildIntroduceReplyPacket() throws IOException {
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		body.write(COMMAND_INTRODUCE_REPLY); // command
			
		return buildPacket(body.toByteArray());
	}
	
	public static byte[] buildPingReqPacket() throws IOException {
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		body.write(COMMAND_PING_REQUEST); // command
		
		return buildPacket(body.toByteArray());
	}
	
	public static byte[] buildPingReplyPacket() throws IOException {
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		body.write(COMMAND_PING_REPLY); // command
		
		return buildPacket(body.toByteArray());
	}
	
	public static byte[] buildReadReqPacket(String deviceId, String sensorId) throws IOException {
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		body.write(COMMAND_READ_REQUEST); // command
		
		body.write(deviceId.getBytes());
		body.write(0); // zero tail
		
		body.write(sensorId.getBytes());
		body.write(0); // zero tail
		
		return buildPacket(body.toByteArray());
	}
	
	public static byte[] buildReadReqPacket(String deviceId, String sensorId, String cipherMethod, String key) throws IOException, InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException {
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		body.write(COMMAND_READ_REQUEST); // command
		
		ByteArrayOutputStream ebody = new ByteArrayOutputStream();
		ebody.write(deviceId.getBytes());
		ebody.write(0); // zero tail
		
		ebody.write(sensorId.getBytes());
		ebody.write(0); // zero tail
		
		body.write(encrypt(ebody.toByteArray(), cipherMethod, key));
		
		return buildPacket(body.toByteArray());
	}
	
	// 2016.08.06 Yu-Cheng Wang
	public static byte[] buildReadReplyPacket() throws IOException {
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		body.write(COMMAND_READ_REPLY); // command
				
		return buildPacket(body.toByteArray());
	}
	
	public static byte[] buildWriteReqPacket(String deviceId, String sensorId, String[] value) throws IOException {
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		body.write(COMMAND_WRITE_REQUEST); // command
		
		body.write(deviceId.getBytes());
		body.write(0); // zero tail
		
		body.write(sensorId.getBytes());
		body.write(0); // zero tail
		
		if(value.length > MAXIMUM_VALUE){
			throw new IOException("The maximum of values is 255!");
		}else{
			body.write(value.length & 0x0FF); // TODO - maximum 255
		}
		
		for (String s : value) { // FIXME - check number of the value
			body.write(s.getBytes());
			body.write(0);
		}
		
		return buildPacket(body.toByteArray());
	}
	
	public static byte[] buildWriteReqPacket(String deviceId, String sensorId, String[] value, String cipherMethod, String key) throws IOException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		body.write(COMMAND_WRITE_REQUEST); // command
		
		ByteArrayOutputStream ebody = new ByteArrayOutputStream();
		ebody.write(deviceId.getBytes());
		ebody.write(0); // zero tail
		
		ebody.write(sensorId.getBytes());
		ebody.write(0); // zero tail
		
		if(value.length > MAXIMUM_VALUE){
			throw new IOException("The maximum of values is 255!");
		}else{
			ebody.write(value.length & 0x0FF); // TODO - maximum 255
		}
		
		for (String s : value) { // FIXME - check number of the value
			ebody.write(s.getBytes());
			ebody.write(0);
		}
		
		body.write(encrypt(ebody.toByteArray(), cipherMethod, key));	
		
		return buildPacket(body.toByteArray());
	}
	
	// 2016.08.06 Yu-Cheng Wang
	public static byte[] buildWriteReplyPacket() throws IOException {
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		body.write(COMMAND_WRITE_REPLY); // command
					
		return buildPacket(body.toByteArray());
	}
	
	public static SecretKeySpec aesKey(String key) throws InvalidKeyException {
		if (key == null) {
			throw new InvalidKeyException("Key can not be null!");
		}
		
		if(key.length() < 2) {
			throw new InvalidKeyException("The key length must be 16!");
		}else{
			key = key.substring(2);
			if(key.length() != 16) {
				throw new InvalidKeyException("The key length must be 16!");
			}
		}
		
		return new SecretKeySpec(key.getBytes(), "AES");
	}
	
	public static String getStringFromInputStream(InputStream is) {

		BufferedReader br = null;
		StringBuilder sb = new StringBuilder();

		String line;
		try {

			br = new BufferedReader(new InputStreamReader(is));
			while ((line = br.readLine()) != null) {
				sb.append(line);
			}

		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		return sb.toString();

	}

	
	public static String parseByte2HexStr(byte buf[]) {  
        StringBuffer sb = new StringBuffer();  
        for (int i = 0; i < buf.length; i++) {  
        	String hex = Integer.toHexString(buf[i] & 0xFF);  
           	if (hex.length() == 1) {  
               	hex = '0' + hex;  
           	}  
            sb.append(hex.toUpperCase());  
        }  
        return sb.toString();  
	}  
	
	public static byte[] parseHexStr2Byte(String hexStr) {  
        if (hexStr.length() < 1)  
                return null;  
        byte[] result = new byte[hexStr.length()/2];  
        for (int i = 0;i< hexStr.length()/2; i++) {  
                int high = Integer.parseInt(hexStr.substring(i*2, i*2+1), 16);  
                int low = Integer.parseInt(hexStr.substring(i*2+1, i*2+2), 16);  
                //int low = "".equals(hexStr.substring(i*2+1, i*2+2)) ? 0 : Integer.parseInt(hexStr.substring(i*2+1, i*2+2), 16);  
                result[i] = (byte) (high * 16 + low);  
        }  
        return result;  
	}  
	// ======
	
	public static final void debug(byte[] bytes) {		
		for (byte b : bytes) {
			System.out.printf("%02X ", b);
		}
		System.out.println();
	}
}
