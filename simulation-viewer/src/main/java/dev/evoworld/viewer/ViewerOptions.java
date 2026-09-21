package dev.evoworld.viewer;

import dev.evoworld.core.SimulationConfig;
import dev.evoworld.runner.RunnerOptions;
import dev.evoworld.runner.survey.Scenario;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * What the viewer was asked to show.
 *
 * @param seed the seed for a new run
 * @param scenario the situation to start in; the plain run unless asked
 * @param tickDuration a tick length overriding the scenario's own
 * @param speedIndex where on {@link ViewerModel#SPEEDS} to start
 * @param load a save to continue instead of starting a new run
 * @param screenshot where to save a picture of one frame, then quit - for
 *     documentation, and for checking the layout without watching
 * @param after how many ticks to run before that picture
 */
public record ViewerOptions(
        long seed,
        Scenario scenario,
        Optional<Duration> tickDuration,
        int speedIndex,
        Optional<Path> load,
        Optional<Path> screenshot,
        long after) {

    public ViewerOptions {
        Objects.requireNonNull(scenario, "scenario");
        Objects.requireNonNull(tickDuration, "tickDuration");
        Objects.requireNonNull(load, "load");
        Objects.requireNonNull(screenshot, "screenshot");
        if (after < 0) {
            throw new IllegalArgumentException("--after must not be negative, was " + after);
        }
        if (speedIndex < 0 || speedIndex >= ViewerModel.SPEEDS.length) {
            throw new IllegalArgumentException("no such speed: " + speedIndex);
        }
    }

    /** The configuration a new run starts from. */
    public SimulationConfig config() {
        SimulationConfig base = scenario.config().apply(seed);
        return tickDuration.map(base::withTickDuration).orElse(base);
    }

    public static ViewerOptions parse(String[] args) {
        long seed = RunnerOptions.DEFAULT_SEED;
        Scenario scenario = Scenario.BASELINE;
        Optional<Duration> tickDuration = Optional.empty();
        int speedIndex = ViewerModel.DEFAULT_SPEED_INDEX;
        Optional<Path> load = Optional.empty();
        Optional<Path> screenshot = Optional.empty();
        long after = 0L;

        for (int i = 0; i < args.length; i++) {
            String flag = args[i];
            switch (flag) {
                case "--seed" -> seed = number(valueFor(args, ++i, flag), flag);
                case "--scenario" -> {
                    String name = valueFor(args, ++i, flag);
                    scenario = Scenario.named(name).orElseThrow(() -> new IllegalArgumentException(
                            "unknown scenario: " + name + " (known: " + known() + ")"));
                }
                case "--tick-minutes" -> {
                    long minutes = number(valueFor(args, ++i, flag), flag);
                    if (minutes < 1) {
                        throw new IllegalArgumentException("--tick-minutes must be positive");
                    }
                    tickDuration = Optional.of(Duration.ofMinutes(minutes));
                }
                case "--speed" -> speedIndex = speedIndexFor(number(valueFor(args, ++i, flag), flag));
                case "--load" -> load = Optional.of(Path.of(valueFor(args, ++i, flag)));
                case "--screenshot" -> screenshot = Optional.of(Path.of(valueFor(args, ++i, flag)));
                case "--after" -> after = number(valueFor(args, ++i, flag), flag);
                default -> throw new IllegalArgumentException("unknown option: " + flag + "\n" + usage());
            }
        }
        return new ViewerOptions(seed, scenario, tickDuration, speedIndex, load, screenshot, after);
    }

    /** The ladder step closest to the ticks per second asked for. */
    static int speedIndexFor(long ticksPerSecond) {
        int best = 0;
        for (int i = 0; i < ViewerModel.SPEEDS.length; i++) {
            if (Math.abs(ViewerModel.SPEEDS[i] - ticksPerSecond)
                    < Math.abs(ViewerModel.SPEEDS[best] - ticksPerSecond)) {
                best = i;
            }
        }
        return best;
    }

    private static String known() {
        return String.join(", ", Scenario.ALL.stream().map(Scenario::name).toList());
    }

    private static String valueFor(String[] args, int index, String flag) {
        if (index >= args.length) {
            throw new IllegalArgumentException(flag + " needs a value");
        }
        return args[index];
    }

    private static long number(String text, String flag) {
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(flag + " expects a number, got '" + text + "'", e);
        }
    }

    public static String usage() {
        return String.join("\n",
                "usage: view [options]",
                "  --seed <n>            seed for a new world (default " + RunnerOptions.DEFAULT_SEED + ")",
                "  --scenario <name>     start in a survey scenario: " + known(),
                "  --tick-minutes <n>    simulated minutes per tick (default: the scenario's)",
                "  --speed <n>           starting ticks per second (default "
                        + (int) ViewerModel.SPEEDS[ViewerModel.DEFAULT_SPEED_INDEX] + ")",
                "  --load <file>         continue a saved run",
                "  --screenshot <file>   save a PNG of one frame and quit",
                "  --after <n>           ticks to run before that screenshot (default 0)");
    }
}
