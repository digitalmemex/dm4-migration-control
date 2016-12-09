package de.taz.migrationcontrol;

import static de.taz.migrationcontrol.MigrationControlService.NS;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;

import de.deepamehta.core.ChildTopics;
import de.deepamehta.core.RelatedTopic;
import de.deepamehta.core.Topic;
import de.deepamehta.core.service.CoreService;
import de.deepamehta.core.service.ModelFactory;
import de.deepamehta.workspaces.WorkspacesService;
import de.taz.migrationcontrol.MigrationControlService.Background;
import de.taz.migrationcontrol.MigrationControlService.BackgroundItem;
import de.taz.migrationcontrol.MigrationControlService.CountriesOverview;
import de.taz.migrationcontrol.MigrationControlService.Country;
import de.taz.migrationcontrol.MigrationControlService.Thesis;

public class DTOHelper {

	static Logger logger = Logger.getLogger(DTOHelper.class.getName());

	CoreService dm4;
	ModelFactory mf;
	WorkspacesService wsService;
	
	public static final String TREATYTYPE_REPATRIATION_AGREEMENT = "Repatriation Agreement";
	public static final String TREATYTYPE_OTHER_AGREEMENT = "Other Migration Agreement";

	public DTOHelper(CoreService dm4, ModelFactory mf, WorkspacesService wsService) {
		this.dm4 = dm4;
		this.mf = mf;
		this.wsService = wsService;
	}
	
	CountriesOverview toCountriesOverview(List<Topic> countryTopics) throws JSONException, IOException {
		CountriesOverviewImpl json = new CountriesOverviewImpl();
		
		ArrayList[] cols = {
				new ArrayList<JSONObject>(),
				new ArrayList<JSONObject>(),
				new ArrayList<JSONObject>(),
				new ArrayList<JSONObject>()
		};

		for (Topic countryTopic : countryTopics) {
			RelatedTopic countryOverviewTopic = countryTopic.getRelatedTopic((String) null, (String) null, (String) null, NS("countryoverview"));
			
			if (countryOverviewTopic == null) {
				continue;
			}
			
			JSONObject countryJson = new JSONObject();
			countryJson.put("id", countryTopic.getId());
			countryJson.put("countryName", countryTopic.getSimpleValue().toString());
			
			logger.log(Level.INFO, "adding country: " + countryJson.getString("countryName"));

			ChildTopics childs = countryOverviewTopic.getChildTopics();
/*			
			countryJson.put("finding", toFinding(childs.getStringOrNull(NS("countryoverview.findinglink")), false));
			
			JSONArray featuresArray = new JSONArray();
			for (String featureLink : toStringListOrNull(safe(childs.getTopicsOrNull(NS("countryoverview.featurelink"))))) {
				featuresArray.put(toFeature(featureLink, false));
			}
			countryJson.put("features", featuresArray);
*/
			int ci = Math.min(childs.getInt(NS("countryoverview.columnindex")), 3);
			
			// Inserts the items sorted by their id: DM gives the IDs monotonically increasing,
			// as the background items are added during import line by line we can use this.
			insertSorted(cols[ci], countryJson);
		}

		json.put("col0", cols[0]);
		json.put("col1", cols[1]);
		json.put("col2", cols[2]);
		json.put("col3", cols[3]);

		return json;
	}
	
	Country toCountryOrNull(Topic countryTopic) throws JSONException, IOException {
		CountryImpl json = new CountryImpl();
		
		json.put("id", countryTopic.getId());
		json.put("countryName", countryTopic.getSimpleValue().toString());
		json.put("data", toStatisticData(countryTopic));
		json.put("factSheet", toFactSheet(countryTopic));

		RelatedTopic countryOverviewTopic = countryTopic.getRelatedTopic((String) null, (String) null, (String) null, NS("countryoverview"));
		
		if (countryOverviewTopic == null)
			return json;
		
		ChildTopics childs = countryOverviewTopic.getChildTopics();
		json.put("finding", toFinding(childs.getStringOrNull(NS("countryoverview.findinglink")), true));
		
		JSONArray featuresArray = new JSONArray();
		for (String featureLink : toStringListOrNull(safe(childs.getTopicsOrNull(NS("countryoverview.featurelink"))))) {
			featuresArray.put(toFeature(featureLink, true));
		}
		json.put("features", featuresArray);
		
		return json;
	}
	
	private JSONObject toFinding(String findingLink, boolean includeCorpus) throws JSONException, IOException {
		Document doc;
		JSONObject json = null;
		try {
			doc = retrieveDocument(findingLink);
			
			Element article = doc.select("content > item[type=article]").first();
			Element headline = article.getElementsByTag("headline").first();
			Element lead = article.getElementsByTag("lead").first();
			
			json = new JSONObject();
			json.put("headline", headline.text());
			json.put("lead", lead.text());
			
			if (includeCorpus) {
				Element corpus = article.getElementsByTag("corpus").first();
				json.put("corpus", fullText(corpus));
			}
			
		} catch (IOException ioe) {
			return null;
		}
		
		return json;
	}
	
