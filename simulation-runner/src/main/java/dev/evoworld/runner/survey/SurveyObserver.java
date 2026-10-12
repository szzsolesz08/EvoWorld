package dev.evoworld.runner.survey;

import dev.evoworld.core.action.ActionOutcome;
import dev.evoworld.core.action.DrinkAction;
import dev.evoworld.core.action.EatAction;
import dev.evoworld.core.action.CraftAction;
import dev.evoworld.core.action.MoveAction;
import dev.evoworld.core.action.RestAction;
import dev.evoworld.core.action.TendFireAction;
import dev.evoworld.core.action.SleepAction;
import dev.evoworld.core.agent.AgentId;
import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.decision.Decision;
import dev.evoworld.core.event.SimulationEvent;
import dev.evoworld.core.goal.ExploreGoal;
import dev.evoworld.core.goal.RecoverHealthGoal;
import dev.evoworld.core.goal.SatisfyHungerGoal;
import dev.evoworld.core.goal.SatisfyThirstGoal;
import dev.evoworld.core.observer.SimulationObserver;
import dev.evoworld.core.world.AgentSnapshot;
import dev.evoworld.core.world.Chunk;
import dev.evoworld.core.world.Location;
import dev.evoworld.core.world.WorldState;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Watches one single-agent run and keeps score.
 *
 * <p>Everything it counts arrives through the observer interface as values -
 * events, outcomes, decisions, snapshots - so measuring a run cannot change it,
 * and nothing here depends on how the console happens to word a log line.
 *
 * <p>Two definitions matter enough to state:
 *
 * <ul>
 *   <li>A <strong>turn-back</strong> is a step that undoes the step taken on the
 *       tick before: A to B, then straight back to A. Two or more in a row is a
 *       <strong>dithering run</strong>. Stepping back to a bush just passed,
 *       gathering, and carrying on has work in between and counts as neither.
 *   <li>An <strong>arrival</strong> is an intent that ends because the agent is
 *       standing on its target. A trip for food or water can also end short:
 *       replaced by another, or given up as stuck. Either way, a trip whose
 *       target had nothing left when it ended went <strong>to nothing</strong>.
 *       Usually the agent sees the bare bush a few steps out and turns away, so
 *       these trips end abandoned rather than arrived - which is why both are
 *       counted.
 * </ul>
 */
public final class SurveyObserver implements SimulationObserver {

    private final long seed;

    private long ticksRun;
    private boolean alive = true;
    private Optional<SeedResult.Death> death = Optional.empty();
    private int dangers;
    private int recoveries;
    private double lowestHealth = Needs.MAX;
    private double finalHealth = Needs.MAX;
    private boolean startedHurt;
    private Optional<Long> ticksToFullHealth = Optional.empty();
    private int rests;
    private int restsForHealth;
    private int turnBacks;
    private int ditheringRuns;
    private int arrivals;
    private int foodTrips;
    private int foodTripsAbandoned;
    private int foodTripsToNothing;
    private final Map<String, Integer> refusals = new TreeMap<>();
    private double tickHours;
    private int sleepTicks;
    private int sleeps;
    private int meals;
    private int drinks;
    // Sittings, not mouthfuls: four bites in a row is one meal, which is what
    // "meals a day" means to a reader - and what makes the number comparable
    // between runs cut into ten-minute and hour-long ticks.
    private long lastMealTick = Long.MIN_VALUE;
    private long lastDrinkTick = Long.MIN_VALUE;
    private int upkeepTicks;
    /** Ticks spent walking for water: the cost this milestone set out to cut. */
    private int waterWalkTicks;
    private int crafts;
    private int firesLit;
    private int firesFed;

    /** Which country it has been in: how much of an endless world one life covers. */
    private final Set<Chunk> chunksEntered = new LinkedHashSet<>();
    private Location twoStepsAgo;
    private Location lastPlace;
    private long lastStepTick = -1;
    private int turnsInARow;
    private Trip trip;
    private WorldState lastSeen;

    public SurveyObserver(long seed) {
        this.seed = seed;
    }

    @Override
    public void onSimulationStarted(WorldState state) {
        onStart(state);
        only(state).ifPresent(agent -> {
            startedHurt = !agent.needs().isFullyHealthy();
            lowestHealth = agent.needs().health();
            lastPlace = agent.location();
            chunksEntered.add(Chunk.containing(agent.location()));
        });
    }

    @Override
    public void onDecision(long tick, AgentId agentId, Decision chosen, List<Decision> considered) {
        // Trips are followed from the choices themselves: a trip one step long
        // starts and ends inside a single tick, and no snapshot would see it.
        if (chosen.goalName().isPresent() && chosen.target().isPresent()) {
            Trip chosenTrip = new Trip(chosen.goalName().get(), chosen.target().get());
            if (trip != null && !trip.equals(chosenTrip)) {
                end(trip, false, lastSeen);
            }
            trip = chosenTrip;
        }
        if (chosen.action().name().equals(RestAction.NAME)
                && chosen.goalName().filter(RecoverHealthGoal.NAME::equals).isPresent()) {
            restsForHealth++;
        }
        int ticks = chosen.action().durationTicks();
        if (chosen.action().name().equals(SleepAction.NAME)) {
            sleeps++;
            sleepTicks += ticks;
        }
        if (isUpkeep(chosen)) {
            upkeepTicks += ticks;
        }
        if (chosen.action().name().equals(MoveAction.NAME)
                && chosen.goalName().filter(SatisfyThirstGoal.NAME::equals).isPresent()) {
            waterWalkTicks += ticks;
        }
    }

