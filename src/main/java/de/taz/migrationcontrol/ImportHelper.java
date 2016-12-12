package de.taz.migrationcontrol;

import static de.taz.migrationcontrol.MigrationControlService.NS;

import java.io.IOException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import de.deepamehta.core.Association;
import de.deepamehta.core.ChildTopics;
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
		importStatistics("HDI", data);
	}

	public void importRemittances(CSVParser data) throws IOException {
		importStatistics("Remittances", data);
	}

	public void importMigrationIntensity(CSVParser data) throws IOException {
		importStatistics("Migration Intensity", data);
	}

	public void importODA(CSVParser data) throws IOException {
		importStatistics("ODA", data);
	}
	
	private void importStatistics(String statName, CSVParser data) throws IOException {
		Topic statType = findStatisticsType(statName);
		
		deleteAllWithMatchingChild(NS("statistic"), NS("statistic.type"), statType);
		logger.info("importing " + statName);
		
		
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
	
	private Topic findTreatyType(String treatyTypeName) {		
		for (Topic t : dm4.getTopicsByType(NS("treaty.type"))) {
			if (treatyTypeName.equals(t.getSimpleValue().toString())) {
				return t;
			}
		}
		
		throw new IllegalStateException("Unknown treaty type: " + treatyTypeName);
	}
	
	private Topic findFrontexCooperationState(String stateName) {		
		for (Topic t : dm4.getTopicsByType(NS("factsheet.frontexcooperationinfo.state"))) {
			if (stateName.equals(t.getSimpleValue().toString())) {
				return t;
			}
		}
		
		throw new IllegalStateException("Unknown frontext cooperation state: " + stateName);
	}
	
	private Topic findThesisDiagramType(String diagramType) {		
		for (Topic t : dm4.getTopicsByType(NS("thesis.diagramtype"))) {
			if (diagramType.equals(t.getSimpleValue().toString())) {
				return t;
			}
		}
		
		throw new IllegalStateException("Unknown thesis diagram type: " + diagramType);
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
		deleteAll(NS("factsheet"));
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
				String frontexCooperationState = row.get(7);
				String frontexCooperationDescription = row.get(8);
				int detentionCentercount = asInt(row.get(9), -1);
				String detentionCenterDescription = row.get(10);
				boolean departureIsIllegal = "ja".equals(row.get(11));
				String departureDescription = row.get(12);

				ChildTopicsModel childs = mf.newChildTopicsModel();
				childs.putRef("dm4.contacts.country",
						findCountryOrCreate(country).getId());
				childs.put(NS("factsheet.refugeesincountry"), refugeesInCountry);
				childs.put(NS("factsheet.refugeesoutsidecountry"), refugeesOutsideCountry);
				childs.put(NS("factsheet.refugeesineu"), refugeesInEU);
				childs.put(NS("factsheet.idp"), idp);
				childs.put(NS("factsheet.applicationsforasylum"), applicationsForAsylum);
				childs.put(NS("factsheet.asylumapprovalrate"), asylumApprovalRate);
				childs.putRef(NS("factsheet.frontexcooperationinfo"), makeFrontexCooperationInfo(frontexCooperationState, frontexCooperationDescription).getId());
				childs.putRef(NS("factsheet.detentioncenterinfo"), makeDetentionCenterInfo(detentionCentercount, detentionCenterDescription).getId());
				childs.putRef(NS("factsheet.departurelegalityinfo"), makeDepartureLegalityInfo(departureIsIllegal, departureDescription).getId());

				// Creates the statistic for one country
				Topic t = dm4.createTopic(mf.newTopicModel(NS("factsheet"), childs));
				
				assignToDataWorkspace(t);
			} catch (NumberFormatException|ParseException e) {
				// Ignored - factsheet for country will not be added
				logger.log(Level.WARNING, "Failed to import factsheet for country: " + country, e);
			}
			
		}
	}
	
	private String tableValueToDMValue(String tableValue) {
		switch (tableValue) {
		case "ja":
			return "yes";
		case "nein":
			return "no";
		case "unbekannt":
			return "unknown";
		case "Verhandlung":
			return "negotiating";
		default:
			return null;
		}
	}
	
	private Topic makeFrontexCooperationInfo(String cooperationStateFromTable, String description) {
		ChildTopicsModel childs = mf.newChildTopicsModel();
		
		childs.putRef(NS("factsheet.frontexcooperationinfo.state"),
				findFrontexCooperationState(tableValueToDMValue(cooperationStateFromTable)).getId());
		
		childs.put(NS("factsheet.frontexcooperationinfo.description"), description);
		
		Topic t = dm4.createTopic(mf.newTopicModel(NS("factsheet.frontexcooperationinfo"), childs));
		
		assignToDataWorkspace(t);
		
		return t;
	}
	
	private Topic makeDetentionCenterInfo(int count, String description) {
		ChildTopicsModel childs = mf.newChildTopicsModel();
		
		childs.put(NS("factsheet.detentioncenterinfo.count"), count);
		childs.put(NS("factsheet.detentioncenterinfo.description"), description);
		
		Topic t = dm4.createTopic(mf.newTopicModel(NS("factsheet.detentioncenterinfo"), childs));
		
		assignToDataWorkspace(t);
		
		return t;
	}
	
	private Topic makeDepartureLegalityInfo(boolean isIllegal, String description) {
		ChildTopicsModel childs = mf.newChildTopicsModel();
		
		childs.put(NS("factsheet.departurelegalityinfo.isillegal"), isIllegal);
		childs.put(NS("factsheet.departurelegalityinfo.description"), description);
		
		Topic t = dm4.createTopic(mf.newTopicModel(NS("factsheet.departurelegalityinfo"), childs));
		
		assignToDataWorkspace(t);
		
		return t;
	}
	
	private int asInt(String string, int defaultValue) {
		try {
			return Integer.parseInt(string);
		} catch (NumberFormatException nfe) {
			return defaultValue;
		}
	}
	
	public void importRepatriationTreaties(CSVParser data) throws IOException {
		importTreaties(DTOHelper.TREATYTYPE_REPATRIATION_AGREEMENT, data);
	}

	public void importOtherTreaties(CSVParser data) throws IOException {
		importTreaties(DTOHelper.TREATYTYPE_OTHER_AGREEMENT, data);
	}
	
	private void importTreaties(String treatyTypeName, CSVParser data) throws IOException {
		Topic treatyType = findTreatyType(treatyTypeName);
		
		deleteAllWithMatchingChild(NS("treaty"), NS("treaty.type"), treatyType);
		logger.info("importing treaty: " + treatyTypeName);
		
		
		// first row :       just header, not used
		// other rows: country name, value, value, value, ...
		// special value: .. -> no value available
		List<CSVRecord> records = data.getRecords();
		logger.info("parsed CSV: " + records.size() + " countries");
		
		// iterates over all countries
		for (int i = 1;i < records.size(); i++) {
			CSVRecord row = records.get(i);
			String country = row.get(0);
			
			// If country is missing, skip the line
			if (country.length() == 0) {
				continue;
			}
			
			logger.info("importing treaty for " + country);
			try {
				String partnerCountry = row.get(1);
				String treatyName = row.get(2);
				String treatyLink = row.get(3);
				String treatyDateString = row.get(8);
				
				if (treatyName.length() == 0) {
					throw new ParseException("Country should not be empty!", -1);
				}

				ChildTopicsModel childs = mf.newChildTopicsModel();
				childs.putRef("dm4.contacts.country",
						findCountryOrCreate(country).getId());
				childs.putRef(NS("treaty.type"), treatyType.getId());
				
				if (partnerCountry.length() > 0) {
					childs.putRef("dm4.contacts.country#" + NS("treaty.partner"),
							findCountryOrCreate(partnerCountry).getId());
				}

				childs.put(NS("treaty.name"), treatyName);
				childs.put(NS("treaty.link"), treatyLink);
				
				if (treatyDateString.length() > 0 && !treatyDateString.equals("..")) {
					// TODO: Make a date instance
				}

				// Creates the statistic for one country
				Topic t = dm4.createTopic(mf.newTopicModel(NS("treaty"), childs));
				
				assignToDataWorkspace(t);
			} catch (NumberFormatException|ParseException e) {
				// Ignored - factsheet for country will not be added
				logger.log(Level.WARNING, "Failed to import treaty for country: " + country + ". Skipping.", e);
			}
			
		}
	}
	
	public void importFindingsAndFeatures(CSVParser data) throws IOException {
		deleteAll(NS("countryoverview"));
		logger.info("importing findings and features");
		
		List<CSVRecord> records = data.getRecords();
		logger.info("parsed CSV: " + records.size() + " countries");
		
		// iterates over all theses
		for (int i = 1;i < records.size(); i++) {
			CSVRecord row = records.get(i);
			String country = row.get(0);
			logger.info("importing findings and features for " + country);
			try {
				String findingUrl = row.get(1);
				String featureUrl1 = row.get(2);
				String featureUrl2 = row.get(3);
				String featureUrl3 = row.get(4);
				int columnIndex = asInt(row.get(5), 0);
				boolean isDonorCountry = row.get(6).equals("ja");
				
				if (findingUrl.length() == 0) {
					throw new ParseException("Finding URL should not be empty!", -1);
				}

				ChildTopicsModel childs = mf.newChildTopicsModel();
				childs.putRef("dm4.contacts.country",
						findCountryOrCreate(country).getId());
				
				childs.put(NS("countryoverview.columnindex"), columnIndex);
				
				childs.put(NS("countryoverview.findinglink"), findingUrl);
			
				if (featureUrl1.length() > 0) {
					childs.add(NS("countryoverview.featurelink"), newFeatureLink(featureUrl1));
				}
				
				if (featureUrl2.length() > 0) {
					childs.add(NS("countryoverview.featurelink"), newFeatureLink(featureUrl2));
				}
				
				if (featureUrl3.length() > 0) {
					childs.add(NS("countryoverview.featurelink"), newFeatureLink(featureUrl3));
				}
				childs.put(NS("countryoverview.isdonorcountry"), isDonorCountry);

				// Creates the statistic for one country
				Topic t = dm4.createTopic(mf.newTopicModel(NS("countryoverview"), childs));
				
				assignToDataWorkspace(t);
			} catch (ParseException e) {
				// Ignored - thesis will not be added
				logger.log(Level.WARNING, "Failed to import a country overview. Skipping.", e);
			}
			
		}
	}
	
	public void importBackground(CSVParser data) throws IOException {
		deleteAll(NS("backgrounditem"));
		deleteTreatyNote();
		// TODO: Delete the dm4.notes.note topic that is associated with a backgrounditem
		
		logger.info("importing background");
		
		List<CSVRecord> records = data.getRecords();
		logger.info("parsed CSV: " + records.size() + " background items");
		
		// iterates over all theses
		for (int i = 1;i < records.size(); i++) {
			CSVRecord row = records.get(i);
			logger.info("importing background " + i);
			try {
				int columnIndex = Integer.parseInt(row.get(0));
				String name = row.get(1);
				String link = row.get(2);
				
				if (name.length() == 0) {
					throw new ParseException("Background name should not be empty!", -1);
				}

				ChildTopicsModel childs = mf.newChildTopicsModel();
				childs.put(NS("backgrounditem.name"), name);
				childs.put(NS("backgrounditem.link"), link);
				childs.put(NS("backgrounditem.columnindex"), columnIndex);

				// Creates the statistic for one country
				Topic backgroundItemTopic = dm4.createTopic(mf.newTopicModel(NS("backgrounditem"), childs));
				assignToDataWorkspace(backgroundItemTopic);
				
				// If the entry does not have a link it is the "Abkommen" item that has the
				// title and text inline. We store this as a "dm4.notes.note" topic
				// and associate it with the BackgroundItem.
				if (link.length() == 0) {
					String title = row.get(3);
					String text = row.get(4);

					ChildTopicsModel childs2 = mf.newChildTopicsModel();
					childs2.put("dm4.notes.title", title);
					childs2.put("dm4.notes.text", text);
					
					Topic notesTopic = 
							dm4.createTopic(mf.newTopicModel(NS("backgrounditem.treaty.note"), "dm4.notes.note", childs2));
					assignToDataWorkspace(notesTopic);
					
					Association asso = dm4.createAssociation(mf.newAssociationModel("dm4.core.composition",
			    			mf.newTopicRoleModel(backgroundItemTopic.getId(), "dm4.core.default"),
						mf.newTopicRoleModel(notesTopic.getId(), "dm4.core.default")));
					
					assignToDataWorkspace(asso);
				}
			} catch (ParseException e) {
				// Ignored - thesis will not be added
				logger.log(Level.WARNING, "Failed to import a background item. Skipping.", e);
			}
			
		}
	}
	
	private TopicModel newFeatureLink(String string) {
		TopicModel tm = mf.newTopicModel(NS("countryoverview.featurelink"));
		tm.setSimpleValue(string);
		
		return tm;
	}

	public void importTheses(CSVParser data) throws IOException {
		deleteAll(NS("thesis"));
		
		logger.info("importing theses");
		
		List<CSVRecord> records = data.getRecords();
		logger.info("parsed CSV: " + records.size() + " theses");
		
		// iterates over all theses
		for (int i = 1;i < records.size(); i++) {
			CSVRecord row = records.get(i);
			try {
				String thesisName = row.get(1);
				String thesisText = row.get(2);
				String thesisContextualisation = row.get(3);
				String thesisSourceInfo = row.get(4);
				String thesisDiagramType = row.get(5);
				String thesisImageLink = row.get(6);
				
				if (thesisName.length() == 0) {
					throw new ParseException("Thesis name should not be empty!", -1);
				}
				if (thesisText.length() == 0) {
					throw new ParseException("Thesis text should not be empty!", -1);
				}
				if (thesisContextualisation.length() == 0) {
					throw new ParseException("Thesis contextualisation should not be empty!", -1);
				}
				if (thesisSourceInfo.length() == 0) {
					throw new ParseException("Thesis source info should not be empty!", -1);
				}
				
				ChildTopicsModel childs = mf.newChildTopicsModel();
				childs.put(NS("thesis.name"), thesisName);
				childs.put(NS("thesis.text"), thesisText);
				childs.put(NS("thesis.contextualisation"), thesisContextualisation);
				childs.put(NS("thesis.sourceinfo"), thesisSourceInfo);
				
				if (thesisDiagramType.length() > 0) {
					Topic diagramTypeTopic = findThesisDiagramType(thesisDiagramType);
					if (diagramTypeTopic == null) {
						throw new ParseException("Thesis diagram type invalid: " + thesisDiagramType, -1);
					}
					childs.putRef(NS("thesis.diagramtype"), diagramTypeTopic.getId());
				} else if (thesisImageLink.length() > 0) {
					childs.put(NS("thesis.imagelink"), thesisImageLink);
				}

				// Creates the statistic for one country
				Topic t = dm4.createTopic(mf.newTopicModel(NS("thesis"), childs));
				
				assignToDataWorkspace(t);
			} catch (ParseException e) {
				// Ignored - thesis will not be added
				logger.log(Level.WARNING, "Failed to import a thesis. Skipping", e);
			}
			
		}
	}
	
	public void importDetentionCenters(CSVParser data) throws IOException {
		deleteAll(NS("detentioncenter"));
		
		logger.info("importing detention centers");
		
		List<CSVRecord> records = data.getRecords();
		logger.info("parsed CSV: " + records.size() + " detention centers");
		
		// iterates over all theses
		for (int i = 1;i < records.size(); i++) {
			CSVRecord row = records.get(i);
			String name = row.get(0);
			
			// Skip empty lines
			if (name.length() == 0)
				continue;
			
			logger.info("importing detention center " + name);
			try {
				String link = row.get(1);
				String country = row.get(2);
				String mapPoint = row.get(3);
				
				if (country.length() == 0) {
					throw new ParseException("Finding URL should not be empty!", -1);
				}

				if (mapPoint.length() == 0) {
					throw new ParseException("Map Point should not be empty!", -1);
				}

				ChildTopicsModel childs = mf.newChildTopicsModel();
				childs.put(NS("detentioncenter.name"), name);
				childs.put(NS("detentioncenter.link"), link);
				childs.putRef("dm4.contacts.country",
						findCountryOrCreate(country).getId());
				
				childs.putRef("dm4.geomaps.geo_coordinate", createGeoCoordinateFromMapPoint(mapPoint).getId());

				// Creates the statistic for one country
				Topic t = dm4.createTopic(mf.newTopicModel(NS("detentioncenter"), childs));
				
				assignToDataWorkspace(t);
			} catch (ParseException|NumberFormatException e) {
				// Ignored - thesis will not be added
				logger.log(Level.WARNING, "Failed to import a detention center. Skipping.", e);
			}
			
		}
	}
	
	private Topic createGeoCoordinateFromMapPoint(String mapPoint) throws ParseException, NumberFormatException {
		String[] parts = mapPoint.split(" ");
		if (parts.length == 2) {
			double lat = Double.parseDouble(parts[0].trim());
			double lon = Double.parseDouble(parts[1].trim());
			
			ChildTopicsModel childs = mf.newChildTopicsModel();
			childs.put("dm4.geomaps.latitude", lat);
			childs.put("dm4.geomaps.longitude", lon);
			
			Topic topic = dm4.createTopic(mf.newTopicModel("dm4.geomaps.geo_coordinate", childs));
			assignToDataWorkspace(topic);
			
			return topic;
		} else {
			throw new ParseException("Map point not well formed: " + mapPoint, parts.length);
		}
	}

	public void importImprint(CSVParser data) throws IOException {
		deleteAll(NS("imprintitem"));
		
		logger.info("importing imprint");
		
		List<CSVRecord> records = data.getRecords();
		logger.info("parsed CSV: " + records.size() + " imprint items");
		
		// iterates over all theses
		for (int i = 1;i < records.size(); i++) {
			CSVRecord row = records.get(i);
			logger.info("importing imprint " + i);
			try {
				String name = row.get(0);
				String text = row.get(1);
				String link = row.get(2);
				
				if (name.length() == 0) {
					throw new ParseException("name should not be empty!", -1);
				}

				ChildTopicsModel childs = mf.newChildTopicsModel();
				childs.put(NS("imprintitem.name"), name);
				childs.put(NS("imprintitem.text"), text);
				childs.put(NS("imprintitem.link"), link);

				// Creates the statistic for one country
				Topic topic = dm4.createTopic(mf.newTopicModel(NS("imprintitem"), childs));
				assignToDataWorkspace(topic);
				
			} catch (ParseException e) {
				// Ignored - thesis will not be added
				logger.log(Level.WARNING, "Failed to import imprint item", e);
			}
			
		}
	}
	 
	private void deleteAllWithMatchingChild(String typeUri, String childTypeUri, Topic statisticTypeTopic) {
		for (Topic topic : dm4.getTopicsByType(typeUri)) {
			ChildTopics childs = topic.getChildTopics();
			if (childs.getTopic(childTypeUri).getId() == statisticTypeTopic.getId()) {
				topic.delete();
			}
		}
	}
	
	private void deleteAll(String typeUri) {
		for (Topic topic : dm4.getTopicsByType(typeUri)) {
			topic.delete();
		}
	}
	
	private void deleteTreatyNote() {
		Topic topic = dm4.getTopicByUri(NS("backgrounditem.treaty.note"));
		if (topic != null) {
			topic.delete();
		}
	}
	
	void resetAllData() {
		deleteAll(NS("imprintitem"));
		deleteAll(NS("countryoverview"));
		deleteAll(NS("detentioncenter"));
		deleteAll(NS("thesis"));
		deleteAll(NS("backgrounditem"));
		deleteAll(NS("countryoverview"));
		deleteAll(NS("treaty"));
		deleteAll(NS("factsheet"));
		deleteAll(NS("statistic"));

		deleteAll("dm4.contacts.country");
		
		deleteTreatyNote();
	}
}
