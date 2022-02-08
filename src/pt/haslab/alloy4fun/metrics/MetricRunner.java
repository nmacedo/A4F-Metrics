package pt.haslab.alloy4fun.metrics;
import static pt.haslab.alloy4fun.metrics.Execution.RESULT.SAT;
import static pt.haslab.alloy4fun.metrics.Execution.RESULT.UNSAT;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.json.JSONException;
import org.json.JSONObject;

import edu.mit.csail.sdg.alloy4.A4Reporter;
import edu.mit.csail.sdg.alloy4.Err;
import edu.mit.csail.sdg.alloy4.ErrorWarning;
import edu.mit.csail.sdg.ast.Command;
import edu.mit.csail.sdg.ast.ExprConstant;
import edu.mit.csail.sdg.ast.Func;
import edu.mit.csail.sdg.parser.CompModule;
import edu.mit.csail.sdg.parser.CompUtil;
import edu.mit.csail.sdg.translator.A4Options;
import edu.mit.csail.sdg.translator.A4Solution;
import edu.mit.csail.sdg.translator.TranslateAlloyToKodkod;
import pt.haslab.alloy4fun.metrics.Execution.RESULT;
import pt.haslab.alloy4fun.metrics.utils.MultiSet;

public class MetricRunner {

	private final static String secretTag = "//SECRET";
	
	private static Class<?> catalog;
	
	private static String module_name; 
	private static String catalog_name; 
	private static final List<String> challenge_cmds = new ArrayList<>();

	private static Model root;
	private static final Map<String,Model> models = new HashMap<>();
	private static final Map<String,Share> shares = new HashMap<>();
	private static final Map<String,Execution> executions = new HashMap<>();
	private static final Map<String,Link> links = new HashMap<>();
	private static final Map<String,Instance> instances = new HashMap<>();
	private static final Map<A4Solution,String> solutions = new HashMap<>();
	private static final Map<Err,String> errors = new IdentityHashMap<>();
	private static final Map<ErrorWarning,String> warnings = new HashMap<>();
	private static final Map<String,Model> derivs = new HashMap<>();
	private static final Map<String,String> metric_desc = new HashMap<>();
	private static final List<String> server_errors = new ArrayList<>();
	private static final List<String> inconsistent_res = new ArrayList<>();
	private static final List<String> inconsistent_msg = new ArrayList<>();
	private static final List<String> timeouts = new ArrayList<>();

	private final static Map<Method,List<Object[]>> metrics = new HashMap<>(); 
	
