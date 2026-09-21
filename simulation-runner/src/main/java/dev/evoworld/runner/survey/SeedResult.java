package dev.evoworld.runner.survey;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * How one run went, as numbers.
 *
 * @param seed the run's seed
 * @param ticksRun how many ticks it actually ran
 * @param alive whether the agent was alive at the end
 * @param death what killed it and when, if it died
 * @param dangers how many times a need started doing damage
 * @param recoveries how many times health came all the way back
 * @param lowestHealth the lowest health seen at the end of any tick
 * @param finalHealth health at the end
 * @param ticksToFullHealth the first tick health was full again, for a run that
 *     started hurt; empty if it never was, or never needed to be
 * @param rests completed rests
 * @param restsForHealth rests chosen to mend rather than to recover energy
 * @param turnBacks steps that undid the step taken the tick before
 * @param ditheringRuns stretches of two or more turn-backs in a row
 * @param arrivals trips of any kind that reached where they set out for
 * @param foodTrips trips for food or water that came to an end, one way or another
 * @param foodTripsAbandoned those that ended short of where they were going
 * @param foodTripsToNothing those whose target had nothing left when the trip
 *     ended - reached, or given up once the agent could see it was bare
 * @param refusals actions the world refused, by action name
 */
public record SeedResult(
        long seed,
        long ticksRun,
        boolean alive,
        Optional<Death> death,
        int dangers,
        int recoveries,
        double lowestHealth,
        double finalHealth,
        Optional<Long> ticksToFullHealth,
        int rests,
        int restsForHealth,
        int turnBacks,
        int ditheringRuns,
        int arrivals,
        int foodTrips,
        int foodTripsAbandoned,
        int foodTripsToNothing,
        Map<String, Integer> refusals) {

    public SeedResult {
        Objects.requireNonNull(death, "death");
        Objects.requireNonNull(ticksToFullHealth, "ticksToFullHealth");
        refusals = new TreeMap<>(refusals);
    }

    public int totalRefusals() {
        return refusals.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** What ended a life, and when. */
    public record Death(String cause, long tick) {

        public Death {
            Objects.requireNonNull(cause, "cause");
        }
    }
}
