package com.audiosyncdroidcast;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager.LayoutParams;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.audiosyncdroidcast.client.JMDnsClient;
import com.audiosyncdroidcast.client.UDPClient;
import com.audiosyncdroidcast.network.NetworkUtils;
import com.audiosyncdroidcast.server.ServerController;
import com.audiosyncdroidcast.server.UDPServer;
import com.audiosyncdroidcast.server.httpd.CustomHTTPD;


/**
 * 
 * UDP Test Activity is the general activity that allows
 * the user to:
 * -Start a UDP Server that listens for UDP Clients connecting to it
 * -Connect to a UDP Server to test connections such as round trip
 * 	time calculations for latency.
 * -Send a command to listening clients to start playing music synchronized.
 * 
 * @author Sam Baldwin, Tyler Coffman
 *
 */
public class ServerActivity extends Activity {
	
	public static final int NEW_SONG_REQUEST = 0;
	public static final int CHANGE_SONG_REQUEST = 1;
	public static final int REQUEST_BLUETOOTH_ENABLED = 2;
	
	private String hostName;
	
	private BluetoothAdapter bluetoothAdapter;
	
	private UIHandler uiHandler;
	private TextView serverView;
	private TextView clientView;
	private TextView songText;

	private TextView serverIP;
	
	private ServerController server;
	private HandlerThread serverThread;
	
	private String playingSong;
	private CustomHTTPD httpServer;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_udp_test);
		
		// Prevent the screen from turning off
		getWindow().addFlags(LayoutParams.FLAG_KEEP_SCREEN_ON);
		
		Intent intent = getIntent();
		hostName = intent.getStringExtra("name");

		uiHandler = new UIHandler();

		final Button startServerButton = (Button) findViewById(R.id.activity_udp_startserver);
		final Button changeMusicButton = (Button) findViewById(R.id.activity_udp_changeMusic);
		final Button startMusicButton = (Button) findViewById(R.id.activity_udp_startmusic);

		serverIP = (TextView) findViewById(R.id.serverIP);
		serverView = (TextView) findViewById(R.id.serverView);
		songText = (TextView) findViewById(R.id.playingSong);
		
		serverThread = null;

		setUpMulticastLock();
		
		startServerButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				
				openMusicLibrary(NEW_SONG_REQUEST);
			}
		});
		
		changeMusicButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				
				openMusicLibrary(CHANGE_SONG_REQUEST);
			}
		});

		startMusicButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				if(server != null)
				{
					Message msg = server.obtainMessage(ServerController.play);
					server.sendMessage(msg);
				}
			}
		});
		
		bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (bluetoothAdapter == null) 
		{
		    // Device does not support Bluetooth
		}
	}
	
	

	public boolean onTouchEvent(MotionEvent event) {
		return false;
	}
	
	private void openMusicLibrary(int requestCode)
	{
		Intent i = new Intent(getApplicationContext(), MusicLibraryActivity.class);
		startActivityForResult(i, requestCode);
	}
	
	protected void onActivityResult(int requestCode, int resultCode, Intent data) 
	{
		super.onActivityResult(requestCode, resultCode, data);
		
		if(requestCode == CHANGE_SONG_REQUEST)
		{
			if(httpServer != null && server != null)
			{
				songText.setText("Song: " + new File(data.getExtras().getString("songTitle")).getName());
				httpServer.setFile(data.getExtras().getString("songTitle"));
				
				Message msg = server.obtainMessage(ServerController.changeMusicSource);
				server.sendMessage(msg);
			}
		}
		else if(requestCode == NEW_SONG_REQUEST)
		{
			playingSong = data.getExtras().getString("songTitle");
			songText.setText("Song: " + new File(playingSong).getName());
			
			if(httpServer != null)
			{
				httpServer.stop();
			}
			
			try 
			{
				httpServer = new CustomHTTPD();
				httpServer.setFile(playingSong);
			} 
			catch (IOException e) 
			{
				e.printStackTrace();
			}
			
			if(serverThread == null)
			{
				serverThread = new HandlerThread("serverThread");
				serverThread.start();
				server = new ServerController(serverThread.getLooper(), uiHandler);
			}
			
			server.newServer(hostName);
		}
		else if(requestCode == REQUEST_BLUETOOTH_ENABLED)
		{
			
		}
	}

	/**
	 * The UI Handler is a inner class that handles all of the UDP messages from
	 * the UDP server. This is all test code that is used by the embedded UDP
	 * client to test round trip latency.
	 */
	public class UIHandler extends Handler {
		
		public void handleMessage(Message msg) {
			if (msg.what == UDPServer.SYNC_MESSAGE)
			{
				serverView.setText((String) msg.obj);
			}
			else if (msg.what == UDPClient.DT_MESSAGE) 
			{
				clientView.setText("Server - Local = "
						+ ((Long) msg.obj).toString() + "ns");
			} 
			else if (msg.what == UDPServer.PORT_MESSAGE) 
			{
				serverIP.setText(NetworkUtils.getIpAddress(ServerActivity.this)
						+ ":"
						+ msg.obj.toString());
				
				// Set up bluetooth
				bluetoothAdapter.setName(NetworkUtils.getIpAddress(ServerActivity.this) + ":" + msg.obj.toString() + " " + hostName);
				
				Intent discoverableIntent = new
				Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
				discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 0);
				startActivityForResult(discoverableIntent, REQUEST_BLUETOOTH_ENABLED);
			}
			else if (msg.what == ServerController.addClient) 
			{
				Toast.makeText(ServerActivity.this, "Client " + ((InetAddress)msg.obj).toString() + " connected", Toast.LENGTH_SHORT).show();
			}
			else
			{
				Log.d("ServerActivity: ", "Received unknown message");
			}
		}
	}

	/**
	 * Set up the Multicast Lock in order to enable the phone to broadcast
	 * multicast DNS packets.
	 */
	private void setUpMulticastLock() {
		// Get the WifiManager from the application context
		WifiManager wifi = (android.net.wifi.WifiManager) getSystemService(android.content.Context.WIFI_SERVICE);
		// Create the multicast lock
		if (JMDnsClient.multicastLock == null)
		{
			JMDnsClient.multicastLock = wifi.createMulticastLock("AudioSyncDNSLock");
			JMDnsClient.multicastLock.setReferenceCounted(true);
			// Enable the lock and aquire it from the system
			JMDnsClient.multicastLock.acquire();
		}
		
	}
	
	/**
	 * Handles when the activity is destroyed.
	 */
	protected void onDestroy() {
		super.onDestroy();

		if(httpServer != null)
		{
			httpServer.stop();
		}
		
		// Release MulticastLock
		if (JMDnsClient.multicastLock != null)
		{
			JMDnsClient.multicastLock.release();
			JMDnsClient.multicastLock = null;
		}
	}
}
