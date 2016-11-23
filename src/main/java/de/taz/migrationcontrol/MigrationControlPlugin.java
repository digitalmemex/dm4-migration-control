package de.taz.migrationcontrol;

import java.io.IOException;

import javax.ws.rs.Consumes;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;

import de.deepamehta.accesscontrol.AccessControlService;
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

	private DTOHelper helper;

	private ImportHelper importHelper;
	
	@Override
	public void init() {
		helper = new DTOHelper(dm4, mf, wsService);
		importHelper = new ImportHelper(dm4, mf, wsService);
	}

	@PUT
	@Consumes(MediaType.TEXT_PLAIN)
	@Path("/v1/import/{importDataType}")
	@Transactional
	public void importData(
			@PathParam("importDataType") String importDataType,
			String importDataCsv) {
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
			case "payments":
				importHelper.importPayments(parser);
				break;
			default:
				throw new IllegalArgumentException("Unkown import data type: " + importDataType);
			}
		} catch (IOException ioe) {
			throw new IllegalArgumentException("Invalid CSV data!", ioe);
		}
		
	}

}
