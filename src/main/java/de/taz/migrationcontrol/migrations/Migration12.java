package de.taz.migrationcontrol.migrations;

import static de.taz.migrationcontrol.MigrationControlService.NS;

import de.deepamehta.core.Topic;
import de.deepamehta.core.service.Inject;
import de.deepamehta.core.service.Migration;
import de.deepamehta.workspaces.WorkspacesService;

/**
 */
public class Migration12 extends Migration {

	@Inject
	private WorkspacesService wsService;

	/** Modifies:
	 * 
	 */
	@Override
	public void run() {
		// Workspace associations
		long dataWsId = wsService.getWorkspace(NS("workspace.types")).getId();
		
		groupAssignToWorkspace(dataWsId,
				NS("factsheet.informaltreatiesdescription")
		);

		dm4.getTopicType(NS("factsheet"))
		.addAssocDef(mf.newAssociationDefinitionModel("dm4.core.composition_def",
			NS("factsheet"), NS("factsheet.informaltreatiesdescription"), "dm4.core.one", "dm4.core.one"));

	}
	
	private void groupAssignToWorkspace(long wsId, String... topicTypeUris) {
		for (String uri : topicTypeUris) {
			Topic topic = dm4.getTopicByUri(uri);
			wsService.assignToWorkspace(topic, wsId);
		}
	}
	
}
