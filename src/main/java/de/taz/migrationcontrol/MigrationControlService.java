package de.taz.migrationcontrol;

public interface MigrationControlService {
	
	void importData(String importDataType, String importDataCsv);

	static String NS(String suffix) {
		return "de.taz.migrationcontrol." + suffix;
	}

}
