package de.taz.migrationcontrol;

import java.util.ArrayList;
import java.util.List;

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
		
		// TODO: slug
		// TODO: statistics
		// TODO: data
		// TODO: features
		
		return json;
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
