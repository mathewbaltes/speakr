package com.audiosyncdroidcast.client;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
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
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;

import com.audiosyncdroidcast.MusicLibraryActivity;
import com.audiosyncdroidcast.R;
import com.audiosyncdroidcast.ServerActivity;
import com.audiosyncdroidcast.ServerActivity.UIHandler;
import com.audiosyncdroidcast.network.NetworkUtils;
import com.audiosyncdroidcast.server.ServerController;
import com.audiosyncdroidcast.server.UDPServer;
import com.audiosyncdroidcast.server.httpd.CustomHTTPD;

/**
 * The activity that is used to play the audio files.
 */
public class AudioPlayerActivity extends Activity implements OnPreparedListener, OnErrorListener {

	public static final String AUDIO_FILE_NAME = "File Name";
	
	public static final int NEW_SONG_REQUEST = 0;
	public static final int CHANGE_SONG_REQUEST = 1;
	public static final int REQUEST_BLUETOOTH_ENABLED = 2;

	// CLIENT
	private boolean isClient;
	
	private MediaPlayer mediaPlayer;
	private MediaController mediaController;
	
	private ClientHandler uiHandler;
	private ServerHandler serverHandler;
	
	private UDPClient udpClient;
	
	private int serverPort;
	private String serverAddress;
	
	private String url;
	
	// SERVER	
	private String hostName;
	
	private BluetoothAdapter bluetoothAdapter;	
	private ServerController server;
	private HandlerThread serverThread;
	private CustomHTTPD httpServer;
	
	private TextView serverView;
	private TextView serverIP;
	private TextView songText;
	
