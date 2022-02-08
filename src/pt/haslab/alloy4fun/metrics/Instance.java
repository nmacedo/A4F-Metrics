package pt.haslab.alloy4fun.metrics;

import org.json.JSONObject;

public class Instance extends Entry {

	public final String model_entry;
	public final JSONObject graph;

	public Instance(JSONObject obj) {
		super(obj);
		this.model_entry = obj.getString("model_id");
		this.graph = obj.getJSONObject("graph");
	}
	
}
