package pt.haslab.alloy4fun.metrics;

import org.json.JSONObject;

public class Entry {

	public final String id;
	
	Entry (JSONObject obj) {
		this.id = obj.getString("_id");
	}
}
