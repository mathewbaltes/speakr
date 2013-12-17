package com.audiosyncdroidcast.client;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.MulticastLock;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceListener;
import java.io.IOException;

/*
 * The JMDnsClient provides all network discovery functionality for the client.
 * @author Mathew Baltes
 */

public class JMDnsClient {

	public static MulticastLock multicastLock;
	
	/**
	 * Start a new JmDNS client
	 * 
	 * @param context
	 *            the context to create the multicast lock from
	 * @param multicastTag
	 *            the tag for the mDNS listener to listen for
	 * @param handler
	 *            the ServiceChangeHAndler that will receive the state change
	 *            calls
	 * @return null at the moment. should return a MulticastLock that needs to
	 *         be freed to prevent massive drain in battery.
	 */
	public static void start(Context context, String multicastTag, Handler handler) 
	{
		ServiceChangeListener listener = new ServiceChangeListener(handler);
		StartTask.Input input = new StartTask.Input(context, multicastTag, listener);
		new StartTask().execute(input);
	}

	/**
	 * This task creates a new multicast lock as well as registers the provided
	 * handler to a JmDNS client that listens to the given tag.
	 */
	private static class StartTask extends AsyncTask<StartTask.Input, Void, WifiManager.MulticastLock> 
	{

		protected WifiManager.MulticastLock doInBackground(Input... params) 
		{
			// Extract input data from payload
			Input input = params[0];
			Context context = input.context;
			String multicastTag = input.mdnsTag;
			ServiceChangeListener listener = input.listener;

			// Aquire a multicast lock
			WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
			
			//If its not null then its been acquired
			if (multicastLock == null)
			{
				multicastLock = wifi.createMulticastLock("AudioSyncDNSLock");
				multicastLock.setReferenceCounted(true);
				multicastLock.acquire();
			}

			// Bind listener to JmDNS instance that is listening to given tag
			JmDNS jmdns = null;
			try {
				jmdns = JmDNS.create();
				jmdns.addServiceListener(multicastTag, listener);
			} catch (IOException e) {
				e.printStackTrace();
			}

			// Return the lock we have aquired. this lock needs to be released
			// at some point.
			return multicastLock;
		}

		/**
		 * A wrapper for the input parameters for the Start Task
		 */
		public static class Input {
			public final Context context;
			public final String mdnsTag;
			public final ServiceChangeListener listener;

			/**
			 * Creates a new wrapper of input params for StartTask
			 * 
			 * @param context
			 *            the context to create the multicast lock from
			 * @param mdnsTag
			 *            the tag for the mDNS listener to listen for
			 * @param listener
			 *            the ServiceChangeListener that will receive the state
			 *            change calls
			 */
			public Input(Context context, String mdnsTag,
					ServiceChangeListener listener) {
				this.context = context;
				this.mdnsTag = mdnsTag;
				this.listener = listener;
			}
		}
	}

	/**
	 * This class listens to service changes and forwards them to a handler
	 * which can perform UI responses to the specific changes.
	 */
	private static class ServiceChangeListener implements ServiceListener {
		private final Handler mHandler;

		/**
		 * Creates a new listener that forwards all ServiceListener calls to the
		 * handler.
		 * 
		 * @param handler
		 *            This handler will receive all the ServiceListener method
		 *            calls asynchronously. Create this handler on the main UI
		 *            thread so that it can modify any UI elements.
		 */
		private ServiceChangeListener(Handler handler) {
			mHandler = handler;
		}

		/**
		 * Multicast Service Added Listener waits till the mDNS service is
		 * resolved and then added then sends the handler a message saying that
		 * its been added.
		 */
		public void serviceAdded(ServiceEvent serviceEvent) {
			Message message = mHandler.obtainMessage(ServiceChangeHandler.WHAT_SERVICE_ADDED, serviceEvent);
			mHandler.sendMessage(message);
		}

		/**
		 * Multicast Service Removed Listener waits till the mDNS service tells
		 * the client that the service has been unregistered. Then sends the
		 * handler a message saying that its been removed.
		 */
		public void serviceRemoved(ServiceEvent serviceEvent) {
			Message message = mHandler.obtainMessage(ServiceChangeHandler.WHAT_SERVICE_REMOVED, serviceEvent);
			mHandler.sendMessage(message);
		}

		/**
		 * Multicast Service Resolved Listener waits till the mDNS service is
		 * resolved then sends the handler a message saying that its been
		 * resolved.
		 */
		public void serviceResolved(ServiceEvent serviceEvent) {
			Message message = mHandler.obtainMessage(ServiceChangeHandler.WHAT_SERVICE_RESOLVED, serviceEvent);
			mHandler.sendMessage(message);
		}
	}

	/**
	 * This class will asynchronously receive any ServiceEvents in their
	 * respective ServiceListener interface methods. This is done by handling
	 * the events as a Handler in the handleMessage(Message) method. Thus, when
	 * you override that method, you must call it's super or else the respective
	 * ServiceListener methods will never be called.
	 */
	public abstract static class ServiceChangeHandler extends Handler implements ServiceListener 
	{
		private static final String TAG = ServiceChangeHandler.class.getName();
		public static final int WHAT_SERVICE_ADDED = 0,
				WHAT_SERVICE_REMOVED = 1, WHAT_SERVICE_RESOLVED = 2;

		/**
		 * This is where the messages are handled that are sent to the handler.
		 */
		public void handleMessage(Message msg) {
			// Make sure we can safely cast to a ServiceEvent object
			if (msg.obj instanceof ServiceEvent) {
				ServiceEvent event = (ServiceEvent) msg.obj;

				// Forward our instance of service event to the respective
				// interface method
				switch (msg.what) { 
				case WHAT_SERVICE_ADDED: // multicast DNS Service was added
											// after being resolved
					serviceAdded(event);
					break;
				case WHAT_SERVICE_REMOVED: // multicast DNS Service was removed
											// after being unregistered
					serviceRemoved(event);
					break;
				case WHAT_SERVICE_RESOLVED: // multicast DNS Service has been
											// discovered
					serviceResolved(event); // now finish resolving it and add
											// it.
					break;
				default:
					Log.d(TAG, "Unknown type of message. What is: " + msg.what);
					break;
				}
			} else {
				Log.d(TAG, "Unknown message object received.");
			}
		}
	}
}
