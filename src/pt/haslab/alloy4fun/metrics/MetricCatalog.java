package pt.haslab.alloy4fun.metrics;

import static pt.haslab.alloy4fun.metrics.Execution.RESULT.*;

import java.util.Arrays;
import java.util.Set;

import edu.mit.csail.sdg.alloy4.Err;
import edu.mit.csail.sdg.alloy4.ErrorWarning;
import edu.mit.csail.sdg.ast.Decl;
import edu.mit.csail.sdg.ast.ExprBinary;
import edu.mit.csail.sdg.ast.ExprHasName;
import edu.mit.csail.sdg.ast.ExprQt;
import edu.mit.csail.sdg.ast.ExprUnary;
import edu.mit.csail.sdg.ast.ExprVar;
import pt.haslab.alloy4fun.metrics.MetricMethod.GROUPBY;
import pt.haslab.alloy4fun.metrics.MetricRunner.SessionMetrics;
import pt.haslab.alloy4fun.metrics.utils.AggregateVisitor;

@MetricSuite(description = "Example metric catalog")
public class MetricCatalog {

	// scalar metrics
	@MetricMethod(rule = "Sat executions", description = "Counts the number of satisfiable executions. Considers commands other than challenges.")
	public static Object[] correctExecs(@ForAllExecutions Execution exe) {
		if (exe.result() == SAT)
			return new Object[] { exe.result() };
		else
			return null;
	}

	@MetricMethod(rule = "Unsat executions", description = "Counts the number of unsatisfiable executions. Considers commands other than challenges.")
	public static Object[] incorrectExecs(@ForAllExecutions Execution exe) {
		if (exe.result() == UNSAT)
			return new Object[] { exe.result() };
		else
			return null;
	}

	@MetricMethod(rule = "Shared models (model)", description = "Counts the number of shared models.")
	public static Object[] sharedModels(@ForAllShares Share sha) {
		if (!sha.instance_share)
			return new Object[] { sha.instance_share };
		else
			return null;
	}

	@MetricMethod(rule = "Shared models (instance)", description = "Counts the number of models from shared instances.")
	public static Object[] sharedInstances(@ForAllShares Share sha) {
		if (sha.instance_share)
			return new Object[] { sha.instance_share };
		else
			return null;
	}

	@MetricMethod(rule = "Shared errored models", description = "Counts the number of shared models that had errors.")
	public static Object[] sharedErrors(@ForAllErrors Err err) {
		Model entry = MetricRunner.getEntry(err);
		if (entry instanceof Share)
			return new Object[] { "shared" };
		else
			return null;
	}

	@MetricMethod(rule = "Executed errored models", description = "Counts the number of executed models that had errors. Considers commands other than challenges.")
	public static Object[] execErrors(@ForAllErrors Err err) {
		Model entry = MetricRunner.getEntry(err);
		if (entry instanceof Execution)
			return new Object[] { "exec" };
		else
			return null;
	}

	@MetricMethod(rule = "Alloy runtime errors", description = "Counts the number Alloy runtime errors (in contrast to parsing errors).")
	public static Object[] runtimeErrors(@ForAllErrors Err err) {
		Model entry = MetricRunner.getEntry(err);
		if (entry instanceof Execution && ((Execution) entry).command() != null)
			return new Object[] { "error" };
		else
			return null;
	}

	@MetricMethod(rule = "Alloy static errors", description = "Counts the number Alloy parsing errors (in contrast to runtime errors).")
	public static Object[] staticErrors(@ForAllErrors Err err) {
		Model entry = MetricRunner.getEntry(err);
		if (entry instanceof Execution && ((Execution) entry).command() == null)
			return new Object[] { "error" };
		else
			return null;
	}

	@MetricMethod(rule = "Unsat executions with warnings", description = "Counts the number of unsatisfiable executions that produced warning messages. Considers commands other than the challenges.")
	public static Object[] correctWarning(@ForAllExecutions Execution exe) {
		if (exe.result() != UNSAT || exe.wns.isEmpty())
			return null;
		return new Object[] { exe.result() };
	}

	@MetricMethod(rule = "Sat executions with warnings", description = "Counts the number of satisfiable executions that produced warning messages. Considers commands other than challenges.")
	public static Object[] incorrectWarning(@ForAllExecutions Execution exe) {
		if (exe.result() != SAT || exe.wns.isEmpty())
			return null;
		return new Object[] { exe.result() };
	}

	@MetricMethod(rule = "Errored executions with warnings", description = "Counts the number of incorrect executions that produced warning messages.")
	public static Object[] erroredWarning(@ForAllExecutions Execution exe) {
		if (exe.result() != ERROR || exe.wns.isEmpty())
			return null;
		return new Object[] { exe.result() };
	}

