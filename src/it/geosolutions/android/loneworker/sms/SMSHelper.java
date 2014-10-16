package it.geosolutions.android.loneworker.sms;

import it.geosolutions.android.loneworker.service.BluetoothService;
import android.content.Context;
import android.preference.PreferenceManager;
import android.telephony.SmsManager;
import android.util.Log;

public class SMSHelper {
	
	private final static String TAG = SMSHelper.class.getSimpleName();
	
	public static void sendSMS(final Context context, final String message){
		
		final String telephone = PreferenceManager.getDefaultSharedPreferences(context).getString(BluetoothService.TELEPHONE_NUMBER, null);

		//TODO this needs more time to be solve problems between receiver lifetime and killing the service context
		
//		 String SENT = "SMS_SENT";
//	        String DELIVERED = "SMS_DELIVERED";

//	        PendingIntent sentPI = PendingIntent.getBroadcast(context, 0,new Intent(SENT), 0);
//
//	        PendingIntent deliveredPI = PendingIntent.getBroadcast(context, 0, new Intent(DELIVERED), 0);
//
//	        //---when the SMS has been sent---
//	        context.registerReceiver(new BroadcastReceiver(){
//	            @Override
//	            public void onReceive(Context arg0, Intent arg1) {
//	            	String message = null;
//	                switch (getResultCode())
//	                {
//	                    case Activity.RESULT_OK:
//	                    	message = context.getString(R.string.sms_sent);
//	                        break;
//	                    case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
//	                    	message = context.getString(R.string.sms_generic_failure);
//	                        break;
//	                    case SmsManager.RESULT_ERROR_NO_SERVICE:
//	                    	message = context.getString(R.string.sms_no_service);
//	                        break;
//	                    case SmsManager.RESULT_ERROR_NULL_PDU:
//	                    	message = context.getString(R.string.sms_null_pdu);
//	                        break;
//	                    case SmsManager.RESULT_ERROR_RADIO_OFF:
//	                    	message = context.getString(R.string.sms_radio_off);
//	                        break;
//	                }
//	                Log.d(TAG, "sent feedback "+ message);
//	                Intent i = new Intent(BluetoothService.UI_UPDATE_SMS_FEEDBACK);
//	        		i.putExtra(BluetoothService.UI_UPDATE_MESSAGE, message);
//	        		context.sendBroadcast(i);
//	        		smsCallback.sent();
//	            }
//	        }, new IntentFilter(SENT));

//	        //---when the SMS has been delivered---
//	        context.registerReceiver(new BroadcastReceiver(){
//	            @Override
//	            public void onReceive(Context arg0, Intent arg1) {
//	            	String message = null;
//	                switch (getResultCode())
//	                {
//	                    case Activity.RESULT_OK:
//	                        message = context.getString(R.string.sms_delivered);
//	                        break;
//	                    case Activity.RESULT_CANCELED:
//	                    	message = context.getString(R.string.sms_not_delivered);
//	                        break;                        
//	                }
//	                Log.d(TAG, "delivered feedback "+ message);
//	                Intent i = new Intent(BluetoothService.UI_UPDATE_SMS_FEEDBACK);
//	        		i.putExtra(BluetoothService.UI_UPDATE_MESSAGE, message);
//	        		context.sendBroadcast(i);
//	            }
//	        }, new IntentFilter(DELIVERED));  
		
		//TODO remove this on production 
//		if(isSimulator()){
			Log.d(TAG, "finish on simulator, sending "+message);

			SmsManager sms = SmsManager.getDefault();
			sms.sendTextMessage(telephone, null, message, null , null); 

//		}else{
//			Log.d(TAG, "finish on device, not sending message");
//
//		}
		
	}

}
