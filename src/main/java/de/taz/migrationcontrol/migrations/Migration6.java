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
				NS("factsheet.frontexcooperationinfo"),
				NS("factsheet.frontexcooperationinfo.state"),
				NS("factsheet.frontexcooperationinfo.description"),
				NS("factsheet.detentioncenterinfo"),
				NS("factsheet.detentioncenterinfo.count"),
				NS("factsheet.detentioncenterinfo.description"),
				NS("factsheet.departurelegalityinfo"),
				NS("factsheet.departurelegalityinfo.isillegal"),
				NS("factsheet.departurelegalityinfo.description")
		);
		
		groupAssignToWorkspace(dataWsId, dm4.getTopicsByType(NS("factsheet.frontexcooperationinfo.state")));

	}
	
	private void groupAssignToWorkspace(long wsId, String... topicTypeUris) {
		for (String uri : topicTypeUris) {
			Topic topic = dm4.getTopicByUri(uri);
			wsService.assignToWorkspace(topic, wsId);
		}
	}
	
	private void groupAssignToWorkspace(long wsId, Iterable<Topic> topics) {
		for (Topic topic : topics) {
			wsService.assignToWorkspace(topic, wsId);
		}
	}

}
