package de.taz.migrationcontrol;

import org.codehaus.jettison.json.JSONObject;

import de.deepamehta.core.JSONEnabled;

@SuppressWarnings("serial")
public abstract class JSONEnabledImpl extends JSONObject implements JSONEnabled {
	
	public final JSONObject toJSON() {
    	return this;
    }
	
}
