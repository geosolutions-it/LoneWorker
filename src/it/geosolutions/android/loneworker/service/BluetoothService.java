package it.geosolutions.android.loneworker.service;

import it.geosolutions.android.loneworker.LoneWorkerActivity;
import it.geosolutions.android.loneworker.R;
import it.geosolutions.android.loneworker.bluetooth.BluetoothConnector;
import it.geosolutions.android.loneworker.constants.Constants;
import it.geosolutions.android.loneworker.location.GeoUtil;
import it.geosolutions.android.loneworker.location.LocationProvider;
import it.geosolutions.android.loneworker.location.LocationProvider.LocationResultCallback;
import it.geosolutions.android.loneworker.sms.SMSHelper;
import java.lang.ref.WeakReference;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences.Editor;
import android.location.Location;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;

/**
 * class which extends an Android Service
 * 
 * it runs independently from the activity which started it in the background
 * 
 * A notification icon in the status bar informs that it is running
 * 
 * It handles the connection to the bluetooth device using the BluetoothConnector
 * and runs a Countdowntimer, which when finished will try to locate the user and
 * send a sms including the location and an id the user set before to the number the
 * user has set before
 * 
 * !!!!!!!!!!!!!!!!!
 * This has the permission to send SMS, thus when the counter expires this may produce real costs
 * Handle carefully
 * !!!!!!!!!!!!!!!!!!
 * 
 * @author Robert Oehler
 *
 */
public class BluetoothService extends Service {
	
	private final static int THIS_SERVICE_ID = 111;

	// Message types sent from the BluetoothService Handler
	public static final int MESSAGE_STATE_CHANGE = 1;
	public static final int MESSAGE_READ = 2;
	public static final int MESSAGE_DEVICE_NAME = 3;
	public static final int MESSAGE_CONNECTION_LOST = 4;
	public static final int MESSAGE_CONNECTION_FAILED = 5;
	
	public static final String DEVICE_NAME = "device_name";
	
	public final static String UI_UPDATE_STATE = "it.geosolutions.android.loneworker.ui_update.state";
	public final static String UI_UPDATE_TIME = "it.geosolutions.android.loneworker.ui_update_time";
	public final static String UI_UPDATE_MESSAGE = "it.geosolutions.android.loneworker.ui_update.message";
	public final static String UI_UPDATE_SMS_FEEDBACK = "it.geosolutions.android.loneworker.ui_update.sms_feedback";
	public final static String STOP_SERVICE = "it.geosolutions.android.loneworker.stop_service";
	public final static String DEVICE_ADDRESS = "it.geosolutions.android.loneworker.device_address";
	public final static String LAST_RESET = "it.geosolutions.android.loneworker.last_reset";
	public final static String TELEPHONE_NUMBER = "it.geosolutions.android.loneworker.telephone_number";
	public final static String TIME_INTERVAL = "it.geosolutions.android.loneworker.time_interval";
	public final static String USER_ID = "it.geosolutions.android.loneworker.user_id";
	public final static String TIME_EXPIRED_INTENT = "it.geosolutions.android.loneworker.time_expired";
	public final static String TIME_EXPIRED_INTENT_EVENTTIME = "it.geosolutions.android.loneworker.time_expired_time";
	
	private static final String TAG = BluetoothService.class.getSimpleName();
	
	private NotificationManager notificationMgr;
	
	// Name of the connected device
	private String mConnectedDeviceAdress = null;
	
	// Local Bluetooth adapter
	private BluetoothAdapter mBluetoothAdapter = null;
	private BluetoothConnector mBtService = null;

	private Vibrator vibrator;
	
	private MStopReceiver mStopReceiver;
	
	private BluetoothHandler btHandler;
	
	static class BluetoothHandler extends Handler{
		WeakReference<BluetoothService> service;

		BluetoothHandler(BluetoothService service) {
			this.service = new WeakReference<BluetoothService>(service);
		}	
		
