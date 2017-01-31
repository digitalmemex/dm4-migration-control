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

	List<CountriesOverview> getCountriesOverview(String languageCode);
	
	List<Country> getCountries(String languageCode);
	
	Country getCountry(String languageCode, long id);
	
	Country getCountry(String languageCode, String countryCode);

	List<Thesis> getTheses(String languageCode);
	
	Thesis getThesis(String languageCode, long id);

	List<BackgroundOverview> getBackground(String languageCode);
	
	BackgroundItem getBackgroundItem(String languageCode, long id);
	
	List<DetentionCenter> getDetentionCenters(String languageCode);
	
	DetentionCenter getDetentionCenter(String languageCode, long id);

	List<ImprintItem> getImprintItems(String languageCode);
	
	ImprintItem getImprintItem(String languageCode, long id);

	void importData(String dataType, String dataCsv);
	
	void resetAllData();

	interface CountriesOverview extends JSONEnabled {}
	
	interface Country extends JSONEnabled {}
	
	interface Thesis extends JSONEnabled {}

	interface BackgroundOverview extends JSONEnabled {}

	interface BackgroundItem extends JSONEnabled {}

	interface DetentionCenter extends JSONEnabled {}

	interface ImprintItem extends JSONEnabled {}

}
