/**
 *
 */
package com.audiosyncdroidcast.server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

import android.content.Context;
import android.net.wifi.WifiManager.MulticastLock;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.util.Pair;

import com.audiosyncdroidcast.client.UDPClient;
import com.audiosyncdroidcast.network.NetworkUtils;

/**
 * UDPServer listens for clients to connect, then sets up time synchronization.
 * Once the client has synchronized, it tells the server controller that owns it
 * to add the client to the client pool on the server.
 * 
 * @author Tyler Coffman, Sam Baldwin
 * 
 */
public class UDPServer implements Runnable {
	public static final int SYNC_MESSAGE = 2; // for handle/message stuff.
	public static final int PORT_MESSAGE = 3;

	private String hostName;
	
	public static final String SERVER_IP = "127.0.0.1";
	private DatagramSocket socket;
	private Handler handler;
	private JmDNS jmDns;
	private ServiceInfo serviceInfo;
	private volatile boolean isKilled;


	/**
	 * Prepares the UDPServer
	 * @param handle the handler that this object will send messages to. Probably the server controller's handler
	 */
	public UDPServer(Handler handle, String hostName)
	{
		this.hostName = hostName;
		
		isKilled = false;
		handler = handle;
		Message message = handler.obtainMessage(SYNC_MESSAGE);
		Message ipMessage = handler.obtainMessage(PORT_MESSAGE);
		try 
		{
			Log.d("UDP", "S: Booting up...");
			socket = new DatagramSocket();
			ipMessage.obj = new Integer(socket.getLocalPort()).toString();

			// alert ui that the server is running
			message.obj = "Server running.";
		} 
		catch (Exception e) 
		{
			Log.e("UDP", "S: Error", e);
			message.obj = "Could not listen on port " + socket.getLocalPort();
			ipMessage.obj = "Could not bind socket";
		}
		handler.sendMessage(message);
		handler.sendMessage(ipMessage);
	}

	/**
	 * Thread entry point.
	 */
	public void run() {
		try {
			// make this service discoverable.
			jmDns = JmDNS.create("0.0.0.0");
			serviceInfo = ServiceInfo.create("_audiosync._udp.local.",
					hostName, socket.getLocalPort(), hostName);

			//the packet listen loop. Waits for clients to connect.
			jmDns.registerService(serviceInfo);
			
			Log.d("UDP", "S: Running...");
			while (!isKilled()) 
			{
				byte[] buffer = new byte[64];

				DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
				
				socket.receive(packet);
				Log.d("UDP", "S: Received message");
				
				long receptionTime = System.nanoTime();
				
				String data = new String(packet.getData()).substring(0,
						packet.getLength());
				// Log.d("UDP", "S: Received " + packet.getLength() +
				// " bytes: '" + data + "'");

				// SERVER_TIME_REQUEST : The client wants the value of System.nanoTime on server.
				// Should open up a new thread to handle the packet and reply.
				// However, immediately replying to ping requests could improve
				// response speed
				if (data.equals("SERVER_TIME_REQUEST")) 
				{
					// No method for converting long to byte[]. We could easily
					// write one but I decided to turn it into a String for now.
					// Just make sure that the Client converts from String into
					// long when it receives the reply.

					byte[] respBuf = new Long(receptionTime).toString().getBytes();
					DatagramPacket response = new DatagramPacket(respBuf,
							respBuf.length, packet.getAddress(),
							packet.getPort());
					
					long sendDuration = System.nanoTime() - receptionTime;
					socket.send(response);
					
					// Send second packet that describes how long the previous packet took to send
					respBuf = new Long(sendDuration).toString().getBytes();
					response = new DatagramPacket(respBuf,
							respBuf.length, packet.getAddress(),
							packet.getPort());
					socket.send(response);
					
					// Log.d("UDP", "S: Sent reply to " + packet.getAddress() +
					// ":" + packet.getPort()+ ": '" + new
					// String(respBuf).substring(0, response.getLength()) +
					// "'");
					
				// SERVER_PING : simply return a ping so client can measure latency.
				} 
				else if (data.equals("SERVER_PING")) 
				{
					byte[] respBuf = ("SERVER_PING_ACK").getBytes();
					DatagramPacket response = new DatagramPacket(respBuf,
							respBuf.length, packet.getAddress(),
							packet.getPort());
					socket.send(response);
				} 
				// SYNCHRONIZED : Client is ready to be added to client pool.
				// The client's information will be sent to the handler that owns this.
				// So that the object that manages this server can add client to client pool.
				else if (data.equals("SYNCHRONIZED"))
				{
					Message message = handler.obtainMessage(SYNC_MESSAGE);
					message.obj = "Client Synchronized.";
					handler.sendMessage(message);
					Message addClientMsg = handler.obtainMessage(ServerController.addClient);
					addClientMsg.obj = new Pair<InetAddress, Integer>(
							packet.getAddress(), packet.getPort());
					handler.sendMessage(addClientMsg);
				} 
				else if (data.equals("RECOMMEND_SONG")) 
				{
					
				}
				else if (data.equals("KILLED")) 
				{
					if(isKilled())
					{
						break;
					}
				}
			}
			
			socket.close();
			jmDns.unregisterService(serviceInfo);
			
			Log.d("UDP", "S: Server shut down.");
			
		} catch (Exception e) {
			e.printStackTrace();
			Log.e("UDP", "S: Error", e);
		}

	}

	/**
	 * getAddress
	 * @return returns the address of the local device as seen by clients.
	 */
	public InetAddress getAddress() {
		return socket.getInetAddress();
	}

	/**
	 * Kills this thread
	 */
	public synchronized void end() {
		isKilled = true;
		
		byte[] buffer = ("KILLED").getBytes();
		DatagramPacket packet = new DatagramPacket(buffer, buffer.length, socket.getLocalAddress(), socket.getLocalPort());
		try {
			DatagramSocket tempSocket = new DatagramSocket();
			tempSocket.send(packet);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			Log.e("UDP", e.getStackTrace().toString());
		}
		
	}
	
	private synchronized boolean isKilled()
	{
		return isKilled;
	}
}
