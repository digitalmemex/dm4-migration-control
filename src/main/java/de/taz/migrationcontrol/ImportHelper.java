package de.taz.migrationcontrol;

import static de.taz.migrationcontrol.MigrationControlService.NS;

import java.io.IOException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.List;
import java.util.Locale;

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
	
	CoreService dm4;
	ModelFactory mf;
	WorkspacesService wsService;
	
	public ImportHelper(CoreService dm4, ModelFactory mf, WorkspacesService wsService) {
		this.dm4 = dm4;
		this.mf = mf;
		this.wsService = wsService;
	}
	
	private static <T> T selfOrDefault(T instance, T defaultValue) {
		return (instance != null) ? instance : defaultValue;
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

	public void importPayments(CSVParser data) throws IOException {
		importStatistic("Payments", data);
	}
	
	public void importODA(CSVParser data) throws IOException {
		importStatistic("ODA", data);
	}
	
	private void importStatistic(String statName, CSVParser data) throws IOException {
		NumberFormat nf = NumberFormat.getInstance(Locale.GERMANY);
		
		Topic statType = findStatisticsType(statName);
		
		// first row :       <none>,  year,  year,  year, ...
		// other rows: country name, value, value, value, ...
		// special value: .. -> no value available
		List<CSVRecord> records = data.getRecords();
		CSVRecord firstRow = records.get(0);
		
		// iterates over all countries
		for (int i = 1;i < records.size(); i++) {
			CSVRecord row = records.get(i);

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

	
}
