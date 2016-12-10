package de.taz.migrationcontrol;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;

import de.deepamehta.core.JSONEnabled;

@SuppressWarnings("serial")
public abstract class JSONEnabledArrayImpl extends JSONArray implements JSONEnabled {
	
	public final JSONObject toJSON() {
    	return this.toJSON();
    }
	
}
