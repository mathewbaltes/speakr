/**
 * 
 */
package com.audiosyncdroidcast.client;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.audiosyncdroidcast.network.NetworkMessage;

/**
 * 
 * This class is used to connect to the server, sync the clock, and
 * wait for commands for server and then instruct the ui to
 * do what the server says to do.
 * 
 * @author Tyler Coffman, Sam Baldwin
 *
 */
public class UDPClient implements Runnable
{
	public static final int DT_MESSAGE = 1; //for message/handler stuff;
	public static final int playNow = 400;
	public static final int playAtTime = 401;
	public static final int changeSource = 402;

	private String stringIp;
	private DatagramSocket socket;
	private Handler handler;
	private InetAddress ipAddress;
	private int port;
	private boolean isKilled;
	private long timeDifference;
	
	/**
	 * Sets up the UDPClient
	 * @param handle the handler to the UI so that the client can send messages back to ui thread.
	 * @param ip ip address
	 * @param port port
	 */
	public UDPClient(Handler handle, String ip, int port)
	{
		this.stringIp = ip;
		
		this.port = port;
		
		isKilled = false;
		handler = handle;
	}
	
	/**
	 * Returns the clock difference between server and client.
	 * Also tells server that client is synchronized, so that the client
	 * will be added to ther server's client pool.
	 * TODO: Throw an exception if there's an error.
	 * @param servAddr
	 * @param servPort
	 * @return
	 */
	public long getServerTimeDifference(InetAddress servAddr, int servPort)
	{
		long serverTime = -1;
		long serverDelay = -1;
		long timeDiff = 0;
		int iterationCount = 100;
		long startTime;
		String serverTimeData = "0";
		String serverDelayData = "0";
		
		byte[] buffer = ("SERVER_PING").getBytes();
		DatagramPacket pingPacket = new DatagramPacket(buffer, buffer.length, servAddr, servPort);

		byte[] buffer2 = ("SERVER_TIME_REQUEST").getBytes();
		DatagramPacket timePacket = new DatagramPacket(buffer2, buffer2.length, servAddr, servPort); 

		byte[] respBuf = new byte[32];
		DatagramPacket respPacket = new DatagramPacket(respBuf, respBuf.length);
		
		byte[] respBuf2 = new byte[32];
		DatagramPacket respPacket2 = new DatagramPacket(respBuf2, respBuf2.length);

		byte[] success = ("SYNCHRONIZED").getBytes();
		DatagramPacket successPacket = new DatagramPacket(success, success.length, servAddr, servPort);
		//If the gc is likely to run soon, we want it to run now before we execute realtime code.
		System.gc();
		
		try 
		{
			// Sets the receive timeout so if there's no response after 50ms an exception is thrown.
			socket.setSoTimeout(1000);

			Log.d("UDPClient", "Beginning pings");
			
			long[] timeDiffList = new long[iterationCount];
			long[] lowestPingTime = new long[iterationCount];
			long elapsedTime = 0;
			long totalTimeDiff = 0;
			for(int x = 0; x < iterationCount; x++)
			{
				try
				{
					// Ask for time and adjust for one way travel time
					startTime = System.nanoTime();
					socket.send(timePacket);
					
					// Receive the first packet. (This contains the servers nanoTime)
					socket.receive(respPacket);
					// Mark the time that the p
					long receivedTime = System.nanoTime();
					elapsedTime = receivedTime - startTime;
					
					// Receive the second packet. This contains info on how long the server took to respond to the request
					socket.receive(respPacket2);

					serverTimeData = new String(respPacket.getData()).substring(0, respPacket.getLength());
					serverDelayData = new String(respPacket2.getData()).substring(0, respPacket2.getLength());

					serverDelay = Long.valueOf(serverDelayData);
					serverTime = Long.valueOf(serverTimeData) + (elapsedTime - serverDelay)/(long)2;
					
					if((serverDelay) / (long)1000000 > (200))
					{
						Log.d("UDPClient", "Server delay too large. Resending request");
						x--;
						continue;
					}
					
					Log.d("UDPClient", x + "). Time difference = " + (serverTime - receivedTime) / (long)1000000 + "ms");
					Log.d("UDPClient", x + "). Ping time = " + elapsedTime / (long)1000000 + "ms");
					Log.d("UDPClient", x + "). One way trip = " + ((elapsedTime - serverDelay)/(long)2) / (long)1000000 + "ms");
					Log.d("UDPClient", x + "). Server delay = " + (serverDelay) / (long)1000000 + "ms\n");

					timeDiffList[x] = (serverTime - receivedTime);
					lowestPingTime[x] =(elapsedTime - serverDelay) / (long)1000000;
					//totalTimeDiff += (serverTime - startProcessDelay);	

					for(int c = 0; c < 32; c++)
					{
						respBuf[c] = 0;
					}
				}
				catch(InterruptedIOException ie)
				{
					Log.d("UDPClient", "Packet was dropped. Resending request");
					x --;
					continue;
				}
			}

			int index = findMinDifferenceIndex(lowestPingTime);
			Log.d("UDPClient", "Shortest ping = " + findMinDifference(lowestPingTime) + ", index = " +  findMinDifferenceIndex(lowestPingTime));
			Log.d("UDPClient", "Calculating time difference");
			//timeDiff = totalTimeDiff / (long)iterationCount;
			timeDiff = timeDiffList[index];
			Log.d("UDPClient", "Time diff = " + timeDiff);

			socket.send(successPacket);
		}
		catch(Exception e)
		{
			Log.e("UDP", "C: Error in server time calculation", e);
		}
		return timeDiff;
	}

