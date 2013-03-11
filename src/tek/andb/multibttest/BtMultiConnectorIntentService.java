// Anders Bøgild, September 2012

package tek.andb.multibttest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.UUID;

import android.app.IntentService;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.util.Log;
import android.widget.SlidingDrawer;

// Connecting the socket (blocking) is handled by an intent service which processes one intent at the time.
// This gives us an ease way of queueing and executing connection requests one-by-one if we want to connect to many sockets.

// NB: An IntentService has _one_ worker thread that handles intents one at the time from a queue. Cannot be interrupted once queued!

// This is a "multi" version that takes an arbitrary number of BT mac addresses and connects, reads 1000 lines in total, and broadcast back each line associated with the ID of the device it came from.

public class BtMultiConnectorIntentService extends IntentService {

	public static final String BT_DEVICE_MAC_ARRAY = "mac_array";
	public static final String BT_DEVICE_STREAM_ID_ARRAY = "id_array";
	public static final String BT_NEW_DATA_INTENT = "tek.andb.multibttestbt.BT_NEW_DATA_INTENT";
	public static final String BT_NEW_DATA_INTENT_EXTRA_BT_DATA = "bt_data";
	public static final String BT_NEW_DATA_INTENT_EXTRA_BT_DATA_STREAM_ID= "bt_id";
	
	private static final UUID RFCOMM_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //128bit UUID for Serial Port Protocol (SPP)
	
	private static final Boolean D = false;
	private static final String TAG = "BtMultiConnectorIntentService";
	
	
	public BtMultiConnectorIntentService() {
		super(TAG);
	}

