/**
 * 
 */
package com.audiosyncdroidcast.network;

import java.util.ArrayList;

/**
 * @author Sam Baldwin
 *
 */
public interface NetworkDiscoveryListener 
{
	public void deviceFound(NetworkDevice device, ArrayList<NetworkDevice> discoveredDevices);
	public void deviceRemoved(NetworkDevice device,  ArrayList<NetworkDevice> discoveredDevices);
}
