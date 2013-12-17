package com.audiosyncdroidcast.server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Map.Entry;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.util.Pair;

import com.audiosyncdroidcast.client.UDPClient;
import com.audiosyncdroidcast.network.NetworkMessage;

/**
 * Server Controller manages clients, manages the UDPServer
 * that listens for new clients, and sends control signals to all
 * client devices.
 * 
 * @author Tyler Coffman
 *
 */
public class ServerController extends Handler {
	public static final int addClient = 301;
	public static final int removeClient = 302;
	public static final int killServer = 303;
	public static final int play = 304;
	public static final int playAtTimeLocal = 305;
	public static final int changeMusicSource = 306;
	private HashMap<InetAddress, Integer> clientList;
	private DatagramSocket socket;
	private Handler uiHandler;
	private UDPServer clientListener;
	private UDPServer serverRef;
	
	Thread clientListenerThread;
	
	/**
	 * Creates a server controller with a looper for the thread this handler 
	 * is supposed to be run inside of.
	 * @param looper looper of the HandlerThread that is going to run this handler.
	 * @param uiHandle handle to the ui thread so we can send messages to the ui.
	 */
	public ServerController(Looper looper, Handler uiHandle) {
		super(looper);
		uiHandler = uiHandle;
		try 
		{
			socket = new DatagramSocket();
		} 
		catch (SocketException e) 
		{
			e.printStackTrace();
		}
		
		clientListener = null;
	}
	
	public void newServer(String hostName)
	{
		clientList = new HashMap<InetAddress, Integer >();
		
		if(clientListener != null)
		{
			
			Log.d("ServerController", "Shutting down previous server");
			
			serverRef = clientListener;
			Message msg = this.obtainMessage(ServerController.killServer);
			this.sendMessage(msg);
		}
		
		clientListener = new UDPServer(this, hostName);
		(clientListenerThread = new Thread(clientListener)).start();
	}
	
	/**
	 * The message loop of this handler.
	 * @param msg Incoming message
	 */
    public void handleMessage(Message msg) 
    {
		switch(msg.what)
		{
		// addClient : a client has been properly authenticated and it's time to add the
		// client to the client pool.
		case addClient :
			Pair<InetAddress, Integer> newClient = (Pair<InetAddress, Integer>) msg.obj;
			if(!clientList.containsKey(newClient.first))
			{
				clientList.put(newClient.first, newClient.second);
			}
			Log.i("ServerController", "Client Added (" + newClient.first.toString() + ")");
			//client connected
			Message clientAddedMsg = uiHandler.obtainMessage(addClient, newClient.first);
			uiHandler.sendMessage(clientAddedMsg);
			break;
		// tells the handler to remove the client from the client pool.
		case removeClient :
			InetAddress theClient = (InetAddress) msg.obj;
			if(clientList.containsKey(theClient)) 
			{
				clientList.remove(theClient);
				//TODO: alert UI that client has disconnected.
			}
			break;
		// Tells the server controller to tell all clients to play immediately
		// without regards for synchronization.
		case UDPClient.playNow : 
			NetworkMessage playNowMessage = new NetworkMessage(UDPClient.playNow, null);
			byte[] buffer = NetworkMessage.serialize(playNowMessage);
			try
			{
				for(Entry<InetAddress, Integer> entry : clientList.entrySet()) {
					DatagramPacket playNowPacket = new DatagramPacket(buffer, buffer.length, 
							entry.getKey(), entry.getValue());
					socket.send(playNowPacket);
				}
			} catch (IOException ex)
			{
				Log.e("ServerController", ex.getMessage());				
			}
			break;
		// Play at a common time across all devices and server.
		
		case changeMusicSource:
			Log.d("ServerController: ", "Sending change source message");
			NetworkMessage changeSourceMessage = new NetworkMessage(UDPClient.changeSource, null);
			byte[] bufferChangeSource = NetworkMessage.serialize(changeSourceMessage);
			try
			{
				for(Entry<InetAddress, Integer> entry : clientList.entrySet()) {
					DatagramPacket changeSourcePacket = new DatagramPacket(bufferChangeSource, bufferChangeSource.length, 
							entry.getKey(), entry.getValue());
					socket.send(changeSourcePacket);
				}
			} catch (IOException ex)
			{
				Log.e("ServerController", ex.getMessage());				
			}
			
			break;
			
		case play:
			Log.d("ServerController: ", "Received play message");
			long playTime = System.nanoTime();
			playTime += 2000000000L;
			Log.d("ServerController: ", "playTime = " + playTime);
			NetworkMessage playTimeMessage = new NetworkMessage(UDPClient.playAtTime, new Long(playTime));
			byte[] bufferPlayTime = NetworkMessage.serialize(playTimeMessage);
			try
			{
				for(Entry<InetAddress, Integer> entry : clientList.entrySet()) 
				{
					DatagramPacket playTimePacket = new DatagramPacket(bufferPlayTime, bufferPlayTime.length, 
							entry.getKey(), entry.getValue());
					socket.send(playTimePacket);
				}
			}
			catch (IOException ex)
			{
				Log.d("ServerController: ", "Error sending playAtTime message");
				ex.printStackTrace();
			}
			
			// This could be wrong. Needs to be tested
			Message playTimeLocalMsg = uiHandler.obtainMessage(UDPClient.playAtTime, new Long(playTime));
			uiHandler.sendMessage(playTimeLocalMsg);
			break;
		// kills the server.
		case killServer :
			//kills udp server
			clientListener.end();
			break;
		// default behavior is to pass the message recieved on to the UI if it doesn't
	    // know how to handle it.
		default :
			Message passedOnMessage = uiHandler.obtainMessage(msg.what, msg.obj);
			uiHandler.sendMessage(passedOnMessage);
			break;
		}
	}
}
