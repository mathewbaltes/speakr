package com.audiosyncdroidcast.network;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

/**
 * 
 * @author Mathew Baltes
 * 
 *         Provides necessary network utilities for the application.
 * 
 */

public class NetworkUtils {

	/**
	 * Static function that retrieves the IP address from the given application
	 * context.
	 * 
	 * @param context
	 *            The application context from which we are stripping the IP
	 *            address.
	 * @return A string representation of the IP address.
	 */
	public static String getIpAddress(Context context) {
		
		
		/*
		 * Get the wifi manager context so that we can retrieve the info
		 */
		WifiManager wifiManager = (WifiManager) context
				.getSystemService(Context.WIFI_SERVICE);
		
		/*
		 * Get the connection info from the wifi manager
		 */
		WifiInfo wifiInfo = wifiManager.getConnectionInfo();
		/*
		 * Get the integer representation of the ip address.
		 */
		int ip = wifiInfo.getIpAddress();

		// Shift the bits until we have the proper number representation
		String ipString = String.format("%d.%d.%d.%d", (ip & 0xff),
				(ip >> 8 & 0xff), (ip >> 16 & 0xff), (ip >> 24 & 0xff));

		return ipString;
	}
}
