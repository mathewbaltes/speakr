/**
 * 
 */
package com.audiosyncdroidcast.network;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * @author Sam Baldwin
 *
 */
public class NetworkPacket 
{
	public static final byte PING_REQUEST = 0;
	public static final byte TIME_REQUEST = 1;
	public static final byte SYNC_NOTIFICATION = 2;
	public static final byte SONG_RECOMMENDATION = 3;
	public static final byte PLAY = 4;
	public static final byte PLAY_WITH_SEEK = 5;
	public static final byte PAUSE = 6;
	public static final byte SEEK = 7;
	public static final byte CHANGE_SOURCE = 8;
	
	private byte[] buffer;
	private int bufferLength;
	
	private InetAddress ipAddress;
	private int port;
	
	public NetworkPacket(byte packetType)
	{
		buffer = new byte[5];
		buffer[0] = packetType;
	}
	
	public NetworkPacket(byte packetType, int identifierCode)
	{
		this(packetType);
		
		byte[] tmp  = ByteBuffer.allocate(4).putInt(identifierCode).order(ByteOrder.LITTLE_ENDIAN).array();
		buffer[1] = tmp[0];
		buffer[2] = tmp[1];
		buffer[3] = tmp[2];
		buffer[4] = tmp[3];
	}
	
	public NetworkPacket(byte[] buffer, int bufferLength)
	{
		this.buffer = buffer;
		this.bufferLength = bufferLength;
		this.ipAddress = null;
		this.port = -1;
	}
	
	public NetworkPacket(byte[] buffer, int bufferLength, InetAddress ipAddress, int port)
	{
		this.buffer = buffer;
		this.bufferLength = bufferLength;
		this.ipAddress = ipAddress;
		this.port = port;
	}
	
	public byte[] getData()
	{
		return buffer;
	}
	
	public int getLength()
	{
		return bufferLength;
	}
	
	public InetAddress getDestAddress()
	{
		return ipAddress;
	}
	
	public int getDestPort()
	{
		return port;
	}
	
}
