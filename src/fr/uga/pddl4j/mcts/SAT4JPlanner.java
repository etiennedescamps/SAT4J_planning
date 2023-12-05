package fr.uga.pddl4j.mcts;

import fr.uga.pddl4j.parser.DefaultParsedProblem;
import fr.uga.pddl4j.plan.Plan;
import fr.uga.pddl4j.plan.SequentialPlan;
import fr.uga.pddl4j.planners.AbstractPlanner;
import fr.uga.pddl4j.problem.*;
import fr.uga.pddl4j.problem.operator.AbstractFluentDescription;
import fr.uga.pddl4j.problem.operator.Action;
import fr.uga.pddl4j.util.BitSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sat4j.minisat.SolverFactory;
import org.sat4j.reader.LecteurDimacs;
import org.sat4j.reader.ParseFormatException;
import org.sat4j.specs.ContradictionException;
import org.sat4j.specs.IProblem;
import org.sat4j.specs.TimeoutException;
import picocli.CommandLine;

import java.io.*;
import java.util.*;

/**
 * This class uses the SAT4J library to solve planning problems.
 *
 * @author E. Descamps
 * @version 1.0 - 05.12.2023
 */
@CommandLine.Command(name = "MCTSPlanner",
        version = "MCTSPlanner 1.0",
        description = "Solves a specified planning problem using the SAT4J library.",
        sortOptions = false,
        mixinStandardHelpOptions = true,
        headerHeading = "Usage:%n",
        synopsisHeading = "%n",
        descriptionHeading = "%nDescription:%n%n",
        parameterListHeading = "%nParameters:%n",
        optionListHeading = "%nOptions:%n")
public class SAT4JPlanner extends AbstractPlanner {

    /**
     * The class logger.
     */
    private static final Logger LOGGER = LogManager.getLogger(SAT4JPlanner.class.getName());

    /**
     * Instantiates the planning problem from a parsed problem.
     *
     * @param problem the problem to instantiate.
     * @return the instantiated planning problem or null if the problem cannot be instantiated.
     */
    @Override
    public Problem instantiate(DefaultParsedProblem problem) {
        final Problem pb = new DefaultProblem(problem);
        pb.instantiate();
        return pb;
    }

