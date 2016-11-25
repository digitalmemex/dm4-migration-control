package de.taz.migrationcontrol;

import static de.taz.migrationcontrol.MigrationControlService.NS;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import de.deepamehta.core.ChildTopics;
import de.deepamehta.core.RelatedTopic;
import de.deepamehta.core.Topic;
import de.deepamehta.core.service.CoreService;
import de.deepamehta.core.service.ModelFactory;
import de.deepamehta.workspaces.WorkspacesService;
import de.taz.migrationcontrol.MigrationControlService.Country;

public class DTOHelper {

	CoreService dm4;
	ModelFactory mf;
	WorkspacesService wsService;

	public DTOHelper(CoreService dm4, ModelFactory mf, WorkspacesService wsService) {
		this.dm4 = dm4;
		this.mf = mf;
		this.wsService = wsService;
	}
	
	Country toCountryOrNull(Topic countryTopic) throws JSONException {
		ChildTopics childs = countryTopic.getChildTopics();
		
		CountryImpl json = new CountryImpl();
		json.put("title", countryTopic.getSimpleValue().toString());
		
		json.put("data", toStatisticData(countryTopic));
		
		json.put("factSheet", toFactSheet(countryTopic));
		
		
		// TODO: slug
		// TODO: statistics -> facthsheet
		// TODO: features
		
		return json;
	}

	private JSONObject toStatisticData(Topic country) throws JSONException {
		JSONObject data = new JSONObject();

		for (RelatedTopic statTopic : country.getRelatedTopics((String) null, (String) null, (String) null, NS("statistic"))) {
			JSONArray statArray = new JSONArray();
			ChildTopics childs = statTopic.getChildTopics();
			
			for (RelatedTopic statEntry : safe(childs.getTopicsOrNull(NS("statistic.entry")))) {
				statArray.put(toStatisticEntry(statEntry));
			}
			
			String key = statNameToJsonKey(childs.getString(NS("statistic.type")));
			data.put(key, statArray);
		}
		
		return data;
	}
	
	private JSONObject toFactSheet(Topic country) throws JSONException {
		Topic factSheetTopic = country.getRelatedTopic((String) null, (String) null, (String) null, NS("factsheet"));

		if (factSheetTopic != null && isFactSheetOfCountry(factSheetTopic, country)) {
			ChildTopics childs= factSheetTopic.getChildTopics();
			JSONObject data = new JSONObject();
			
			data.put("refugeesInCountry", childs.getIntOrNull(NS("factsheet.refugeesincountry")));
			data.put("refugeesOutsideCountry", childs.getIntOrNull(NS("factsheet.refugeesoutsidecountry")));
			data.put("refugeesInEU", childs.getIntOrNull(NS("factsheet.refugeesineu")));
			data.put("idp", childs.getIntOrNull(NS("factsheet.idp")));
			data.put("applicationsForAsylum", childs.getIntOrNull(NS("factsheet.applicationsforasylum")));
			data.put("asylumApprovalRate", childs.getIntOrNull(NS("factsheet.asylumapprovalrate")));
			data.put("countriesRepatriationAgreement", toStringListOrNull(childs.getTopicsOrNull("dm4.contacts.country#"+NS("factsheet.repatriationagreement"))));
			data.put("countriesOtherMigrationAgreement", toStringListOrNull(childs.getTopicsOrNull("dm4.contacts.country#"+NS("factsheet.othermigrationagreement"))));
			data.put("hasFrontexCooperation", childs.getBooleanOrNull(NS("factsheet.hasfrontexcooperation")));
			data.put("detentionCenterCount", childs.getIntOrNull(NS("factsheet.detentioncentercount")));
			data.put("departureIsIllegal", childs.getBooleanOrNull(NS("factsheet.departureisillegal")));
			
			return data;			
		}
		
		return null;
	}
	
	private boolean isFactSheetOfCountry(Topic factSheetTopic, Topic countryTopic) {
		ChildTopics childs = factSheetTopic.getChildTopics();
		return childs.getTopic("dm4.contacts.country").getId() == countryTopic.getId();
	}
	
	private List<RelatedTopic> safe(List<RelatedTopic> originalList){
		return originalList != null ? originalList : Collections.emptyList();
	}
	
	private String statNameToJsonKey(String statName) {
		if ("Migration Intensity".equals(statName)) {
			return "migrationIntensity";
		} else {
			return statName.toLowerCase();
		}
	}
	
	private JSONObject toStatisticEntry(Topic statEntry) throws JSONException {
		JSONObject obj = new JSONObject();
		
		ChildTopics childs = statEntry.getChildTopics();
		String yearAsString = String.valueOf(childs.getInt("dm4.datetime.year"));
		double value = childs.getDouble(NS("statistic.entry.value"));
		
		obj.put(yearAsString, value);
		
		return obj;
	}
	
	private static List<String> toStringListOrNull(List<RelatedTopic> topics) {
		if (topics != null && topics.size() > 0) {
			List<String> list = new ArrayList<String>();
			for (Topic t : topics) {
				list.add(t.getSimpleValue().toString());
			}

			return list;
		} else {
			return null;
		}
	}
	
	private static class CountryImpl extends JSONEnabledImpl implements Country {
	}

}
