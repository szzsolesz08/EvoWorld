package dev.evoworld.runner.survey;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.TreeMap;
import java.util.function.ToIntFunction;

/**
 * One scenario across every seed it was run on.
 *
 * @param scenario what was run
 * @param ticks how many ticks each seed was asked for
 * @param seeds how each seed went, in seed order
 */
public record ScenarioResult(Scenario scenario, long ticks, List<SeedResult> seeds) {

    public ScenarioResult {
        Objects.requireNonNull(scenario, "scenario");
        seeds = List.copyOf(seeds);
    }

    public int runs() {
        return seeds.size();
    }

    public int survived() {
        return (int) seeds.stream().filter(SeedResult::alive).count();
    }

    public List<SeedResult> deaths() {
        return seeds.stream().filter(seed -> !seed.alive()).toList();
    }

    public int dangers() {
        return sum(SeedResult::dangers);
    }

    public double lowestHealth() {
        return seeds.stream().mapToDouble(SeedResult::lowestHealth).min().orElse(0.0);
    }

    public double meanFinalHealth() {
        return seeds.stream().mapToDouble(SeedResult::finalHealth).average().orElse(0.0);
    }

    /** Runs that started hurt; zero when the scenario starts everyone whole. */
    public int startedHurt() {
        return scenario.startingAs().isFullyHealthy() ? 0 : runs();
    }

    public int mended() {
        return (int) seeds.stream().filter(seed -> seed.ticksToFullHealth().isPresent()).count();
    }

    public OptionalDouble meanTicksToMend() {
        return seeds.stream()
                .filter(seed -> seed.ticksToFullHealth().isPresent())
                .mapToLong(seed -> seed.ticksToFullHealth().get())
                .average();
    }

    public int rests() {
        return sum(SeedResult::rests);
    }

    public int restsForHealth() {
        return sum(SeedResult::restsForHealth);
    }

    public int turnBacks() {
        return sum(SeedResult::turnBacks);
    }

    public int ditheringRuns() {
        return sum(SeedResult::ditheringRuns);
    }

    public int arrivals() {
        return sum(SeedResult::arrivals);
    }

    public int foodTrips() {
        return sum(SeedResult::foodTrips);
    }

    public int foodTripsAbandoned() {
        return sum(SeedResult::foodTripsAbandoned);
    }

    public int foodTripsToNothing() {
        return sum(SeedResult::foodTripsToNothing);
    }

    public Map<String, Integer> refusals() {
        Map<String, Integer> all = new TreeMap<>();
        seeds.forEach(seed -> seed.refusals().forEach((action, count) -> all.merge(action, count, Integer::sum)));
        return all;
    }

    /** Across every seed: hours asleep per simulated day. */
    public double sleepHoursPerDay() {
        return perDay(seeds.stream().mapToDouble(SeedResult::sleepHours).sum());
    }

    public double sleepsPerDay() {
        return perDay(seeds.stream().mapToInt(SeedResult::sleeps).sum());
    }

    public double mealsPerDay() {
        return perDay(seeds.stream().mapToInt(SeedResult::meals).sum());
    }

    public double drinksPerDay() {
        return perDay(seeds.stream().mapToInt(SeedResult::drinks).sum());
    }

    /** The share of all the time lived that was left over after upkeep. */
    public double freeShare() {
        double lived = seeds.stream().mapToDouble(SeedResult::hoursLived).sum();
        double upkeep = seeds.stream().mapToDouble(SeedResult::upkeepHours).sum();
        return lived <= 0.0 ? 0.0 : Math.max(0.0, 1.0 - upkeep / lived);
    }

    /** How much country a life covers, averaged over the seeds. */
    public double chunksPerRun() {
        return seeds.isEmpty()
                ? 0.0
                : seeds.stream().mapToInt(SeedResult::chunksEntered).average().orElse(0.0);
    }

    private double perDay(double total) {
        double days = seeds.stream().mapToDouble(SeedResult::days).sum();
        return days <= 0.0 ? 0.0 : total / days;
    }

    public int totalRefusals() {
        return sum(SeedResult::totalRefusals);
    }

    private int sum(ToIntFunction<SeedResult> what) {
        return seeds.stream().mapToInt(what).sum();
    }
}
