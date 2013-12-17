package com.audiosyncdroidcast.client;

import java.util.ArrayList;
import java.util.List;

import android.app.Dialog;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.audiosyncdroidcast.R;
import com.audiosyncdroidcast.ServerActivity;
import com.audiosyncdroidcast.network.NetworkAdapter;
import com.audiosyncdroidcast.network.NetworkDevice;
import com.audiosyncdroidcast.network.NetworkDiscoveryListener;
import com.audiosyncdroidcast.network.NetworkUtils;

/**
 * Provides the client with an interface to: 1) Join a network 2) Download audio
 * files from a network 3) Synchronize into the audio file from the network 4)
 * Play synchronized with the network
 * 
 * @author Mathew Baltes
 */

public class ClientActivity extends ListActivity implements NetworkDiscoveryListener
{
	private static final int REQUEST_ENABLE_BT = 1;
	
	private List<NetworkDevice> deviceList = null;
	private ListAdapter listAdapter = null;
	
	private NetworkAdapter networkAdapter;
	private BluetoothAdapter bluetoothAdapter;
	
	private boolean registeredBT;

	public void onCreate(Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState);

		// Set up content view
		setContentView(R.layout.activity_client);
		
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		// Set up ListView
		deviceList = new ArrayList<NetworkDevice>();
		listAdapter = new ListAdapter(this, deviceList);
		setListAdapter(listAdapter);		
		
		//Add host button listener to start server
		View hostButton = findViewById(R.id.activity_client_host);
		hostButton.setOnClickListener(new View.OnClickListener() 
		{
			public void onClick(View v) 
			{
				Dialog dialog = new Dialog(ClientActivity.this);
				dialog.setContentView(R.layout.dialog_host);

				dialog.setTitle("Choose a name:");
				
				final EditText editName = (EditText) dialog.findViewById(R.id.dialog_host_name);
				
				editName.requestFocus();
 
				final Button continueButton = (Button) dialog.findViewById(R.id.dialog_host_continue);
				continueButton.setOnClickListener(new View.OnClickListener()
				{
					public void onClick(View view) 
					{
						// Get the intent to switch from the client activity to the audio player
						// activity
						Intent intent = new Intent(ClientActivity.this, AudioPlayerActivity.class);
						
						// Insert information about the IP & Port of the server into the intent
						intent.putExtra("client", false);
						intent.putExtra("host", editName.getText().toString());
						
						// Launch the server activity
						startActivity(intent);
					}
				});

				dialog.show();
				
				
			}
		});
		
		//Add manual connect listener
		View manualConnectButton = findViewById(R.id.activity_client_manuallyConnect);
		manualConnectButton.setOnClickListener(new View.OnClickListener() 
		{
			public void onClick(View view) 
			{
				Dialog dialog = new Dialog(ClientActivity.this);
				dialog.setContentView(R.layout.dialog_ip_port);

				dialog.setTitle("Manually Connect:");
				
				final EditText ipEditText = (EditText) dialog.findViewById(R.id.dialog_ip_port_ip);
				final EditText portEditText = (EditText) dialog.findViewById(R.id.dialog_ip_port_port);
				
				ipEditText.setText(NetworkUtils.getIpAddress(getApplicationContext()));
				ipEditText.setSelection(ipEditText.getText().length());
				ipEditText.requestFocus();

				final Button connectButton = (Button) dialog.findViewById(R.id.dialog_ip_port_connect);
				connectButton.setOnClickListener(new View.OnClickListener()
				{
					public void onClick(View view) 
					{
						// Get the intent to switch from the client activity to the audio player
						// activity
						Intent intent = new Intent(ClientActivity.this, AudioPlayerActivity.class);
						
						// Insert information about the IP & Port of the server into the intent
						intent.putExtra("client", true);
						intent.putExtra("ip", ipEditText.getText().toString());
						intent.putExtra("port", Integer.parseInt(portEditText.getText().toString()));
						
						// Switch the activity
						startActivity(intent);
					}
				});

				dialog.show();
			}
		});
		
		//Add manual connect listener
		View refreshBtn = findViewById(R.id.activity_client_refresh);
		refreshBtn.setOnClickListener(new View.OnClickListener() 
		{
			public void onClick(View view) 
			{
				if(networkAdapter != null)
				{
					networkAdapter.refreshDiscovery();
				}
			}
		});
		// Set up the network adapter
		
		bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (bluetoothAdapter == null) 
		{
		    // Device does not support Bluetooth
		}
		
		if (!bluetoothAdapter.isEnabled()) 
		{
		    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
		    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
		}
		
