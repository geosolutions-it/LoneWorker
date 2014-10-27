package it.geosolutions.android.loneworker.location;

import android.location.Location;

public class GeoUtil {

	/**
	 * From the reference application for the GeoSMS specification
	 * https://code.google.com/p/android-i-am-here/
	 * https://code.google.com/r/bellseanm-i-am-here-android/source/browse/IAmHere/src/au/com/darkside/iamhere/GeoUri.java
	 * 
	 * @param loc
	 * @return
	 */
	public static String locationToString ( final Location  loc ) {
		
		StringBuilder sb = new StringBuilder("geo:");

		// Add location
		sb.append(doubleToString (loc.getLatitude (), 6)).append(",")
				.append(doubleToString (loc.getLongitude (), 6));

		// Add altitude
		if (loc.hasAltitude()){
			sb.append(",").append(doubleToString (loc.getAltitude (), 3));
		}

		// Add accuracy
		if (loc.hasAccuracy ()){
			sb.append(";u=").append(doubleToString (loc.getAccuracy (), 3));
		}

		return sb.toString();
	}

	/*
	 * Return a real number as a string, with a maximum number of
	 * decimal places.
	 */
	private static String  doubleToString (double d,int decimalPlaces) {
		String          ret;

		if (d < 0.0) {
			d = -d;
			ret = "-";
		} else
			ret = "";

		String          s = String.valueOf ((int) Math.round (d * Math.pow (10,
				decimalPlaces)));

		if (s == "0")
			return s;

		while (s.length () < decimalPlaces + 1)
			s = "0" + s;

		ret += s.substring (0, s.length() - decimalPlaces);     // Integer component.
		s = s.substring (s.length () - decimalPlaces);  // Fractional component.

		while (s.length () > 0 && s.charAt (s.length () - 1) == '0')
			s = s.substring(0, s.length () - 1);    // Strip trailing zeroes.

		if (s.length () > 0)
			ret += "." + s;         // Append decimal places, if any.

		return ret;
	}

}