	public static void main(String[] args) throws ClassNotFoundException, JSONException, IOException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		final boolean all = args.length > 3;
		final String model_json = args[0];
		final String link_json = all?args[1]:null;
		final String instance_json = all?args[2]:null;
		final String original_id = args[all?3:1];
		final Class<?> catalog = Class.forName(args[all?4:2]);
		run(original_id,model_json,link_json,instance_json,catalog);
	}
	
	static void run(String model_id, String model_json, String link_json, String instance_json, Class<?> catalog) throws JSONException, IOException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		String jline;

		BufferedReader file = new BufferedReader(new FileReader(model_json));
		while ((jline = file.readLine()) != null) {
			final JSONObject obj = new JSONObject(jline);
			if (obj.has("original") && obj.getString("original").equals(model_id)) {
				Map<String,Model> mdls = Model.fromJSON(obj);
				models.putAll(mdls);
				for (Model mdl : mdls.values())
					if (mdl instanceof Execution)
						executions.put(mdl.id,(Execution) mdl);
					else
						shares.put(mdl.id,(Share) mdl);
				
			}
		}
		file.close();
		
		if (link_json != null) {
			file = new BufferedReader(new FileReader(link_json));
			while ((jline = file.readLine()) != null) {
				final JSONObject obj = new JSONObject(jline);
				if (models.containsKey(obj.getString("model_id"))) {
					links.put(obj.getString("_id"),new Link(obj));
				}
			}
			file.close();
		}

		if (instance_json != null) { 
			file = new BufferedReader(new FileReader(instance_json));
			while ((jline = file.readLine()) != null) {
				final JSONObject obj = new JSONObject(jline);
				if (models.containsKey(obj.getString("model_id"))) {
					instances.put(obj.getString("_id"),new Instance(obj));
				}
			}
			file.close();
		}

		MetricRunner.catalog = catalog;
		MetricSuite suite = MetricRunner.catalog.getAnnotation(MetricSuite.class);
		if (MetricRunner.catalog.getAnnotation(MetricSuite.class) == null)
			throw new IllegalArgumentException("Invalid catalog annotations.");
		MetricRunner.catalog_name = suite.description().isEmpty()?catalog.getName():suite.description();
		
		Model original = models.get(model_id);
		
		CompModule or = CompUtil.parseEverything_fromString(new A4Reporter(),original.code);
		module_name = or.getModelName();
		for (Command c : or.getAllCommands())
			challenge_cmds.add(c.label);

		for (String id : models.keySet())
			calculateDerivTree(id);
		
		for (String id: server_errors)
			models.remove(id);
		
		root = derivs.get(model_id);
		
		System.out.println("Models: "+getTotalModels());
		System.out.println("Execs: "+getTotalExecs());
		System.out.println("Shares: "+getTotalShares());
		System.out.println("Errors: "+getTotalErrors());
		System.out.println("Warnings: "+getTotalWithWarnings());
		System.out.println("Solutions: "+getTotalSolutions());
		System.out.println("Sessions: "+getTotalSessions());
		System.out.println("Timeouts: "+getTotalTimeouts());
		System.out.println("Rejected: "+getTotalServerErrors());
		System.out.println("Inconsistent msg: "+getTotalInconsistentMsg());
		System.out.println("Inconsistent res: "+getTotalInconsistentRes());
		System.out.println("Instances: "+getTotalInstances());
		System.out.println("Links: "+getTotalLinks());
		
		processMetrics();
		
	}
	
	public static Map<String,MapOrMSet<?>> aggregatedMetrics() {
		Map<String, MapOrMSet<?>> mets = new HashMap<>();
		for (Method s : metrics.keySet()) {
			MapOrMSet<?> res;
			if (metrics.get(s).isEmpty()) {
				MapOrMSet<Integer> aux = new MapOrMSet<Integer>(Integer.class);
				aux.obj.add(0);
				res = new MapOrMSet<>(Integer.class);
				((MapOrMSet<Integer>) res).map.put("empty", aux);
			}
			else {
				Object[] fst = metrics.get(s).get(0);
				res = new MapOrMSet<>(fst[fst.length-1].getClass());
				List<Object[]> rs = metrics.get(s);
				for (Object[] o : rs)
					aggregate(res, o);
				switch (s.getAnnotation(MetricMethod.class).groupby()) {
				case COUNT:
					res = res.count();
					break;
				case SUM:
					res = res.sum();
					break; 
				case AVG:
					res = res.avg();
					break;
				case MIN:
					res = res.min();
					break;
				case MAX:
					res = res.max();
					break;
				default:
					break;
				}
			}
			mets.put(s.getAnnotation(MetricMethod.class).rule(), res);
		}
		return mets;
	}
	
	private static void aggregate(MapOrMSet res, Object[] inp) {
		if (inp.length == 1)
			res.obj.add(inp[0]);
		else {
			MapOrMSet nxt = res.children(inp[0]);
			aggregate(nxt, Arrays.copyOfRange(inp, 1, inp.length));
		}
	}
	
	static class MapOrMSet<T> {
		final Map<Object,MapOrMSet<T>> map = new HashMap<Object,MapOrMSet<T>>();
		final MultiSet<T> obj;
		
		boolean leaf() { return map.isEmpty(); }
		
		// returns children or creates it
		MapOrMSet<T> children(Object c, Class<T> t) {
			return map.computeIfAbsent(c, k -> new MapOrMSet<T>(t));
		}

		MapOrMSet<T> children(Object c) {
			return map.computeIfAbsent(c, k -> new MapOrMSet<T>());
		}

		MapOrMSet() {
			this.obj = new MultiSet<T>();
		}

		MapOrMSet(Class<T> obj) {
			this.obj = MultiSet.factory(obj);
		}
		
		public MapOrMSet(T i) {
			this.obj = new MultiSet<T>();
			this.obj.add(i);
		}

		int level() {
			if (leaf()) return 0;
			else if (map.isEmpty()) return 1;
			else return 1 + map.values().iterator().next().level();
		}

		MapOrMSet<T> min() {
			MapOrMSet<T> res = new MapOrMSet<T>();
			if (!leaf()) {
				for (Object c : map.keySet()) 
					res.map.put(c,map.get(c).min());
			}
			else {
				T f = obj.min();
				MapOrMSet<T> c = res.children("min",(Class<T>) f.getClass());
				c.obj.add(f);
				obj.clear();
			}
			return res;
		}

		MapOrMSet<T> max() {
			MapOrMSet<T> res = new MapOrMSet<T>();
			if (!leaf()) {
				for (Object c : map.keySet()) 
					res.map.put(c,map.get(c).max());
			}
			else {
				T f = obj.max();
				MapOrMSet<T> c = res.children("max",(Class<T>) f.getClass());
				c.obj.add(f);
				obj.clear();
			}
			return res;
		}
		
		MapOrMSet<Double> avg() {
			MapOrMSet<Double> res = new MapOrMSet<Double>(Double.class);
			if (!leaf()) {
				for (Object c : map.keySet()) 
					res.map.put(c,map.get(c).avg());
			}
			else {
				Double f = obj.avg();
				MapOrMSet<Double> c = res.children("avg",Double.class);
				c.obj.add(f);
				obj.clear();
			}
			return res;
		}
		
		MapOrMSet<T> sum() {
			MapOrMSet<T> res = new MapOrMSet<T>();
			if (!leaf()) {
				for (Object c : map.keySet()) 
					res.map.put(c,map.get(c).sum());
			}
			else {
				T f = obj.sum();
				MapOrMSet<T> c = res.children("sum",(Class<T>) f.getClass());
				c.obj.add(f);
				obj.clear();
			}
			return res;
		}

		MapOrMSet<Integer> count() {
			MapOrMSet<Integer> res = new MapOrMSet<Integer>(Integer.class);
			if (!leaf()) {
				for (Object c : map.keySet()) 
					res.map.put(c,map.get(c).count());
			}
			else {
				for (T o : obj.elems()) {
					MapOrMSet<Integer> c = res.children(o,Integer.class);
					c.obj.add(obj.count(o));
				}
				obj.clear();
			}
			return res;
		}
}
	
	private static void processMetrics() throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		Method[] methods = catalog.getMethods();
		Map<List<Class<?>>,List<Method>> mts = new HashMap<>();
		for (Method method : methods) {
			MetricMethod annos = method.getAnnotation(MetricMethod.class);
			if (annos != null) {
				metric_desc.put(annos.rule(), annos.description());
				List<Class<?>> anns = Arrays.stream(method.getParameterAnnotations()).map(k -> k[0].annotationType()).collect(Collectors.toList());
				List<Method> tmp = mts.computeIfAbsent(anns, k -> new ArrayList<Method>());
				tmp.add(method);
			}
		}
		for (List<Class<?>> as : mts.keySet())
			processMetric(as, mts.get(as), new ArrayList<>());
		
	}
	
	// TODO: this will repeat iteration if permutation of identical parameters
	private static void processMetric(List<Class<?>> p, List<Method> ms, List<Object> a) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		if (p.isEmpty()) {
			for (Method m : ms) {
				Object[] res = (Object[]) m.invoke(null, a.toArray());
				List<Object[]> reses = metrics.computeIfAbsent(m, k -> new ArrayList<Object[]>());
				if (res != null)
					reses.add(res);
			}
		}
		else {
			Iterator<?> it = iteratorParam(p.get(0));
			while (it.hasNext()) {
				List<Object> lst = new ArrayList<>(a);
				lst.add(it.next());
				processMetric(p.subList(1, p.size()), ms, lst);
			}
		}
	}
	
	private static Model calculateDerivTree(String id) {
		if (!models.containsKey(id)) {
			System.out.println("Could not find parent model "+id);
			return null;
		}
		if (derivs.containsKey(id))
			return derivs.get(id);
		
		Model obj = models.get(id);
		
		Model parent = null;
		try {
			parent = derivs.get(obj.parent_entry);
			if (parent == null)
				parent = calculateDerivTree(obj.parent_entry);
		} catch (Exception e) {
			System.out.println("Problems with derivationOf of "+id);
		}
		
		String model = obj.code;
		
		if (extractSecrets(model).isEmpty()) {
			List<String> secrets = extractSecrets(models.get(obj.root_entry).code);
			for (String secret : secrets)
				model += secret;
		}
		
		final List<ErrorWarning> wns = new ArrayList<>();
		final A4Reporter rep = new A4Reporter() {
			public void warning(ErrorWarning msg) {
				super.warning(msg);
				wns.add(msg);
			};
		};
	
		try {
			final CompModule wrl = CompUtil.parseEverything_fromString(rep, model);
			for (ErrorWarning e : wns) {
				warnings.put(e,id);
				obj.addWarning(e);
			}
			if (obj instanceof Execution) {

				final Command cmd = wrl.getAllCommands().get(((Execution) obj).cmd_index);
				Err err = null;
				executions.get(id).setCommand(cmd);
				ExecutorService executor = Executors.newCachedThreadPool();
				Callable<A4Solution> task = new Callable<A4Solution>() {
				   public A4Solution call() {
				      return TranslateAlloyToKodkod.execute_command(rep, wrl.getAllReachableSigs(), cmd, new A4Options());
				   }
				};
				Future<A4Solution> future = executor.submit(task);
				A4Solution sol = null;
				try {
					sol = future.get(60, TimeUnit.SECONDS); 
					solutions.put(sol, id);
				} catch (TimeoutException e) {
					timeouts.add(id);
					System.out.println("Timed out: "+obj.id);
				} catch (InterruptedException e) {
				} catch (ExecutionException e) {
					if (e.getCause() instanceof Err) {
						// Alloy runtime errors
						err = (Err) e.getCause();
						obj.error = err;
						errors.put(err, id);
					}
				} finally {
				   future.cancel(true);
				   executor.shutdownNow();
				}
				
			
				if (((Execution) obj).result() == RESULT.ERROR && sol != null) {
					for (ErrorWarning w : obj.wns)
						warnings.remove(w);
					errors.remove(err);
					executions.remove(id);
					solutions.remove(sol);
					server_errors.add(id);
					System.out.println("Server error, disregarded during analysis: "+((Execution) obj).msg+" ("+obj.id+")");
				}
				else if (((Execution) obj).result() == RESULT.ERROR && err == null) {
					System.out.println("Had error result registered but ran ok: "+((Execution) obj).msg.replace("\n", " ")+" ("+obj.id+")");
					inconsistent_res.add(id);
				} else if (((Execution) obj).msg != null && wns.isEmpty() && err == null) {
					System.out.println("Had error message registered but ran ok: "+((Execution) obj).msg.replace("\n", " ")+" ("+obj.id+")");
					inconsistent_msg.add(id);
				}
			}

		} catch (Err e) {
			if (errors.containsKey(e)) {
				System.out.println("alarm");
				e.printStackTrace();
			}
			errors.put(e, id);
			obj.error = e;
		} catch (Exception e) {
			e.printStackTrace();
		}

		if (parent != null)
			parent.addChild(obj);
		
		derivs.put(id, obj);

		return obj;
	}
	
	
	private static List<String> extractSecrets(String code) {
	    String tag = Pattern.quote(secretTag)+"\\s*?\\n";
	    String paragraphKeywords = "sig|fact|assert|check|fun|pred|run";
	    String pgd = "(?:(?:var|one|abstract|lone|some)\\s+)*"+paragraphKeywords;
	    String cmnts = "(?:(?:/\\*(?:.|\\n)*?\\*/\\s*)|(?://.*\\n)|(?:--.*\\n))";
	    String exp = "(?:("+tag+"\\s*"+ cmnts +"*?\\s*(?:"+pgd+")(?:.|\\n)*?)(?:"+tag+"\\s*)?(?:"+ cmnts +"*?\\s*(?:"+pgd+")\\s+|$))";

	    List<String> mts = new ArrayList<String>();
	    Matcher m = Pattern.compile(exp).matcher(code);
	    int where = 0;
	    while (m.find(where)) {
	    	mts.add(m.group(1));
	    	where = m.end(1);
	    }
	    
	    return mts;
	}
	
	private static Iterator<?> iteratorParam(Class<?> param) {
		if (param == ForAllSolutions.class)
			return solutions.keySet().iterator();
		else if (param == ForAllErrors.class)
			return errors.keySet().iterator();
		else if (param == ForAllWarnings.class)
			return warnings.keySet().iterator();
		else if (param == ForAllModels.class)
			return models.values().iterator();
		else if (param == ForAllExecutions.class)
			return executions.values().iterator();
		else if (param == ForAllShares.class)
			return shares.values().iterator();
		else if (param == ForAllSessions.class)
			return root.children().iterator();
		else if (param == ForAllCommands.class)
			return challenge_cmds.iterator();
		else if (param == ForAllInstances.class)
			return instances.values().iterator();
		else if (param == ForAllLinks.class)
			return links.values().iterator();
		
		return null;
	}

	public static int getTotalModels() {
		return models.size();
	}

	public static int getTotalExecs() {
		return executions.size();
	}

	public static int getTotalShares() {
		return shares.size();
	}
	
	public static int getTotalInstances() {
		return instances.size();
	}
	
	public static int getTotalLinks() {
		return links.size();
	}

	public static int getTotalErrors() {
		return errors.size();
	}

	public static int getTotalSessions() {
		return root.children().size();
	}

	public static int getTotalWithWarnings() {
		return warnings.size();
	}

	public static int getTotalSolutions() {
		return solutions.size();
	}

	public static int getTotalServerErrors() {
		return server_errors.size();
	}
	
	public static int getTotalInconsistentRes() {
		return inconsistent_res.size();
	}

	public static int getTotalInconsistentMsg() {
		return inconsistent_msg.size();
	}

	public static int getTotalTimeouts() {
		return timeouts.size();
	}

	public static List<String> getChallenges() {
		return new ArrayList<>(challenge_cmds);
	}

	public static boolean isChallenge(String c) {
		return challenge_cmds.contains(c);
	}

	public static Model getEntry(A4Solution sol) {
		return models.get(solutions.get(sol));
	}

	public static Model getEntry(Err err) {
		return models.get(errors.get(err));
	}

	public static Model getEntry(ErrorWarning wrs) {
		return models.get(warnings.get(wrs));
	}

	public static String getCatalogName() {
		return catalog_name;
	}

	public static String getModuleName() {
		return module_name;
	}

	public static LocalDate getCreationDate() {
		return root.time.toLocalDate();
	}
	
	public static Model getRoot() {
		return root;
	}
	
	public static String getMetricDesc(String metric) {
		return metric_desc.get(metric);
	}
	
	static class SessionMetrics {
	    final MultiSet<String> sat_cmds = new MultiSet<>();
	    final MultiSet<String> unsat_cmds = new MultiSet<>();
	    int shared_mdls = 0;
	    int shared_insts = 0;
	    int depth = 0;
	    
	    SessionMetrics(Model d) {
	    	depth++;
	    	if (d instanceof Execution) {
	            if (((Execution) d).result() == SAT) {
	            	sat_cmds.add(((Execution) d).cmd_name);
	            }
	            else if (((Execution) d).result() == UNSAT) {
	            	unsat_cmds.add(((Execution) d).cmd_name);
	            }
	    	} else if (d instanceof Share) {
	        	if (!((Share) d).instance_share)
	            	shared_mdls++;
	        	else 
	            	shared_insts++;
	    	}
		}

		void merge(SessionMetrics m) {
            this.sat_cmds.merge(m.sat_cmds);
            this.unsat_cmds.merge(m.unsat_cmds);
            this.shared_mdls+=m.shared_mdls;
            this.shared_insts+=m.shared_insts;
            this.depth=Math.max(this.depth, m.depth+1);
	    }
		
		Set<String> unsatCommands() {
			return new HashSet<String>(unsat_cmds.elems());
		}

		Set<String> satCommands() {
			return new HashSet<String>(sat_cmds.elems());
		}
		
		int numSatExecs() {
			return sat_cmds.size();
		}

		int numUnsatExecs() {
			return unsat_cmds.size();
		}
}
	
    static SessionMetrics sessionMetrics(Model d) {
    	if (derivMetrics.containsKey(d))
    		return derivMetrics.get(d);
    	
    	SessionMetrics res = new SessionMetrics(d);
    	for (Model c : d.children) {
    		SessionMetrics ms = sessionMetrics(c);
    		res.merge(ms);
    	}
    	derivMetrics.put(d, res);
    	
    	return res;
    }
    
    private static Map<Model,SessionMetrics> derivMetrics = new HashMap<Model,SessionMetrics>();
    
    
    static Set<String> challengPreds() {
		Share root = (Share) MetricRunner.getRoot();
		final CompModule wrl = CompUtil.parseEverything_fromString(new A4Reporter(), root.code);
		Set<String> empties = new HashSet<String>();
		for (Func f : wrl.getAllFunc())
			if (f.getBody().isSame(ExprConstant.TRUE)) 
				empties.add(f.label);
		return empties;
    }
    
	static <T> List<T> normalizeIndices(Set<T> idxs) {

		List<T> norm_idsx;
		Iterator<T> it = idxs.iterator();
		T x = it.next();
		if (x instanceof Integer) {
			List<Integer> aux = new ArrayList<>();
			for (Integer i = Collections.min((Set<Integer>) idxs); i <= Collections.max((Set<Integer>) idxs); i++)
				aux.add(i);
			norm_idsx = (List<T>) aux;
		} else if (x instanceof LocalDate) {
			List<LocalDate> aux = new ArrayList<>();
			for (LocalDate i = MetricRunner.getCreationDate(); i.isBefore(LocalDate.now()) || i.equals(LocalDate.now()); i = i.plusDays(1))
				aux.add(i);
			norm_idsx = (List<T>) aux;
		} else if (x instanceof String && idxs.contains(MetricRunner.getChallenges().get(0))) {
			norm_idsx = (List<T>) MetricRunner.getChallenges();
			List<T> aux = new ArrayList<T>(idxs);
			aux.removeAll(norm_idsx);
			norm_idsx.addAll(aux);
		} else {
			norm_idsx = new ArrayList<T>(idxs);
		}
		
		return norm_idsx;
	}
	
	public static String normUpMessages(String string) {
		return string.replaceAll("name \".*?\"", "name _").replaceAll("\\s\\s.*?\\n", "  _\n")
				.replaceAll("\\{...*?\\}", "{_}").replaceAll("<...*?>", "<_>")
				.replaceAll("filename=.*?als", "filename=_.als").replaceAll("line\\s.*?\\s", "line _ ")
				.replaceAll("column\\s.*?\\s", "column _ ").replaceAll("parameters\\sare\\s.*?:", "parameters are _:")
				.replaceAll("field\\s.*?\\s<:\\s.*?(\\s|$)", "_ ").replaceAll("fun\\s.*?/.*?(\\s|$)", "_ ")
				.replaceAll("sig\\s.*?/.*?(\\s|$)", "_ ").replaceAll("pred\\s.*?/.*?(\\s|$)", "_ ")
				.replaceAll("to\\spred\\s.*?\\.", "to pred _.").replaceAll("to\\sfun\\s.*?\\.", "to fun _.")
				.replaceAll("side\\sis\\s.*?\\(type", "side is _ (type").replaceAll("^.*?is\\salready", "_ is already")
				.replaceAll("\\^.*?\\sis redundant", "^_ is redundant").replaceAll("\\s\\(.*?\\)", "(_)");
	}

    
}