	@Override
	protected void onHandleIntent(Intent intent) {

		if (D) Log.i(TAG,"Got intent!");
		
		BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
		if (adapter == null) return;
		adapter.cancelDiscovery();
		
		ArrayList<String> mac_array = intent.getStringArrayListExtra(BT_DEVICE_MAC_ARRAY);
		ArrayList<Integer> id_array = intent.getIntegerArrayListExtra(BT_DEVICE_STREAM_ID_ARRAY);
		
		ArrayList<BluetoothDevice> btdevice_array = new ArrayList<BluetoothDevice>();
		for (String mac: mac_array)
			btdevice_array.add(adapter.getRemoteDevice(mac));
		
		ArrayList<BluetoothSocket> btsocket_array = new ArrayList<BluetoothSocket>();
	
		try {
			for (BluetoothDevice device: btdevice_array)
				btsocket_array.add(device.createInsecureRfcommSocketToServiceRecord(RFCOMM_UUID));
		} catch (IOException e) {
			if (D) Log.e(TAG,"could not create BT socket" );
			e.printStackTrace();
		}
	
		
		try {
			for (BluetoothSocket socket: btsocket_array)
				socket.connect();
		} catch (IOException e) {
			if (D) Log.e(TAG,"could not connect BT socket");
			e.printStackTrace();
		}

		//SocketChannel sc = SocketChannel.open(); //create new opened and unconnected socketchannel
		//sc.configureBlocking(false);
		//sc.connect(btdevice_array.get(0)); // :-( socketchannel cannot connect to bluetooth ???
		
		
		// hmm now we want to keep the socket and read data from it from a thread, but how to return a socket that is not parcelable ?
		// -perhaps the best way after all is to have all this as a thread instead of async task or intent service. 
		
		ArrayList<InputStream> inputstream_array = new ArrayList<InputStream>();
		
		try {
			for (BluetoothSocket socket: btsocket_array)
				inputstream_array.add(socket.getInputStream());
		} catch (IOException e) {
			if (D) Log.e(TAG,"could not get inputstream from socket");
			e.printStackTrace();
		}
		
		ArrayList<BufferedReader> bufferedreader_arraylist = new ArrayList<BufferedReader>();
		for (InputStream inputstream: inputstream_array)
			bufferedreader_arraylist.add(new BufferedReader(new InputStreamReader(inputstream)));
		
		String line;
		
		int num_bt_devices = bufferedreader_arraylist.size();
		init_balanced_selector(num_bt_devices);
		
		int linecount = 10000;
		
		while ( linecount > 0 ) {
			try {
				
				int i = balanced_selector(inputstream_array);
				if( i > -1){
					line = bufferedreader_arraylist.get(i).readLine(); //blocking
					linecount--;
				
					//if (D) Log.i(TAG,"read from socket:" + line);
				
					// broadcast the received data
					Intent iout = new Intent(BT_NEW_DATA_INTENT);
					iout.putExtra(BT_NEW_DATA_INTENT_EXTRA_BT_DATA, line);
					iout.putExtra(BT_NEW_DATA_INTENT_EXTRA_BT_DATA_STREAM_ID, id_array.get(i) ); //used by broadcast receiver to distinguish which device data came from
					sendBroadcast(iout);
				}else{
					if (D) Log.i(TAG,"No BT sockets has anything to repport");
					Thread.sleep(10);					
				}
		
				/*for (int i=0; i < bufferedreader_arraylist.size(); i++){
					line = bufferedreader_arraylist.get(i).readLine(); //blocking
					linecount--;
				
					//if (D) Log.i(TAG,"read from socket:" + line);
				
					// broadcast the received data
					Intent iout = new Intent(BT_NEW_DATA_INTENT);
					iout.putExtra(BT_NEW_DATA_INTENT_EXTRA_BT_DATA, line);
					iout.putExtra(BT_NEW_DATA_INTENT_EXTRA_BT_DATA_STREAM_ID, id_array.get(i) ); //used by broadcast receiver to distinguish which device data came from
					sendBroadcast(iout);
				}*/

			} catch (Exception e) {
				e.printStackTrace();
			}
			
		}
		
		// close down nicely, 
    	try {
			for(BufferedReader r: bufferedreader_arraylist) r.close();
	    	for(InputStream i: inputstream_array) i.close();
	    	for(BluetoothSocket s: btsocket_array) s.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
	
	// simply returns the index of the of the most filled input stream in the array list
	// pro: simple 
	// con: input streams in the beginning of the arraylist are selected more often under load (starving)
	private int selector(ArrayList<InputStream> inputstreams) throws IOException{
		
		int max = 0;
		int idx = -1;
		int curr_idx = -1;
		
		for(InputStream is: inputstreams){
			curr_idx++;
			if(is.available() > max){
				max=is.available();
				idx=curr_idx;
			}
		}
		
		if (D) Log.i(TAG,"max=" + max); //HTC desire C, Samsung GS3 seems to have a max BT buffersize of 10 bytes
		
		return idx;
		
	}
	
	// Simply returns the index of the of the most filled input stream in the array list.
	// Visit order is maintained: the last served are placed last in a list, this should ensure balance under load. 
	private ArrayList<Integer> visit_order = null;
	private void init_balanced_selector(int number_of_devices){
		visit_order = new ArrayList<Integer>();
		for (int i = 0; i<number_of_devices; i++) visit_order.add(i);
	}	
	
	private int balanced_selector(ArrayList<InputStream> inputstreams) throws IOException{
		
		int max = 0;
		int idx = -1;
		
		for(Integer i: visit_order){
			if(inputstreams.get(i).available() > max){
				max=inputstreams.get(i).available();
				idx=i;
			}
		}
		
		// placed the served index last in the "queue"
		// -we dont check if served index was actually the last one.. 
		if(idx > -1){
			Integer i = visit_order.remove(idx);
			visit_order.add(i);
			if (D) Log.i(TAG,"reordered: " + visit_order);
		}
		
		if (D) Log.i(TAG,"max=" + max); //HTC desire C, Samsung GS3 seems to have a max BT buffersize of 10 bytes
		
		return idx;

	}
	
}
