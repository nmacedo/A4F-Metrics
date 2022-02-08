package pt.haslab.alloy4fun.metrics;

import org.json.JSONObject;


public class Share extends Model {

	public final Boolean instance_share;
	public final JSONObject theme;

	Share(JSONObject obj) {
		super(obj);
		instance_share = obj.has("sat");
		theme = obj.getJSONObject("theme");
	}

}
