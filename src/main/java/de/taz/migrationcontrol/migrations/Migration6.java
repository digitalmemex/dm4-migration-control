package de.taz.migrationcontrol.migrations;

import static de.taz.migrationcontrol.MigrationControlService.NS;

import de.deepamehta.core.Topic;
import de.deepamehta.core.service.Inject;
import de.deepamehta.core.service.Migration;
import de.deepamehta.workspaces.WorkspacesService;

/**
 */
public class Migration6 extends Migration {

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
				NS("factsheet"),
				NS("factsheet.refugeesincountry"),
				NS("factsheet.refugeesoutsidecountry"),
				NS("factsheet.refugeesineu"),
				NS("factsheet.idp"),
				NS("factsheet.applicationsforasylum"),
				NS("factsheet.asylumapprovalrate"),
				NS("factsheet.hasfrontexcooperation"),
				NS("factsheet.detentioncentercount"),
				NS("factsheet.departureisillegal")
		);

	}
	
	private void groupAssignToWorkspace(long wsId, String... topicTypeUris) {
		for (String uri : topicTypeUris) {
			Topic topic = dm4.getTopicByUri(uri);
			wsService.assignToWorkspace(topic, wsId);
		}
	}
	
}
