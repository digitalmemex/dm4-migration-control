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
import org.codehaus.jettison.json.JSONArray;

import de.deepamehta.core.Association;
import de.deepamehta.core.ChildTopics;
import de.deepamehta.core.DeepaMehtaObject;
import de.deepamehta.core.RelatedTopic;
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

	public void importSinglePayments(CSVParser data) throws IOException {
		importStatistics("Single Payments", data);
	}

	public void importSinglePaymentsSources(CSVParser data) throws IOException {
		importStatisticsExtra("Single Payments", NS("statistic.entry.source"), data);
	}

	public void importSinglePaymentsLinks(CSVParser data) throws IOException {
		importStatisticsExtra("Single Payments", NS("statistic.entry.link"), data);
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

	private void importStatisticsExtra(String statName, String textEntryUri, CSVParser data) throws IOException {
		Topic statType = findStatisticsType(statName);
		
		logger.info("importing statistic extra " + statName + " - extra: " + textEntryUri);
		
		// first row :       <none>,  year,  year,  year, ...
		// other rows: country name, value, value, value, ...
		// special value: .. -> no value available
		List<CSVRecord> records = data.getRecords();
		CSVRecord firstRow = records.get(0);
		logger.info("parsed CSV: " + records.size() + " countries");
		
		// iterates over all countries
		for (int i = 1;i < records.size(); i++) {
			CSVRecord row = records.get(i);
			logger.info("importing " + statName + " (extra) for " + row.get(0));

			String countryName = row.get(0);
			Topic statisticTopic = findStatisticOrNull(statType, findCountryOrCreate(countryName));
			
			if (statisticTopic == null) {
				logger.warning("cannot add statistic extra for country'" + countryName);
				continue;
			}
						
			for (int j = 1; j < row.size(); j++) {
				int year = Integer.parseInt(firstRow.get(j));	// this is expected never to fail
				String value = row.get(j);
				if (value.length() > 0) {
					// there is an actual value to store
					Topic entryTopic = findEntryOrNull(statisticTopic, year);
					
					if (entryTopic != null) {
						ChildTopics childs = entryTopic.getChildTopics();

						childs.set(textEntryUri, value);
					} else {
						// Missing statistic entry (no way to add an extra)
						logger.warning("cannot add extra '" + value + "' to statistic of year: " + year);
					}
				}
			}
			
		}
	}
	
	private Topic findStatisticOrNull(Topic statType, Topic countryTopic) {
		for (RelatedTopic statTopic : countryTopic.getRelatedTopics((String) null, (String) null, (String) null, NS("statistic"))) {
			ChildTopics childs = statTopic.getChildTopics();
			
			
			if (childs.getTopic(NS("statistic.type")).getId() == statType.getId()) {
				return statTopic;
			}
		}
		
		return null;
	}
	
	private Topic findEntryOrNull(Topic statisticTopic, int year) {
		ChildTopics childs = statisticTopic.getChildTopics();
		
		for (RelatedTopic statEntry : DTOHelper.safe(childs.getTopicsOrNull(NS("statistic.entry")))) {
			ChildTopics childs2 = statEntry.getChildTopics();
			
			if (year == childs2.getIntOrNull("dm4.datetime.year")) {
				return statEntry;
			}
		}
		return null;
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
		
		throw new IllegalStateException("Unknown frontex cooperation state: " + stateName);
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
		String uri = NS("country." + CountryHelper.getCountryCode(countryName));
		
		// Lookup via URI
		Topic topic = dm4.getTopicByUri(uri);
		if (topic != null){
			return topic;
		}

		for (Topic country : dm4.getTopicsByType("dm4.contacts.country")) {
			if (countryName.equals(country.getSimpleValue().toString())) {
				return country;
			}
		}
		
		return createCountryTopic(uri, countryName);		
	}
	
	private Topic createCountryTopic(String uri, String countryName) {
		Topic countryTopic = dm4.createTopic(mf.newTopicModel(uri, "dm4.contacts.country", new SimpleValue(countryName)));
		
		// Do geocoding
		double[] geoCoords = GeoCodingHelper.geocode(countryName);
		if (geoCoords != null) {
			double lat = geoCoords[0];
			double lon = geoCoords[1];
			Topic geoTopic = createGeoCoordinateTopic(lat, lon);
			
			Association asso = dm4.createAssociation(mf.newAssociationModel("dm4.core.composition",
	    			mf.newTopicRoleModel(countryTopic.getId(), "dm4.core.default"),
				mf.newTopicRoleModel(geoTopic.getId(), "dm4.core.default")));
			
			assignToDataWorkspace(asso);
		} else {
			logger.warning("no geo coordinates for country: " + countryName);
		}

		assignToDataWorkspace(countryTopic);
		
		return countryTopic;
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
				ChildTopics tcm = t.getChildTopics();
				setTranslationWhenExists(tcm, NS("factsheet.frontexcooperationinfo"), "en", row.get(14));
				setTranslationWhenExists(tcm, NS("factsheet.detentioncenterinfo"), "en", row.get(15));
				setTranslationWhenExists(tcm, NS("factsheet.departurelegalityinfo"), "en", row.get(16));

				setTranslationWhenExists(tcm, NS("factsheet.frontexcooperationinfo"), "fr", row.get(18));
				setTranslationWhenExists(tcm, NS("factsheet.detentioncenterinfo"), "fr", row.get(19));
				setTranslationWhenExists(tcm, NS("factsheet.departurelegalityinfo"), "fr", row.get(20));
				
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
					throw new ParseException("Treatyname should not be empty!", -1);
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
				addOrder(childs, i);
				
				if (treatyDateString.length() > 0 && !treatyDateString.equals("..")) {
					childs.putRef("dm4.datetime.date", toDateTopicModel(treatyDateString).getId());
				}

				// Creates the statistic for one country
				Topic t = dm4.createTopic(mf.newTopicModel(NS("treaty"), childs));
				ChildTopics tcm = t.getChildTopics();
				setTranslationWhenExists(tcm, NS("treaty.name"), "en", row.get(4));
				setTranslationWhenExists(tcm, NS("treaty.link"), "en", row.get(5));
				setTranslationWhenExists(tcm, NS("treaty.name"), "fr", row.get(6));
				setTranslationWhenExists(tcm, NS("treaty.link"), "fr", row.get(7));
				
				assignToDataWorkspace(t);
			} catch (NumberFormatException|ParseException e) {
				// Ignored - factsheet for country will not be added
				logger.log(Level.WARNING, "Failed to import treaty for country: " + country + ". Skipping.", e);
			}
			
		}
	}
	
	private TopicModel toDateTopicModel(String dateString) throws ParseException {
		int year = -1;
		int month = -1;
		int day = -1;
		
		String[] parts = dateString.split("\\.");
		
		switch (parts.length) {
		case 3:
			day = Integer.parseInt(parts[0]);
			month = Integer.parseInt(parts[1]);
			year = Integer.parseInt(parts[2]);
			break;
		case 2:
			month = Integer.parseInt(parts[0]);
			year = Integer.parseInt(parts[1]);
			break;
		case 1:
			year = Integer.parseInt(parts[0]);
			break;
		default:
			throw new ParseException("Dateformat is wrong.", -1);
		}
		

		ChildTopicsModel childs = mf.newChildTopicsModel();

		if (year > -1)
			putRefOrCreate(childs, "dm4.datetime.year", year);
		
		if (month > -1)
			putRefOrCreate(childs, "dm4.datetime.month", month);
		
		if (day > -1)
			putRefOrCreate(childs, "dm4.datetime.day", day);
		
		Topic t = dm4.createTopic(mf.newTopicModel("dm4.datetime.date", childs));

		assignToDataWorkspace(t);
		
		return t.getModel();
	}
	
	private boolean urlExists(String s) {
		return s.length() > 0 && !s.equals("..");
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
			
			// Skip invalid country
			if (country.length() == 0)
				continue;
			
			logger.info("importing findings and features for " + country);

			String findingUrl = row.get(1);
			String featureUrl1 = row.get(2);
			String featureUrl2 = row.get(3);
			String featureUrl3 = row.get(4);
			int columnIndex = asInt(row.get(5), 0);
			boolean isDonorCountry = row.get(6).equals("ja");
			
			// Value exists but is not used.
			//String countryCode = row.get(7);
			Topic countryTopic = findCountryOrCreate(country);
			setTranslationWhenExists(countryTopic, "en", row.get(8));
			setTranslationWhenExists(countryTopic, "fr", row.get(13));
			
			ChildTopicsModel childs = mf.newChildTopicsModel();
			childs.putRef("dm4.contacts.country",
					countryTopic.getId());
			
			childs.put(NS("countryoverview.columnindex"), columnIndex);
			
			if (urlExists(findingUrl)) {
				childs.put(NS("countryoverview.findinglink"), findingUrl);
			}
		
			if (urlExists(featureUrl1)) {
				childs.add(NS("countryoverview.featurelink"), newFeatureLink(featureUrl1));
			}
			
			if (urlExists(featureUrl2)) {
				childs.add(NS("countryoverview.featurelink"), newFeatureLink(featureUrl2));
			}
			
			if (urlExists(featureUrl3)) {
				childs.add(NS("countryoverview.featurelink"), newFeatureLink(featureUrl3));
			}
			childs.put(NS("countryoverview.isdonorcountry"), isDonorCountry);
			addOrder(childs, i);

			// Creates the statistic for one country
			Topic t = dm4.createTopic(mf.newTopicModel(NS("countryoverview"), childs));
			
			ChildTopics tcm = t.getChildTopics();
			setTranslationWhenExists(tcm, NS("countryoverview.findinglink"), "en", row.get(9));
			setTranslationWhenExists(tcm, NS("countryoverview.featurelink"), 0, "en", row.get(10));
			setTranslationWhenExists(tcm, NS("countryoverview.featurelink"), 1, "en", row.get(11));
			setTranslationWhenExists(tcm, NS("countryoverview.featurelink"), 2, "en", row.get(12));

			setTranslationWhenExists(tcm, NS("countryoverview.findinglink"), "fr", row.get(14));
			setTranslationWhenExists(tcm, NS("countryoverview.featurelink"), 0, "fr", row.get(15));
			setTranslationWhenExists(tcm, NS("countryoverview.featurelink"), 1, "fr", row.get(16));
			setTranslationWhenExists(tcm, NS("countryoverview.featurelink"), 2, "fr", row.get(17));
			
			assignToDataWorkspace(t);
			
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
				addOrder(childs, i);

				// Creates the statistic for one country
				Topic backgroundItemTopic = dm4.createTopic(mf.newTopicModel(NS("backgrounditem"), childs));
				assignToDataWorkspace(backgroundItemTopic);
				
				ChildTopics tcm = backgroundItemTopic.getChildTopics();
				setTranslationWhenExists(tcm, NS("backgrounditem.name"), "en", row.get(5));
				setTranslationWhenExists(tcm, NS("backgrounditem.link"), "en", row.get(6));

				setTranslationWhenExists(tcm, NS("backgrounditem.name"), "fr", row.get(9));
				setTranslationWhenExists(tcm, NS("backgrounditem.link"), "fr", row.get(10));
				
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

					tcm = notesTopic.getChildTopics();
					setTranslationWhenExists(tcm, "dm4.notes.title", "en", row.get(7));
					setTranslationWhenExists(tcm, "dm4.notes.text", "en", row.get(8));

					setTranslationWhenExists(tcm, "dm4.notes.title", "fr", row.get(11));
					setTranslationWhenExists(tcm, "dm4.notes.text", "fr", row.get(12));
					
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
				addOrder(childs, i);
				
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
				ChildTopics tcm = t.getChildTopics();
				setTranslationWhenExists(tcm, NS("thesis.name"), "en", row.get(7));
				setTranslationWhenExists(tcm, NS("thesis.text"), "en", row.get(8));
				setTranslationWhenExists(tcm, NS("thesis.contextualisation"), "en", row.get(9));
				setTranslationWhenExists(tcm, NS("thesis.sourceinfo"), "en", row.get(10));

				setTranslationWhenExists(tcm, NS("thesis.name"), "fr", row.get(11));
				setTranslationWhenExists(tcm, NS("thesis.text"), "fr", row.get(12));
				setTranslationWhenExists(tcm, NS("thesis.contextualisation"), "fr", row.get(13));
				setTranslationWhenExists(tcm, NS("thesis.sourceinfo"), "fr", row.get(14));
				
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
			
			return createGeoCoordinateTopic(lat, lon);
		} else {
			throw new ParseException("Map point not well formed: " + mapPoint, parts.length);
		}
	}

	private Topic createGeoCoordinateTopic(double lat, double lon) {
		ChildTopicsModel childs = mf.newChildTopicsModel();
		childs.put("dm4.geomaps.latitude", lat);
		childs.put("dm4.geomaps.longitude", lon);
		
		Topic topic = dm4.createTopic(mf.newTopicModel("dm4.geomaps.geo_coordinate", childs));
		assignToDataWorkspace(topic);
		
		return topic;
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
				//childs.put(NS("imprintitem.link"), link);

				// Creates the statistic for one country
				Topic topic = dm4.createTopic(mf.newTopicModel(NS("imprintitem"), childs));
				assignToDataWorkspace(topic);
				ChildTopics tcm = topic.getChildTopics();
				setTranslationWhenExists(tcm, NS("imprintitem.name"), "en", row.get(2));
				setTranslationWhenExists(tcm, NS("imprintitem.text"), "en", row.get(3));

				setTranslationWhenExists(tcm, NS("imprintitem.name"), "en", row.get(4));
				setTranslationWhenExists(tcm, NS("imprintitem.text"), "en", row.get(5));
				
			} catch (ParseException e) {
				// Ignored - thesis will not be added
				logger.log(Level.WARNING, "Failed to import imprint item", e);
			}
			
		}
	}
	
	private void addOrder(ChildTopicsModel childs, int order) {
		childs.put("de.taz.migrationcontrol.order", order);
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
		deleteAll(NS("translatedtext"));
		
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
		deleteAll("dm4.geomaps.geo_coordinate");
		
		deleteTreatyNote();
	}

	void setTranslationWhenExists(ChildTopics childs, String typeUri, String languageCode, String translatedString) {
		if (urlExists(translatedString)){
			setTranslation(childs, typeUri, languageCode, translatedString);
		}		
	}
	
	/** Adds translation to a child that is like a property. */
	void setTranslation(ChildTopics childs, String typeUri, String languageCode, String translatedString) {
		Topic topic = childs.getTopicOrNull(typeUri);
		if (topic == null) {
			logger.warning("cannot add translation because base data does not exist: " + translatedString);
			return;
		}
		
		setTranslation(topic, languageCode, translatedString);
	}
	
	void setTranslationWhenExists(ChildTopics childs, String typeUri, int index, String languageCode, String translatedString) {
		if (urlExists(translatedString)){
			setTranslation(childs, typeUri, index, languageCode, translatedString);
		}		
	}
	
	/** Adds translation to a child that is like an array. */
	void setTranslation(ChildTopics childs, String typeUri, int index, String languageCode, String translatedString) {
		List<RelatedTopic> topics = childs.getTopicsOrNull(typeUri);
		if (topics == null) {
			logger.warning("cannot add translation because base data does not exist: " + translatedString);
			return;
		}
		
		Topic topic;
		if (topics.size() >= index) {
			topic = topics.get(index);
		} else {
			logger.warning("cannot add translation because base data does not exist: " + translatedString);
			return;
		}
		
		setTranslation(topic, languageCode, translatedString);
	}

	void setTranslationWhenExists(Topic topic, String languageCode, String translatedString) {
		if (urlExists(translatedString)){
			setTranslation(topic, languageCode, translatedString);
		}		
	}
	
	/** Sets a translation for the given topic. */
	void setTranslation(Topic topic, String languageCode, String translatedString) {
		TopicModel translationModel = mf.newTopicModel(NS("translatedtext"), new SimpleValue(translatedString));
		Topic translatedTextTopic = dm4.createTopic(translationModel);
		
		Association asso = dm4.createAssociation(mf.newAssociationModel(NS("translation"),
    			mf.newTopicRoleModel(topic.getId(), "dm4.core.default"),
			mf.newTopicRoleModel(translatedTextTopic.getId(), "dm4.core.default")));
		asso.setSimpleValue(languageCode);
		
		assignToDataWorkspace(translatedTextTopic);
		assignToDataWorkspace(asso);
		
	}
	
}
