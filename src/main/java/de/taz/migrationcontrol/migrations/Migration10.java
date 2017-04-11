package de.taz.migrationcontrol.migrations;

import static de.taz.migrationcontrol.MigrationControlService.NS;

import de.deepamehta.core.Topic;
import de.deepamehta.core.service.Inject;
import de.deepamehta.core.service.Migration;
import de.deepamehta.workspaces.WorkspacesService;

/**
 */
public class Migration10 extends Migration {

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
				NS("language"),
				NS("translatedtext"),
				NS("translation")
		);

		// Assigns all the values for the 'type' topics
		groupAssignToWorkspace(dataWsId, dm4.getTopicsByType(NS("language")));
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