	@MetricMethod(rule = "Shares with warnings", description = "Counts the number of incorrect executions that produced warning messages.")
	public static Object[] shareWarning(@ForAllShares Share sha) {
		if (sha.wns.isEmpty())
			return null;
		return new Object[] { "share" };
	}

	@MetricMethod(rule = "Number of shared sessions", description = "Counts the number of sessions for which an entry has been shared.")
	public static Object[] sharedSessions(@ForAllSessions Model ses) {
		SessionMetrics ms = MetricRunner.sessionMetrics(ses);
		if (ms.shared_mdls > 0 || ms.shared_insts > 0)
			return new Object[] { "shared" };
		else
			return null;
	}

	@MetricMethod(rule = "Longest session", groupby = GROUPBY.MAX, description = "The size of the longest session.")
	public static Object[] longestSession(@ForAllSessions Model ses) {
		SessionMetrics ms = MetricRunner.sessionMetrics(ses);
		return new Object[] { ms.depth };
	}

	@MetricMethod(rule = "Average session", groupby = GROUPBY.AVG, description = "The average session length.")
	public static Object[] avgSession(@ForAllSessions Model ses) {
		SessionMetrics ms = MetricRunner.sessionMetrics(ses);
		return new Object[] { ms.depth };
	}

	@MetricMethod(rule = "Average % unsatisfiable", groupby = GROUPBY.AVG, description = "The average of session unsatisfiable execution ratio.")
	public static Object[] rationCorrectSession(@ForAllSessions Model ses) {
		SessionMetrics ms = MetricRunner.sessionMetrics(ses);
		if (ms.numSatExecs() + ms.numUnsatExecs() == 0)
			return null;
		return new Object[] { Double.valueOf(ms.numUnsatExecs()) / (ms.numSatExecs() + ms.numUnsatExecs()) };
	}

	// overall metrics
	@MetricMethod(rule = "Sessions by length", description = "The number of sessions with a certain length.")
	public static Integer[] sessionLength(@ForAllSessions Model ses) {
		SessionMetrics ms = MetricRunner.sessionMetrics(ses);
		return new Integer[] { ms.depth };
	}

	@MetricMethod(rule = "Sessions by # of solved challenges", description = "The number of sessions with a certain number of challenges correctly solved.")
	public static Object[] sessionChallenge(@ForAllSessions Model ses) {
		Set<String> us = MetricRunner.sessionMetrics(ses).unsatCommands();
		us.retainAll(MetricRunner.getChallenges());
		return new Object[] { us.size() };
	}

	@MetricMethod(rule = "Errors by type", description = "The number of errors by normalized message.")
	public static Object[] errorMessages(@ForAllErrors Err err) {
		return new Object[] { MetricRunner.normUpMessages(err.msg) };
	}

	@MetricMethod(rule = "Warnings by type", description = "The number of warnings by normalized message.")
	public static Object[] warningMessages(@ForAllWarnings ErrorWarning err) {
		return new Object[] { MetricRunner.normUpMessages(err.msg) };
	}

	@MetricMethod(rule = "Models by number of warnings", description = "The number of entries with a certain number of warning messages.")
	public static Object[] numWarningsByEntry(@ForAllModels Model entry) {
		return new Object[] { entry.wns.size() };
	}

	// overall metrics classified
	@MetricMethod(rule = "Model entries over time", description = "The number of model entries by date, classified by type and result. Considers commands other than challenges.")
	public static Object[] resultsTime(@ForAllModels Model entry) {
		if (entry instanceof Share)
			return new Object[] { entry.time.toLocalDate(), "SHARE" };
		else {
			return new Object[] { entry.time.toLocalDate(), ((Execution) entry).result() };
		}
	}

	@MetricMethod(rule = "Execution results by command", description = "The number of executions by command, classified by result. Errored executions are not considered. Considers commands other than challenges.")
	public static Object[] execsChallenge(@ForAllExecutions Execution entry) {
		if (entry.result() == ERROR)
			return null;
		return new Object[] { entry.cmd_name, entry.result() };
	}

	@MetricMethod(rule = "Session results by challenge", description = "The number of sessions in which a certain challenge has been correctly solved.")
	public static String[] sessionChallenge(@ForAllSessions Model ses, @ForAllCommands String chl) {
		SessionMetrics ms = MetricRunner.sessionMetrics(ses);
		return new String[] { chl, ms.unsat_cmds.count(chl) > 0 ? "solved" : "unsolved" };
	}

