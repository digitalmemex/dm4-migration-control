package de.taz.migrationcontrol;

import static de.taz.migrationcontrol.MigrationControlService.NS;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.codehaus.jettison.json.JSONException;

import de.deepamehta.accesscontrol.AccessControlService;
import de.deepamehta.core.Topic;
import de.deepamehta.core.osgi.PluginActivator;
import de.deepamehta.core.service.Inject;
import de.deepamehta.core.service.Transactional;
import de.deepamehta.workspaces.WorkspacesService;

@Path("/migrationcontrol")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MigrationControlPlugin extends PluginActivator implements MigrationControlService {

	@Inject
	private WorkspacesService wsService; // needed by migration 1

	@Inject
	private AccessControlService acService; // needed by migration 1

	private DTOHelper dtoHelper;

	private ImportHelper importHelper;

	@Override
	public void init() {
		dtoHelper = new DTOHelper(dm4, mf, wsService);
		importHelper = new ImportHelper(dm4, mf, wsService);
	}

	@GET
	@Path("/v1/{languageCode}/countriesoverview")
	public List<CountriesOverview> getCountriesOverview(@PathParam("languageCode") String languageCode) {
		try {
			return dtoHelper.toCountriesOverviewList(dm4.getTopicsByType("dm4.contacts.country"));
		} catch (JSONException|IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	@GET
	@Path("/v1/{languageCode}/countries")
	@Override
	public List<Country> getCountries(@PathParam("languageCode") String languageCode) {
		List<Country> results = new ArrayList<>();
		
		for (Topic topic : dm4.getTopicsByType("dm4.contacts.country")) {
			try {
				Country country = dtoHelper.toCountryOrNull(topic);
				if (country != null)
					results.add(country);
			} catch (JSONException|IOException jsone) {
				// TODO: Log what object was dropped
			}
		}
		
		return results;
	}

	@GET
	@Path("/v1/{languageCode}/country/{id}")
	public Country getCountry(@PathParam("languageCode") String languageCode, @PathParam("id") long id) {
		Topic topic = dm4.getTopic(id);

		try {
			if (topic != null)
				return dtoHelper.toCountryOrNull(topic);
		} catch (JSONException|IOException e) {
			throw new RuntimeException(e);
		}
		
		return null;
	}

	@GET
	@Path("/v1/{languageCode}/detentioncenters")
	@Override
	public List<DetentionCenter> getDetentionCenters(@PathParam("languageCode") String languageCode) {
		List<DetentionCenter> results = new ArrayList<>();
		
		for (Topic topic : dm4.getTopicsByType(NS("detentioncenter"))) {
			try {
				DetentionCenter dto = dtoHelper.toDetentionCenterOrNull(topic);
				if (dto != null)
					results.add(dto);
			} catch (JSONException e) {
				// TODO: Log what object was dropped
			}
		}
		
		return results;
	}

	@GET
	@Path("/v1/{languageCode}/detentioncenter/{id}")
	public DetentionCenter getDetentionCenter(@PathParam("languageCode") String languageCode, @PathParam("id") long id) {
		Topic topic = dm4.getTopic(id);

		try {
			if (topic != null)
				return dtoHelper.toDetentionCenterOrNull(topic);
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
		
		return null;
	}
	
	@GET
	@Path("/v1/{languageCode}/theses")
	@Override
	public List<Thesis> getTheses(@PathParam("languageCode") String languageCode) {
		List<Thesis> results = new ArrayList<>();
		
		for (Topic topic : dm4.getTopicsByType(NS("thesis"))) {
			try {
				Thesis thesis = dtoHelper.toThesisOrNull(topic);
				if (thesis != null)
					results.add(thesis);
			} catch (JSONException jsone) {
				// TODO: Log what object was dropped
			}
		}
		
		return results;
	}

	@GET
	@Path("/v1/{languageCode}/thesis/{id}")
	public Thesis getThesis(@PathParam("languageCode") String languageCode, @PathParam("id") long id) {
		Topic topic = dm4.getTopic(id);

		try {
			if (topic != null)
				return dtoHelper.toThesisOrNull(topic);
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
		
		return null;
	}
	
	@GET
	@Path("/v1/{languageCode}/backgroundoverview")
	public List<BackgroundOverview> getBackground(@PathParam("languageCode") String languageCode) {
		try {
			return dtoHelper.toBackgroundOverviewList(dm4.getTopicsByType(NS("backgrounditem")));
		} catch (JSONException|IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	@GET
	@Path("/v1/{languageCode}/background/{id}")
	public BackgroundItem getBackgroundItem(@PathParam("languageCode") String languageCode, @PathParam("id") long id) {
		Topic topic = dm4.getTopic(id);

		try {
			if (topic != null)
				return dtoHelper.toBackgroundItem(topic);
		} catch (JSONException|IOException e) {
			throw new RuntimeException(e);
		}
		
		return null;
	}
	
	@GET
	@Path("/v1/{languageCode}/imprint")
	@Override
	public List<ImprintItem> getImprintItems(@PathParam("languageCode") String languageCode) {
		List<ImprintItem> results = new ArrayList<>();
		
		for (Topic topic : dm4.getTopicsByType(NS("imprintitem"))) {
			try {
				ImprintItem dto = dtoHelper.toImprintItem(topic);
				if (dto != null)
					results.add(dto);
			} catch (JSONException e) {
				// TODO: Log what object was dropped
			}
		}
		
		return results;
	}

	@GET
	@Path("/v1/{languageCode}/imprint/{id}")
	public ImprintItem getImprintItem(@PathParam("languageCode") String languageCode, @PathParam("id") long id) {
		Topic topic = dm4.getTopic(id);

		try {
			if (topic != null)
				return dtoHelper.toImprintItem(topic);
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
		
		return null;
	}
	
	@PUT
	@Consumes(MediaType.TEXT_PLAIN)
	@Path("/v1/import/{importDataType}")
	@Transactional
	public void importData(@PathParam("importDataType") String importDataType, String importDataCsv) {
		if (importDataType == null)
			throw new IllegalArgumentException("Missing import data type!");

		CSVParser parser = null;
		try {
			parser = CSVParser.parse(importDataCsv, CSVFormat.DEFAULT.withTrim());

			switch (importDataType) {
			case "oda":
				importHelper.importODA(parser);
				break;
			case "hdi":
				importHelper.importHDI(parser);
				break;
			case "remittances":
				importHelper.importRemittances(parser);
				break;
			case "migrationintensity":
				importHelper.importMigrationIntensity(parser);
				break;
			case "factsheet":
				importHelper.importFactSheet(parser);
				break;
			case "repatriation_treaties":
				importHelper.importRepatriationTreaties(parser);
				break;
			case "other_treaties":
				importHelper.importOtherTreaties(parser);
				break;
			case "theses":
				importHelper.importTheses(parser);
				break;
			case "findings":
				importHelper.importFindingsAndFeatures(parser);
				break;
			case "backgrounditems":
				importHelper.importBackground(parser);
				break;
			case "detentioncenterdata":
				importHelper.importDetentionCenters(parser);
				break;
			case "imprintdata":
				importHelper.importImprint(parser);
				break;
			default:
				throw new IllegalArgumentException("Unkown import data type: " + importDataType);
			}
		} catch (IOException ioe) {
			throw new IllegalArgumentException("Invalid CSV data!", ioe);
		}

	}

	@DELETE
	@Path("/v1/reset")
	@Transactional
	public void resetAllData() {
		importHelper.resetAllData();
	}
	
}
