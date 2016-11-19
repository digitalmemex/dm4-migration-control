package de.taz.migrationcontrol.migrations;

import static de.taz.migrationcontrol.DTOHelper.NS;

import de.deepamehta.core.Topic;
import de.deepamehta.core.service.Inject;
import de.deepamehta.core.service.Migration;
import de.deepamehta.workspaces.WorkspacesService;

/**
 */
public class Migration4 extends Migration {

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
				NS("statistic"),
				NS("statistic.type"),
				NS("statistic.entry"),
				NS("statistic.entry.value"),
				NS("countryoverview"),
				NS("countryoverview.dossiertitle"),
				NS("countryoverview.dossiersubtitle"),
				NS("countryoverview.intro"),
				NS("countryoverview.featurelink")
		);

		// Assigns all the values for the 'type' topics
		groupAssignToWorkspace(dataWsId, dm4.getTopicsByType(NS("statistic.type")));
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
