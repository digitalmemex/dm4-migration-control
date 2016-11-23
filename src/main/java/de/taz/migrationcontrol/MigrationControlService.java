package de.taz.migrationcontrol;

import de.deepamehta.core.JSONEnabled;

public interface MigrationControlService {

	static String NS(String suffix) {
		return "de.taz.migrationcontrol." + suffix;
	}
	
	Country getCountry(String languageCode, long id);
	
	void importData(String importDataType, String importDataCsv);

	interface Country extends JSONEnabled {}

}