	private JSONObject toFeature(String featureLink, boolean includeCorpus) throws JSONException, IOException {
		Document doc;
		JSONObject json = null;
		try {
			doc = retrieveDocument(featureLink);
			
			Element article = doc.select("content > item[type=article]").first();
			Element headline = article.getElementsByTag("headline").first();
			Element lead = article.getElementsByTag("lead").first();
			Element kicker = article.getElementsByTag("kicker").first();
			
			json = new JSONObject();
			json.put("headline", headline.text());
			json.put("lead", lead.text());
			json.put("kicker", kicker.text());
			
			JSONArray imagesArray = new JSONArray();
			for (Element picture : article.select("extra[type=picture] > picture")) {
				Element descr = picture.getElementsByTag("descr").first();
				Element caption = picture.getElementsByTag("caption").first();
				Element pixmapXL = picture.select("pixmap[size=slideXL").first();
				
				// Skip image if somehting is missing.
				if (descr == null || caption == null || pixmapXL == null) {
					continue;
				}
				
				JSONObject imageJson = new JSONObject();
				imageJson.put("alt", descr.text());
				imageJson.put("caption", caption.text());
				imageJson.put("src", makeImageUrl(featureLink, pixmapXL.attr("src")));
				
				imagesArray.put(imageJson);
			}
			
			json.put("images", imagesArray);
			
			if (includeCorpus) {
				Element corpus = article.getElementsByTag("corpus").first();
				json.put("corpus", fullText(corpus));
			}
			
		} catch (IOException ioe) {
			return null;
		}
		
		return json;
	}
	
	private String makeImageUrl(String articleUrl, String imagePath) throws MalformedURLException {
		return new URL(new URL(articleUrl), imagePath).toString();
	}
	
	private String fullText(Element e) {
		StringBuilder sb = new StringBuilder();
		for (Node tn : e.childNodes()) {
			sb.append(tn.toString());
		}
		
		return sb.toString();
	}
	
	private Document retrieveDocument(String tazUrl) throws IOException {
		if (!tazUrl.endsWith("/")) {
			tazUrl += "/";
		}
		
		return Jsoup.connect(tazUrl + "c.xml").get();
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

		if (factSheetTopic != null) {
			ChildTopics childs= factSheetTopic.getChildTopics();
			JSONObject data = new JSONObject();
			
			data.put("refugeesInCountry", childs.getIntOrNull(NS("factsheet.refugeesincountry")));
			data.put("refugeesOutsideCountry", childs.getIntOrNull(NS("factsheet.refugeesoutsidecountry")));
			data.put("refugeesInEU", childs.getIntOrNull(NS("factsheet.refugeesineu")));
			data.put("idp", childs.getIntOrNull(NS("factsheet.idp")));
			data.put("applicationsForAsylum", childs.getIntOrNull(NS("factsheet.applicationsforasylum")));
			data.put("asylumApprovalRate", (Number) childs.getDoubleOrNull(NS("factsheet.asylumapprovalrate")));
			data.put("countriesRepatriationAgreement", toStringListOfChildTopic(getTreatiesForCountry(country, TREATYTYPE_REPATRIATION_AGREEMENT), "dm4.contacts.country#" + NS("treaty.partner")));
			data.put("otherMigrationAgreements", toStringListOfChildTopic(getTreatiesForCountry(country, TREATYTYPE_OTHER_AGREEMENT), NS("treaty.name")));
			data.put("hasFrontexCooperation", childs.getBooleanOrNull(NS("factsheet.hasfrontexcooperation")));
			data.put("detentionCenterCount", childs.getIntOrNull(NS("factsheet.detentioncentercount")));
			data.put("departureIsIllegal", childs.getBooleanOrNull(NS("factsheet.departureisillegal")));
			
			return data;			
		}
		
		return null;
	}
	
	private List<RelatedTopic> getTreatiesForCountry(Topic country, String treatyType) {
		List<RelatedTopic> treaties = country.getRelatedTopics((String) null, (String) null, (String) null, NS("treaty"));
		
		ArrayList<RelatedTopic> result = new ArrayList<>();
		for (RelatedTopic topic : treaties) {
			ChildTopics childs = topic.getChildTopics();
			
			if (treatyType.equals(childs.getStringOrNull("de.taz.migrationcontrol.treaty.type"))) {
				result.add(topic);
			}
		}
		
		return result;
	}

	Thesis toThesisOrNull(Topic thesisTopic) throws JSONException {
		ChildTopics childs = thesisTopic.getChildTopics();
		
		ThesisImpl json = new ThesisImpl();
		json.put("title", childs.getString(NS("thesis.name")));
		
		json.put("text", childs.getString(NS("thesis.text")));
		json.put("contextualisation", childs.getString(NS("thesis.contextualisation")));
		json.put("sourceinfo", childs.getString(NS("thesis.sourceinfo")));
		
		json.put("diagramType", childs.getString(NS("thesis.diagramtype")));
		
		return json;
	}
	