	private String playingSong;
	
	
	/**
	 * Entry point of the activity.
	 */
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_audio_player);
		
		// Prevent the screen from turning off. (
		getWindow().addFlags(LayoutParams.FLAG_KEEP_SCREEN_ON);
		
		// Increase thread priority to increase time delay consistency
		android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
		
		songText = ((TextView)findViewById(R.id.now_playing_text));
		serverIP = (TextView) findViewById(R.id.serverIP);
		serverView = (TextView) findViewById(R.id.serverView);
		
		serverView.setText("Song: ");
		serverView.setText("");
		serverIP.setText("");
		
		// Get the IP Address and Port number to connect to from the intent
		Intent intent = getIntent();
		
		if(intent.getBooleanExtra("client", true) == true)
		{
			serverAddress = intent.getStringExtra("ip");
			serverPort = intent.getIntExtra("port", 0);
			
			isClient = true;
			initClient(serverAddress, serverPort);
		}
		else
		{
			hostName = intent.getStringExtra("host");
			isClient = false;
			initServer(hostName);
		}
	}
	
	public void initClient(String ipAddress, int port)
	{		
		uiHandler = new ClientHandler();

		songText.setText(AUDIO_FILE_NAME);

		mediaController = new MediaController(this);
		mediaPlayer = new MediaPlayer();

		mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
		url = "http://" + serverAddress + ":" + CustomHTTPD.PORT + "/song.mp3";
		
		mediaPlayer.setOnPreparedListener(this);
		
		try 
		{
			mediaPlayer.setDataSource(url);
			mediaPlayer.prepareAsync();
			serverView.setText("Buffering");
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}

		udpClient = new UDPClient(uiHandler, serverAddress, serverPort);

		new Thread(udpClient).start();
	}
	
	public void initServer(String hostName)
	{
		this.hostName = hostName;
		
		serverHandler = new ServerHandler();
		uiHandler = new ClientHandler();

		final Button changeMusicBtn = (Button)findViewById(R.id.activity_client_RecommendMusic);
		final Button startMusicButton = (Button) findViewById(R.id.activity_client_playMusic);
		
		serverThread = null;
		serverAddress = null;

		setUpMulticastLock();
		
		changeMusicBtn.setText("Open Library");
		changeMusicBtn.setOnClickListener(new View.OnClickListener()
				{
					public void onClick(View view) 
					{
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

		mediaController = new MediaController(this);
		mediaPlayer = new MediaPlayer();
		mediaPlayer.setOnPreparedListener(this);
		mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
		
		openMusicLibrary(NEW_SONG_REQUEST);		
		
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
				
				if(serverAddress == null)
				{
					serverAddress = NetworkUtils.getIpAddress(AudioPlayerActivity.this);
				}
				
				url = "http://" + serverAddress + ":" + CustomHTTPD.PORT + "/song.mp3";
				
				mediaPlayer.setDataSource(url);
				mediaPlayer.prepareAsync();				
			} 
			catch (IOException e) 
			{
				e.printStackTrace();
			}
			
			if(serverThread == null)
			{
				serverThread = new HandlerThread("serverThread");
				serverThread.start();
				server = new ServerController(serverThread.getLooper(), serverHandler);
			}
			
			server.newServer(hostName);
		}
		else if(requestCode == REQUEST_BLUETOOTH_ENABLED)
		{
			
		}
	}
	
	/**
	 * Handles messages from the UDPClient.
	 * @author Tyler Coffman
	 *
	 */
	public class ClientHandler extends Handler {
		
	    public void handleMessage(Message msg) 
	    {			
			switch(msg.what) 
			{
			//plays at the time designated by the server.
			case UDPClient.playAtTime :				
				mediaPlayer.setVolume(0.0f, 0.0f);
				
				mediaPlayer.start();
			
				//makes song play faster.
				mediaPlayer.seekTo(0);

				long delay = System.nanoTime() + (((Long)msg.obj).longValue() - System.nanoTime())/2;
				while(System.nanoTime() < delay);

				long startTime = System.nanoTime();
				mediaPlayer.seekTo(0);
				long startDelay = System.nanoTime() - startTime;

				Log.d("AudioPlayerActivity: ", "Calculated Start delay = " + (float)(startDelay/1000000) + "ms");
				
				//wait until the right time.
				
				long timeToPlay = ((Long)msg.obj).longValue() - startDelay;
				while(System.nanoTime() <= timeToPlay);
				
				//startTime = System.nanoTime();
				mediaPlayer.seekTo(0);
				//long actualDelay = System.nanoTime() - startTime;
				
				mediaPlayer.setVolume(1.0f, 1.0f);
				
				//Log.d("AudioPlayerActivity: ", "Actual Start delay = " + (float)(actualDelay/1000000.0f) + "ms");
				Log.d("AudioPlayerActivity: ", "Media player started");
				
				break;
				
			case UDPClient.changeSource:
				
				Log.d("AudioPlayerActivity: ", "Changing audio source.");
		    	
		    	if(mediaPlayer.isPlaying())
		    	{
		    		mediaPlayer.pause();
		    		mediaPlayer.stop();
		    	}
		    
				try 
				{
					mediaPlayer.reset();
					mediaPlayer.setDataSource(url);
					mediaPlayer.setOnPreparedListener(AudioPlayerActivity.this);
					mediaPlayer.prepareAsync();
					Log.d("AudioPlayerActivity: ", "Retrieving song from: " + url);
					Log.d("AudioPlayerActivity: ", "Prepared.");

				} 
				catch (Exception e) 
				{
					e.printStackTrace();
				}
				
				break;
			default:
					break;
			}
	    }
	}
	
	public class ServerHandler extends Handler {
		
		public void handleMessage(Message msg) {
			if (msg.what == UDPServer.SYNC_MESSAGE)
			{
				serverView.setText((String) msg.obj);
			}
			else if (msg.what == UDPServer.PORT_MESSAGE) 
			{
				serverAddress = NetworkUtils.getIpAddress(AudioPlayerActivity.this);
				serverPort = Integer.parseInt(msg.obj.toString());
				serverIP.setText(serverAddress + ":" + msg.obj.toString());
				
				udpClient = new UDPClient(uiHandler, serverAddress, serverPort);

				new Thread(udpClient).start();
				
				// Set up bluetooth
				bluetoothAdapter.setName(NetworkUtils.getIpAddress(AudioPlayerActivity.this) + ":" + msg.obj.toString() + " " + hostName);
				
				Intent discoverableIntent = new
				Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
				discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 0);
				startActivityForResult(discoverableIntent, REQUEST_BLUETOOTH_ENABLED);
			}
			else if (msg.what == ServerController.addClient) 
			{
				Toast.makeText(AudioPlayerActivity.this, "Client " + ((InetAddress)msg.obj).toString() + " connected", Toast.LENGTH_SHORT).show();
			}
			else
			{
				Log.d("ServerActivity: ", "Received unknown message");
			}
		}
	}
	
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

	public void onPrepared(MediaPlayer mp) 
	{
		Toast.makeText(AudioPlayerActivity.this, "BUFFERING COMPLETE", Toast.LENGTH_SHORT).show();
		
		if(isClient)
		{
			serverView.setText("Ready");
		}
		
		Log.d("AudioPlayerActivity: ", "Buffering Complete.");
	}

	public boolean onError(MediaPlayer mp, int what, int extra) 
	{
		if(what == 1 && extra == -1004)
		{
			mediaPlayer.setOnPreparedListener(AudioPlayerActivity.this);
			mp.prepareAsync();
			
			Log.d("AudioPlayerActivity: ", "Found Error. Restarting media prep.");
		}
		return false;
	}
	
	public void onRadioButtonClicked(View view)
	{	    
	    switch(view.getId()) 
	    {
	    case R.id.radio_left:
	    	mediaPlayer.setVolume(1.0f, 0.0f);
	    	break;
	    case R.id.radio_mono:
	    	mediaPlayer.setVolume(1.0f, 1.0f);
	    	break;
	    case R.id.radio_right:
	    	mediaPlayer.setVolume(0.0f, 1.0f);
	    	break;
	    }
	}
	
	/**
	 * cleans up wake lock when activity is destroyed.
	 */
	protected void onDestroy() 
	{
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
		
		mediaPlayer.release();
	}
	
	/**
	 * cleans up media player when activity stops.
	 */
	protected void onStop() {
			super.onStop();
			mediaController.hide();
	}
	
	/**
	 * Shows the media controls when you touch the screen.
	 */
	public boolean onTouchEvent(MotionEvent event) {
		//the MediaController will hide after 3 seconds - tap the screen to make it appear again
		mediaController.show();
		return false;
	}
}