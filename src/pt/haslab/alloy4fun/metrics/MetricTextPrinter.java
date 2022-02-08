package pt.haslab.alloy4fun.metrics;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONException;

import pt.haslab.alloy4fun.metrics.Execution.RESULT;
import pt.haslab.alloy4fun.metrics.MetricRunner.MapOrMSet;

public class MetricTextPrinter {
	
	private static void printDerivTree(Model obj, int indent, FileWriter fw) throws IOException {
		 
		fw.write(new String(new char[indent]).replace("\0", "  ") +printShortObj(obj)+"\n");
		for (String s : obj.code.split("\\n"))
			fw.write(new String(new char[indent]).replace("\0", "  ") +s+"\n");
		for (Model entry : obj.children())
			printDerivTree(entry, indent+1, fw);
		
	}
	
	private static String printShortObj(Model obj) {
		StringBuilder sb = new StringBuilder();
		sb.append(obj.id);
		sb.append(", ");
		sb.append(obj.time);
		sb.append(" (");
		if (obj instanceof Share)
			if (((Share) obj).instance_share)
				sb.append("instance share");
			else
				sb.append("model share");
		else if (obj instanceof Execution) {
			if (((Execution) obj).result() == RESULT.SAT || ((Execution) obj).result() == RESULT.UNSAT) {
				sb.append(((Execution) obj).cmd_name);
				sb.append(",");
				sb.append(((Execution) obj).result() == RESULT.SAT?"incorrect":"correct");
				sb.append(",");
				sb.append(((Execution) obj).cmd_check?"check":"run");
				sb.append(",");
			}
			if (((Execution) obj).msg != null)
				sb.append(((Execution) obj).msg.replace("\n", " "));
		}
		sb.append(")");
		return sb.toString();
	}

	public static void main(String[] args) throws SecurityException, IOException, ClassNotFoundException, JSONException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		final boolean all = args.length > 3;
		final String model_json = args[0];
		final String link_json = all?args[1]:null;
		final String instance_json = all?args[2]:null;
		final String original_id = args[all?3:1];
		final Class<?> catalog = Class.forName(args[all?4:2]);
		MetricRunner.run(original_id,model_json,link_json,instance_json,catalog);

		FileWriter fw = new FileWriter(original_id+".txt");
		
		fw.write(original_id+" ("+LocalDate.now()+")\n");
		
		fw.write("\n* Meta-data\n");
		fw.write("Challenge name: "+MetricRunner.getModuleName()+"\n");
		fw.write("Creation time: "+MetricRunner.getCreationDate()+"\n");
		fw.write("# sub-challenges: "+MetricRunner.getChallenges().size()+"\n");
		fw.write("Metric catalog: "+MetricRunner.getCatalogName()+"\n");

		fw.write("\n* Overall metric statistics\n");
		fw.write("Total model entries: "+MetricRunner.getTotalModels()+"\n");
		fw.write("Total executions: "+MetricRunner.getTotalExecs()+"\n");
		fw.write("Total shares: "+MetricRunner.getTotalShares()+"\n");
		fw.write("Total error messages: "+MetricRunner.getTotalErrors()+"\n");
		fw.write("Total warning messages: "+MetricRunner.getTotalWithWarnings()+"\n");
		fw.write("Total solutions: "+MetricRunner.getTotalSolutions()+"\n");
		fw.write("Total sessions: "+MetricRunner.getTotalSessions()+"\n");
		fw.write("Timed out re-executions: "+MetricRunner.getTotalTimeouts()+"\n");
		fw.write("Rejected due to server errors: "+MetricRunner.getTotalServerErrors()+"\n");
		fw.write("Inconsisistent result reports: "+MetricRunner.getTotalInconsistentRes()+"\n");
		fw.write("Inconsisistent message reports: "+MetricRunner.getTotalInconsistentMsg()+"\n");
		fw.write("Total instance entries: "+MetricRunner.getTotalInstances()+"\n");
		fw.write("Total link entries: "+MetricRunner.getTotalLinks()+"\n");
		
		Map<String,MapOrMSet<?>> metrics =  MetricRunner.aggregatedMetrics();
		
		fw.write("\n* Scalar metrics\n");
		for (String metric_name : metrics.keySet())
			if (metrics.get(metric_name).map.size() == 1 && metrics.get(metric_name).level() == 1)
				fw.write(metric_name+"\t"+metrics.get(metric_name).map.values().iterator().next().obj+"\n");

		fw.write("\n* Overall metrics\n");
		for (String metric_name : metrics.keySet())
			if (metrics.get(metric_name).map.size() > 1 && metrics.get(metric_name).level() == 1) {
				fw.write("\n"+metric_name+"\n");

				List<? extends Object> norm_idsx = MetricRunner.normalizeIndices(metrics.get(metric_name).map.keySet());

				for (Object c : norm_idsx) {
					fw.write(c.toString().replace("\n", " ")+"\t"+metrics.get(metric_name).map.getOrDefault(c,new MapOrMSet(0)).obj+"\n");
				}
			}
		
		fw.write("\n* Overall metrics, classified\n");
		for (String metric_name : metrics.keySet()) {
			if (metrics.get(metric_name).map.size() > 0 && metrics.get(metric_name).level() == 2) {
				fw.write("\n"+metric_name+"\n");
				fw.write(printClassifiedBars(metrics.get(metric_name), metric_name));
			}
		}
		
		fw.write("\n* Metrics by sub-challenge, classified\n");
		for (String metric_name : metrics.keySet()) {
			if (metrics.get(metric_name).map.size() > 0 && metrics.get(metric_name).level() == 3) {
				fw.write("\n"+metric_name+"\n");
				List<? extends Object> norm_idsx = MetricRunner.normalizeIndices(metrics.get(metric_name).map.keySet());
				for (Object challenge_name : norm_idsx) {
					if (metrics.get(metric_name).map.containsKey(challenge_name) && metrics.get(metric_name).map.get(challenge_name).map.size() > 0) {
						fw.write("\n"+challenge_name+"\n");
						fw.write(printClassifiedBars(metrics.get(metric_name).map.get(challenge_name), metric_name+challenge_name));
					}
				}
			}
		}

		fw.write("\n* Derivation tree\n");
		printDerivTree(MetricRunner.getRoot(),0,fw);
		fw.close();
		
		System.out.println("Done");
		System.exit(0);
	}

	private static String printClassifiedBars(MapOrMSet<?> map, String name) {
		StringBuilder sb = new StringBuilder();
		Set<Object> labels = new HashSet<>();
		for (MapOrMSet<?> m : map.map.values())
			labels.addAll(m.map.keySet());

		List<? extends Object> norm_idsx = MetricRunner.normalizeIndices(map.map.keySet());
		
		sb.append("m\t");
		for (Object lbl : labels) {
			sb.append(lbl+"\t");
		}
		sb.append("\n");

		for (Object c : norm_idsx) {
			sb.append(c.toString().replace("\n", " ")+"\t");
			for (Object lbl : labels) {
				MapOrMSet<?> mc = map.map.getOrDefault(c,new MapOrMSet());
				MapOrMSet<?> mv = mc.map.getOrDefault(lbl,new MapOrMSet(0));
				sb.append(mv.obj+"\t");
			}
			sb.append("\n");
		}

		return sb.toString();
	}
}
