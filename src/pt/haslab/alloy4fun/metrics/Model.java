package pt.haslab.alloy4fun.metrics;

import java.util.Map;
import java.util.HashMap;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.json.JSONObject;

import edu.mit.csail.sdg.alloy4.Err;
import edu.mit.csail.sdg.alloy4.ErrorWarning;

abstract public class Model extends Entry {

	public final String parent_entry;
	public final String root_entry;
	public final LocalDateTime time;
	public final String id;
	public final String code;
	public Err error = null;
	Set<ErrorWarning> wns = new HashSet<>();
	Model parent;
    protected final List<Model> children;

	static public Map<String,Model> fromJSON(JSONObject obj) {
		Map<String,Model> mdls = new HashMap();
		if (obj.has("theme"))
			mdls.put(obj.getString("_id"),new Share(obj));
		else 
			mdls.put(obj.getString("_id"),new Execution(obj));
		
		
		if (obj.has("children"))
			for (Object chld : obj.getJSONArray("children"))
				mdls.putAll(fromJSON((JSONObject) chld));
		return mdls;
	}

	Model(JSONObject obj) {
		super(obj);
		this.time = strToTime(obj.getString("time"));
		this.parent_entry = !obj.isNull("derivationOf") ? obj.getString("derivationOf") : null;
		this.root_entry = obj.getString("original");
		this.id = obj.getString("_id");
        this.children = new LinkedList<Model>();
		this.code = obj.getString("code");
	}


    public void addChild(Model child) {
    	child.parent = this;
        this.children.add(child);
    }

	public List<Model> children() {
		children.sort((c1,c2) -> c1.time.compareTo(c2.time));
		return children;
	}
	

	public void addWarning(ErrorWarning e) {
		wns.add(e);
	}
	
	public static LocalDateTime strToTime(String strtime) {
		String[] daux = (strtime.split(" ")[0]).split("-");
		String[] taux = (strtime.split(" ")[1]).split(":");
		LocalDate date = LocalDate.of(Integer.valueOf(daux[0]), Integer.valueOf(daux[1]), Integer.valueOf(daux[2]));
		LocalTime time = LocalTime.of(Integer.valueOf(taux[0]), Integer.valueOf(taux[1]), Integer.valueOf(taux[2]));
		return LocalDateTime.of(date, time);
	}
	
}
