package it.geosolutions.android.loneworker;

import it.geosolutions.android.loneworker.service.BluetoothService;
import it.geosolutions.android.loneworker.sms.SMSHelper;

import java.text.SimpleDateFormat;
import java.util.Locale;

import android.app.Activity;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

//TODO in future check this

public class AlarmDialogActivity extends Activity {
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.alarm_dialog);
		
		long alarmTime = getIntent().getLongExtra(BluetoothService.TIME_EXPIRED_INTENT_EVENTTIME, 0);
		
		SimpleDateFormat sdf  = new SimpleDateFormat("HH:mm dd.MM.yy",Locale.getDefault());
		
		final TextView alarm_tv = (TextView) findViewById(R.id.alarm_dialog_tv);
		
		alarm_tv.setText(getString(R.string.alarm_sent,sdf.format(alarmTime)));
		
		final Button cancelButton = (Button) findViewById(R.id.cancel_button);
		
		cancelButton.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				
				clearAlarmTime();
				
				finish();
				
			}
		});
		
		final Button false_alarm_button = (Button) findViewById(R.id.false_alarm_button);
		
		false_alarm_button.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				
				clearAlarmTime();
				
				final String id = PreferenceManager.getDefaultSharedPreferences(getBaseContext()).getString(BluetoothService.USER_ID, null);
				
				 String message = getString(R.string.false_alarm_message);
				
				if(id != null){
					message += " "+ id;
				}
				
				SMSHelper.sendSMS(getBaseContext(), message);
				
				finish();
				
			}
		});
	}
	
	public void clearAlarmTime(){
		
		Editor ed = PreferenceManager.getDefaultSharedPreferences(getBaseContext()).edit();
		ed.putLong(BluetoothService.TIME_EXPIRED_INTENT_EVENTTIME, 0);
		ed.commit();
	}

}
