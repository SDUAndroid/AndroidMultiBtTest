// Anders Bøgild, September 2012
package tek.andb.multibttest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.UUID;

import android.app.IntentService;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.util.Log;

// Connecting the socket (blocking) is handled by an intent service which processes one intent at the time.
// This gives us an ease way of queueing and executing connection requests one-by-one if we want to connect to many sockets.

// NB: An IntentService has _one_ worker thread that handles intents one at the time from a queue. Cannot be interrupted once queued!

public class BtConnectorIntentService extends IntentService {

	public static final String BT_DEVICE_MAC = "mac";
	public static final String BT_DEVICE_STREAM_ID = "id";
	public static final String BT_NEW_DATA_INTENT = "tek.andb.multibttestbt.BT_NEW_DATA_INTENT";
	public static final String BT_NEW_DATA_INTENT_EXTRA_BT_DATA = "bt_data";
	public static final String BT_NEW_DATA_INTENT_EXTRA_BT_DATA_STREAM_ID= "bt_id";
	
	private static final UUID RFCOMM_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //128bit UUID for Serial Port Protocol (SPP)
	
	private static final Boolean D = true;
	private static final String TAG = "BtConnectorIntentService";
	
	
	public BtConnectorIntentService() {
		super(TAG);
	}

	@Override
	protected void onHandleIntent(Intent intent) {

		if (D) Log.i(TAG,"Got intent!");
		
		int linecount = 100;
		
		BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
		if (adapter == null) return;
		adapter.cancelDiscovery();
		
		BluetoothDevice device = adapter.getRemoteDevice(intent.getStringExtra(BT_DEVICE_MAC));//(BluetoothDevice)intent.getExtras().getParcelable(BT_DEVICE);		
		BluetoothSocket btsocket = null;
		
		final int device_stream_id = intent.getIntExtra(BT_DEVICE_STREAM_ID,0);
		
		try {
			btsocket = device.createInsecureRfcommSocketToServiceRecord(RFCOMM_UUID);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			if (D) Log.e(TAG,"could not create BT socket" );
			e.printStackTrace();
		}
	
		
		try {
			btsocket.connect();
		} catch (IOException e) {
			if (D) Log.e(TAG,"could not connect BT socket");
			e.printStackTrace();
		}

		// hmm now we want to keep the socket and read data from it from a thread, but how to return a socket that is not parcelable ?
		// -perhaps the best way after all is to have all this as a thread instead of async task or intent service. 
		
		InputStream inputstream = null;
		
		try {
			inputstream = btsocket.getInputStream();
		} catch (IOException e) {
			if (D) Log.e(TAG,"could not get inputstream from socket");
			e.printStackTrace();
		}
		
		BufferedReader bufferedreader = new BufferedReader(new InputStreamReader(inputstream));
		String line;

		while ( linecount > 0 ) {
			try {
				line = bufferedreader.readLine(); //blocking
				linecount--;
				
				//if (D) Log.i(TAG,"read from socket:" + line);
				
				// broadcast the received data
				Intent i = new Intent(BT_NEW_DATA_INTENT);
				i.putExtra(BT_NEW_DATA_INTENT_EXTRA_BT_DATA, line);
				i.putExtra(BT_NEW_DATA_INTENT_EXTRA_BT_DATA_STREAM_ID, device_stream_id); //used by broadcast receiver to distinguish which device data came from
				sendBroadcast(i);

			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		}
		
		// close down nicely, 
    	try {
			bufferedreader.close();
	    	inputstream.close();
	    	btsocket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
	
}
