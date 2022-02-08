package pt.haslab.alloy4fun.metrics;

import org.json.JSONObject;

public class Link extends Entry {

	public final String model_entry;
	public final boolean private_link;

	public Link(JSONObject obj) {
		super(obj);
		this.model_entry = obj.getString("model_id");
		this.private_link = obj.getBoolean("private");
	}

}