	Background toBackground(List<Topic> backgroundItemTopics) throws JSONException, IOException {
		BackgroundImpl json = new BackgroundImpl();
		
		ArrayList[] cols = {
				new ArrayList<JSONObject>(),
				new ArrayList<JSONObject>(),
				new ArrayList<JSONObject>()
		};

		for (Topic topic : backgroundItemTopics) {
			ChildTopics childs = topic.getChildTopics();
			
			JSONObject item = new JSONObject();
			item.put("id", topic.getId());
			item.put("title", childs.getString(NS("backgrounditem.name")));
			String link = childs.getStringOrNull(NS("backgrounditem.link"));
			
			if (link != null && link.length() > 0) {
				Document doc = retrieveDocument(link);
				Element article = doc.select("content > item[type=article]").get(0);
				Element headline = article.getElementsByTag("headline").get(0);
				Element lead = article.getElementsByTag("lead").get(0);
				
				item.put("headline", headline.text());
				item.put("lead", lead.text());
			} else {
				// No link, then use built-in text
				// There is no link, try to get the 
				RelatedTopic notes = topic.getRelatedTopic(null, null, null, "dm4.notes.note");
				if (notes != null) {
					ChildTopics childs2 = notes.getChildTopics();
					item.put("headline", childs2.getString("dm4.notes.title"));
					item.put("lead", childs2.getString("dm4.notes.text"));
				}

				item.put("treaties", toTreaties());
			}
			
			int ci = Math.min(childs.getInt(NS("backgrounditem.columnindex")), 2);
			
			// Inserts the items sorted by their id: DM gives the IDs monotonically increasing,
			// as the background items are added during import line by line we can use this.
			insertSorted(cols[ci], item);
		}
		
		json.put("col0", cols[0]);
		json.put("col1", cols[1]);
		json.put("col2", cols[2]);
		
		return json;
	}
	
	List<JSONObject> toTreaties() throws JSONException {
		ArrayList<JSONObject> result = new ArrayList<>();
		
		// TODO: First EU, then EU countries, then african countries
		addTreatiesForCountries(result, dm4.getTopicsByType("dm4.contacts.country"));
		
		return result;
	}
	
	private void addTreatiesForCountries(List<JSONObject> list, List<Topic> countryTopics) throws JSONException {
		for (Topic countryTopic : countryTopics) {
			List<RelatedTopic> treaties = safe(countryTopic.getRelatedTopics((String) null, (String) null, (String) null, NS("treaty")));
			
			JSONArray treatyArray = new JSONArray();
			
			for (RelatedTopic treatyTopic : treaties) {
				ChildTopics childs = treatyTopic.getChildTopics();
				
				JSONObject json = new JSONObject();
				json.put("name", childs.getString(NS("treaty.name")));
				json.put("country", childs.getString("dm4.contacts.country"));
				json.put("partner", childs.getString("dm4.contacts.country#" + NS("treaty.partner")));
				json.put("link", childs.getString(NS("treaty.link")));
				
				treatyArray.put(json);
			}
			
			JSONObject countryJson = new JSONObject();
			countryJson.put("country", countryTopic.getSimpleValue().toString());
			countryJson.put("treaties", treatyArray);
			
			list.add(countryJson);
		}

	}
	
	BackgroundItem toBackgroundItem(Topic backgroundItem) throws JSONException, IOException {
		BackgroundItemImpl json = new BackgroundItemImpl();
		
		ChildTopics childs = backgroundItem.getChildTopics();
			
		json.put("id", backgroundItem.getId());
		String link = childs.getStringOrNull(NS("backgrounditem.link"));
			
		if (link != null && link.length() > 0) {
			Document doc = retrieveDocument(link);
			Element article = doc.select("content > item[type=article]").get(0);
			Element headline = article.getElementsByTag("headline").get(0);
			Element lead = article.getElementsByTag("lead").get(0);
			Element corpus = article.getElementsByTag("corpus").first();
			
			json.put("headline", headline.text());
			json.put("lead", lead.text());
			json.put("corpus", fullText(corpus));
		}
		return json;
	}
	
	private void insertSorted(List<JSONObject> list, JSONObject item) throws JSONException {
		int sortKey = item.getInt("id");
		final int length = list.size();
		
		if (length == 0) {
			list.add(item);
			return;
		}
		
		int insertPos = 0;
		for (int i = 0; i < length; i++) {
			insertPos = i;
			if (list.get(i).getLong("id") > sortKey) {
				break;
			}
		}
		list.add(insertPos, item);
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
	
	private List<String> toStringListOfChildTopic(List<RelatedTopic> topics, String typeUri) {
		List<String> list = new ArrayList<String>();
		if (topics != null && topics.size() > 0) {
			for (Topic t : topics) {
				String string = t.getChildTopics().getStringOrNull(typeUri);
				if (string != null)
					list.add(string);
			}

			return list;
		} else {
			return list;
		}
	}
	
	private static class CountriesOverviewImpl extends JSONEnabledImpl implements CountriesOverview {
	}

	private static class CountryImpl extends JSONEnabledImpl implements Country {
	}

	private static class ThesisImpl extends JSONEnabledImpl implements Thesis {
	}

	private static class BackgroundImpl extends JSONEnabledImpl implements Background {
	}

	private static class BackgroundItemImpl extends JSONEnabledImpl implements BackgroundItem {
	}

}
