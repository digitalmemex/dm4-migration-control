package de.taz.migrationcontrol;

import java.net.URL;
import java.util.logging.Logger;

import org.codehaus.jettison.json.JSONObject;

import de.deepamehta.core.util.JavaUtils;

public class GeoCodingHelper {
	
	private static Logger logger = Logger.getLogger(GeoCodingHelper.class.getName());
	
	private static final String GEOCODER_URL = "http://maps.googleapis.com/maps/api/geocode/json?" +
	        "address=%s&sensor=false";
	
	public static double[] geocode(String countryName) {
        URL url = null;
        try {
            // perform request
            String address = countryName;
            url = new URL(String.format(GEOCODER_URL, JavaUtils.encodeURIComponent(address)));
            logger.info("### Geocoding \"" + address + "\"\n    url=\"" + url + "\"");
            JSONObject response = new JSONObject(JavaUtils.readTextURL(url));
            // check response status
            String status = response.getString("status");
            if (!status.equals("OK")) {
                throw new RuntimeException(status);
            }
            // parse response
            JSONObject location = response.getJSONArray("results").getJSONObject(0).getJSONObject("geometry")
                .getJSONObject("location");
            double lng = location.getDouble("lng");
            double lat = location.getDouble("lat");
            
            return new double[] { lat, lng };
        } catch (Exception e) {
            return null;
        }
    }

}
