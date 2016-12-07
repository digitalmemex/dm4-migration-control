package de.taz.migrationcontrol;

import static de.taz.migrationcontrol.MigrationControlService.NS;

import java.io.IOException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import de.deepamehta.core.DeepaMehtaObject;
import de.deepamehta.core.Topic;
import de.deepamehta.core.model.ChildTopicsModel;
import de.deepamehta.core.model.SimpleValue;
import de.deepamehta.core.model.TopicModel;
import de.deepamehta.core.service.CoreService;
import de.deepamehta.core.service.ModelFactory;
import de.deepamehta.workspaces.WorkspacesService;

public class ImportHelper {
	
	static Logger logger = Logger.getLogger(ImportHelper.class.getName());
	
	CoreService dm4;
	ModelFactory mf;
	WorkspacesService wsService;

	NumberFormat nf = NumberFormat.getInstance(Locale.GERMANY);
	
	public ImportHelper(CoreService dm4, ModelFactory mf, WorkspacesService wsService) {
		this.dm4 = dm4;
		this.mf = mf;
		this.wsService = wsService;
	}
	
	public void importHDI(CSVParser data) throws IOException {
		importStatistic("HDI", data);
	}

	public void importRemittances(CSVParser data) throws IOException {
		importStatistic("Remittances", data);
	}

	public void importMigrationIntensity(CSVParser data) throws IOException {
		importStatistic("Migration Intensity", data);
	}

	public void importODA(CSVParser data) throws IOException {
		importStatistic("ODA", data);
	}
	
	private void importStatistic(String statName, CSVParser data) throws IOException {
		logger.info("importing " + statName);
		
		Topic statType = findStatisticsType(statName);
		
		// first row :       <none>,  year,  year,  year, ...
		// other rows: country name, value, value, value, ...
		// special value: .. -> no value available
		List<CSVRecord> records = data.getRecords();
		CSVRecord firstRow = records.get(0);
		logger.info("parsed CSV: " + records.size() + " countries");
		
		// iterates over all countries
		for (int i = 1;i < records.size(); i++) {
			CSVRecord row = records.get(i);
			logger.info("importing " + statName + " for " + row.get(0));

			ChildTopicsModel childs = mf.newChildTopicsModel();
			childs.putRef(NS("statistic.type"), statType.getId());
			childs.putRef("dm4.contacts.country",
					findCountryOrCreate(row.get(0)).getId());
			
			for (int j = 1; j < row.size(); j++) {
				try {
					double value = nf.parse(row.get(j)).doubleValue(); 	// this is expected to fail sometimes
					int year = Integer.parseInt(firstRow.get(j));	// this is expected never to fail
					Topic statisticEntry = createStatisticEntry(year, value);
					
					childs.addRef(NS("statistic.entry"), statisticEntry.getId());
				} catch (NumberFormatException|ParseException e) {
					// Ignored - no value is put into DB for the current year
				}
			}
			
			// Creates the statistic for one country
			Topic t = dm4.createTopic(mf.newTopicModel(NS("statistic"), childs));
			
			assignToDataWorkspace(t);
		}
	}
	
	private Topic createStatisticEntry(int year, double value) {
		ChildTopicsModel childs = mf.newChildTopicsModel();
		childs.put(NS("statistic.entry.value"), value);
		
		putRefOrCreate(childs, "dm4.datetime.year", year);
		
		Topic t = dm4.createTopic(mf.newTopicModel(NS("statistic.entry"), childs));
		
		assignToDataWorkspace(t);
		
		return t;
	}
	
	private Topic findStatisticsType(String statType) {		
		for (Topic t : dm4.getTopicsByType(NS("statistic.type"))) {
			if (statType.equals(t.getSimpleValue().toString())) {
				return t;
			}
		}
		
		throw new IllegalStateException("Unknown statistics type: " + statType);
	}
	
	private Topic findCountryOrCreate(String countryName) {		
		for (Topic country : dm4.getTopicsByType("dm4.contacts.country")) {
			if (countryName.equals(country.getSimpleValue().toString())) {
				return country;
			}
		}
				
		Topic topic = dm4.createTopic(mf.newTopicModel("dm4.contacts.country", new SimpleValue(countryName)));

		assignToDataWorkspace(topic);
		
		return topic;		
	}

	private void putRefOrCreate(ChildTopicsModel childs, String typeUri, Object value) {
		SimpleValue sv = new SimpleValue(value);
		List<Topic> results = dm4.getTopicsByType(typeUri);
		for (Topic t : results) {
			if (t.getSimpleValue().equals(sv)) {
				childs.putRef(typeUri, t.getId());
				
				return;
			}
		}
		
		TopicModel tm = mf.newTopicModel(typeUri);
		tm.setSimpleValue(sv);
		Topic t = dm4.createTopic(tm);
		
		assignToDataWorkspace(t);
		
		childs.putRef(typeUri, t.getId());
	}

	private void assignToDataWorkspace(DeepaMehtaObject obj) {
		// Assigns the new value to the 'data' workspace
		long wsId = wsService.getWorkspace(NS("workspace.data")).getId();
		wsService.assignToWorkspace(obj, wsId);
	}

	public void importFactSheet(CSVParser data) throws IOException {
		logger.info("importing factsheet");
		
		// first row :       just header, not used
		// other rows: country name, value, value, value, ...
		// special value: .. -> no value available
		List<CSVRecord> records = data.getRecords();
		logger.info("parsed CSV: " + records.size() + " countries");
		
		// iterates over all countries
		for (int i = 1;i < records.size(); i++) {
			CSVRecord row = records.get(i);
			String country = row.get(0);
			logger.info("importing factsheet for " + country);
			try {
				int refugeesInCountry = Integer.parseInt(row.get(1));
				int refugeesOutsideCountry = Integer.parseInt(row.get(2));
				int refugeesInEU = Integer.parseInt(row.get(3));
				int idp = Integer.parseInt(row.get(4));
				int applicationsForAsylum = Integer.parseInt(row.get(5));
				double asylumApprovalRate = nf.parse(row.get(6)).doubleValue();
				boolean hasFrontexCooperation = "ja".equals(row.get(7));
				int detentionCentercount = Integer.parseInt(row.get(8));
				boolean departureIsIllegal = "ja".equals(row.get(9));

				ChildTopicsModel childs = mf.newChildTopicsModel();
				childs.putRef("dm4.contacts.country",
						findCountryOrCreate(country).getId());
				childs.put(NS("factsheet.refugeesincountry"), refugeesInCountry);
				childs.put(NS("factsheet.refugeesoutsidecountry"), refugeesOutsideCountry);
				childs.put(NS("factsheet.refugeesineu"), refugeesInEU);
				childs.put(NS("factsheet.idp"), idp);
				childs.put(NS("factsheet.applicationsforasylum"), applicationsForAsylum);
				childs.put(NS("factsheet.asylumapprovalrate"), asylumApprovalRate);
				childs.put(NS("factsheet.hasfrontexcooperation"), hasFrontexCooperation);
				childs.put(NS("factsheet.detentioncentercount"), detentionCentercount);
				childs.put(NS("factsheet.departureisillegal"), departureIsIllegal);

				// Creates the statistic for one country
				Topic t = dm4.createTopic(mf.newTopicModel(NS("factsheet"), childs));
				
				assignToDataWorkspace(t);
			} catch (NumberFormatException|ParseException e) {
				// Ignored - factsheet for country will not be added
				logger.warning("Failed to import factsheet for country: " + country);
			}
			
		}
	}
	
}