	// metrics by sub-challenge classified
	@MetricMethod(rule = "Max nested quantifier level", description = "The number of executions, for a particular challenge, by the maximum nested level of first-order quantifiers, grouped by result. Each declaration (possibly with multiple variables) counts once.")
	public static Object[] quantifierDepth(@ForAllExecutions Execution entry) {
		if (!MetricRunner.isChallenge(entry.cmd_name))
			return null;
		AggregateVisitor<Integer> qnt = new AggregateVisitor<Integer>(Integer::max, 0, MetricRunner.challengPreds()) {

			@Override
			public Integer visit(ExprQt x) throws Err {
				return x.decls.size() + super.visit(x);
			}

		};
		try {
			return new Object[] { entry.cmd_name, entry.command().formula.accept(qnt), entry.result() };
		} catch (Exception e) {
			throw e;
		}
	}

	@MetricMethod(rule = "Number of quantified variables", description = "The number of executions, for a particular challenge, by the number of occurring first-order quantifiers, grouped by result. Each declared variable is counts once.")
	public static Object[] numQuants(@ForAllExecutions Execution entry) {
		if (!MetricRunner.isChallenge(entry.cmd_name))
			return null;
		AggregateVisitor<Integer> qnt = new AggregateVisitor<Integer>(Integer::sum, 0, MetricRunner.challengPreds()) {

			@Override
			public Integer visit(ExprQt x) {
				return x.count() + super.visit(x);
			}

		};

		return new Object[] { entry.cmd_name, entry.command().formula.accept(qnt), entry.result() };

	}

	@MetricMethod(rule = "Number of lone/one quantifiers", description = "The number of executions, for a particular challenge, by the number of occurring lone/one quantifiers, grouped by result. Each declared variable is counts once.")
	public static Object[] numLOneQuants(@ForAllExecutions Execution exe) {
		if (!MetricRunner.isChallenge(exe.cmd_name))
			return null;
		AggregateVisitor<Integer> qnt = new AggregateVisitor<Integer>(Integer::sum, 0, MetricRunner.challengPreds()) {

			@Override
			public Integer visit(ExprQt exp) throws Err {
				return exp.count() + super.visit(exp);
			}

		};

		return new Object[] { exe.cmd_name, exe.command().formula.accept(qnt), exe.result() };

	}

	@MetricMethod(rule = "Number multiplicity arrows", description = "The number of executions, for a particular challenge, by the number of occurring multiplicity arrow tests, grouped by result.")
	public static Object[] numMultArrows(@ForAllExecutions Execution entry) {
		if (!MetricRunner.isChallenge(entry.cmd_name))
			return null;
		AggregateVisitor<Integer> qnt = new AggregateVisitor<Integer>(Integer::sum, 0, MetricRunner.challengPreds()) {

			@Override
			public Integer visit(ExprBinary x) {
				int dlt = 0;
				switch (x.op) {
				case ANY_ARROW_LONE:
				case ANY_ARROW_ONE:
				case ANY_ARROW_SOME:
				case LONE_ARROW_ANY:
				case LONE_ARROW_LONE:
				case LONE_ARROW_ONE:
				case ONE_ARROW_ANY:
				case ONE_ARROW_LONE:
				case ONE_ARROW_ONE:
				case ONE_ARROW_SOME:
				case SOME_ARROW_ANY:
				case SOME_ARROW_LONE:
				case LONE_ARROW_SOME:
				case SOME_ARROW_ONE:
				case SOME_ARROW_SOME:
					dlt = 1;
				default:
					break;
				}
				return dlt + super.visit(x);
			}

		};

		return new Object[] { entry.cmd_name, entry.command().formula.accept(qnt), entry.result() };

	}

	@MetricMethod(rule = "Number of disj quantifications", description = "The number of executions, for a particular challenge, by the number of occurring disjoint first-order quantifications, grouped by result.")
	public static Object[] numDisjQuants(@ForAllExecutions Execution entry) {
		if (!MetricRunner.isChallenge(entry.cmd_name))
			return null;
		AggregateVisitor<Integer> qnt = new AggregateVisitor<Integer>(Integer::sum, 0, MetricRunner.challengPreds()) {

			@Override
			public Integer visit(ExprQt x) throws Err {
				int qn = 0;
				for (Decl d : x.decls)
					if (d.disjoint != null)
						qn++;
				return qn + super.visit(x);
			}

		};

		return new Object[] { entry.cmd_name, entry.command().formula.accept(qnt), entry.result() };
	}

