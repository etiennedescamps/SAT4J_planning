package fr.uga.pddl4j.mcts;

import fr.uga.pddl4j.planners.InvalidConfigurationException;
import fr.uga.pddl4j.planners.LogLevel;

import java.io.File;

public class SAT4JPlannerConfiguration {


    /**
     * The main method of the class.
     *
     * @param args the command line arguments. No argument is used.
     */
    public static void main(String[] args) {

        // The path to the resources directory
        final String path_resources = new File("").getAbsolutePath() + File.separator + "resources" + File.separator;

        // Creates the planner
        SAT4JPlanner planner = new SAT4JPlanner();
        // Sets the domain of the problem to solve
        planner.setDomain(path_resources + "domain.pddl");
        // Sets the problem to solve
        planner.setProblem(path_resources + "p01.pddl");
        // Sets the timeout of the search in seconds
        planner.setTimeout(1000);
        // Sets log level
        planner.setLogLevel(LogLevel.INFO);
        // Selects the heuristic to use
//        planner.setHeuristic(StateHeuristic.Name.MAX);
        // Sets the weight of the heuristic
//        planner.setHeuristicWeight(1.2);

        // Solve and print the result
        try {
            planner.solve();
        } catch (InvalidConfigurationException e) {
            e.printStackTrace();
        }

    }
}