    /**
     * Search a solution plan to a specified domain and problem using A*.
     *
     * @param problem the problem to solve.
     * @return the plan found or null if no plan was found.
     */
    @Override
    public Plan solve(final Problem problem) {
        List<Fluent> problem_fluents = problem.getFluents();
        List<Action> problem_actions = problem.getActions();
        int total_variables_per_step = problem_fluents.size() + problem_actions.size();

        for (int search_range = 2; search_range <= 30; search_range++) {    // TODO: change that someday
            System.out.println("\nAttempting to find a plan of max length " + search_range + "...");

            // The content of the CNF file to be sent to SAT4J.
            StringBuilder cnf_text = new StringBuilder();

            // The number of formulae written in the CNF file.
            int total_formulae = 0;

            // The value of the newest variable written in the CNF file ; a fortiori also usable as a total.
            int index = 0;

            // This map links an action with all the values its variable can assume over the entire search.
            Map<Action, List<Integer>> actions = new HashMap<>();

            // Same as the action map, but for fluents. The keys are their position in the fluent list, for convenience.
            Map<Integer, List<Integer>> fluents = new HashMap<>();

            for (int s = 0; s <= search_range; s++){
                for (Fluent fluent : problem_fluents) {
                    index++;
                    int fluent_value = problem.getFluents().indexOf(fluent) + 1;
                    fluents.putIfAbsent(fluent_value, new ArrayList<>());
                    fluents.get(fluent_value).add(index);
                }

                // The last step has no next step to transition to, so there is no need to define actions for it.
                if (s != search_range) {
                    for (Action action : problem_actions) {
                        index++;
                        actions.putIfAbsent(action, new ArrayList<>());
                        actions.get(action).add(index);
                    }
                }
            }

            // Initial state and goal state

            List<Integer> fluents_init = get_fluents(problem.getInitialState());
            for (int fluent : fluents.keySet()) {
                if (fluents_init.contains(fluent)) {
                    cnf_text.append(fluent);
                } else {
                    cnf_text.append(-fluent);
                }
                cnf_text.append(" 0\n");
                total_formulae++;
            }
            for (int fluent : get_fluents(problem.getGoal())) {
                cnf_text.append(fluent + index - problem_fluents.size());
                cnf_text.append(" 0\n");
                total_formulae++;
            }

            // Actions

            for (Action action : actions.keySet()) {
                for (int step = 0; step < actions.get(action).size(); step++) {
                    List<Integer> preconditions = get_fluents(action.getPrecondition());
                    List<Integer> effects = get_fluents(action.getUnconditionalEffect());
                    List<Integer> components = new ArrayList<>();

                    for (int fluent : fluents.keySet()) {

                        // Using iterators speeds up the loop by removing the fluents that have been accounted for.
                        Iterator<Integer> iterator_p = preconditions.iterator();
                        Iterator<Integer> iterator_e = effects.iterator();
                        while (iterator_p.hasNext()) {
                            int precondition = iterator_p.next();
                            if (precondition == fluent) {
                                components.add(fluents.get(fluent).get(step));
                                iterator_p.remove();
                            } else if (precondition == -fluent) {
                                components.add(-fluents.get(fluent).get(step));
                                iterator_p.remove();
                            }
                        }
                        while (iterator_e.hasNext()) {
                            int effect = iterator_e.next();
                            if (effect == fluent) {
                                components.add(fluents.get(fluent).get(step + 1));
                                iterator_e.remove();
                            } else if (effect == -fluent) {
                                components.add(-fluents.get(fluent).get(step + 1));
                                iterator_e.remove();
                            }
                        }

                        // We end the loop early once all the fluents have been accounted for.
                        if (preconditions.isEmpty() & effects.isEmpty()) {
                            break;
                        }
                    }
                    for (int component : components) {
                        cnf_text.append(-actions.get(action).get(step));
                        cnf_text.append(" ");
                        cnf_text.append(component);
                        cnf_text.append(" 0\n");
                        total_formulae++;
                    }
                }

            }

            // State transitions

            for (int fluent : fluents.keySet()) {
                List<Integer> fluent_values = fluents.get(fluent);

                // The lists of actions that have the given fluent as a positive/negative effect, respectively.
                List<Action> transitions_pos = new ArrayList<>();
                List<Action> transitions_neg = new ArrayList<>();

                for (Action action : actions.keySet()) {
                    List<Integer> effects = get_fluents(action.getUnconditionalEffect());
                    if (effects.contains(fluent)) {
                        transitions_pos.add(action);
                    }
                    if (effects.contains(-fluent)) {
                        transitions_neg.add(action);
                    }
                }
                for (int step = 0; step < fluent_values.size() - 1; step++) {
                    cnf_text.append(fluent_values.get(step));
                    cnf_text.append(" ");
                    cnf_text.append(-fluent_values.get(step + 1));
                    for (Action transition : transitions_pos) {
                        cnf_text.append(" ");
                        cnf_text.append(actions.get(transition).get(step));
                    }
                    cnf_text.append(" 0\n");
                    total_formulae++;

                    cnf_text.append(-fluent_values.get(step));
                    cnf_text.append(" ");
                    cnf_text.append(fluent_values.get(step + 1));
                    for (Action transition : transitions_neg) {
                        cnf_text.append(" ");
                        cnf_text.append(actions.get(transition).get(step));
                    }
                    cnf_text.append(" 0\n");
                    total_formulae++;
                }
            }

            // Action disjunctions

            for (int s = 0; s < search_range; s++){

                // These specific index ranges correspond to the value ranges used for the actions at step s.
                // There is obviously no need to make disjunctions between actions that belong to different steps.
                for (
                        int index1 = s * total_variables_per_step + fluents.size() + 1;
                        index1 <= (s + 1) * total_variables_per_step;
                        index1++
                ) {
                    for (
                            int index2 = s * total_variables_per_step + fluents.size() + 2;
                            index2 <= (s + 1) * total_variables_per_step;
                            index2++
                    ) {
                        if (index2 > index1) {
                            cnf_text.append(-index1);
                            cnf_text.append(" ");
                            cnf_text.append(-index2);
                            cnf_text.append(" 0\n");
                            total_formulae++;
                        }
                    }
                }
            }

            // Finishing the CNF file, sending it to SAT4J and displaying the results

            StringBuilder header = new StringBuilder();
            header.append("c The problem described by this file contains ");
            header.append(total_variables_per_step);
            header.append(" variables (");
            header.append(fluents.size());
            header.append(" fluents, ");
            header.append(actions.size());
            header.append(" actions).\n");
            header.append("c These variables were described over ");
            header.append(search_range);
            header.append(" steps in order to find a plan that satisfies all the formulae listed below.\n");
            header.append("c All variables that are equal with a modulo ");
            header.append(total_variables_per_step);
            header.append(" represent the same action or fluent at different steps.\n");
            header.append("p cnf ");
            header.append(index);
            header.append(" ");
            header.append(total_formulae);
            header.append("\n");
            cnf_text.insert(0, header);

            try {
                FileWriter writer = new FileWriter("problem.cnf");
                writer.write(cnf_text.toString());
                writer.close();

                // The newly-written CNF file is sent to a SAT4J solver for planning.
                LecteurDimacs solver = new LecteurDimacs(SolverFactory.newDefault());
                IProblem parsed_problem = solver.parseInstance("problem.cnf");
                if (parsed_problem.isSatisfiable()) {
                    System.out.println("Plan found! It goes as follows:\n");

                    // The plan to be returned by the method.
                    Plan plan = new SequentialPlan();

                    // The order indicator for the actions within the plan.
                    int order = 0;

                    // The array of all variables specified in the CNF file. The plan is defined by their signs.
                    int[] model = parsed_problem.model();

                    // The printed explanation of the objective of the plan.
                    // A 1 means the predicate associated to the fluent at the given position is true.
                    // A 0 means the predicate is false.
                    // A _ means the predicate doesn't matter.
                    StringBuilder goal = new StringBuilder();
                    List<Integer> goal_fluents = get_fluents(problem.getGoal());
                    for (int fluent : fluents.keySet()) {
                        if (goal_fluents.contains(fluent)) {
                            goal.append("1 ");
                        } else if (goal_fluents.contains(-fluent)) {
                            goal.append("0 ");
                        } else {
                            goal.append("_ ");
                        }
                    }
                    goal.append("(objective)\n\n");

                    for (int s = 0; s <= search_range; s++) {

                        // The printed explanation of the current state of the predicates.
                        // After each step of the plan, it is compared to the objective for reference (and debugging).
                        StringBuilder state = new StringBuilder();
                        for (int i = 0; i < fluents.size(); i++) {
                            if (model[s * total_variables_per_step + i] > 0) {
                                state.append("1 ");
                            } else {
                                state.append("0 ");
                            }
                        }
                        state.append("(state ");
                        state.append(s);
                        state.append(")");
                        System.out.println(state);
                        System.out.println(goal);
                        if (s != search_range) {

                            // The printed explanation of the preconditions and effects of the next action in the plan.
                            StringBuilder transition = new StringBuilder();
                            boolean is_action = false;
                            transition.append("Taking action: ");
                            for (int i = 0; i < actions.size(); i++) {
                                if (model[s * total_variables_per_step + fluents.size() + i] > 0) {
                                    transition.append(i + 1);
                                    transition.append("\n");

                                    Action action = problem_actions.get(i);
                                    plan.add(order, action);
                                    order++;

                                    List<Integer> preconditions = get_fluents(action.getPrecondition());
                                    for (int fluent : fluents.keySet()) {
                                        if (preconditions.contains(fluent)) {
                                            transition.append("1 ");
                                        } else if (preconditions.contains(-fluent)) {
                                            transition.append("0 ");
                                        } else {
                                            transition.append("_ ");
                                        }
                                    }
                                    transition.append("(preconditions)\n");

                                    List<Integer> effects = get_fluents(action.getUnconditionalEffect());
                                    for (int fluent : fluents.keySet()) {
                                        if (effects.contains(fluent)) {
                                            transition.append("1 ");
                                        } else if (effects.contains(-fluent)) {
                                            transition.append("0 ");
                                        } else {
                                            transition.append("_ ");
                                        }
                                    }
                                    transition.append("(effects)\n\n");
                                    is_action = true;
                                    break;
                                }
                            }

                            // Technically, it is possible for a step to have no action associated (in case the search
                            // went deeper than what the plan needed, for example). This statement handles those cases.
                            if (!is_action) {
                                transition.append("none\n\n");
                            }
                            System.out.println(transition);
                        }
                    }
                    return plan;
                }
            } catch (IOException | ParseFormatException | TimeoutException e) {
                throw new RuntimeException(e);

            // ContradictionExceptions can happen on early values of depth, but we still need the search to carry on.
            } catch (ContradictionException e) {
                System.out.print("");
            }
        }
        System.out.println("Could not find a valid plan within the chosen search range.");
        return null;
    }

