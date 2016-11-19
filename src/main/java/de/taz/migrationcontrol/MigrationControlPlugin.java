package de.taz.migrationcontrol;

import javax.ws.rs.Consumes;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import de.deepamehta.accesscontrol.AccessControlService;
import de.deepamehta.core.osgi.PluginActivator;
import de.deepamehta.core.service.Inject;
import de.deepamehta.workspaces.WorkspacesService;

@Path("/gamechangers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MigrationControlPlugin extends PluginActivator implements MigrationControlService {

	@Inject
	private WorkspacesService wsService; // needed by migration 1

	@Inject
	private AccessControlService acService; // needed by migration 1

	private DTOHelper helper;
	
	@Override
	public void init() {
		helper = new DTOHelper(dm4, mf, wsService);
	}

}
