/**
 * 
 */
package com.audiosyncdroidcast.network;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;

import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import com.audiosyncdroidcast.Utils;
import com.audiosyncdroidcast.client.JMDnsClient;

/**
 * @author Sam Baldwin
 *
 */
public class NetworkAdapter extends BroadcastReceiver
{	
	private static final int REQUEST_ENABLE_BT = 1;
	
	
	private Context context;
	
	private DatagramSocket udpSocket;
	private BluetoothAdapter bluetoothAdapter;
	
	private ArrayList<NetworkDevice> discoveredDevices;
	
	private NetworkDiscoveryListener ndListener;
	
	public NetworkAdapter(BluetoothAdapter bluetoothAdapter)
	{
		this.bluetoothAdapter = bluetoothAdapter;
		
		discoveredDevices = new ArrayList<NetworkDevice>();
	}
	
	public void startNetworkDiscovery(Context context)
	{
		this.context = context;
	
		IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
		context.registerReceiver(this, filter);
		
		JMDnsHandler jmdnsHandler = new JMDnsHandler();
		JMDnsClient.start(context, Utils.getNetworkDiscoveryTag(), jmdnsHandler);
		
		bluetoothAdapter.startDiscovery();
	}
	
	// Used to refresh the bluetooth discovery
	public void refreshDiscovery()
	{
		bluetoothAdapter.startDiscovery();
	}
	
	public void setNetworkDiscoveryListener(NetworkDiscoveryListener listener)
	{
		ndListener = listener;
	}	
	
	// Bluetooth Discovery
	public void onReceive(Context context, Intent intent) 
	{
        String action = intent.getAction();
        // When discovery finds a device
        if (BluetoothDevice.ACTION_FOUND.equals(action))
        {
            // Get the BluetoothDevice object from the Intent
            BluetoothDevice bluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

            String ipAddress = null;
            try
            {
            	ipAddress = bluetoothDevice.getName().substring(0, bluetoothDevice.getName().indexOf(':'));
            }
            catch(Exception e)
            {
            	Log.d("NetworkAdapter: ", "Found improper device");
            	return;
            }
            int port = Integer.parseInt(bluetoothDevice.getName().substring(bluetoothDevice.getName().indexOf(':') + 1, bluetoothDevice.getName().indexOf(' ')));
            String name = bluetoothDevice.getName().substring(bluetoothDevice.getName().indexOf(' ') + 1);
            
            Log.d("NetworkAdapter: ", "Done");
            
            NetworkDevice device = null;
            Log.d("NetworkAdapter: ", "address = " + ipAddress);
            try 
            {
				device = new NetworkDevice(InetAddress.getByName(ipAddress), port, name);
			} 
            catch (UnknownHostException e)
			{
				e.printStackTrace();
			}
            
            if(!discoveredDevices.contains(device))
			{
				discoveredDevices.add(device);
				ndListener.deviceFound(device, discoveredDevices);
				Log.d("NetworkAdapter: ", "Found new Bluetooth device. Name = " + bluetoothDevice.getName());
			}
        }
    }
	
	// JMDNS Discovery
	private class JMDnsHandler extends JMDnsClient.ServiceChangeHandler 
	{

		public void serviceAdded(ServiceEvent serviceEvent) 
		{
			// Wait for serviceResolved
		}

		public void serviceRemoved(ServiceEvent serviceEvent) 
		{
			ServiceInfo info = serviceEvent.getInfo();
			
			NetworkDevice device;

			// If we have a qualifying service, then add it to our list
			if (info.getInet4Addresses().length > 0)
			{
				device = new NetworkDevice(info.getInet4Addresses()[0], info.getPort());
				
				if(!discoveredDevices.contains(device))
				{
					discoveredDevices.remove(device);
					ndListener.deviceRemoved(device, discoveredDevices);
				}
				
			}
		}

		public void serviceResolved(ServiceEvent serviceEvent)
		{
			// Obtain the info from the service event
			ServiceInfo info = serviceEvent.getInfo();
			
			NetworkDevice device;

			// If we have a qualifying service, then add it to our list
			if (info.getInet4Addresses().length > 0)
			{
				device = new NetworkDevice(info.getInet4Addresses()[0], info.getPort(), info.getName());
				
				if(!discoveredDevices.contains(device))
				{
					discoveredDevices.add(device);
					ndListener.deviceFound(device, discoveredDevices);
				}
			}
		}
	}
}