    /**
     * Fine as it is so far, but definitely change it if the print shows up somehow.
     *
     * @param problem the problem to check for support.
     * @return whether the problem is supported by the planner.
     */
    @Override
    public boolean isSupported(Problem problem) {
        System.out.println("That one method was used somewhere at some point.");
        return true;
    }

    /**
     * The main method of the <code>Sat4JPlanner</code> planner.
     *
     * @param args the arguments of the command line.
     */
    public static void main(String[] args) {
        try {
            final SAT4JPlanner planner = new SAT4JPlanner();
            CommandLine cmd = new CommandLine(planner);
            cmd.execute(args);
        } catch (IllegalArgumentException e) {
            LOGGER.fatal(e.getMessage());
        }
    }

    private List<Integer> get_fluents(AbstractFluentDescription description) {
        List<Integer> fluents = new ArrayList<>();
        BitSet fluents_pos = description.getPositiveFluents();
        for(int i = fluents_pos.nextSetBit(0); i >= 0; i = fluents_pos.nextSetBit(i + 1)) {
            fluents.add(i + 1);
        }
        BitSet fluents_neg = description.getNegativeFluents();
        for(int i = fluents_neg.nextSetBit(0); i >= 0; i = fluents_neg.nextSetBit(i + 1)) {
            fluents.add(-i - 1);
        }
        return fluents;
    }
}