	/**
	 * Now that client is connected and synchronized, the client waits
	 * for messages from the server and handles the action requests.
	 */
	public void handleActionRequests()
	{
		// Turn off timeout
		try 
		{
			socket.setSoTimeout(0);
		} 
		catch (SocketException e) 
		{
			e.printStackTrace();
		}
		
		while(!isKilled)
		{
			byte[] ibuff = new byte[512];
			DatagramPacket packet = new DatagramPacket(ibuff, ibuff.length);
			try
			{
				socket.receive(packet);
				NetworkMessage msg = NetworkMessage.deSerialize(ibuff);
				
				if(msg == null)
				{
					Log.d("UDPClient", "Received overflow message");
					continue;
				}
				
				switch(msg.getType()) 
				{
				//the server is telling the client to play at the time given, 
				//adjusted for clock differences
				case playAtTime:
					//calculate local time at which to play.
					Long localPlayTime = ((Long)msg.getObj()).longValue() - timeDifference;
					
					Log.d("UDPClient", "Received play message. Playing in " + (float)((localPlayTime - System.nanoTime())/1000000000.0f) + " seconds");
					
					Message playAtTimeMsg = handler.obtainMessage(playAtTime, localPlayTime);
					handler.sendMessage(playAtTimeMsg);
					break;
				case changeSource:
					Log.d("UDPClient", "Received change source message.");
					Message changeSourceMsg = handler.obtainMessage(changeSource, null);
					handler.sendMessage(changeSourceMsg);
					break;
				default:
					break;
				}
			} 
			catch ( IOException ex )
			{	
				ex.printStackTrace();
				continue;
				//just keep going
			}
			
		}
	}
	
	/**
	 * Entry point of the thread.
	 */
	public void run() 
	{
		try 
		{
			//set up server ip address.
			this.ipAddress = InetAddress.getByName(stringIp);
		} 
		catch (UnknownHostException e1)
		{
			e1.printStackTrace();
		}
		
		try
		{
			//creating client datagram socket.
			Log.d("UDP", "C: Connecting...");
			socket = new DatagramSocket();
		}
		catch(SocketException e)
		{
			Log.e("UDP", "C: Error Connecting", e);
		}
		
		try
		{
			// Calculate server time difference
			timeDifference = getServerTimeDifference(ipAddress, port);
			
			handleActionRequests();
		}
		catch(Exception e)
		{
			Log.e("UDP", "C: Error", e);
		}
		
	}
	
	/**
	 * sets the ip address of the client.
	 * @param ip server ip address
	 * @param port server port.
	 */
	public void setIPAddress(InetAddress ip, int port)
	{
		this.ipAddress = ip;
		this.port = port;
	}
	
	private int findMinDifferenceIndex(long[] array)
	{
		long min = array[0];
		int index = 0;
		
		for(int counter = 1; counter < array.length; counter ++)
		{
			if(Math.abs(array[counter]) < Math.abs(min))
			{
				min = array[counter];
				index = counter;
			}
		}
		
		return index;		
	}
	
	private long findMinDifference(long[] array)
	{
		long min = array[0];
		
		for(int counter = 1; counter < array.length; counter ++)
		{
			if(Math.abs(array[counter]) < Math.abs(min))
			{
				min = array[counter];
			}
		}
		
		return min;		
	}
}
