package de.taz.migrationcontrol.migrations;

import static de.taz.migrationcontrol.MigrationControlService.NS;

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
				NS("countryoverview.findinglink"),
				NS("countryoverview.featurelink"),
				NS("treaty"),
				NS("treaty.name"),
				NS("treaty.type"),
				NS("treaty.link"),
				NS("treaty.partner"),
				NS("thesis.name"),
				NS("thesis.text"),
				NS("thesis.contextualisation"),
				NS("thesis.sourceinfo"),
				NS("thesis.diagramtype"),
				NS("backgrounditem"),
				NS("backgrounditem.name"),
				NS("backgrounditem.link"),
				NS("backgrounditem.columnindex")
		);

		// Assigns all the values for the 'type' topics
		groupAssignToWorkspace(dataWsId, dm4.getTopicsByType(NS("statistic.type")));
		groupAssignToWorkspace(dataWsId, dm4.getTopicsByType(NS("treaty.type")));
		groupAssignToWorkspace(dataWsId, dm4.getTopicsByType(NS("thesis.diagramtype")));
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
