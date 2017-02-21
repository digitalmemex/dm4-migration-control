package de.taz.migrationcontrol;

import java.util.HashMap;
import java.util.Map;

public class CountryHelper {

	static Map<String, String> countryCodeMap = new HashMap<>();

	static {
		put("Ägypten","eq");
		put("Algerien","dz");
		put("Äthiopien","et");
		put("Benin","bj");
		put("Burkina Faso", "bf");
		put("Dschibuti", "dj");
		put("Elfenbeinküste", "ci");
		put("Eritrea", "er");
		put("Gambia", "gm");
		put("Ghana", "gh");
		put("Guinea", "gn");
		put("Kamerun", "cm");
		put("Kap Verde", "cv");
		put("Kenia", "ke");
		put("Libyen", "ly");
		put("Mali", "ml");
		put("Marokko", "ma");
		put("Mauretanien", "mr");
		put("Niger", "ne");
		put("Nigeria", "ng");
		put("Senegal", "sn");
		put("Sierra Leone", "sl");
		put("Somalia", "so");
		put("Sudan", "sd");
		put("Südsudan", "ss");
		put("Togo", "tg");
		put("Tschad", "td");
		put("Tunesien", "tn");
		put("Uganda", "ug");
		put("ECOWAS", "ecowas");
		put("Türkei", "tr");
		put("EU", "eu");
		put("Deutschland", "de");
		put("Italien", "it");
		put("Belgien", "be");
		put("Dänemark", "dk");
		put("Niederlande", "nl");
		put("Spanien", "es");
		put("Israel", "il");
		put("Frankreich", "fr");
		put("Mosambik", "mz");
		put("Guinea Bissau", "gw");
		put("Griechenland", "gr");
		put("UK", "uk");
	}

	private static void put(String name, String countryCode) {
		countryCodeMap.put(name, countryCode);
	}
	
	public static String getCountryCode(String germanCountryName) {
		return countryCodeMap.get(germanCountryName);
	}
}
