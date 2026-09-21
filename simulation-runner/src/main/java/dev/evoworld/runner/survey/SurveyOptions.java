package dev.evoworld.runner.survey;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.LongStream;

/**
 * What a survey was asked to run.
 *
 * @param seeds the seeds, in the order they are reported
 * @param scenarios the scenarios, in the order they are reported
 * @param ticks a tick count for every scenario, overriding each one's own
 * @param detail whether to print a row per seed as well as the summary
 * @param out where to write the report
 * @param threads how many runs to have going at once; the report does not
 *     depend on it
 */
public record SurveyOptions(
        List<Long> seeds,
        List<Scenario> scenarios,
        Optional<Long> ticks,
        boolean detail,
        Path out,
        int threads) {

    public static final Path DEFAULT_OUT = Path.of("build", "reports", "survey", "survey.md");

    public SurveyOptions {
        seeds = List.copyOf(seeds);
        scenarios = List.copyOf(scenarios);
        Objects.requireNonNull(ticks, "ticks");
        Objects.requireNonNull(out, "out");
        if (seeds.isEmpty()) {
            throw new IllegalArgumentException("a survey needs at least one seed");
        }
        if (scenarios.isEmpty()) {
            throw new IllegalArgumentException("a survey needs at least one scenario");
        }
        if (ticks.isPresent() && ticks.get() < 1) {
            throw new IllegalArgumentException("ticks must be positive, was " + ticks.get());
        }
        if (threads < 1) {
            throw new IllegalArgumentException("threads must be positive, was " + threads);
        }
    }

    public static SurveyOptions defaults() {
        return parse(new String[0]);
    }

    /** How long a scenario runs in this survey. */
    public long ticksFor(Scenario scenario) {
        return ticks.orElse(scenario.defaultTicks());
    }

    public static SurveyOptions parse(String[] args) {
        List<Long> seeds = range(1, 20);
        List<Scenario> scenarios = Scenario.ALL;
        Optional<Long> ticks = Optional.empty();
        boolean detail = false;
        Path out = DEFAULT_OUT;
        int threads = Runtime.getRuntime().availableProcessors();

        for (int i = 0; i < args.length; i++) {
            String flag = args[i];
            switch (flag) {
                case "--seeds" -> seeds = seeds(valueFor(args, ++i, flag));
                case "--scenarios" -> scenarios = scenarios(valueFor(args, ++i, flag));
                case "--ticks" -> ticks = Optional.of(number(valueFor(args, ++i, flag), flag));
                case "--detail" -> detail = true;
                case "--out" -> out = Path.of(valueFor(args, ++i, flag));
                case "--threads" -> threads = (int) number(valueFor(args, ++i, flag), flag);
                default -> throw new IllegalArgumentException("unknown option: " + flag + "\n" + usage());
            }
        }
        return new SurveyOptions(seeds, scenarios, ticks, detail, out, threads);
    }

    /** "1-20", "3,7,10" or a mix: "1-5,9". */
    static List<Long> seeds(String spec) {
        List<Long> seeds = new ArrayList<>();
        for (String part : spec.split(",")) {
            String trimmed = part.trim();
            int dash = trimmed.indexOf('-', 1);
            if (dash > 0) {
                long from = number(trimmed.substring(0, dash), "--seeds");
                long to = number(trimmed.substring(dash + 1), "--seeds");
                if (to < from) {
                    throw new IllegalArgumentException("--seeds range runs backwards: " + trimmed);
                }
                seeds.addAll(range(from, to));
            } else {
                seeds.add(number(trimmed, "--seeds"));
            }
        }
        return seeds.stream().distinct().toList();
    }

    static List<Scenario> scenarios(String spec) {
        if (spec.equals("all")) {
            return Scenario.ALL;
        }
        List<Scenario> chosen = new ArrayList<>();
        for (String name : spec.split(",")) {
            chosen.add(Scenario.named(name.trim()).orElseThrow(() -> new IllegalArgumentException(
                    "unknown scenario: " + name.trim() + " (known: "
                            + String.join(", ", Scenario.ALL.stream().map(Scenario::name).toList())
                            + ")")));
        }
        return chosen;
    }

    private static List<Long> range(long from, long to) {
        return LongStream.rangeClosed(from, to).boxed().toList();
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
                "usage: survey [options]",
                "  --seeds <spec>        seeds to run: 1-20, 3,7,10 or 1-5,9 (default 1-20)",
                "  --scenarios <names>   comma-separated, or all (default all): "
                        + String.join(", ", Scenario.ALL.stream().map(Scenario::name).toList()),
                "  --ticks <n>           run every scenario this long, instead of its own length",
                "  --detail              add a row per seed",
                "  --out <file>          where to write the report (default " + DEFAULT_OUT + ")",
                "  --threads <n>         runs at once (default: one per processor)");
    }
}
