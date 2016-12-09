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

	CountriesOverview getCountryOverview(String languageCode);
	
	List<Country> getCountries(String languageCode);
	
	Country getCountry(String languageCode, long id);
	
	List<Thesis> getTheses(String languageCode);
	
	Thesis getThesis(String languageCode, long id);

	Background getBackground(String languageCode);
	
	BackgroundItem getBackgroundItem(String languageCode, long id);
	
	void importData(String importDataType, String importDataCsv);

	interface CountriesOverview extends JSONEnabled {}
	
	interface Country extends JSONEnabled {}
	
	interface Thesis extends JSONEnabled {}

	interface Background extends JSONEnabled {}

	interface BackgroundItem extends JSONEnabled {}

}
