/**
 * Copyright (C) 2010-2018 Gordon Fraser, Andrea Arcuri and EvoSuite
 * contributors
 * <p>
 * This file is part of EvoSuite.
 * <p>
 * EvoSuite is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3.0 of the License, or
 * (at your option) any later version.
 * <p>
 * EvoSuite is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser Public License for more details.
 * <p>
 * You should have received a copy of the GNU Lesser General Public
 * License along with EvoSuite. If not, see <http://www.gnu.org/licenses/>.
 */
package org.evosuite.ga.metaheuristics;

import org.evosuite.Properties;
import org.evosuite.ga.ChromosomeFactory;
import org.evosuite.ga.ConstructionFailedException;
import org.evosuite.ga.NoveltyFunction;
import org.evosuite.testcase.TestChromosome;
import org.evosuite.utils.Randomness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static java.util.Collections.reverseOrder;
import static java.util.Comparator.comparingDouble;

public class NoveltySearch extends GeneticAlgorithm<TestChromosome> {

    private final static Logger logger = LoggerFactory.getLogger(NoveltySearch.class);
    private static final long serialVersionUID = -1047550745990198972L;

    private NoveltyFunction<TestChromosome> noveltyFunction;

    public NoveltySearch(ChromosomeFactory<TestChromosome> factory) {
        super(factory);

        noveltyFunction = null; //(NoveltyFunction<T>) new BranchNoveltyFunction();
        // setReplacementFunction(new FitnessReplacementFunction());
    }

    public void setNoveltyFunction(NoveltyFunction<TestChromosome> function) {
        this.noveltyFunction = function;
    }

    /**
     * Sort the population by novelty
     */
    protected void sortPopulation(List<TestChromosome> population, Map<TestChromosome, Double> noveltyMap) {
        // TODO: Handle case when no novelty value is stored in map
        population.sort(reverseOrder(comparingDouble(noveltyMap::get)));
    }

    /**
     * Calculate fitness for all individuals
     */
    protected void calculateNoveltyAndSortPopulation() {
        logger.debug("Calculating novelty for " + population.size() + " individuals");

        Iterator<TestChromosome> iterator = population.iterator();
        Map<TestChromosome, Double> noveltyMap = new LinkedHashMap<>();

        while (iterator.hasNext()) {
            TestChromosome c = iterator.next();
            if (isFinished()) {
                if (c.isChanged())
                    iterator.remove();
            } else {
                // TODO: This needs to take the archive into account
                double novelty = noveltyFunction.getNovelty(c, population);
                noveltyMap.put(c, novelty);
            }
        }

        // Sort population
        sortPopulation(population, noveltyMap);
    }

    @Override
    public void initializePopulation() {
        notifySearchStarted();
        currentIteration = 0;

        // Set up initial population
        generateInitialPopulation(Properties.POPULATION);

        // Determine novelty
        calculateNoveltyAndSortPopulation();
        this.notifyIteration();
    }

    @Override
    protected void evolve(int parentsNumber) {

        List<TestChromosome> newGeneration = new ArrayList<>();

        while (!isNextPopulationFull(newGeneration)) {
            List<TestChromosome> parents = new ArrayList<>();
            List<TestChromosome> offsprings = new ArrayList<>();

            for (int i = 0; i < parentsNumber; i++) {
                TestChromosome parent = selectionFunction.select(population);
                parents.add(parent);
                offsprings.add(parent.clone());
            }

            try {
                if (Randomness.nextDouble() <= Properties.CROSSOVER_RATE) {
                    crossoverFunction.crossOver(offsprings);
                }

                for (TestChromosome offspring : offsprings) {
                    notifyMutation(offspring);
                    offspring.mutate();

                    if (offspring.isChanged()) {
                        offspring.updateAge(currentIteration);
                    }
                }
            } catch (ConstructionFailedException e) {
                logger.info("CrossOver/Mutation failed.");
                continue;
            }

            for (int i = 0; i < parentsNumber; i++) {
                TestChromosome offspring = offsprings.get(i);

                if (!isTooLong(offspring))
                    newGeneration.add(offspring);
                else
                    newGeneration.add(parents.get(i));
            }
        }

        population = newGeneration;
        //archive
        updateFitnessFunctionsAndValues();
        //
        currentIteration++;
    }

    @Override
    public void generateSolution() {

        if (population.isEmpty())
            initializePopulation();

        logger.warn("Starting evolution of novelty search algorithm");

        while (!isFinished()) {
            logger.warn("Current population: " + getAge() + "/" + Properties.SEARCH_BUDGET);
            //logger.info("Best fitness: " + getBestIndividual().getFitness());

            evolve();

            // TODO: Sort by novelty
            calculateNoveltyAndSortPopulation();

            this.notifyIteration();
        }

        updateBestIndividualFromArchive();
        notifySearchFinished();

    }
}
