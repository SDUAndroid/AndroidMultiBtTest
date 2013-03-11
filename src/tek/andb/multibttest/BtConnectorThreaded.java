// Anders Bøgild, September 2012

package tek.andb.multibttest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

// This is a use-once object because thread and socket are use-once.

public class BtConnectorThreaded{

	private BtThread mBtThread = new BtThread();
	
	private BluetoothAdapter adapter = null;
	private BluetoothDevice device = null;
	private BluetoothSocket btsocket = null;
	private Context context = null;
	
	int stream_id;
	
	private static final UUID RFCOMM_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //128bit UUID for Serial Port Protocol (SPP)
	
	private static final Boolean D = true;
	private static final String TAG = "BtConnectorThreaded";
	
	public static final String BT_NEW_DATA_INTENT = "tek.andb.multibttestbt.BT_NEW_DATA_INTENT";
	public static final String BT_NEW_DATA_INTENT_EXTRA_BT_DATA = "bt_data";
	public static final String BT_NEW_DATA_INTENT_EXTRA_BT_DATA_STREAM_ID= "bt_id";
	
	public BtConnectorThreaded(Context context, String mac, int id){
		
		this.stream_id = id;
		this.adapter = BluetoothAdapter.getDefaultAdapter();
		this.device = adapter.getRemoteDevice(mac);
		this.context = context;

		try {
			btsocket = device.createInsecureRfcommSocketToServiceRecord(RFCOMM_UUID);
		} catch (IOException e) {
			if (D) Log.e(TAG,"could not create BT socket" );
			e.printStackTrace();
		}

		
	}
	
	
	@Override
	protected void finalize() throws Throwable {
		super.finalize();
		if (D) Log.i(TAG,"Garbage collector reclaiming.. stream_id=" + stream_id);
		
	}

	public void connect(){
		adapter.cancelDiscovery();
		mBtThread.start();
	}
	
	
	public void disconnect(){
		mBtThread.runThread = false;
	}
	
	
	private class BtThread extends Thread{
		
		private Boolean runThread = true;

		@Override
		public void run() {
			super.run();
			
			if (D) Log.i(TAG,"running thread for stream_id=" + stream_id);
			
			try {
				btsocket.connect();
			} catch (IOException e) {
				if (D) Log.e(TAG,"could not connect BT socket");
				if (D) e.printStackTrace();
				return;
			}

			
			InputStream inputstream = null;
			
			try {
				inputstream = btsocket.getInputStream();
			} catch (IOException e) {
				if (D) Log.e(TAG,"could not get inputstream from socket");
				if (D) e.printStackTrace();
				return;
			}
			
			BufferedReader bufferedreader = new BufferedReader(new InputStreamReader(inputstream));
			String line;

			while ( this.runThread == true ) {
				try {
					
					//Here we read data from the sensor.
					//Sensor data is received as text strings, lines, folowed by a newline character ('\n') at approx 20 Hz 
					//Each line consists of an integer representing the most recent sensor value.
					//e.g.:
					//"25235\n"
					//"25232\n"
					//"24669\n
					
					line = bufferedreader.readLine(); //blocking until a whole line has been read.
					
					//if (D) Log.i(TAG,"read from socket:" + line);
					
					// broadcast the received data
					Intent i = new Intent(BT_NEW_DATA_INTENT);
					i.putExtra(BT_NEW_DATA_INTENT_EXTRA_BT_DATA, line);
					i.putExtra(BT_NEW_DATA_INTENT_EXTRA_BT_DATA_STREAM_ID, stream_id); //used by broadcast receiver to distinguish which device data came from
					context.sendBroadcast(i);

				} catch (IOException e) {
					if (D) Log.e(TAG,"could not read line from buffered reader");
					if (D) e.printStackTrace();
					return;
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
			
			if (D) Log.i(TAG,"thread for stream_id=" + stream_id + " done");
		}
	
	}
}