		@Override
		public void handleMessage(Message msg) {
			BluetoothService bs = service.get();
			
			switch (msg.what) {
			case MESSAGE_STATE_CHANGE:
				
				switch (msg.arg1) {
				case BluetoothConnector.STATE_CONNECTED:
					//the device was connected
					
					//save last state to recover if this service dies
					Editor ed = PreferenceManager.getDefaultSharedPreferences(bs).edit();
					ed.putLong(LAST_RESET, System.currentTimeMillis());
					ed.putString(DEVICE_ADDRESS, bs.mConnectedDeviceAdress);
					ed.commit();
					
					bs.vibrator.cancel();
					bs.mCountDown.cancel();
					bs.mCountDown.start();
										
					break;
				case BluetoothConnector.STATE_CONNECTING:
				case BluetoothConnector.STATE_LISTEN:
				case BluetoothConnector.STATE_NONE:
					break;
				}
				break;
			case MESSAGE_READ:
				byte[] readBuf = (byte[]) msg.obj;
				// construct a string from the valid bytes in the buffer
				String readMessage = new String(readBuf, 0, msg.arg1);
				Log.d(TAG, "MESSAGE_READ: " + readMessage);
				bs.sendUIUpdate(UI_UPDATE_STATE,readMessage);

				break;
			case MESSAGE_DEVICE_NAME:
				break;
			case MESSAGE_CONNECTION_LOST:
				if(bs.mConnectedDeviceAdress != null){
					bs.connectDevice(bs.mConnectedDeviceAdress);
				}
				break;
			case MESSAGE_CONNECTION_FAILED:
				//Toast.makeText(bs.getBaseContext(), "connection failed",Toast.LENGTH_SHORT).show();
				break;
			}
		}
	};
	private MCountDownTimer mCountDown;
	class MCountDownTimer extends CountDownTimer {

		public MCountDownTimer(long millisInFuture, long countDownInterval) {
			super(millisInFuture, countDownInterval);
		}
		
