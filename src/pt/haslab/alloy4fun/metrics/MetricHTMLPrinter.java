package pt.haslab.alloy4fun.metrics;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.json.JSONException;

import pt.haslab.alloy4fun.metrics.Execution.RESULT;
import pt.haslab.alloy4fun.metrics.MetricRunner.MapOrMSet;

public class MetricHTMLPrinter {
	
	private final static Map<String,String> colors = Stream.of(new String[][] {
		  { "unsolved", "#f0e878" }, 
		  { RESULT.SAT.toString(), "#f0e878" }, 
		  { "solved", "#93e388" }, 
		  { RESULT.UNSAT.toString(), "#93e388" }, 
		  { RESULT.ERROR.toString(), "#f06779" }, 
		  { "warning", "ffffff" }, 
		  { "model share", "#6c79ad" }, 
		  { "instance share", "#6179ad" }, 
		}).collect(Collectors.toMap(data -> data[0], data -> data[1]));

	
	private static void printDerivTree(Model obj, int indent, FileWriter fw) throws IOException {
		 
		String cls;
		if (obj instanceof Execution) {
			if (((Execution) obj).result() == RESULT.ERROR)
				cls = "list-group-item-danger";
			else if (((Execution) obj).result() == RESULT.UNSAT)
				cls = "list-group-item-success";
			else
				cls = "list-group-item-warning";
		}
		else
			cls = "list-group-item-info";
		
		String ico = obj.children().isEmpty()?"glyphicon-minus":"glyphicon-chevron-right";
		
		fw.write("<a href=\"#item-"+obj.id+"\" class=\"more list-group-item "+cls+"\" data-toggle=\"collapse\" style=\"padding-left:"+indent*10+"px\">\n" + 
				"    <i id=\"icon-"+obj.id+"\" class=\"glyphicon "+ico+"\"></i>"+printShortObj(obj)+"</a>\n" + 
						"  <div class=\"list-group collapse show\" id=\"item-"+obj.id+"\">");
		for (Model entry : obj.children()) {
			printDerivTree(entry, indent+1, fw);
		}
		fw.write("</div>\n");
		
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

		FileWriter fw = new FileWriter(original_id+".html");
		
		fw.write("<head>\n" + 
				"  <meta charset=\"UTF-8\">\n" + 
				"  <meta name=\"description\" content=\"Free Web tutorials\">\n" + 
				"  <meta name=\"keywords\" content=\"HTML, CSS, JavaScript\">\n" + 
				"  <meta name=\"author\" content=\"John Doe\">\n" + 
				"  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
				"  <link rel=\"stylesheet\" href=\"https://maxcdn.bootstrapcdn.com/bootstrap/3.4.1/css/bootstrap.min.css\">\n" + 
				"  <link rel=\"stylesheet\" href=\"metrics.css\">\n" +
				"</head>\n" +
				"<body>\n" +
				"<script src=\"https://cdnjs.cloudflare.com/ajax/libs/Chart.js/2.6.0/Chart.min.js\"></script>\n");
		
		fw.write("<h1>"+original_id+" ("+LocalDate.now()+")</h1>\n");
		
		fw.write("<h2>Meta-data</h2>\n");
		fw.write("<dl>");		
		fw.write("<dt>Challenge name</dt><dd>"+MetricRunner.getModuleName()+"</dd>\n");
		fw.write("<dt>Creation time</dt><dd>"+MetricRunner.getCreationDate()+"</dd>\n");
		fw.write("<dt># sub-challenges</dt><dd>"+MetricRunner.getChallenges().size()+"</dd>\n");
		fw.write("<dt>Metric catalog</dt><dd>"+MetricRunner.getCatalogName()+"</dd>\n");
		fw.write("</dl>");		
		fw.write("<h2>Overall metric statistics</h2>\n");
		fw.write("Overall statistics for this challenge. Note that for challenges correctness is unsatisfiability. Totals may contain executions of commands other than the sub-challenges, if declared by the user.");
		fw.write("<dl>");		
		fw.write("<dt>Total model entries</dt><dd>"+MetricRunner.getTotalModels()+"</dd>\n");
		fw.write("<dt>Total executions</dt><dd>"+MetricRunner.getTotalExecs()+"</dd>\n");
		fw.write("<dt>Total shares</dt><dd>"+MetricRunner.getTotalShares()+"</dd>\n");
		fw.write("<dt>Total error messages</dt><dd>"+MetricRunner.getTotalErrors()+"</dd>\n");
		fw.write("<dt>Total warning messages</dt><dd>"+MetricRunner.getTotalWithWarnings()+"</dd>\n");
		fw.write("<dt>Total solutions</dt><dd>"+MetricRunner.getTotalSolutions()+"</d>\n");
		fw.write("<dt>Total sessions</dt><dd>"+MetricRunner.getTotalSessions()+"</d>\n");
		fw.write("<dt>Timed out re-executions</dt><dd>"+MetricRunner.getTotalTimeouts()+"</d>\n");
		fw.write("<dt>Rejected due to server errors</dt><dd>"+MetricRunner.getTotalServerErrors()+"</d>\n");
		fw.write("<dt>Inconsistent result reports</dt><dd>"+MetricRunner.getTotalInconsistentRes()+"</d>\n");
		fw.write("<dt>Inconsistent message reports</dt><dd>"+MetricRunner.getTotalInconsistentMsg()+"</d>\n");
		fw.write("<dt>Total instance entries</dt><dd>"+MetricRunner.getTotalInstances()+"</dd>\n");
		fw.write("<dt>Total link entries</dt><dd>"+MetricRunner.getTotalLinks()+"</dd>\n");
		fw.write("</dl>");		
		
		Map<String,MapOrMSet<?>> metrics =  MetricRunner.aggregatedMetrics();
		
		fw.write("<h2>Scalar metrics</h2>\n");
		fw.write("<dl>");		
		for (String metric_name : metrics.keySet())
			if (metrics.get(metric_name).map.size() == 1 && metrics.get(metric_name).level() == 1)
				fw.write("<dt data-toggle=\"tooltip\" title=\""+MetricRunner.getMetricDesc(metric_name)+"\">"+metric_name+"</dt><dd>"+metrics.get(metric_name).map.values().iterator().next().obj+"</dd>\n");
		fw.write("</dl>");		

		fw.write("<h2>Overall metrics</h2>\n");
		for (String metric_name : metrics.keySet())
			if (metrics.get(metric_name).map.size() > 1 && metrics.get(metric_name).level() == 1) {
				fw.write("<h3>"+metric_name+"</h3>\n");
				fw.write("<p>"+MetricRunner.getMetricDesc(metric_name)+"</p>\n");

				fw.write("<canvas id=\""+metric_name.replaceAll("[^A-Za-z]", "")+"\" width=\"1400\"></canvas>");
				fw.write("<script>\n" + 
						"  var chart = new Chart("+metric_name.replaceAll("[^A-Za-z]", "")+", {\n" + 
						"   type: 'bar',\n" + 
						"   data: {\n" + 
						"      labels: [");
				
				List<? extends Object> norm_idsx = MetricRunner.normalizeIndices(metrics.get(metric_name).map.keySet());
				for (Object c : norm_idsx)
					fw.write("'"+c.toString().replace("\n", " ").replace("\\", "\\\\").replace("'", "\\'")+"',");

				fw.write("]," + 
						"      datasets: [\n");
				
				fw.write("{backgroundColor: '#6c79ad', data: [");
				
				int max_len = 0;
				for (Object c : norm_idsx) {
					max_len = Math.max(max_len, c.toString().length());
					fw.write(metrics.get(metric_name).map.getOrDefault(c,new MapOrMSet()).obj+",");
				}

				fw.write("]},");

				fw.write("]");
					
					fw.write("},\n" + 
					"   options: {\n" + 
					"      responsive: false,\n" + 
					"      legend: {\n" + 
					"         display: false\n" + 
					"      },\n" + 
					"      scales: {\n" + 
					"         xAxes: [{\n" + 
					"                ticks: {\n" + 
					"                    autoSkip: "+(max_len>20?false:true)+",\n" + 
					"                    minRotation:"+ (max_len>20?90:0) +",\n" + 
					"                    maxRotation:"+ (max_len>20?90:20) +",\n" + 
					"                }\n" + 
					"            }],\n" + 
					"      }\n" + 
					"   }\n" + 
					"});\n" + 
					"</script>");
			
			}		
		
		fw.write("<h2>Overall metrics, classified</h2>\n");
		for (String metric_name : metrics.keySet()) {
			if (metrics.get(metric_name).map.size() > 0 && metrics.get(metric_name).level() == 2) {
				fw.write("<h3>"+metric_name+"</h3>\n");
				fw.write("<p>"+MetricRunner.getMetricDesc(metric_name)+"</p>\n");
				fw.write(printClassifiedBars(metrics.get(metric_name), metric_name));
			}
		}
		
		fw.write("<h2>Metrics by sub-challenge, classified</h2>\n");
		for (String metric_name : metrics.keySet()) {
			if (metrics.get(metric_name).map.size() > 0 && metrics.get(metric_name).level() == 3) {
				fw.write("<h3>"+metric_name+"</h3>\n");
				fw.write("<p>"+MetricRunner.getMetricDesc(metric_name.toString())+"</p>\n");
				List<? extends Object> norm_idsx = MetricRunner.normalizeIndices(metrics.get(metric_name).map.keySet());
				for (Object challenge_name : norm_idsx) {
					try {
						if (metrics.get(metric_name).map.get(challenge_name).map.size() > 0) {
							fw.write("<h4>"+challenge_name+"</h4>\n");
							fw.write(printClassifiedBars(metrics.get(metric_name).map.get(challenge_name), metric_name+challenge_name));
						}
					} catch(Exception e) {
						
					}
				}
			}
		}

		fw.write("<h2>Derivation tree</h2>\n" +
		"<div class=\"just-padding\">\n" + 
		"<div class=\"list-group list-group-root well\">\n");
		printDerivTree(MetricRunner.getRoot(),0,fw);
		fw.write("</div>\n" + 
				"</div>");

		fw.write(
				"<script src=\"https://code.jquery.com/jquery-3.3.1.slim.min.js\" integrity=\"sha384-q8i/X+965DzO0rT7abK41JStQIAqVgRVzpbzo5smXKp4YfRvH+8abtTE1Pi6jizo\" crossorigin=\"anonymous\"></script>\n"
						+ "<script src=\"https://cdnjs.cloudflare.com/ajax/libs/popper.js/1.14.7/umd/popper.min.js\" integrity=\"sha384-UO2eT0CpHqdSJQ6hJty5KVphtPhzWj9WO1clHTMGa3JDZwrnQq4sF86dIHNDz0W1\" crossorigin=\"anonymous\"></script>\n"
						+ "<script src=\"https://stackpath.bootstrapcdn.com/bootstrap/4.3.1/js/bootstrap.min.js\" integrity=\"sha384-JjSmVgyd0p3pXB1rRibZUAYoIIy6OrQ6VrjIEaFf/nJGzIxFDsf4x0xIM+B07jRM\" crossorigin=\"anonymous\"></script>\n");

		fw.write("<script>\n" + 
				"  $(\".more\").click(function() {  \n" + 
				"  \n" + 
				"  icon = $(this).find(\"i\");\n" + 
				"  icon.toggleClass(\"glyphicon-chevron-right glyphicon-chevron-up\");\n" + 
				"  });\n" + 
				"</script>\n" + 
				"");

		fw.write("</body>");
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
		
		sb.append("<canvas id=\""+name.replaceAll("[^A-Za-z0-9]", "")+"\" ></canvas>");
		sb.append("<script>\n" + 
				"  var chart = new Chart("+name.replaceAll("[^A-Za-z0-9]", "")+", {\n" + 
				"   type: 'bar',\n" + 
				"   indexLabel: '#percent%',\n"+
				"   data: {\n" + 
				"      labels: [");
		
		for (Object c : norm_idsx)
			sb.append("'"+c+"',");
		
		sb.append("]," + 
				"      datasets: [\n");

		for (Object lbl : labels) {
			sb.append("{label: '"+lbl+"', backgroundColor: '"+colors.get(lbl.toString())+"', data: [");
		
			for (Object c : norm_idsx) {
				MapOrMSet<?> mc = map.map.getOrDefault(c,new MapOrMSet());
				MapOrMSet<?> mv = mc.map.getOrDefault(lbl,new MapOrMSet());
				sb.append(mv.obj+",");
			}

			sb.append("]},");
		}

		sb.append("]");
			
		sb.append("},\n" + 
			"   options: {\n" + 
			"	tooltips: {\n" + 
			"      enabled: true,\n" + 
			"      mode: 'single',\n" + 
			"      callbacks: {\n" + 
			"        label: function(value, context) {\n" + 
			"          if (value == 0) return '';\n" + 
			"          let dataArr = context.datasets;\n" + 
			"          let sum = 0;\n" + 
			"          dataArr.map(data => {\n" + 
			"              sum += data.data[value.index];\n" + 
			"          });\n" + 
			"          return context.datasets[value.datasetIndex].label +\": \"+value.yLabel +' ('+Math.round(1000 / sum * value.yLabel) / 10 + '%)';\n" + 
			"        }\n" + 
			"      }\n" + 
			"    },"+
			"      responsive: true,\n" + 
			"	   maintainAspectRatio: false,\n" + 
			"      legend: {\n" + 
			"         position: 'right'\n" + 
			"      },\n" + 
			"      scales: {\n" + 
			"         xAxes: [{\n" + 
			"            maxBarThickness: 100, stacked: true\n" + 
			"         }],\n" + 
			"         yAxes: [{\n" + 
			"            beginAtZero:true, stacked: true\n" + 
			"         }]\n" + 
			"      }\n" + 
			"   }\n" + 
			"});\n" + 
			"</script>");	
		
		return sb.toString();
	}
}
