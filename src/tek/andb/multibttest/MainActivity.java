// Anders Bøgild, September 2012

package tek.andb.multibttest;

import java.util.ArrayList;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.ToggleButton;

public class MainActivity extends Activity {
	
	private static final Boolean D = true;
	private static final String TAG = "MainActivity";

	private EditText mET1 = null, mET2 = null, mET3 = null;
	private int mET1_lines = 0, mET2_lines = 0, mET3_lines = 0;
	
	private BtMultiResponseReceiver btMultiResponseReceiver = null;
	private IntentFilter multiFilter = null;
	private IntentFilter multiHrFilter = null;
	
	
	private static final String BT_DEVICE_1_MAC = "00:06:66:44:F4:B9"; //PEP 1 //"00:06:66:06:62:1C"; //klemme sensor 1
	private static final int BT_DEVICE_1_ID = 1;
	private static final String BT_DEVICE_2_MAC = "00:06:66:42:91:7E"; //pude sensor 1
	private static final int BT_DEVICE_2_ID = 2;
	private static final String BT_DEVICE_3_MAC = "00:06:66:44:F4:C8"; //Q sensor 1
	private static final int BT_DEVICE_3_ID = 3;
	
	private BtConnectorThreaded btct1 = null,btct2 = null;
	private BtConnectorPolarThreaded btct3 = null;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (D) Log.d(TAG, "+++ On Create +++");
        
        setContentView(R.layout.activity_main);

        // Setup views
        mET1 = (EditText)findViewById(R.id.editText1);
        mET2 = (EditText)findViewById(R.id.editText2);
        mET3 = (EditText)findViewById(R.id.editText3);
        mET1.setEnabled(false);
        mET2.setEnabled(false);
        mET3.setEnabled(false);
        ((ToggleButton)findViewById(R.id.toggleButton1)).setOnClickListener(mOnClickListener);
        ((ToggleButton)findViewById(R.id.toggleButton2)).setOnClickListener(mOnClickListener);
        ((ToggleButton)findViewById(R.id.toggleButton3)).setOnClickListener(mOnClickListener);
        
        // Setup broadcast receiver
        btMultiResponseReceiver = new BtMultiResponseReceiver();
        multiFilter = new IntentFilter(BtConnectorThreaded.BT_NEW_DATA_INTENT);
        multiHrFilter = new IntentFilter(BtConnectorPolarThreaded.BT_NEW_DATA_INTENT);       
        
    }

    
    
	@Override
	protected void onStart() {
		super.onStart();
		if (D) Log.d(TAG, "++ On Start ++");
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (D) Log.d(TAG, "+ On Resume +");
		registerReceiver(btMultiResponseReceiver, multiFilter);
		registerReceiver(btMultiResponseReceiver, multiHrFilter);
	}
    
    
    @Override
	protected void onPause() {
		super.onPause();
		if (D) Log.d(TAG, "- On Pause -");
		unregisterReceiver(btMultiResponseReceiver);
	}
    

	@Override
	protected void onStop() {
		if (D) Log.d(TAG, "-- On Stop --");
		super.onStop();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (D) Log.d(TAG, "--- On Destroy ---");
		if (btct1 != null) btct1.disconnect();
		if (btct2 != null) btct2.disconnect();
		if (btct3 != null) btct3.disconnect();
	}
	
	

	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		if (D) Log.d(TAG, "+ On Restore Instance State +");
		super.onRestoreInstanceState(savedInstanceState);
		
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		if (D) Log.d(TAG, "- On Save Instance State -");
		super.onSaveInstanceState(outState);
	}



