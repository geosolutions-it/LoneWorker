package it.geosolutions.android.loneworker.location;

import it.geosolutions.android.loneworker.constants.Constants;

import java.util.Timer;
import java.util.TimerTask;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

public class LocationProvider {
	
	private final static String TAG = LocationProvider.class.getSimpleName();
	
	private Timer timer;
	private LocationManager locationManager;
	private LocationResultCallback locationResultCallback;
	private boolean gps_enabled=false;
	private boolean network_enabled=false;


	public boolean getLocation(Context context, LocationResultCallback result)	{

		try{
			
			locationResultCallback = result;
			
			if(locationManager == null){			
				locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
			}

			//exceptions will be thrown if provider is not permitted.
			try{
				gps_enabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
				}catch(Exception ex){					
					//does not interest here
				}
			try{
				network_enabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
				}catch(Exception ex){
					//does not interest here
				}

			//don't start listeners if no provider is enabled
			if(!gps_enabled && !network_enabled){				
				return false;
			}
			//if available, get gps location
			if(gps_enabled){
				locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListenerGps);				
			}
			//if available, get network location
			if(network_enabled){
				locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListenerNetwork);				
			}
			
			//start a timer which will start after the timeout threshold
			timer = new Timer();
			timer.schedule(new GetLastLocation(), Constants.LOCATION_ACQUIRE_TIMEOUT);
			
		}catch(final Exception e){
			Log.e(TAG, "error gotLocation",e);
		}
		return true;
	}

	LocationListener locationListenerGps = new LocationListener() {
		public void onLocationChanged(Location location) {
			try{
				//gps location found, cancel timer, report location, remove listener
				timer.cancel();
				locationResultCallback.gotLocation(location);
				locationManager.removeUpdates(this);
				locationManager.removeUpdates(locationListenerNetwork);
			}catch(final Exception e){
				Log.e(TAG, "error gotLocation",e);
			}
		}
		public void onProviderDisabled(String provider) {}
		public void onProviderEnabled(String provider) {}
		public void onStatusChanged(String provider, int status, Bundle extras) {}
	};

	LocationListener locationListenerNetwork = new LocationListener() {
		public void onLocationChanged(Location location) {
			try{
				//network location found, cancel timer, report location, remove listener
				timer.cancel();
				locationResultCallback.gotLocation(location);
				locationManager.removeUpdates(this);
				locationManager.removeUpdates(locationListenerGps);
			}catch(final Exception e){
				Log.e(TAG, "error gotLocation",e);
			}
		}
		public void onProviderDisabled(String provider) {}
		public void onProviderEnabled(String provider) {}
		public void onStatusChanged(String provider, int status, Bundle extras) {}
	};


	class GetLastLocation extends TimerTask {
		@Override
		public void run() {
			try{
				//the TIMEOUT period was reached, the listeners did not fire, remove updates
				locationManager.removeUpdates(locationListenerGps);
				locationManager.removeUpdates(locationListenerNetwork);

				//check if there is at least a last known location
				Location net_loc=null, gps_loc=null;
				if(gps_enabled){					
					gps_loc=locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
				}
				if(network_enabled){					
					net_loc=locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
				}

				//if there are both values use the latest one
				if(gps_loc!=null && net_loc!=null){
					if(gps_loc.getTime() > net_loc.getTime()){
						locationResultCallback.gotLocation(gps_loc);
					}else{
						locationResultCallback.gotLocation(net_loc);
					}
					return;
				}
				//if available, report
				if(gps_loc != null){
					locationResultCallback.gotLocation(gps_loc);
					return;
				}
				if(net_loc != null){
					locationResultCallback.gotLocation(net_loc);
					return;
				}
				//all failed, report null
				locationResultCallback.gotLocation(null);
				
			}catch(final Exception e){
				Log.e(TAG, "error gotLocation",e);
			}
		}
	}

	public static abstract class LocationResultCallback{
		public abstract void gotLocation(Location location);
	}
}