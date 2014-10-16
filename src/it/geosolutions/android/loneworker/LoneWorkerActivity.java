package it.geosolutions.android.loneworker;

import it.geosolutions.android.loneworker.service.BluetoothService;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.ColorDrawable;
import android.location.LocationManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/**
 * activity which handles the flow of the Loneworker Activity
 * 
 * it will check that the user has set the available values (for now telephone)
 * that Bluetooth is available, enabled (will ask if not)
 * 
 * If that all is true, paired devices are shown in ListDeviceActitivity to select a device
 * to listen to
 * 
 * The BluetoothService is started and the Activity listens to UI updates coming from the service
 * it can be dismissed / killed the services will go on until the "disconnect button is used finally"
 * 
 * if you disconnected, you can reconnect using the menu
 * also the settings are available from the menu
 * 
 * 
 * @author robertoehler
 *
 */
public class LoneWorkerActivity  extends Activity{

	private final static String TAG = LoneWorkerActivity.class.getSimpleName();
	
	private final static String SERVICE_ID = "it.geosolutions.android.loneworker.service.BluetoothService";

	// Intent request codes
	private static final int REQUEST_CONNECT_DEVICE = 1;
	private static final int REQUEST_ENABLE_BT = 2;
	private static final int REQUEST_SETTINGS = 3;
	private static final int REQUEST_LOCATION = 4;
	private static final int REQUEST_ALARM = 5;

	private TextView loneWorker_TV;
	private TextView time_TV;
	private Button closeButton;

	private UIUpdateReceiver mUIUpdateReceiver;
	
	private String mConnectedDeviceAdress = null;

	private MenuItem menu_connect;
	private MenuItem menu_settings;


