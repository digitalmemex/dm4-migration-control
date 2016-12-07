package de.taz.migrationcontrol;

import java.util.List;

import de.deepamehta.core.JSONEnabled;

public interface MigrationControlService {

	static String NS(String suffix) {
		return "de.taz.migrationcontrol." + suffix;
	}

	static String NS(String type, String associationType) {
		return "de.taz.migrationcontrol." + type + "#" + "de.taz.migrationcontrol." + associationType;
	}
	
	List<Country> getCountries(String languageCode);
	
	Country getCountry(String languageCode, long id);
	
	void importData(String importDataType, String importDataCsv);

	interface Country extends JSONEnabled {}

}
