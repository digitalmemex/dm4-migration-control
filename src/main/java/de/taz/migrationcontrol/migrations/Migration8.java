package de.taz.migrationcontrol.migrations;

import static de.taz.migrationcontrol.MigrationControlService.NS;

import de.deepamehta.core.Topic;
import de.deepamehta.core.service.Inject;
import de.deepamehta.core.service.Migration;
import de.deepamehta.workspaces.WorkspacesService;

/**
 */
public class Migration8 extends Migration {

	@Inject
	private WorkspacesService wsService;

	/**
	 * Modifies:
	 * 
	 */
	@Override
	public void run() {

		// Workspace associations
		long dataWsId = wsService.getWorkspace(NS("workspace.types")).getId();

		groupAssignToWorkspace(dataWsId, NS("statistic.entry.source"), NS("statistic.entry.link"));

		dm4.getTopicType(NS("statistic.entry"))
			.addAssocDef(mf.newAssociationDefinitionModel("dm4.core.aggregation_def",
				NS("statistic.entry"), NS("statistic.entry.source"), "dm4.core.one", "dm4.core.one"))
			.addAssocDef(mf.newAssociationDefinitionModel("dm4.core.aggregation_def",
					NS("statistic.entry"), NS("statistic.entry.link"), "dm4.core.one", "dm4.core.one"));
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