	@SuppressLint("NewApi")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.main_layout);
		
		getActionBar().setBackgroundDrawable(new ColorDrawable(0xff007DC5));
		
		mUIUpdateReceiver = new UIUpdateReceiver();

		loneWorker_TV = (TextView) findViewById(R.id.loneWorkerTV);

		time_TV = (TextView) findViewById(R.id.timeTV);

		closeButton = (Button) findViewById(R.id.close_button);

		closeButton.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {

				//only if service is running do something
				if(isMyServiceRunning(getBaseContext())){
					
					sendBroadcast(new Intent(BluetoothService.STOP_SERVICE));
					stopService(new Intent(LoneWorkerActivity.this, BluetoothService.class));
					
					loneWorker_TV.setText(getString(R.string.connection_restart));
					
					closeButton.setText(R.string.connection_closed);
					
					toogleMenu(true);
				}else{
					
				}
				
			}
		});

		// If the bluetooth adapter is null, then Bluetooth is not supported, cannot run this app
		if (BluetoothAdapter.getDefaultAdapter() == null) {
			Toast.makeText(this, getString(R.string.bt_not_available), Toast.LENGTH_LONG).show();
			finish();
			return;
		}
		
		//TODO instead of this get actual state of service none, running, expired
		//and configure ui here according to it
		
		if(!isMyServiceRunning(getBaseContext())){
			//not running, check properties
			checkAllNecessaryProps();
		}
		
		// Set the default time
		if(time_TV != null){
			time_TV.setText(getString(R.string.default_display_time));
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		Log.d(TAG, "onStart");
		
		registerReceiver(mUIUpdateReceiver, new IntentFilter(BluetoothService.UI_UPDATE_TIME));
		registerReceiver(mUIUpdateReceiver, new IntentFilter(BluetoothService.UI_UPDATE_STATE));
		registerReceiver(mUIUpdateReceiver, new IntentFilter(BluetoothService.UI_UPDATE_SMS_FEEDBACK));
		registerReceiver(mUIUpdateReceiver, new IntentFilter(BluetoothService.TIME_EXPIRED_INTENT));
	
	}

	@Override
	protected void onStop() {
		super.onStop();
		
		unregisterReceiver(mUIUpdateReceiver);
	}
	
	private void checkAllNecessaryProps() {
		
		final String telephone = PreferenceManager.getDefaultSharedPreferences(getBaseContext()).getString(BluetoothService.TELEPHONE_NUMBER, null);
//		final long expired = PreferenceManager.getDefaultSharedPreferences(getBaseContext()).getLong(BluetoothService.TIME_EXPIRED_INTENT_EVENTTIME, 0);
		
//		//0. did the timer expire earlier ? 
//		if(expired > 0){
//			
//			startActivityForResult(new Intent(this, AlarmDialogActivity.class),REQUEST_ALARM);	
//			
//		//1. location services enabled ?
//		}else
		if (!locationServicesAvailable()){
			
			Toast.makeText(getBaseContext(), getString(R.string.enable_location), Toast.LENGTH_LONG).show();
			startActivityForResult(new Intent("android.settings.LOCATION_SOURCE_SETTINGS"),REQUEST_LOCATION);
			
		//2. bluetooth enabled ?
		}else if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
			// If BT is not on, request that it be enabled.
			// setupChat() will then be called during onActivityResult
			Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
			
		//3. target telephone set ?
		}else
			if(telephone == null){
			
			startActivityForResult(new Intent(this, SettingsDialogActivity.class),REQUEST_SETTINGS);
			
		//4. device selected?
		}else if(mConnectedDeviceAdress ==  null){
			
			Intent serverIntent = new Intent(this, DeviceListActivity.class);
			startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
			
		//5. services already running ?
		}else if(!isMyServiceRunning(getBaseContext())){
			// Otherwise, setup start service if possible
			startServiceIfNecessary();
			
		// services runs, all fine, must be recreation of activity, hide menu
		} else {
			toogleMenu(false);
		}
		
	}
	
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		
		Log.i(TAG, "onActivityResult " + resultCode);
		switch (requestCode) {
		case REQUEST_ALARM:
			
			closeButton.setVisibility(View.GONE);
			
			break;
		case REQUEST_LOCATION:
			if(locationServicesAvailable()){
				checkAllNecessaryProps();
			}else{
				Toast.makeText(getBaseContext(), R.string.enable_location_not_enable, Toast.LENGTH_SHORT).show();
				finish();
			}
			break;
		case REQUEST_SETTINGS:
			if (resultCode == Activity.RESULT_OK) {
				checkAllNecessaryProps();
			}
			break;
		case REQUEST_CONNECT_DEVICE:
			// When DeviceListActivity returns with a device to connect
			if (resultCode == Activity.RESULT_OK) {
				mConnectedDeviceAdress = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);		
				checkAllNecessaryProps();
			}
			break;
		case REQUEST_ENABLE_BT:
			// When the request to enable Bluetooth returns
			if (resultCode == Activity.RESULT_OK) {
				
				checkAllNecessaryProps();
				
			} else {
				
				// User did not enable Bluetooth or an error occurred
				Log.d(TAG, "BT not enabled");
				Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
				finish();
				
			}
		}
	}
	
	public boolean locationServicesAvailable(){

		LocationManager locManager = (LocationManager) getSystemService( Context.LOCATION_SERVICE );
		if (locManager.isProviderEnabled(LocationManager.GPS_PROVIDER ) ||
				locManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)){
			return true;
		}
		return false;

	}

	public void startServiceIfNecessary(){
		
		if (!isMyServiceRunning(getBaseContext())) {

			Intent i = new Intent(LoneWorkerActivity.this, BluetoothService.class);
			i.putExtra(BluetoothService.DEVICE_ADDRESS, mConnectedDeviceAdress);
			startService(i);	
			toogleMenu(false);
			closeButton.setText(R.string.close_connection);
			loneWorker_TV.setText(R.string.waiting_for_device);

		}	
	}
	

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {

		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.option_menu, menu);
		
		menu_connect = menu.findItem(R.id.connect);
		menu_settings = menu.findItem(R.id.settings);
		
		if(isMyServiceRunning(getBaseContext())){
			toogleMenu(false);
		}
		
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Intent serverIntent = null;
		switch (item.getItemId()) {
		case R.id.connect:
			// Launch the DeviceListActivity to see devices and do scan
			serverIntent = new Intent(this, DeviceListActivity.class);
			startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
			closeButton.setText(getString(R.string.close_connection));
			loneWorker_TV.setText(getString(R.string.waiting_for_device));
			return true;
		case R.id.settings:
			startActivityForResult(new Intent(this, SettingsDialogActivity.class), REQUEST_SETTINGS);
			return true;
		}
		return false;
	}
	
	public void toogleMenu(boolean show){
		
		if(menu_connect != null){
			menu_connect.setVisible(show);
		}
		if(menu_settings != null){
			menu_settings.setVisible(show);
		}
	}
	
	public boolean isMyServiceRunning(Context c) {
	    ActivityManager manager = (ActivityManager) c.getSystemService(ACTIVITY_SERVICE);
	    for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
	        if (SERVICE_ID.equals(service.service.getClassName())) {
	            return true;
	        }
	    }
	    return false;
	}
	/**
	 * receives messages from the service between onStart and onStop
	 *
	 * @author Robert Oehler
	 *
	 */
	private class UIUpdateReceiver extends BroadcastReceiver{

		@Override
		public void onReceive(Context context, Intent intent) {

			if(intent.getAction().equals(BluetoothService.TIME_EXPIRED_INTENT)){
				
				closeButton.setText(getString(R.string.connection_closed));
				loneWorker_TV.setText(getString(R.string.locating_you));
				toogleMenu(true);
				
			}else{ //message state 

				final String message = intent.getStringExtra(BluetoothService.UI_UPDATE_MESSAGE);

				if(message == null ){
					Log.e(TAG, "intent without message received");
					return;
				}

				if(intent.getAction().equals(BluetoothService.UI_UPDATE_STATE) ||
						intent.getAction().equals(BluetoothService.UI_UPDATE_SMS_FEEDBACK)){

					loneWorker_TV.setText(message);
				}else{

					time_TV.setText(message);
				}
			}
		}	
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		
		// re-set the default time
		time_TV = (TextView) findViewById(R.id.timeTV);
		if(time_TV != null){
			time_TV.setText(getString(R.string.default_display_time));
		}
	}
}