		networkAdapter = new NetworkAdapter(bluetoothAdapter);
		
		networkAdapter.setNetworkDiscoveryListener(this);
		networkAdapter.startNetworkDiscovery(this);
	}
	
	protected void onActivityResult(int requestCode, int resultCode, Intent data) 
	{
		super.onActivityResult(requestCode, resultCode, data);
		
		if(requestCode == REQUEST_ENABLE_BT)
		{
			if(resultCode == RESULT_OK)
			{
				networkAdapter = new NetworkAdapter(bluetoothAdapter);
				
				IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
				registerReceiver(networkAdapter, filter);
				registeredBT = true;
				
				networkAdapter.setNetworkDiscoveryListener(this);
				networkAdapter.startNetworkDiscovery(this);
			}
			else
			{
				// Could not start BlueTooth
			}
		}
	}
	
	protected void onResume() 
	{
		super.onResume();
		if(networkAdapter != null && registeredBT == false)
		{
			IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
			registerReceiver(networkAdapter, filter);
			
			registeredBT = true;
		}
	}
	
	protected void onPause() 
	{
		super.onPause();
		
		if(networkAdapter != null && registeredBT == true)
		{
			unregisterReceiver(networkAdapter);
			registeredBT = false;
		}
	}
	/**
	 * Handles when the activity is destroyed.
	 */
	protected void onDestroy() 
	{
		super.onDestroy();

		if(networkAdapter != null && registeredBT == true)
		{
			unregisterReceiver(networkAdapter);
			registeredBT = false;
		}
		
		// Release MulticastLock
		if (JMDnsClient.multicastLock != null)
		{
			JMDnsClient.multicastLock.release();
			JMDnsClient.multicastLock = null;
		}
	}

	/**
	 * Handles when the user clicks an item from the list of available DJ's.
	 */
	protected void onListItemClick(ListView l, View v, int position, long id) 
	{
		super.onListItemClick(l, v, position, id);

		// Get the intent to switch from the client activity to the audio player
		// activity
		Intent intent = new Intent(this, AudioPlayerActivity.class);
		// Insert information about the IP & Port of the server into the intent
		NetworkDevice device = deviceList.get(position);
		intent.putExtra("client", true);
		intent.putExtra("ip", device.getIpAddress().toString().substring(1));
		intent.putExtra("port", device.getPort());
		
		// Switch the activity
		startActivity(intent);
	}

	// Network discovery stuff
	public void deviceFound(NetworkDevice device, ArrayList<NetworkDevice> discoveredDevices) 
	{
		deviceList.clear();
		deviceList.addAll(discoveredDevices);
		listAdapter.notifyDataSetChanged();
	}

	public void deviceRemoved(NetworkDevice device, ArrayList<NetworkDevice> discoveredDevices) 
	{
		deviceList.clear();
		deviceList.addAll(discoveredDevices);
		listAdapter.notifyDataSetChanged();
	}

	/**
	 * Custom list adapter for displaying a list of NetworkDevices
	 */
	private static class ListAdapter extends BaseAdapter 
	{
		private final List<NetworkDevice> deviceList;
		private final Context mContext;

		/**
		 * Creats a new BaseAdapter that specializes in displaying NetworkDevices
		 * 
		 * @param context
		 *            the context to generate new views from
		 * @param deviceList
		 *            the list of devices to display
		 */
		private ListAdapter(Context context, List<NetworkDevice> deviceList) {
			this.deviceList = deviceList;
			mContext = context;
		}

		/**
		 * Get the number of devices.
		 * @return The number of devices registered.
		 */
		public int getCount() {
			return deviceList.size();
		}

		/**
		 * Get the view from the list.
		 * @param position The position in the list array.
		 * @return The view object from the list.
		 */
		public Object getItem(int position) {
			return deviceList.get(position);
		}

		public long getItemId(int position) {
			return 0;
		}

		/**
		 * Add the server information into the list.
		 * @param position The position in the list array to place the item.
		 * @param convertView The view of the item.
		 * @param parent The parent of the item.
		 * @return The view of the device item.
		 */
		public View getView(int position, View convertView, ViewGroup parent) {
			NetworkDevice device = deviceList.get(position);

			String name = device.getDeviceName();
			//String ipAddress = device.getIpAddress().toString();
			//int port = device.getPort();

			if (convertView == null)
			{
				convertView = LayoutInflater.from(mContext).inflate(R.layout.list_item_service_info, parent, false);
			}

			TextView textView = (TextView) convertView.findViewById(R.id.list_item_service_info_text);
			
			textView.setText(name);

			return convertView;
		}
	}
}
