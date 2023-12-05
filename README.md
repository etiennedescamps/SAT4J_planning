# SAT4J Planning

## 1. Description

This library contains an automated planner class based on the PDDL4J library by 
D. Pellier and H. Fiorino (http://pddl4j.imag.fr), which solves planning
problems using the SAT4J library (http://sat4j.org).

Written by Etienne Descamps.

## 2. Dependencies

* [Java JDK](https://adoptopenjdk.net/>) version 21 or higher.

All the necessary libraries are included in the 'lib' directory.

## 3. How to use

If you already had an older version of Java downloaded on your computer, make sure
that you are using the correct one. You can check which version you are using with
the command line ```java -version```, then modify the JAVA_HOME system environment 
variable accordingly if necessary.

The files in the 'src' directory have already been compiled in the 'classes' 
directory. To execute the planner, use the following command line in the root 
directory:

```
java -cp classes;lib/* fr.uga.pddl4j.mcts.SAT4JPlannerConfiguration {domain file name} {problem file name}
```

The planner will use PDDL files located in the 'resources' directory for its 
domain and problem instanciation. Simply put the files you wish to usein it and 
use them as arguments without the .pddl extension. A simple way to test how it 
works is to use 'domain' and 'p01' as arguments.

Once the files have been successfully loaded, it will take some time for the 
planning to occur, after which a written explanation of the plan will be displayed.
The problem's predicates will then be shown at each step and for each action taken, 
with a 1 meaning the predicate is true, and 0 false. A predicate represented by _ 
does not matter for the action or objective considered.

## 4. Documentation

* SAT4J library: http://sat4j.org/r17/doc/

* PDDL4J library: http://pddl4j.imag.fr/repository/pddl4j/api/4.0.0/

* D. Pellier & H. Fiorino (2017) PDDL4J: a planning domain description library for java, Journal of Experimental & Theoretical Artificial Intelligence, 30:1, 143-176
https://doi.org/10.1080/0952813X.2017.1409278
