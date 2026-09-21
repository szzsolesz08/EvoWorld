package dev.evoworld.core.decision;

import dev.evoworld.core.goal.Motivation;
import dev.evoworld.core.perception.Perception;
import dev.evoworld.core.random.SimulationRandom;

/**
 * Turns perception into a chosen action.
 *
 * <p>Deliberation is a single step that yields both the candidates and the
 * winner, so scoring happens exactly once per tick. This is the seam where
 * goals, memory and learning will plug in without the rest of the simulation
 * changing.
 *
 * <p>The only ways in are a {@link Perception}, already filtered to what the
 * agent can sense, and a {@link Motivation}, already weighed. A decision maker
 * has no route to the world and no say in what the agent wants: it answers one
 * question, which is what to do about it.
 *
 * <p>Randomness is borrowed, not owned: the simulation passes in its one
 * stream. A decision maker that kept a private generator would reproduce on its
 * own but drift from the rest of the run, and a saved run could not restore it.
 */
public interface DecisionMaker {

    /**
     * @param perception what the agent can tell about the world
     * @param motivation what it wants, already weighed
     * @param random the run's stream, for anything that needs a choice made for it
     */
    Deliberation deliberate(
            Perception perception, Motivation motivation, SimulationRandom random);
}