    @Override
    public void onActionPerformed(long tick, AgentId agentId, ActionOutcome outcome) {
        if (outcome.succeeded() && outcome.actionName().equals(RestAction.NAME)) {
            rests++;
        }
        if (outcome.succeeded() && outcome.actionName().equals(CraftAction.NAME)) {
            crafts++;
        }
        if (outcome.succeeded() && outcome.actionName().equals(TendFireAction.NAME)) {
            if (outcome.detail().startsWith("lit")) {
                firesLit++;
            } else {
                firesFed++;
            }
        }
        if (outcome.succeeded() && outcome.actionName().equals(EatAction.NAME)) {
            if (tick != lastMealTick + 1) {
                meals++;
            }
            lastMealTick = tick;
        }
        if (outcome.succeeded() && outcome.actionName().equals(DrinkAction.NAME)) {
            if (tick != lastDrinkTick + 1) {
                drinks++;
            }
            lastDrinkTick = tick;
        }
        if (outcome.wasBlocked() || outcome.wasImpossible()) {
            refusals.merge(outcome.actionName(), 1, Integer::sum);
        }
    }

    @Override
    public void onEvent(SimulationEvent event) {
        switch (event) {
            case SimulationEvent.DangerBegan danger -> dangers++;
            case SimulationEvent.Recovered recovered -> recoveries++;
            case SimulationEvent.Died died -> {
                alive = false;
                death = Optional.of(new SeedResult.Death(died.cause(), died.tick()));
            }
            default -> { }
        }
    }

    @Override
    public void onTickCompleted(WorldState state) {
        ticksRun = state.tick();
        if (tickHours == 0.0) {
            onStart(state);
        }
        only(state).ifPresent(agent -> {
            follow(agent, state.tick());
            if (trip != null && agent.location().equals(trip.target())) {
                end(trip, true, state);
                trip = null;
            } else if (trip != null && agent.alive() && agent.intent().isEmpty()) {
                // Given up as stuck, without anything else chosen in its place.
                end(trip, false, state);
                trip = null;
            }
            Needs needs = agent.needs();
            lowestHealth = Math.min(lowestHealth, needs.health());
            finalHealth = needs.health();
            if (startedHurt && ticksToFullHealth.isEmpty() && needs.isFullyHealthy()) {
                ticksToFullHealth = Optional.of(state.tick());
            }
        });
        lastSeen = state;
    }

    private void follow(AgentSnapshot agent, long tick) {
        Location here = agent.location();
        chunksEntered.add(Chunk.containing(here));
        if (here.equals(lastPlace)) {
            return;
        }
        boolean turnedBack = here.equals(twoStepsAgo) && lastStepTick == tick - 1;
        if (turnedBack) {
            turnBacks++;
            turnsInARow++;
            if (turnsInARow == 2) {
                ditheringRuns++;
            }
        } else {
            turnsInARow = 0;
        }
        twoStepsAgo = lastPlace;
        lastPlace = here;
        lastStepTick = tick;
    }

    /** A trip came to an end: reached, or given up for something else. */
    private void end(Trip ended, boolean reached, WorldState world) {
        if (reached) {
            arrivals++;
        }
        if (ended.goal().equals(ExploreGoal.NAME)) {
            return;
        }
        foodTrips++;
        if (!reached) {
            foodTripsAbandoned++;
        }
        boolean somethingThere = world != null && world.resourceAt(ended.target())
                .filter(node -> node.isAvailable())
                .isPresent();
        if (!somethingThere) {
            foodTripsToNothing++;
        }
    }

    /** Somewhere the agent set out for, and why. */
    private record Trip(String goal, Location target) {
    }

    /**
     * Whether a choice was the body's business rather than the agent's own:
     * sleep, rest, a meal or a drink, and gathering or walking for food and
     * water. Exploring, and experiments made out of curiosity, are free time.
     */
    private static boolean isUpkeep(Decision chosen) {
        String action = chosen.action().name();
        if (action.equals(SleepAction.NAME) || action.equals(RestAction.NAME)
                || action.equals(EatAction.NAME) || action.equals(DrinkAction.NAME)) {
            return true;
        }
        return chosen.goalName()
                .filter(goal -> goal.equals(SatisfyHungerGoal.NAME) || goal.equals(SatisfyThirstGoal.NAME))
                .isPresent();
    }

    private void onStart(WorldState state) {
        long tick = state.tick();
        // The tick length, read off the clock once a tick has passed; the start
        // of a fresh run has none, so the next snapshot supplies it.
        if (tick > 0) {
            tickHours = state.time().elapsed().getSeconds() / 3600.0 / tick;
        }
    }

    /** The one agent a survey run has. */
    private static Optional<AgentSnapshot> only(WorldState state) {
        return state.agents().stream().findFirst();
    }

    public SeedResult result() {
        return new SeedResult(
                seed,
                ticksRun,
                alive,
                death,
                dangers,
                recoveries,
                lowestHealth,
                finalHealth,
                ticksToFullHealth,
                rests,
                restsForHealth,
                turnBacks,
                ditheringRuns,
                arrivals,
                foodTrips,
                foodTripsAbandoned,
                foodTripsToNothing,
                refusals,
                ticksRun * tickHours,
                sleepTicks * tickHours,
                sleeps,
                meals,
                drinks,
                upkeepTicks * tickHours,
                chunksEntered.size(),
                waterWalkTicks * tickHours,
                crafts,
                firesLit,
                firesFed);
    }
}
