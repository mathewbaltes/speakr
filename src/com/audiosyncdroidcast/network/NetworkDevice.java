/**
 * 
 */
package com.audiosyncdroidcast.network;

import java.net.InetAddress;

/**
 * @author Sam Baldwin
 *
 */
public class NetworkDevice 
{
	private InetAddress ipAddress;
	private int port;
	
	private String deviceName;
	
	public NetworkDevice(InetAddress ipAddress, int port)
	{
		this.ipAddress = ipAddress;
		this.port = port;
		
		this.deviceName = "Unknown";
	}
	
	public NetworkDevice(InetAddress ipAddress, int port, String deviceName)
	{
		this.ipAddress = ipAddress;
		this.port = port;
		
		this.deviceName = deviceName;
	}
	
	public InetAddress getIpAddress()
	{
		return ipAddress;
	}
	
	public int getPort()
	{
			return this.port;
	}
	
	public String getDeviceName()
	{
		return deviceName;
	}
}
