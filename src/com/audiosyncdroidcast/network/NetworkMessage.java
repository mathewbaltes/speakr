package com.audiosyncdroidcast.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * The structure of messages used in our protocol for communication between
 * authenticated clients and server.
 * @author Tyler Coffman
 *
 */
public class NetworkMessage implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	// simple latency measurement.
	public static final int ping = 1;
	// nano time request. send back nanoTime value from System.nanoTime()
	public static final int nanoTimeRequest = 2;
	// tell server that client is ready to be added to client list.
	public static final int clientSynchronized = 7;
	// acknowledge that client was added to pool.
	public static final int ackSynchronized = 3;
	// tell client to prepare to play. currently not used.
	public static final int prepareToPlay = 4; //gives an mp3 url for the song to prepare to play.
	// ready to play mp3 from url.
	public static final int readyToPlay = 5;
	// client is disconnecting from server, remove from client pool.
	public static final int disconnect = 6;
	
	private int _type;
	private Object _obj;
	
	/**
	 * 
	 * @return message type.
	 */
	public int getType() {
		return _type;
	}
	
	/**
	 * 
	 * @return message payload.
	 */
	public Object getObj() {
		return _obj;
	}
	
	/**
	 * Create network message
	 * @param type message type
	 * @param obj message payload.
	 */
	public NetworkMessage(int type, Object obj) {
		_type = type;
		_obj = obj;
	}
	
	/**
	 * Prepare to be sent over network.
	 * @param message
	 * @return
	 */
	public static byte[] serialize(NetworkMessage message)
	{
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ObjectOutput out = null;
		byte[] messageData=null;
		try {
			out = new ObjectOutputStream(bos);   
			out.writeObject(message);
			messageData = bos.toByteArray();
			
			out.close();
		  	bos.close();
		} 
		catch(Exception ex)
		{
			int x = 0;
			//TODO: handle exception
		}
	  	return messageData;
	  	//todo: maybe a finally situation?
	}
	
	/**
	 * Turn network data back into the object.
	 * @param bytes
	 * @return
	 */
	public static NetworkMessage deSerialize(byte[] bytes)
	{
		ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
		ObjectInputStream in = null;
		Object o = null;
		
		try 
		{
			in = new ObjectInputStream(bis);
			o = (NetworkMessage)in.readObject();
			bis.close();
			in.close();
		} catch(Exception ex)
		{
			//TODO: handle exception
		}

		return (NetworkMessage) o;
	}
}