//	@Override
//	public void onConfigurationChanged(Configuration newConfig) {
//		super.onConfigurationChanged(newConfig);
//		if (D) Log.d(TAG, "~ On Configuration Changed ~");
//		//newConfig.orientation
//	}


	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }
    

    OnClickListener mOnClickListener = new OnClickListener() {
		
		@Override
		public void onClick(View v) {
			
			switch(v.getId()){
			
			case R.id.toggleButton1:
				if ( ((ToggleButton)v).isChecked() ){
					mET1.append("\nEnabled");
					btct1 = new BtConnectorThreaded(getApplicationContext(), BT_DEVICE_1_MAC, BT_DEVICE_1_ID);
					btct1.connect();
//					Intent btConnectIntent = new Intent(getApplicationContext(), BtConnectorIntentService.class);
//					btConnectIntent.putExtra(BtConnectorIntentService.BT_DEVICE_MAC, BT_DEVICE_1_MAC); //Qsensor 1
//					btConnectIntent.putExtra(BtConnectorIntentService.BT_DEVICE_STREAM_ID, BT_DEVICE_1_ID);
//					startService(btConnectIntent);
				}else{
					mET1.append("\nDisabled");
					btct1.disconnect();
				}
				break;
				
			case R.id.toggleButton2:
				if ( ((ToggleButton)v).isChecked() ){
					mET2.append("\nEnabled");
					btct2 = new BtConnectorThreaded(getApplicationContext(), BT_DEVICE_2_MAC, BT_DEVICE_2_ID);
					btct2.connect();
//					Intent btConnectIntent = new Intent(getApplicationContext(), BtConnectorIntentService.class);
//					btConnectIntent.putExtra(BtConnectorIntentService.BT_DEVICE_MAC, BT_DEVICE_2_MAC);
//					btConnectIntent.putExtra(BtConnectorIntentService.BT_DEVICE_STREAM_ID, BT_DEVICE_2_ID);
//					startService(btConnectIntent);
				}else{
					mET2.append("\nDisabled");
					btct2.disconnect();
				}

				break;

			case R.id.toggleButton3:
				if ( ((ToggleButton)v).isChecked() ){
					mET3.append("\nEnabled");
					
					//btct3 = new BtConnectorThreaded(getApplicationContext(), BT_DEVICE_3_MAC, BT_DEVICE_3_ID);
					btct3 = new BtConnectorPolarThreaded(getApplicationContext(), getString(R.string.polar_hr_sensor_mac_1), BT_DEVICE_3_ID);
					btct3.connect();
					
//					ArrayList<String> mac_array = new ArrayList();
//					mac_array.add(BT_DEVICE_1_MAC);
//					mac_array.add(BT_DEVICE_2_MAC);
//					mac_array.add(BT_DEVICE_3_MAC);
//					
//					ArrayList<Integer> id_array = new ArrayList();
//					id_array.add(BT_DEVICE_1_ID);
//					id_array.add(BT_DEVICE_2_ID);
//					id_array.add(BT_DEVICE_3_ID);
//					
//					
//					Intent btMultiConnectIntent = new Intent(getApplicationContext(), BtMultiConnectorIntentService.class);				
//					btMultiConnectIntent.putStringArrayListExtra(BtMultiConnectorIntentService.BT_DEVICE_MAC_ARRAY, mac_array);
//					btMultiConnectIntent.putIntegerArrayListExtra(BtMultiConnectorIntentService.BT_DEVICE_STREAM_ID_ARRAY,id_array);
//					startService(btMultiConnectIntent);
					
				}else{
					mET3.append("\nDisabled");
					btct3.disconnect();
				}

				break;

			}
			
		}
	};

	private class BtMultiResponseReceiver extends BroadcastReceiver{

		@Override
		public void onReceive(Context context, Intent intent) {
			
			int id = 0;
			String line = "";
			//int heart_rate = 0;
			
			if (intent.hasExtra(BtConnectorThreaded.BT_NEW_DATA_INTENT_EXTRA_BT_DATA)){
				line = intent.getStringExtra(BtConnectorThreaded.BT_NEW_DATA_INTENT_EXTRA_BT_DATA);			
				id = intent.getIntExtra(BtConnectorThreaded.BT_NEW_DATA_INTENT_EXTRA_BT_DATA_STREAM_ID,0);
				
			}else if(intent.hasExtra(BtConnectorPolarThreaded.BT_NEW_DATA_INTENT_EXTRA_BT_HR_DATA)){
				line = "" + intent.getIntExtra(BtConnectorPolarThreaded.BT_NEW_DATA_INTENT_EXTRA_BT_HR_DATA,0);
				id = intent.getIntExtra(BtConnectorPolarThreaded.BT_NEW_DATA_INTENT_EXTRA_BT_DATA_STREAM_ID,0);				
			}
			
			switch(id){
			
			case BT_DEVICE_1_ID:
				mET1_lines++;
				//mET1.append("\n" + mET1_lines + ": " + line);
				mET1.setText("\n" + mET1_lines + ": " + line);
				break;

			case BT_DEVICE_2_ID:
				mET2_lines++;
				mET2.setText("\n" + mET2_lines + ": " + line);
				break;

			case BT_DEVICE_3_ID:
				mET3_lines++;
				mET3.setText("\n" + mET3_lines + ": " + line);
				break;

				
			}
						
			
		}
		
	}

}

