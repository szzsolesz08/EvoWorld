package dev.evoworld.runner.survey;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A survey written up as Markdown: a summary row per scenario, then what died
 * and what was refused, then - if asked - a row per seed.
 *
 * <p>No timestamps and no timings, so the same code on the same seeds writes
 * the same bytes, and {@code git diff} on a committed report shows exactly
 * what a change did.
 */
public final class SurveyReport {

    private SurveyReport() {
    }

    /**
     * @param source which code produced the numbers, e.g. "commit b449bdb"
     */
    public static String markdown(List<ScenarioResult> results, SurveyOptions options, String source) {
        List<String> lines = new ArrayList<>();
        lines.add("# EvoWorld survey");
        lines.add("");
        lines.add(String.format(Locale.ROOT,
                "Seeds %s, measured on %s. Regenerate with `./gradlew survey`.",
                describeSeeds(options.seeds()), source));
        lines.add("");
        lines.add("| Scenario | Ticks | Survived | Close calls | Lowest health | Mean final health "
                + "| Mended | Ticks to mend | Rests | For health | Turn-backs | Dithering runs "
                + "| Food/water trips | Abandoned | To nothing | Refused |");
        lines.add("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: "
                + "| ---: | ---: | ---: | ---: |");
        for (ScenarioResult result : results) {
            lines.add(summaryRow(result));
        }

        lines.add("");
        lines.add("## A day in the life");
        lines.add("");
        lines.add("Averaged over every seed and every simulated day each run lived.");
        lines.add("");
        lines.add("| Scenario | Sleep h/day | Sleeps/day | Meals/day | Drinks/day | Free time |");
        lines.add("| --- | ---: | ---: | ---: | ---: | ---: |");
        for (ScenarioResult result : results) {
            lines.add(String.format(Locale.ROOT, "| %s | %.1f | %.2f | %.1f | %.1f | %.0f%% |",
                    result.scenario().name(),
                    result.sleepHoursPerDay(),
                    result.sleepsPerDay(),
                    result.mealsPerDay(),
                    result.drinksPerDay(),
                    result.freeShare() * 100));
        }

        lines.add("");
        lines.add("## Scenarios");
        lines.add("");
        for (ScenarioResult result : results) {
            lines.add(String.format(Locale.ROOT, "- **%s**: %s.", result.scenario().name(),
                    result.scenario().description()));
        }

        lines.add("");
        lines.add("## Deaths");
        lines.add("");
        boolean anyDeaths = false;
        for (ScenarioResult result : results) {
            if (!result.deaths().isEmpty()) {
                anyDeaths = true;
                lines.add(String.format(Locale.ROOT, "- **%s**: %s", result.scenario().name(),
                        result.deaths().stream()
                                .map(seed -> String.format(Locale.ROOT, "seed %d, %s at t%d",
                                        seed.seed(), seed.death().orElseThrow().cause(),
                                        seed.death().orElseThrow().tick()))
                                .collect(Collectors.joining("; "))));
            }
        }
        if (!anyDeaths) {
            lines.add("None.");
        }

        lines.add("");
        lines.add("## Refused actions");
        lines.add("");
        for (ScenarioResult result : results) {
            lines.add(String.format(Locale.ROOT, "- **%s**: %s", result.scenario().name(),
                    describe(result.refusals())));
        }

        if (options.detail()) {
            for (ScenarioResult result : results) {
                lines.add("");
                lines.add("## " + result.scenario().name() + ", seed by seed");
                lines.add("");
                lines.add("| Seed | Ticks | Outcome | Close calls | Lowest health | Final health "
                        + "| Mended at | Rests | For health | Turn-backs | Dithering runs "
                        + "| Food/water trips | Abandoned | To nothing | Refused |");
                lines.add("| ---: | ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: "
                        + "| ---: | ---: | ---: | ---: |");
                for (SeedResult seed : result.seeds()) {
                    lines.add(seedRow(seed));
                }
            }
        }
        return String.join("\n", lines) + "\n";
    }

    private static String summaryRow(ScenarioResult result) {
        return String.format(Locale.ROOT,
                "| %s | %,d | %d/%d | %,d | %.1f | %.1f | %s | %s | %,d | %,d | %,d | %,d | %,d | %,d | %,d | %,d |",
                result.scenario().name(),
                result.ticks(),
                result.survived(),
                result.runs(),
                result.dangers(),
                result.lowestHealth(),
                result.meanFinalHealth(),
                result.startedHurt() == 0
                        ? "-"
                        : result.mended() + "/" + result.startedHurt(),
                result.meanTicksToMend().isPresent()
                        ? String.format(Locale.ROOT, "%.0f", result.meanTicksToMend().getAsDouble())
                        : "-",
                result.rests(),
                result.restsForHealth(),
                result.turnBacks(),
                result.ditheringRuns(),
                result.foodTrips(),
                result.foodTripsAbandoned(),
                result.foodTripsToNothing(),
                result.totalRefusals());
    }

    private static String seedRow(SeedResult seed) {
        String outcome = seed.death()
                .map(death -> String.format(Locale.ROOT, "died of %s at t%d", death.cause(), death.tick()))
                .orElse("alive");
        return String.format(Locale.ROOT,
                "| %d | %,d | %s | %d | %.1f | %.1f | %s | %,d | %,d | %,d | %,d | %,d | %,d | %,d | %,d |",
                seed.seed(),
                seed.ticksRun(),
                outcome,
                seed.dangers(),
                seed.lowestHealth(),
                seed.finalHealth(),
                seed.ticksToFullHealth().map(tick -> "t" + tick).orElse("-"),
                seed.rests(),
                seed.restsForHealth(),
                seed.turnBacks(),
                seed.ditheringRuns(),
                seed.foodTrips(),
                seed.foodTripsAbandoned(),
                seed.foodTripsToNothing(),
                seed.totalRefusals());
    }

    private static String describe(Map<String, Integer> refusals) {
        if (refusals.isEmpty()) {
            return "none";
        }
        return refusals.entrySet().stream()
                .map(entry -> String.format(Locale.ROOT, "%s %,d", entry.getKey(), entry.getValue()))
                .collect(Collectors.joining(", "));
    }

    /** "1-20" for a run of consecutive seeds, otherwise the list. */
    static String describeSeeds(List<Long> seeds) {
        boolean consecutive = true;
        for (int i = 1; i < seeds.size(); i++) {
            consecutive &= seeds.get(i) == seeds.get(i - 1) + 1;
        }
        if (consecutive && seeds.size() > 2) {
            return seeds.get(0) + "-" + seeds.get(seeds.size() - 1);
        }
        return seeds.stream().map(String::valueOf).collect(Collectors.joining(", "));
    }
}
