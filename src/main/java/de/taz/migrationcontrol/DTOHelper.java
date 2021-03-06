package de.taz.migrationcontrol;

import static de.taz.migrationcontrol.MigrationControlService.NS;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

import de.deepamehta.core.Association;
import de.deepamehta.core.ChildTopics;
import de.deepamehta.core.RelatedTopic;
import de.deepamehta.core.Topic;
import de.deepamehta.core.service.CoreService;
import de.deepamehta.core.service.ModelFactory;
import de.deepamehta.workspaces.WorkspacesService;
import de.taz.migrationcontrol.MigrationControlService.BackgroundItem;
import de.taz.migrationcontrol.MigrationControlService.BackgroundOverview;
import de.taz.migrationcontrol.MigrationControlService.CountriesOverview;
import de.taz.migrationcontrol.MigrationControlService.Country;
import de.taz.migrationcontrol.MigrationControlService.DetentionCenter;
import de.taz.migrationcontrol.MigrationControlService.ImprintItem;
import de.taz.migrationcontrol.MigrationControlService.Thesis;
import de.taz.migrationcontrol.MigrationControlService.TreatiesOverview;

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
	
	List<CountriesOverview> toCountriesOverviewList(String languageCode, List<Topic> countryTopics) throws JSONException, IOException {
		ArrayList[] cols = {
				new ArrayList<Wrapped<JSONObject>>(),
				new ArrayList<Wrapped<JSONObject>>(),
				new ArrayList<Wrapped<JSONObject>>(),
				new ArrayList<Wrapped<JSONObject>>()
		};

		for (Topic countryTopic : countryTopics) {
			RelatedTopic countryOverviewTopic = countryTopic.getRelatedTopic((String) null, (String) null, (String) null, NS("countryoverview"));
			
			if (countryOverviewTopic == null) {
				continue;
			}
			
			JSONObject countryJson = new JSONObject();
			countryJson.put("id", countryTopic.getId());
			countryJson.put("countryCode", toCountryId(countryTopic));
			countryJson.put("name", getTranslatedStringOrDefault(languageCode, countryTopic));
			
			logger.log(Level.INFO, "adding country: " + countryJson.getString("name"));

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
			
			// If the index is -1,then the country is not supposed to appear in the table
			if (ci == -1) {
				continue;
			}
			
			int weight = childs.getInt(NS("order"));
			
			cols[ci].add(new Wrapped(countryJson, weight));
		}
		
		ArrayList<CountriesOverview> result = new ArrayList<>();
		
		for (int i = 0;i<cols.length;i++) {
			CountriesOverviewImpl json = new CountriesOverviewImpl();
			json.put("columnIndex", i);
			json.put("entries", new JSONArray(unwrapList(sortByWeight((List<Wrapped<JSONObject>>) cols[i]))));
			
			result.add(json);
		}

		return result;
	}
	
	private String toCountryId(Topic countryTopic) {
		String uri = countryTopic.getUri();
		if (uri != null && uri.length() > 0) {
			return uri.substring(NS("country.").length());			
		} else {
			return null;
		}
		
	}
	
	Country toCountryOrNull(String languageCode, Topic countryTopic) throws JSONException, IOException {
		CountryImpl json = new CountryImpl();
		
		json.put("id", countryTopic.getId());
		json.put("countryCode", toCountryId(countryTopic));
		json.put("name", getTranslatedStringOrDefault(languageCode, countryTopic));
		json.put("data", toStatisticData(countryTopic));
		json.put("factSheet", toFactSheet(languageCode, countryTopic));

		RelatedTopic countryOverviewTopic = countryTopic.getRelatedTopic((String) null, (String) null, (String) null, NS("countryoverview"));
		
		if (countryOverviewTopic == null)
			return json;
		
		ChildTopics childs = countryOverviewTopic.getChildTopics();
		json.put("finding", toFinding(getTranslatedStringOrNull(childs, languageCode, NS("countryoverview.findinglink")), true));
		json.put("isDonorCountry", childs.getBooleanOrNull(NS("countryoverview.isdonorcountry")));
		
		JSONArray featuresArray = new JSONArray();
		for (String featureLink : safe(toStringListOrNull(languageCode, childs.getTopicsOrNull(NS("countryoverview.featurelink"))))) {
			featuresArray.put(toFeature(featureLink, true));
		}
		json.put("features", featuresArray);
		json.put("treaties", toTreatiesForCountry(languageCode, countryTopic));
		
		return json;
	}
	
	private JSONObject toFinding(String findingLink, boolean includeCorpus) throws JSONException, IOException {
		if (findingLink == null)
			return null;
		
		Document doc;
		JSONObject json = null;
		try {
			doc = retrieveDocument(findingLink);
			if (doc == null) {
				logger.warning("Article cannot be retrieved for finding: " + findingLink);
				return json;
			}

			Element article = doc.select("content > item[type=article]").first();
			Element headline = article.getElementsByTag("headline").first();
			Element lead = article.getElementsByTag("lead").first();
			
			json = new JSONObject();
			json.put("headline", headline.text());
			json.put("lead", lead.text());
			
			JSONArray imagesArray = new JSONArray();
			for (Element picture : article.select("extra[type=picture] > picture")) {
				Element descr = picture.getElementsByTag("descr").first();
				Element caption = picture.getElementsByTag("caption").first();
				Element credit = picture.getElementsByTag("credit").first();
				Element pixmapXL = picture.select("pixmap[size=slideXL").first();
				
				// Skip image if somehting is missing.
				if (credit == null || caption == null || pixmapXL == null) {
					continue;
				}
				
				JSONObject imageJson = new JSONObject();
				if (descr != null) {
					imageJson.put("alt", descr.text());
				}
				imageJson.put("caption", caption.text());
				imageJson.put("src", makeImageUrl(findingLink, pixmapXL.attr("src")));
				imageJson.put("credit", credit.text());
				
				imagesArray.put(imageJson);
			}
			
			json.put("images", imagesArray);
			
			if (includeCorpus) {
				Element corpus = article.getElementsByTag("corpus").first();
				json.put("corpus", fullText(corpus));
				
				Element author = article.select("author").first();
				if (author != null) {
					JSONArray jsonArrayNames = new JSONArray();
					Elements authorNames = author.select("name");
					for (Element name : authorNames) {
						jsonArrayNames.put(name.text());
					}
					json.put("authors", jsonArrayNames);
				}
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
			if (doc == null) {
				logger.warning("Article cannot be retrieved for feature: " + featureLink);
				return json;
			}
			
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
				Element credit = picture.getElementsByTag("credit").first();
				Element pixmapXL = picture.select("pixmap[size=slideXL").first();
				
				// Skip image if somehting is missing.
				if (credit == null || caption == null || pixmapXL == null) {
					continue;
				}
				
				JSONObject imageJson = new JSONObject();
				if (descr != null) {
					imageJson.put("alt", descr.text());
				}
				imageJson.put("caption", caption.text());
				imageJson.put("src", makeImageUrl(featureLink, pixmapXL.attr("src")));
				imageJson.put("credit", credit.text());
				
				imagesArray.put(imageJson);
			}
			
			json.put("images", imagesArray);
			
			if (includeCorpus) {
				Element corpus = article.getElementsByTag("corpus").first();
				json.put("corpus", fullText(corpus));
				
				Element author = article.select("author").first();
				if (author != null) {
					JSONArray jsonArrayNames = new JSONArray();
					Elements authorNames = author.select("name");
					for (Element name : authorNames) {
						jsonArrayNames.put(name.text());
					}
					json.put("authors", jsonArrayNames);
				}

			}
			
		} catch (IOException ioe) {
			return null;
		}
		
		return json;
	}
	
	private String makeImageUrl(String articleUrl, String imagePath) throws MalformedURLException {
		// change automatically replace "http:" with "https:" to avoid mixed content warnings
		return new URL(new URL(articleUrl.replace("http:", "https:")), imagePath).toString();
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
		
		try {
			return Jsoup.connect(tazUrl + "c.xml").get();
		} catch (HttpStatusException e) {
			logger.warning("Failed to retrieve: " + tazUrl);
			return null;
		}
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
	
	private JSONObject toFactSheet(String languageCode, Topic country) throws JSONException {
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
			data.put("informalTreatiesDescription", getTranslatedStringOrNull(childs, languageCode, NS("factsheet.informaltreatiesdescription")));
			data.put("countriesRepatriationAgreement", toStringListOfChildTopic(languageCode, getTreatiesForCountry(country, TREATYTYPE_REPATRIATION_AGREEMENT), "dm4.contacts.country#" + NS("treaty.partner")));
			
			// Apparently not needed anymore.
			//data.put("otherMigrationAgreements", toStringListOfChildTopic(languageCode, getTreatiesForCountry(country, TREATYTYPE_OTHER_AGREEMENT), NS("treaty.name")));
			
			data.put("frontexCooperation", toFrontexCooperationInfo(languageCode, childs.getTopicOrNull(NS("factsheet.frontexcooperationinfo"))));
			data.put("detentionCenter", toDetentionCenterInfo(languageCode, childs.getTopicOrNull(NS("factsheet.detentioncenterinfo"))));
			data.put("departureLegality", toDepartureLegalityInfo(languageCode, childs.getTopicOrNull(NS("factsheet.departurelegalityinfo"))));
			
			return data;			
		}
		
		return null;
	}
	
	JSONObject toFrontexCooperationInfo(String languageCode, Topic topic) throws JSONException {
		if (topic == null)
			return null;
		
		JSONObject json = new JSONObject();
		ChildTopics childs = topic.getChildTopics();
		
		json.put("state", childs.getString(NS("factsheet.frontexcooperationinfo.state")));
		json.put("description", getTranslatedStringOrNull(childs, languageCode, NS("factsheet.frontexcooperationinfo.description")));
		
		return json;
	}

	JSONObject toDetentionCenterInfo(String languageCode, Topic topic) throws JSONException {
		if (topic == null)
			return null;
		
		JSONObject json = new JSONObject();
		ChildTopics childs = topic.getChildTopics();
		
		json.put("count", childs.getInt(NS("factsheet.detentioncenterinfo.count")));
		json.put("description", getTranslatedStringOrNull(childs, languageCode, NS("factsheet.detentioncenterinfo.description")));
		
		return json;
	}
	
	JSONObject toDepartureLegalityInfo(String languageCode, Topic topic) throws JSONException {
		if (topic == null)
			return null;
		
		JSONObject json = new JSONObject();
		ChildTopics childs = topic.getChildTopics();
		
		json.put("isIllegal", childs.getBoolean(NS("factsheet.departurelegalityinfo.isillegal")));
		json.put("description", getTranslatedStringOrNull(childs, languageCode, NS("factsheet.departurelegalityinfo.description")));
		
		return json;
	}

	DetentionCenter toDetentionCenterOrNull(Topic topic) throws JSONException {
		DetentionCenterImpl json = new DetentionCenterImpl();
		
		ChildTopics childs = topic.getChildTopics();
		
		json.put("id", topic.getId());
		json.put("name", childs.getStringOrNull(NS("detentioncenter.name"))); 
		json.put("link", childs.getStringOrNull(NS("detentioncenter.link")));
		
		Topic countryTopic = childs.getTopic("dm4.contacts.country");
		if (countryTopic == null) {
			return null;
		}
		JSONObject countryJson = new JSONObject();
		countryJson.put("id", countryTopic.getId());
		countryJson.put("name", countryTopic.getSimpleValue().toString());
		json.put("country", countryJson);
		
		Topic geoCoordTopic = childs.getTopic("dm4.geomaps.geo_coordinate");
		if (geoCoordTopic == null) {
			return null;
		}
		ChildTopics childs2 = geoCoordTopic.getChildTopics();
		json.put("lat", childs2.getDouble("dm4.geomaps.latitude"));
		json.put("lon", childs2.getDouble("dm4.geomaps.longitude"));
		
		return json;
	}
	
	JSONObject toCoordinate(Topic geoCoordTopic) throws JSONException {
		ChildTopics childs = geoCoordTopic.getChildTopics();
		
		JSONObject json = new JSONObject();
		json.put("lat", childs.getDouble("dm4.geomaps.latitude"));
		json.put("lon", childs.getDouble("dm4.geomaps.longitude"));

		return json;
	}
	
	private List<RelatedTopic> getTreatiesForCountry(Topic country, String treatyType) {
		List<RelatedTopic> treaties = country.getRelatedTopics((String) null, (String) null, (String) null, NS("treaty"));
		
		ArrayList<RelatedTopic> result = new ArrayList<>();
		for (RelatedTopic topic : treaties) {
			ChildTopics childs = topic.getChildTopics();
			
			if (treatyType == null || treatyType.equals(childs.getStringOrNull("de.taz.migrationcontrol.treaty.type"))) {
				result.add(topic);
			}
		}
		
		return result;
	}
	
	private List<Topic> getTreaties(String treatyType) {
		List<Topic> treaties = dm4.getTopicsByType(NS("treaty"));
		
		ArrayList<Topic> result = new ArrayList<>();
		for (Topic topic : treaties) {
			ChildTopics childs = topic.getChildTopics();
			
			if (treatyType == null || treatyType.equals(childs.getStringOrNull("de.taz.migrationcontrol.treaty.type"))) {
				result.add(topic);
			}
		}
		
		return result;
	}
	
	List<Thesis> toTheses(String languageCode) throws JSONException {
		ArrayList<Wrapped> list = new ArrayList<Wrapped>();
		
		/*
		for (Topic thesisTopic : ) {
			insertSorted(list, new Wrapped(thesisTopic, thesisTopic.getId()));
		}*/
		
		ArrayList<Thesis> result = new ArrayList<Thesis>();
		for (Topic thesisTopic : sortByOrder(safe(dm4.getTopicsByType(NS("thesis"))))) {
			ChildTopics childs = thesisTopic.getChildTopics();
			
			String name = getTranslatedStringOrNull(childs, languageCode, NS("thesis.name"));
			String text = getTranslatedStringOrNull(childs, languageCode, NS("thesis.text"));
			String contextualisation = getTranslatedStringOrNull(childs, languageCode, NS("thesis.contextualisation"));
			String sourceinfo = getTranslatedStringOrNull(childs, languageCode, NS("thesis.sourceinfo"));
			
			if (name == null
					|| text == null
					|| contextualisation == null) {
				// Skip because something is missing
				continue;
			}
			
			ThesisImpl json = new ThesisImpl();
			json.put("id", thesisTopic.getId());
			json.put("name", name);
			json.put("text", text);
			json.put("contextualisation", contextualisation);
			json.put("sourceinfo", sourceinfo);
			
			json.put("diagramType", childs.getStringOrNull(NS("thesis.diagramtype")));
			json.put("imageUrl", childs.getStringOrNull(NS("thesis.imagelink")));
			
			result.add(json);
		}
		
		return result;
	}

	Thesis toThesisOrNull(String languageCode, Topic thesisTopic) throws JSONException {
		ChildTopics childs = thesisTopic.getChildTopics();
		
		String name = getTranslatedStringOrNull(childs, languageCode, NS("thesis.name"));
		String text = getTranslatedStringOrNull(childs, languageCode, NS("thesis.text"));
		String contextualisation = getTranslatedStringOrNull(childs, languageCode, NS("thesis.contextualisation"));
		String sourceinfo = getTranslatedStringOrNull(childs, languageCode, NS("thesis.sourceinfo"));
		
		if (name == null
				|| text == null
				|| contextualisation == null
				|| sourceinfo == null) {
			// Skip because something is missing
			return null;
		}
		
		ThesisImpl json = new ThesisImpl();
		json.put("id", thesisTopic.getId());
		json.put("name", name);
		json.put("text", text);
		json.put("contextualisation", contextualisation);
		json.put("sourceinfo", sourceinfo);
		
		json.put("diagramType", childs.getStringOrNull(NS("thesis.diagramtype")));
		json.put("imageUrl", childs.getStringOrNull(NS("thesis.imagelink")));
		
		return json;
	}
	
	List<BackgroundOverview> toBackgroundOverviewList(String languageCode, List<Topic> backgroundItemTopics) throws JSONException, IOException {
		ArrayList[] cols = {
				new ArrayList<Wrapped<JSONObject>>(),
				new ArrayList<Wrapped<JSONObject>>(),
				new ArrayList<Wrapped<JSONObject>>()
		};

		for (Topic topic : backgroundItemTopics) {
			ChildTopics childs = topic.getChildTopics();
			
			String name = getTranslatedStringOrNull(childs, languageCode, NS("backgrounditem.name"));
			
			if (name == null) {
				// Skip the entry
				continue;
			}
			
			JSONObject item = new JSONObject();
			item.put("id", topic.getId());
			item.put("name", name);
/*
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
*/			
			int ci = Math.min(childs.getInt(NS("backgrounditem.columnindex")), 2);
			
			int order = childs.getInt(NS("order"));
			
			// Inserts the items sorted by their id: DM gives the IDs monotonically increasing,
			// as the background items are added during import line by line we can use this.
			cols[ci].add(new Wrapped<JSONObject>(item, order));
		}
		
		ArrayList<BackgroundOverview> result = new ArrayList<>();
		
		for (int i = 0;i<cols.length;i++) {
			BackgroundOverviewImpl json = new BackgroundOverviewImpl();
			json.put("columnIndex", i);
			json.put("entries", new JSONArray(unwrapList(sortByWeight((List<Wrapped<JSONObject>>) cols[i]))));
			
			result.add(json);
		}

		return result;
	}
	
	List<JSONObject> toTreaties(String languageCode) throws JSONException {
		ArrayList<JSONObject> result = new ArrayList<>();
		
		ArrayList[] cols = {
				new ArrayList<Wrapped<Topic>>(),
				new ArrayList<Wrapped<Topic>>(),
				new ArrayList<Wrapped<Topic>>(),
				new ArrayList<Wrapped<Topic>>(),
		};
		
		for(Topic countryTopic : safe(dm4.getTopicsByType("dm4.contacts.country"))) {
			RelatedTopic countryOverviewTopic = countryTopic.getRelatedTopic((String) null, (String) null, (String) null, NS("countryoverview"));
			int ci = 3;
			long weight = 0;
			if (countryOverviewTopic != null) {
				ChildTopics childs = countryOverviewTopic.getChildTopics();
				
				ci = Math.min(childs.getInt(NS("countryoverview.columnindex")), 3);
				weight = countryOverviewTopic.getId();
			}
			
			// columnIndex might be -1 which means it should not appear in the table
			// at all
			if (ci >= 0) {
				cols[ci].add(new Wrapped<Topic>(countryTopic, weight));
			}
		}
		
		for (ArrayList<Wrapped<Topic>> col: cols) {
			addTreatiesForCountries(languageCode, result, unwrapList(sortByWeight((col))));
		}
		
		return result;
	}
	
	List<TreatiesOverview> toTreatiesOverviewList(String languageCode) throws JSONException, IOException {
		List<Topic> treaties = getTreaties(TREATYTYPE_REPATRIATION_AGREEMENT);
		
		ArrayList<TreatiesOverview> result = new ArrayList<>();
		for (Topic treaty : treaties) {
			ChildTopics childs = treaty.getChildTopics();
			
			Topic countryTopic = childs.getTopic("dm4.contacts.country");
			Topic partnerTopic = childs.getTopicOrNull("dm4.contacts.country#" + NS("treaty.partner"));
			
			String name = getTranslatedStringOrNull(childs, languageCode, NS("treaty.name"));
			String link = getTranslatedStringOrDefault(childs, languageCode, NS("treaty.link"));
			
			if (name == null){
				// Skipping treaty because when name is missing.
				continue;
			}
			
			TreatiesOverviewImpl json = new TreatiesOverviewImpl();
			json.put("name", name);
			json.put("country", getTranslatedStringOrDefault(languageCode, countryTopic));
			json.put("partner", getTranslatedStringOrDefault(languageCode, partnerTopic));
			json.put("link", link);
			json.put("date", toDateOrNull(childs.getTopicOrNull("dm4.datetime.date")));
			
			RelatedTopic countryCoordsTopic = countryTopic.getRelatedTopic((String) null, (String) null,(String) null, "dm4.geomaps.geo_coordinate");
			if (countryCoordsTopic != null)
				json.put("countryCoords", toCoordinate(countryCoordsTopic));
			
			RelatedTopic partnerCoordsTopic = partnerTopic.getRelatedTopic((String) null, (String) null,(String) null, "dm4.geomaps.geo_coordinate");
			if (partnerCoordsTopic != null)
				json.put("partnerCoords", toCoordinate(partnerCoordsTopic));
			
			result.add(json);
		}

		return result;
	}
	
	private static class Wrapped<T> {
		long weight;
		T value;
		
		Wrapped(T value, long weight) {
			this.value = value;
			this.weight = weight;
		}
		
	}
	
	private static <T> List<T> unwrapList(List<Wrapped<T>> sourceList) {
		ArrayList<T> result = new ArrayList<>();
		for (Wrapped<T> wrapped : sourceList) {
			result.add(wrapped.value);
		}
		
		return result;
	}
	
	private void addTreatiesForCountries(String languageCode, List<JSONObject> list, List<Topic> countryTopics) throws JSONException {
		for (Topic countryTopic : countryTopics) {
			
			JSONObject countryJson = new JSONObject();
			countryJson.put("country", getTranslatedStringOrDefault(languageCode, countryTopic));
			countryJson.put("treaties", toTreatiesForCountry(languageCode, countryTopic));

			list.add(countryJson);
		}

	}
	
	private JSONArray toTreatiesForCountry(String languageCode, Topic countryTopic) throws JSONException {
		List<RelatedTopic> treaties = safe(countryTopic.getRelatedTopics((String) null, (String) null, (String) null, NS("treaty")));
		
		JSONArray treatyArray = new JSONArray();
		
		for (RelatedTopic treatyTopic : treaties) {
			ChildTopics childs = treatyTopic.getChildTopics();
			
			String name = getTranslatedStringOrNull(childs, languageCode, NS("treaty.name"));
			String link = getTranslatedStringOrNull(childs, languageCode, NS("treaty.link"));
			
			if (name == null || link == null) {
				// Skip the treaty if name or link is missing.
				continue;
			}
			
			Topic treatyCountryTopic = childs.getTopic("dm4.contacts.country");
			Topic partnerTopic = childs.getTopicOrNull("dm4.contacts.country#" + NS("treaty.partner"));

			JSONObject json = new JSONObject();
			json.put("name", name);
			json.put("country", getTranslatedStringOrDefault(languageCode, treatyCountryTopic));
			json.put("partner", getTranslatedStringOrDefault(languageCode, partnerTopic));
			json.put("link", link);
			json.put("date", toDateOrNull(childs.getTopicOrNull("dm4.datetime.date")));
			
			treatyArray.put(json);
		}

		return treatyArray;
	}
	
	private JSONObject toDateOrNull(Topic dateTopic) throws JSONException {
		if (dateTopic == null)
			return null;
		
		ChildTopics childs = dateTopic.getChildTopics();
		
		JSONObject json = new JSONObject();
		json.put("year", childs.getIntOrNull("dm4.datetime.year"));
		json.put("month", childs.getIntOrNull("dm4.datetime.month"));
		json.put("day", childs.getIntOrNull("dm4.datetime.day"));
		
		return json;
	}
	
	BackgroundItem toBackgroundItemOrNull(String languageCode, Topic backgroundItem) throws JSONException, IOException {
		BackgroundItemImpl json = new BackgroundItemImpl();
		
		ChildTopics childs = backgroundItem.getChildTopics();
		
		String name = getTranslatedStringOrNull(childs, languageCode, NS("backgrounditem.name"));
		if (name == null) {
			return null;
		}
			
		json.put("id", backgroundItem.getId());
		json.put("name", name);
		
		String link = getTranslatedStringOrNull(childs, languageCode, NS("backgrounditem.link"));
			
		if (link != null && link.length() > 0) {
			Document doc = retrieveDocument(link);
			if (doc == null) {
				logger.warning("Article cannot be retrieved for background: " + link);
				return json;
			}
			Element article = doc.select("content > item[type=article]").get(0);
			Element headline = article.getElementsByTag("headline").get(0);
			Element lead = article.getElementsByTag("lead").get(0);
			Element corpus = article.getElementsByTag("corpus").first();
			
			json.put("headline", headline.text());
			json.put("lead", lead.text());
			json.put("corpus", fullText(corpus));
			
			Element author = article.select("author").first();
			if (author != null) {
				JSONArray jsonArrayNames = new JSONArray();
				Elements authorNames = author.select("name");
				for (Element authorName : authorNames) {
					jsonArrayNames.put(authorName.text());
				}
				json.put("authors", jsonArrayNames);
			}

		} else {
			// No link, then use built-in text
			// There is no link, try to get the 
			RelatedTopic notes = backgroundItem.getRelatedTopic(null, null, null, "dm4.notes.note");
			if (notes != null) {
				ChildTopics childs2 = notes.getChildTopics();
				
				json.put("headline", getTranslatedStringOrNull(childs2, languageCode, "dm4.notes.title"));
				json.put("lead", getTranslatedStringOrNull(childs2, languageCode, "dm4.notes.text"));
			}

			json.put("treaties", toTreaties(languageCode));
		}
		return json;
	}
	
	ImprintItem toImprintItemOrNull(String languageCode, Topic topic) throws JSONException {
		
		ChildTopics childs = topic.getChildTopics();
		
		String name = getTranslatedStringOrNull(childs, languageCode, NS("imprintitem.name"));
		String text = getTranslatedStringOrNull(childs, languageCode, NS("imprintitem.text"));
		
		if (name == null || text == null) {
			// Skip entry
			return null;
		}
		
		ImprintItemImpl json = new ImprintItemImpl();
		json.put("id", topic.getId());
		json.put("name", name);
		json.put("text", text);
		// TODO: imageUrl was not used anywhere in the imprint
		//json.put("imageUrl", childs.getStringOrNull(NS("imprintitem.link")));
			
		return json;
	}
	
	private static String getTranslatedStringOrNull(ChildTopics childs, String languageCode, String typeUri) {
		if (languageCode == null || languageCode.equals("de")) {
			return childs.getStringOrNull(typeUri);
		}
		
		Topic topic = childs.getTopicOrNull(typeUri);
		if (topic == null) {
			return null;
		}
		
		return getTranslatedStringOrNull(languageCode, topic);
	}

	private static String getTranslatedStringOrDefault(ChildTopics childs, String languageCode, String typeUri) {
		if (languageCode == null || languageCode.equals("de")) {
			return childs.getStringOrNull(typeUri);
		}
		
		Topic topic = childs.getTopicOrNull(typeUri);
		if (topic == null) {
			return childs.getStringOrNull(typeUri);
		}
		
		return getTranslatedStringOrDefault(languageCode, topic);
	}

	private static String getTranslatedStringOrNull(String languageCode, Topic topic) {
		if (languageCode == null || languageCode.equals("de")) {
			return topic.getSimpleValue().toString();
		}
		
		// Try to look up translation
		List<RelatedTopic> translatedTexts = topic.getRelatedTopics(NS("translation"), "dm4.core.default", "dm4.core.default", NS("translatedtext"));
		for (RelatedTopic possibleTranslationTopic : translatedTexts) {
			Association association = possibleTranslationTopic.getRelatingAssociation();
			if (languageCode.equals(association.getSimpleValue().toString())) {
				return possibleTranslationTopic.getSimpleValue().toString();
			}
		}
		
		// If translation is not found, deliver as non-existing.
		return null;
	}

	private static String getTranslatedStringOrDefault(String languageCode, Topic topic) {
		// Shortcut: If the untranslated topic does not exist, then return null.
		if (topic == null) {
			return null;
		}
		
		if (languageCode == null || languageCode.equals("de")) {
			return topic.getSimpleValue().toString();
		}
		
		// Try to look up translation
		List<RelatedTopic> translatedTexts = topic.getRelatedTopics(NS("translation"), "dm4.core.default", "dm4.core.default", NS("translatedtext"));
		for (RelatedTopic possibleTranslationTopic : translatedTexts) {
			Association association = possibleTranslationTopic.getRelatingAssociation();
			if (languageCode.equals(association.getSimpleValue().toString())) {
				return possibleTranslationTopic.getSimpleValue().toString();
			}
		}
		
		// If translation is not found, deliver as non-existing.
		return topic.getSimpleValue().toString();
	}

	private List<Topic> sortByOrder(List<Topic> list) {
		list.sort(new Comparator<Topic>() {
			
			private int order(Topic t) {
				Integer l = t.getChildTopics().getIntOrNull(NS("order"));
				return (l != null) ? l : 0;
			}

			@Override
			public int compare(Topic o1, Topic o2) {
				
				return (int) (order(o1) - order(o2));
			}
		});
		
		return list;
	}

	private static <T> List<Wrapped<T>> sortByWeight(List<Wrapped<T>> list) {
		list.sort(new Comparator<Wrapped<T>>() {

			@Override
			public int compare(Wrapped<T> o1, Wrapped<T> o2) {
				return (int) (o1.weight - o2.weight);
			}
		});
		
		return list;
	}
	
	private List<JSONObject> sortByJsonId(List<JSONObject> list) {
		list.sort(new Comparator<JSONObject>() {

			@Override
			public int compare(JSONObject o1, JSONObject o2) {
				try {
					return (int) (o1.getLong("id") - o2.getLong("id"));
				} catch (JSONException jsone) {
					throw new RuntimeException("id not existing");
				}
			}
		});
		
		return list;
	}
	
	static <T> List<T> safe(List<T> originalList){
		return originalList != null ? originalList : Collections.<T>emptyList();
	}
	
	private String statNameToJsonKey(String statName) {
		switch (statName) {
		case "Asylum Figures":
			return "asylumFigures";
		case "Migration Intensity":
			return "migrationIntensity";
		case "Single Payments":
			return "singlePayments";
		default:
			return statName.toLowerCase();
		}
	}
	
	private JSONObject toStatisticEntry(Topic statEntry) throws JSONException {
		JSONObject obj = new JSONObject();
		
		ChildTopics childs = statEntry.getChildTopics();
		String yearAsString = String.valueOf(childs.getInt("dm4.datetime.year"));
		
		double value = childs.getDouble(NS("statistic.entry.value"));
		String source = childs.getStringOrNull(NS("statistic.entry.source"));
		String link = childs.getStringOrNull(NS("statistic.entry.link"));
		
		JSONObject valueObject = new JSONObject();
		valueObject.put("value", value);
		valueObject.put("source", source);
		valueObject.put("link", link);
		
		obj.put(yearAsString, valueObject);
		
		return obj;
	}
	
	/** Translation-aware converter of list of topic to strings. Of each topic the translation is looked up
	 * first and only exiting values added to the result.
	 * 
	 * @param languageCode
	 * @param topics
	 * @return
	 */
	private static List<String> toStringListOrNull(String languageCode, List<RelatedTopic> topics) {
		if (topics != null && topics.size() > 0) {
			List<String> list = new ArrayList<String>();
			for (Topic t : topics) {
				String v = getTranslatedStringOrNull(languageCode, t);
				if (v != null) {
					list.add(v);
				}
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

	private List<String> toStringListOfChildTopic(String languageCode, List<RelatedTopic> topics, String typeUri) {
		List<String> list = new ArrayList<String>();
		if (topics != null && topics.size() > 0) {
			for (Topic t : topics) {
				String string = getTranslatedStringOrNull(t.getChildTopics(), languageCode, typeUri);
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

	private static class BackgroundOverviewImpl extends JSONEnabledImpl implements BackgroundOverview {
	}

	private static class BackgroundItemImpl extends JSONEnabledImpl implements BackgroundItem {
	}

	private static class DetentionCenterImpl extends JSONEnabledImpl implements DetentionCenter {
	}

	private static class ImprintItemImpl extends JSONEnabledImpl implements ImprintItem {
	}

	private static class TreatiesOverviewImpl extends JSONEnabledImpl implements TreatiesOverview {
	}
}
