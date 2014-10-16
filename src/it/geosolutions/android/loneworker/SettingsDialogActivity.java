package it.geosolutions.android.loneworker;


import it.geosolutions.android.loneworker.service.BluetoothService;

import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.telephony.PhoneNumberUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
/**
 * activity in dialog style which gets the settings from the user
 * 
 * @author Robert Oehler
 *
 */
public class SettingsDialogActivity extends Activity{
	
	private final static String TAG = SettingsDialogActivity.class.getSimpleName();
	
	private EditText hours_et;
	private EditText minutes_et;
	private EditText id_et;
	private EditText telephone_et;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.settings_dialog);
		
		telephone_et = (EditText) findViewById(R.id.target_telephone_et);
		hours_et = (EditText) findViewById(R.id.time_interval_hours_et);
		minutes_et = (EditText) findViewById(R.id.time_interval_minutes_et);
		id_et = (EditText) findViewById(R.id.id_et);
		
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		
		final String telephone = prefs.getString(BluetoothService.TELEPHONE_NUMBER, null);
		
		final long time_interval = prefs.getLong(BluetoothService.TIME_INTERVAL, 0);
		
		final String id = prefs.getString(BluetoothService.USER_ID, null);
		
		if(telephone != null){
			telephone_et.setText(telephone);
		}else{
			telephone_et.setHint(R.string.set_hint_telephone);
		}
		
		if(time_interval != 0){
			
			hours_et.setText(Long.toString(TimeUnit.MILLISECONDS.toHours(time_interval)));
			minutes_et.setText(Long.toString(TimeUnit.MILLISECONDS.toMinutes(time_interval)));
			
		}else{
			hours_et.setHint(R.string.set_hint_hours);
			minutes_et.setHint(R.string.set_hint_minutes);
		}
		
		if(id != null){
			id_et.setText(id);
		}else{
			id_et.setHint(R.string.set_hint_id);
		}
		
		findViewById(R.id.done_button).setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				
				Intent intent = SettingsDialogActivity.this.getIntent();
				setResult(RESULT_OK, intent);
							
				save();
					
			}
		});
		
		findViewById(R.id.close_button).setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				
				Intent intent = SettingsDialogActivity.this.getIntent();
				setResult(RESULT_CANCELED, intent);
				
				save();
			}
		});
		
		
	}
	
	public void save(){
		
		final String telephone = telephone_et.getText().toString();
		
		if(telephone == null || telephone.equals("")){
			Toast.makeText(getBaseContext(), R.string.settings_telephone_missing, Toast.LENGTH_LONG).show();
			return;
		}
		
		if(!PhoneNumberUtils.isGlobalPhoneNumber(telephone)){
			Toast.makeText(getBaseContext(), R.string.settings_telephone_invalid, Toast.LENGTH_LONG).show();
			return;
		}
		
		int hours = 0, minutes = 0;
		try{
			String _hours = hours_et.getText().toString();
			String _mins  = minutes_et.getText().toString();
			
			//if the user did not enter numbers, he is satisfied with the hints -> use them
			if(_hours == null || _hours.equals("")){
				_hours = hours_et.getHint().toString();
			}
			if(_mins == null || _mins.equals("")){
				_mins = minutes_et.getHint().toString();
			}
			
			hours = Integer.parseInt(_hours);
			minutes = Integer.parseInt(_mins);
			
			if(hours <= 0 && minutes <= 0){
				Toast.makeText(getBaseContext(), R.string.settings_wrong_numbers, Toast.LENGTH_LONG).show();
				return;
			}
			
		}catch(NumberFormatException e){
			Toast.makeText(getBaseContext(), R.string.settings_wrong_numbers, Toast.LENGTH_LONG).show();
			return;
		}
		
		final long millis = ((hours * 3600) + (minutes * 60)) * 1000;
		
		final String id = id_et.getText().toString();
		
		Editor ed = PreferenceManager.getDefaultSharedPreferences(getBaseContext()).edit();
		
		Log.d(TAG, "saving phone : "+telephone+"\n millis "+millis+"\nId : "+id);
		
		ed.putString(BluetoothService.TELEPHONE_NUMBER, telephone);
		ed.putLong(BluetoothService.TIME_INTERVAL, millis);
		ed.putString(BluetoothService.USER_ID, id);
		
		ed.commit();
		
		finish();
	}


}
