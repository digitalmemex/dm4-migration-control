package de.taz.migrationcontrol.migrations;

import static de.taz.migrationcontrol.DTOHelper.NS;

import de.deepamehta.accesscontrol.AccessControlService;
import de.deepamehta.core.Topic;
import de.deepamehta.core.service.Inject;
import de.deepamehta.core.service.Migration;
import de.deepamehta.core.service.accesscontrol.SharingMode;
import de.deepamehta.workspaces.WorkspacesService;

/**
 */
public class Migration1 extends Migration {

	@Inject
	private WorkspacesService wsService;

	@Inject
	private AccessControlService acService;

	/** Creates workspaces for Gamechangers:
	 * - types: Gamechangers topic types
	 * - data: Persons, groups, institutions, etc
	 * - comments: Comments and proposals
	 */
	@Override
	public void run() {
		Topic typesWs = wsService.createWorkspace("Migration-Control Types", NS("workspace.types"),
				SharingMode.PUBLIC);
		acService.setWorkspaceOwner(typesWs, AccessControlService.ADMIN_USERNAME);

		Topic dataWs = wsService.createWorkspace("Migration-Control Data", NS("workspace.data"),
				SharingMode.PUBLIC);
		acService.setWorkspaceOwner(dataWs, AccessControlService.ADMIN_USERNAME);
	}
}
