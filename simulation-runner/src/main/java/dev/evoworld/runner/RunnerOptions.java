package dev.evoworld.runner;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Command line options for a single simulation run.
 *
 * @param seed          random seed for a new run; the same seed always produces the same run
 * @param ticks         how many ticks to run - for a loaded run, how many more
 * @param tickDuration  how much simulated time one tick represents, for a new run
 * @param logEvery      at debug level, log the per-tick trace every n ticks
 * @param scripted      drive a hand-written action script instead of deciding
 * @param logLevel      how much of the run to show
 * @param speed         ticks per real second; zero runs as fast as the machine allows
 * @param saveTo        where to save the run when it ends, if anywhere
 * @param loadFrom      a save to carry on from instead of starting fresh
 * @param autosaveEvery ticks between automatic saves to {@code saveTo}; zero for none
 */
public record RunnerOptions(
        long seed,
        long ticks,
        Duration tickDuration,
        long logEvery,
        boolean scripted,
        LogLevel logLevel,
        double speed,
        Optional<Path> saveTo,
        Optional<Path> loadFrom,
        long autosaveEvery) {

    public static final long DEFAULT_SEED = 42L;

    /** Ten simulated days at the default tick duration: long enough to watch a life. */
    public static final long DEFAULT_TICKS = 1440L;

    public static final Duration DEFAULT_TICK_DURATION = Duration.ofMinutes(10);
    public static final long DEFAULT_LOG_EVERY = 1L;

    /** As fast as the machine allows. */
    public static final double UNPACED = 0.0;

    public RunnerOptions {
        Objects.requireNonNull(tickDuration, "tickDuration");
        Objects.requireNonNull(logLevel, "logLevel");
        Objects.requireNonNull(saveTo, "saveTo");
        Objects.requireNonNull(loadFrom, "loadFrom");
        if (ticks < 0) {
            throw new IllegalArgumentException("tick count must not be negative, was " + ticks);
        }
        if (tickDuration.isZero() || tickDuration.isNegative()) {
            throw new IllegalArgumentException("tick duration must be positive, was " + tickDuration);
        }
        if (logEvery < 1) {
            throw new IllegalArgumentException("log interval must be at least 1, was " + logEvery);
        }
        if (speed < 0.0 || Double.isNaN(speed) || Double.isInfinite(speed)) {
            throw new IllegalArgumentException(
                    "speed must be zero (unpaced) or a positive number of ticks per second, was "
                            + speed);
        }
        if (autosaveEvery < 0) {
            throw new IllegalArgumentException("autosave interval must not be negative");
        }
        if (autosaveEvery > 0 && saveTo.isEmpty()) {
            throw new IllegalArgumentException("--autosave-every needs --save <file> to say where");
        }
        if (scripted && (saveTo.isPresent() || loadFrom.isPresent())) {
            // A script's position in its list is state the save format does
            // not hold; saving it would resume a different run.
            throw new IllegalArgumentException("a scripted run cannot be saved or loaded");
        }
    }

    public static RunnerOptions defaults() {
        return new RunnerOptions(
                DEFAULT_SEED,
                DEFAULT_TICKS,
                DEFAULT_TICK_DURATION,
                DEFAULT_LOG_EVERY,
                false,
                LogLevel.NORMAL,
                UNPACED,
                Optional.empty(),
                Optional.empty(),
                0L);
    }

    public boolean isPaced() {
        return speed > 0.0;
    }

    /** Whether the per-tick trace is on - {@code --trace} is short for {@code --log-level debug}. */
    public boolean trace() {
        return logLevel == LogLevel.DEBUG;
    }

    public static String usage() {
        return String.join(System.lineSeparator(),
                "usage: simulation-runner [options]",
                "  --seed <n>            seed for a new world (default 42)",
                "  --ticks <n>           ticks to run, or to run on for after --load (default 1440)",
                "  --tick-minutes <n>    simulated minutes per tick, for a new world (default 10)",
                "  --speed <ticks/s>     real-time pacing; 0 is as fast as possible (default 0)",
                "  --log-level <level>   quiet | normal | verbose | debug (default normal)",
                "  --trace               the same as --log-level debug",
                "  --log-every <n>       at debug level, trace every n ticks (default 1)",
                "  --save <file>         save the run to this file when it ends",
                "  --autosave-every <n>  also save to that file every n ticks",
                "  --load <file>         carry on from a save instead of starting fresh",
                "  --script              run a hand-written action list instead of deciding");
    }

    /** Parses the supported flags; anything else is rejected rather than ignored. */
    public static RunnerOptions parse(String[] args) {
        long seed = DEFAULT_SEED;
        long ticks = DEFAULT_TICKS;
        Duration tickDuration = DEFAULT_TICK_DURATION;
        long logEvery = DEFAULT_LOG_EVERY;
        boolean scripted = false;
        LogLevel logLevel = LogLevel.NORMAL;
        double speed = UNPACED;
        Optional<Path> saveTo = Optional.empty();
        Optional<Path> loadFrom = Optional.empty();
        long autosaveEvery = 0L;
        for (int i = 0; i < args.length; i++) {
            String flag = args[i];
            switch (flag) {
                case "--seed" -> seed = longValue(args, ++i, flag);
                case "--ticks" -> ticks = longValue(args, ++i, flag);
                case "--tick-minutes" -> tickDuration = Duration.ofMinutes(longValue(args, ++i, flag));
                case "--log-every" -> logEvery = longValue(args, ++i, flag);
                case "--speed" -> speed = doubleValue(args, ++i, flag);
                case "--log-level" -> logLevel = LogLevel.parse(valueFor(args, ++i, flag));
                case "--trace" -> logLevel = LogLevel.DEBUG;
                case "--save" -> saveTo = Optional.of(Path.of(valueFor(args, ++i, flag)));
                case "--load" -> loadFrom = Optional.of(Path.of(valueFor(args, ++i, flag)));
                case "--autosave-every" -> autosaveEvery = longValue(args, ++i, flag);
                case "--script" -> scripted = true;
                default -> throw new IllegalArgumentException("unknown option '" + flag + "'");
            }
        }
        return new RunnerOptions(
                seed, ticks, tickDuration, logEvery, scripted, logLevel, speed,
                saveTo, loadFrom, autosaveEvery);
    }

    private static String valueFor(String[] args, int index, String flag) {
        if (index >= args.length) {
            throw new IllegalArgumentException("missing value for " + flag);
        }
        return args[index];
    }

    private static long longValue(String[] args, int index, String flag) {
        try {
            return Long.parseLong(valueFor(args, index, flag));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("value for " + flag + " must be a number", e);
        }
    }

    private static double doubleValue(String[] args, int index, String flag) {
        try {
            return Double.parseDouble(valueFor(args, index, flag));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("value for " + flag + " must be a number", e);
        }
    }
}
