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
		
		// TODO: slug
		// TODO: statistics
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
	
	private RelatedTopic findTopicWithMatchingChildValue(List<RelatedTopic> topics, String childTypeUri, String value) {
		for (RelatedTopic t : topics) {
			ChildTopics childs = t.getChildTopics();
			
			if (value.equals(childs.getStringOrNull(childTypeUri))) {
				return t;
			}
		}
		
		return null;
	}
	
	private Topic findStatisticsType(String statType) {		
		for (Topic t : dm4.getTopicsByType(NS("statistic.type"))) {
			if (statType.equals(t.getSimpleValue().toString())) {
				return t;
			}
		}
		
		throw new IllegalStateException("Unknown statistics type: " + statType);
	}


	private List<String> toStringListOrNull(List<RelatedTopic> topics) {
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

	private int getInt(ChildTopics childs, String assocDefUri, int defaultValue) {
		Integer value = childs.getIntOrNull(assocDefUri);

		return (value != null) ? value.intValue() : defaultValue;
	}

	private static <T> T selfOrDefault(T instance, T defaultValue) {
		return (instance != null) ? instance : defaultValue;
	}
	
	private static class CountryImpl extends JSONEnabledImpl implements Country {
	}

}
