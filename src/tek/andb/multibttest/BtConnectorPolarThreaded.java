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

public class BtConnectorPolarThreaded{

	private BtThread mBtThread = new BtThread();
	
	private BluetoothAdapter adapter = null;
	private BluetoothDevice device = null;
	private BluetoothSocket btsocket = null;
	private Context context = null;
	
	int stream_id;
	
	private static final UUID RFCOMM_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //128bit UUID for Serial Port Protocol (SPP)
	
	private static final Boolean D = true;
	private static final String TAG = "BtConnectorPolarThreaded";
	
	public static final String BT_NEW_DATA_INTENT = "tek.andb.multibttestbt.BT_NEW_POLAR_HR_INTENT";
	public static final String BT_NEW_DATA_INTENT_EXTRA_BT_HR_DATA = "bt_polar_hr_data";
	public static final String BT_NEW_DATA_INTENT_EXTRA_BT_DATA_STREAM_ID= "bt_id";
	
	public BtConnectorPolarThreaded(Context context, String mac, int id){
		
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

		private static final int BT_BUF_SIZE = 255;
		
		// From PolarMEssagePArser.java from Google MyTracks
		private boolean packetValid(byte[] buffer, int i) {
			boolean headerValid = (buffer[i] & 0xFF) == 0xFE;
			boolean checkbyteValid = (buffer[i + 2] & 0xFF) == (0xFF - (buffer[i + 1] & 0xFF));
			boolean sequenceValid = (buffer[i + 3] & 0xFF) < 16;

			return headerValid && checkbyteValid && sequenceValid;
		}
		
		
		
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

			// hmm now we want to keep the socket and read data from it from a thread, but how to return a socket that is not parcelable ?
			// -perhaps the best way after all is to have all this as a thread instead of async task or intent service. 
			
			InputStream inputstream = null;
			
			try {
				inputstream = btsocket.getInputStream();
			} catch (IOException e) {
				if (D) Log.e(TAG,"could not get inputstream from socket");
				if (D) e.printStackTrace();
				return;
			}
			
			byte[] buf = new byte[BT_BUF_SIZE];
			int buf_len = 0;
			int read_len = 0;

			while ( this.runThread == true ) {
				try {
					
					//line = bufferedreader.readLine(); //blocking
					//streamreader.read(buf);
										
					read_len = inputstream.read(buf, buf_len, BT_BUF_SIZE-buf_len); //blocking
					if (read_len < 0){
						if (D) Log.e(TAG,"end of file readched");
					}else{
						buf_len += read_len;
						if (buf_len >= BT_BUF_SIZE){
							if (D) Log.e(TAG,"buf_len >= " + BT_BUF_SIZE + " -resetting");
							buf_len = 0; //reset buffer
						}
					}
					
					if (D) Log.i(TAG,"1: read_len = " + read_len + " -> buf_len = " + buf_len);
					
				    boolean heartrateValid = false;
				    int heart_rate = 0;
				    int packet_size = 0;
				    
				    // Search for packet in buffer
				    // Minimum length Polar packets is 8, so stop search 8 bytes before buffer ends.
				    for (int packet_start = 0; packet_start < buf_len - 8; packet_start++) {
				      heartrateValid = packetValid(buf,packet_start); 
				      if (heartrateValid)  {
				        heart_rate = buf[packet_start + 5] & 0xFF;
				        packet_size = buf[packet_start + 1] & 0xFF;
				        
				        if ( (packet_start + packet_size) > buf_len ){
				        	if (D) Log.i(TAG,"skipping, complete packet not in yet: ");
				        	break; //in case the whole packet has not been read yet
				        }
				        
				        if (D) Log.i(TAG,"2: packet_start = " + packet_start + "  packet_size = " + packet_size);
				        
					    // remove parsed packet from input buffer
					    for(int i=0; i<packet_size; i++){
					    	buf[i] = buf[i + packet_start];				    	
					    }
					    buf_len -= packet_start;
					    buf_len -= packet_size;
			        
					    if (D) Log.i(TAG,"3: buf_len = " + buf_len);
					    
				        break; 
				      }
				    }
				    
				    if (D) Log.i(TAG,"heart_rate = " + heart_rate);
					
					// broadcast the received data
				    if (heart_rate > 50){
				    	Intent i = new Intent(BT_NEW_DATA_INTENT);
				    	i.putExtra(BT_NEW_DATA_INTENT_EXTRA_BT_HR_DATA, heart_rate);
				    	i.putExtra(BT_NEW_DATA_INTENT_EXTRA_BT_DATA_STREAM_ID, stream_id); //used by broadcast receiver to distinguish which device data came from
				    	context.sendBroadcast(i);
				    }

				} catch (IOException e) {
					if (D) Log.e(TAG,"could not read from inputstream");
					if (D) e.printStackTrace();
					return;
				} 
				
			}
			
			// close down nicely, 
			try {
				//bufferedreader.close();
		    	inputstream.close();
		    	btsocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			if (D) Log.i(TAG,"thread for stream_id=" + stream_id + " done");
		}
	
	}
}