		@SuppressLint("DefaultLocale")
		public String timeStringFromLong(long millis){
			
			return String.format(Locale.getDefault(),"%02d:%02d:%02d", 
					TimeUnit.MILLISECONDS.toHours(millis),
					TimeUnit.MILLISECONDS.toMinutes(millis),
					TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis))
					);
		}

		@Override
		public void onFinish() {
			

			sendBroadcast(new Intent(TIME_EXPIRED_INTENT));
			
			sendUIUpdate(UI_UPDATE_TIME, timeStringFromLong(0));

			acquireLocationAndSendSMS();
			
			Editor ed = PreferenceManager.getDefaultSharedPreferences(getBaseContext()).edit();	
			ed.putLong(TIME_EXPIRED_INTENT_EVENTTIME, System.currentTimeMillis());
			ed.commit();
			
		}

		@Override
		public void onTick(long millis) {

			sendUIUpdate(UI_UPDATE_TIME, timeStringFromLong(millis));

			if(millis < Constants.VIBRATE_THRESHOLD){
				//interpolate vibration time to increase vibration when time is elapsing
				int frequency = (int) ((Constants.MAX_VIBRATE * (1 - ((float) millis / Constants.VIBRATE_THRESHOLD))) + Constants.MIN_VIBRATE);
				Log.d(TAG, "vibrating for :"+frequency);

				vibrator.vibrate(frequency);
				
			}
			
			if(millis < Constants.PLAY_SOUND_THRESHOLD){
				try {
				    Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
				    Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
				    r.play();
//				    playsSound = true;
				} catch (Exception e) {
					Log.e(TAG, e.getClass().getSimpleName(),e);
				}
			}
			Log.d(TAG, "remaining :"+millis);
		}   
	};
	
	@Override
	public void onCreate() {
		super.onCreate();
		Log.d(TAG, "onCreate");
		
		vibrator = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);		
		
		// Get local Bluetooth adapter
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		
		mStopReceiver = new MStopReceiver();
		registerReceiver(mStopReceiver, new IntentFilter(STOP_SERVICE));
		
		notificationMgr =(NotificationManager)getSystemService( NOTIFICATION_SERVICE);
		Notification n = displayNotificationMessage(getResources().getString(R.string.notification_start),true);
		
		startForeground(THIS_SERVICE_ID, n);
		
		btHandler =  new BluetoothHandler(this);
		
		
	}
	@Override
	public int onStartCommand(Intent intent, int flags, int startId){
		super.onStartCommand(intent, flags, startId);
		Log.d(TAG, "on StartCommand, startID : "+startId);
		
		String address = null;
		final long savedInterval = PreferenceManager.getDefaultSharedPreferences(getBaseContext()).getLong(BluetoothService.TIME_INTERVAL, 0);
		long remainingMillis = savedInterval;
		
		if (startId == 2) { // handles service crash
			
			//TODO test this
			
			long lastReset = PreferenceManager.getDefaultSharedPreferences(getBaseContext()).getLong(LAST_RESET, 0);
			address = PreferenceManager.getDefaultSharedPreferences(getBaseContext()).getString(DEVICE_ADDRESS, null);
			
			long timePassed = System.currentTimeMillis() - lastReset;
			remainingMillis = savedInterval - timePassed;
			
		}else{
			if(intent != null){				
				address = intent.getStringExtra(DEVICE_ADDRESS);
			}
		}
		
		
		if(address == null || address.equals("")){
			//invalid, cannot continue
			throw new IllegalArgumentException("no device address provided");
		}
		
		mCountDown = new MCountDownTimer(remainingMillis, Constants.COUNTDOWN_FREQUENCY);
		
		mConnectedDeviceAdress = address;
		
		setupBluetoothConnector();
		
		return Service.START_STICKY;
	}
	@Override
	public void onDestroy() {
		super.onDestroy();
        Log.d(TAG, "onDestroy");
        
        stop();
                
	}
	
	private void setupBluetoothConnector() {
		Log.d(TAG, "setupService()");

		// Initialize the BluetoothChatService to perform bluetooth connections
		mBtService = new BluetoothConnector(this, btHandler);

		connectDevice(mConnectedDeviceAdress);
		mCountDown.start();
	}

	@Override
	public IBinder onBind(Intent intent) {
		
		return null;
	}
	
	@SuppressWarnings("deprecation")
	private Notification displayNotificationMessage(String message, boolean autoCancel) {
		
		Notification notification = null;	
	
		try{
			NotificationCompat.Builder builder  = new NotificationCompat.Builder(this)
			.setContentTitle(message)
			.setAutoCancel(autoCancel)
			.setContentText(getString(R.string.app_name))
			.setSmallIcon(R.drawable.ic_stat_lw);
			
			Intent resultIntent = new Intent(this, LoneWorkerActivity.class);
			TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
			// Adds the back stack for the Intent (but not the Intent itself)
			stackBuilder.addParentStack(LoneWorkerActivity.class);
			// Adds the Intent that starts the Activity to the top of the stack
			stackBuilder.addNextIntent(resultIntent);
			PendingIntent resultPendingIntent = stackBuilder.getPendingIntent( 0,  PendingIntent.FLAG_UPDATE_CURRENT);
			builder.setContentIntent(resultPendingIntent);
			notification = builder.build();
		}catch(Exception e){
			Log.e(TAG, "error using NotificationCompat", e);
			//happened to me in production quite often, use instead deprecated notification system
			notification = new Notification(R.drawable.ic_stat_lw, message,System.currentTimeMillis());
			PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, LoneWorkerActivity.class), 0);
			notification.setLatestEventInfo(this, getString(R.string.app_name),message, contentIntent);
			notificationMgr.notify(THIS_SERVICE_ID, notification);		
		}

		return notification;
	}
	
	private void connectDevice(String address) {

		BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
		// Attempt to connect to the device
		mBtService.connect(device);

	}

	public static boolean isSimulator(){

		return "sdk".equals( Build.PRODUCT );
	}
	
	public void acquireLocationAndSendSMS(){
		
		//acquire location callback, invoked when found
		LocationResultCallback locationResult = new LocationResultCallback(){
			@Override
			public void gotLocation(final Location location){
				
				final String id = PreferenceManager.getDefaultSharedPreferences(getBaseContext()).getString(BluetoothService.USER_ID, null);
				String toSend = null;
				
				//the location can still be null if after timeout no location was available. This should happen very rarely
				if(location == null){
					//TODO what to do when there is no location ? wait unlimited, send another message ?
					//for now send anyway with a message that the location could not be found
					toSend = getString(R.string.could_not_acquire_location);
					
					if(id != null){
						//TODO define this different, better, normalized ?
						toSend += " "+id;
					}			
				}else{

					toSend = GeoUtil.locationToString(location);
					
					if(id != null){
						//TODO define this different, better, normalized ?
						toSend += " "+id;
					}
				}	
				
				//send
				SMSHelper.sendSMS(getBaseContext(), toSend);
				
				sendUIUpdate(UI_UPDATE_STATE,getString(R.string .message_sent)+ " "+toSend);
				
				//stop this service
				stop();

				
				
			}
		};
		//retrieve location with timeout set in constants
		new LocationProvider().getLocation(BluetoothService.this, locationResult);
	}
	

	
	public void sendUIUpdate(String intent_id,String message){
		
		Intent i = new Intent(intent_id);
		
		if(message != null){
			i.putExtra(UI_UPDATE_MESSAGE, message);
		}
		
		sendBroadcast(i);
	}
	
	private class MStopReceiver extends BroadcastReceiver{

		@Override
		public void onReceive(Context context, Intent intent) {
			if(intent.getAction().equals(STOP_SERVICE)){
				
				stop();
			}
		}
	}
	
	public void stop(){
		
        stopForeground(true);
        
        if(mStopReceiver != null){
        	unregisterReceiver(mStopReceiver);
        	mStopReceiver = null;
        }
        
        //inform user dervice stopped
		displayNotificationMessage(getResources().getString(R.string.notification_stop),true);
		
		//remove notification after delay
		new Handler().postDelayed(new Runnable() {		
			@Override
			public void run() {		
				notificationMgr.cancel(THIS_SERVICE_ID);		
			}
		},2000);
		
		if(mCountDown != null){
			mCountDown.cancel();
		}
		if(mBtService != null){
			mBtService.stop();
		}
		
    	BluetoothService.this.stopSelf();
	}

}