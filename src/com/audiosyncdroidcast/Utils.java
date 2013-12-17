package com.audiosyncdroidcast;

import android.app.Activity;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

/**
 * 
 * @author Mathew Baltes
 * 
 *         Provides necessary network utilities for the application.
 * 
 */

public class Utils extends Activity {

	private static String networkDiscoveryTag = "_audiosync._udp.local.";
	
	/*
	 * Returns the netork discovery tag for jmDNS to use.
	 */
	public static String getNetworkDiscoveryTag() {
		return networkDiscoveryTag;
	}
	
	
}