	@MetricMethod(rule = "Max nested temporal operator level", description = "The number of executions, for a particular challenge, by the maximum nested level temporal operators, grouped by result.")
	public static Object[] nestedTemporalLevel(@ForAllExecutions Execution entry) {
		if (!MetricRunner.isChallenge(entry.cmd_name))
			return null;
		AggregateVisitor<Integer> qnt = new AggregateVisitor<Integer>(Integer::max, 0, MetricRunner.challengPreds()) {

			@Override
			public Integer visit(ExprBinary x) throws Err {
				int dlt = 0;
				if (Arrays.asList(ExprBinary.Op.SINCE, ExprBinary.Op.UNTIL, ExprBinary.Op.RELEASES,
						ExprBinary.Op.TRIGGERED).contains(x.op))
					dlt = 1;
				return dlt + super.visit(x);
			}

			@Override
			public Integer visit(ExprUnary x) throws Err {
				int dlt = 0;
				if (Arrays.asList(ExprUnary.Op.AFTER, ExprUnary.Op.ALWAYS, ExprUnary.Op.EVENTUALLY, ExprUnary.Op.BEFORE,
						ExprUnary.Op.HISTORICALLY, ExprUnary.Op.ONCE, ExprUnary.Op.PRIME).contains(x.op))
					dlt = 1;
				return dlt + super.visit(x);
			}

		};

		return new Object[] { entry.cmd_name, entry.command().formula.accept(qnt), entry.result() };

	}

	@MetricMethod(rule = "Number of binary temporal operators", description = "The number of executions, for a particular challenge, by the number of occurring binary temporal operators, grouped by result..")
	public static Object[] numBinaryTemp(@ForAllExecutions Execution entry) {
		if (!MetricRunner.isChallenge(entry.cmd_name))
			return null;
		AggregateVisitor<Integer> qnt = new AggregateVisitor<Integer>(Integer::sum, 0, MetricRunner.challengPreds()) {

			@Override
			public Integer visit(ExprBinary x) throws Err {
				int dlt = 0;
				if (Arrays.asList(ExprBinary.Op.SINCE, ExprBinary.Op.UNTIL, ExprBinary.Op.RELEASES,
						ExprBinary.Op.TRIGGERED).contains(x.op))
					dlt = 1;
				return dlt + super.visit(x);
			}

		};

		return new Object[] { entry.cmd_name, entry.command().formula.accept(qnt), entry.result() };

	}

	@MetricMethod(rule = "Number of inverted navigation", description = "The number of executions, for a particular challenge, by the number of occurring expressions of the shape \"r.x\".")
	public static Object[] invertedNav(@ForAllExecutions Execution exe) {
		if (!MetricRunner.isChallenge(exe.cmd_name))
			return null;
		AggregateVisitor<Integer> qnt = new AggregateVisitor<Integer>(Integer::sum, 0, MetricRunner.challengPreds()) {

			@Override
			public Integer visit(ExprBinary x) throws Err {
				int dlt = 0;
				if (x.op == ExprBinary.Op.JOIN)
					if (x.right.deNOP() instanceof ExprVar && x.left.deNOP() instanceof ExprHasName)
						dlt = 1;
				return dlt + super.visit(x);
			}

		};

		return new Object[] { exe.cmd_name, exe.command().formula.accept(qnt), exe.result() };
	}

	@MetricMethod(rule = "Number of reversed relation navigation", description = "The number of executions, for a particular challenge, by the number of occurring expressions of the shape \"x.~r\".")
	public static Object[] reverseNav(@ForAllExecutions Execution exe) {
		if (!MetricRunner.isChallenge(exe.cmd_name))
			return null;
		AggregateVisitor<Integer> qnt = new AggregateVisitor<Integer>(Integer::sum, 0, MetricRunner.challengPreds()) {

			@Override
			public Integer visit(ExprBinary x) throws Err {
				int dlt = 0;
				if (x.op == ExprBinary.Op.JOIN)
					if (x.left.deNOP() instanceof ExprVar && x.right.deNOP() instanceof ExprUnary
							&& ((ExprUnary) x.right.deNOP()).op == ExprUnary.Op.TRANSPOSE)
						dlt = 1;
				return dlt + super.visit(x);
			}

		};

		return new Object[] { exe.cmd_name, exe.command().formula.accept(qnt), exe.result() };
	}

	@MetricMethod(rule = "Size in 10s of nodes", description = "The number of executions, for a particular challenge, by the size of a command AST in tens of nodes, grouped by result.")
	public static Object[] nodeSize10(@ForAllExecutions Execution exe) {
		if (!MetricRunner.isChallenge(exe.cmd_name))
			return null;
		AggregateVisitor<Integer> qnt = new AggregateVisitor<Integer>((k, l) -> k + l + 1, 1,
				MetricRunner.challengPreds()) {
		};

		return new Object[] { exe.cmd_name, exe.command().formula.accept(qnt) / 10, exe.result() };
	}

}
