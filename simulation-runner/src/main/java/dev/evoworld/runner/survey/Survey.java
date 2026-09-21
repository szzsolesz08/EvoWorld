package dev.evoworld.runner.survey;

import dev.evoworld.core.Simulation;
import dev.evoworld.runner.LogLevel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Runs every scenario on every seed and reports how the agent fared.
 *
 * <p>The answer to "did this change make things better?" - a table to compare
 * before and after, rather than a feeling from watching one run. Runs are
 * independent simulations, so they go in parallel; results are gathered back
 * in seed order, so the report is the same however many threads there were.
 */
public final class Survey {

    private Survey() {
    }

    public static void main(String[] args) {
        SurveyOptions options;
        try {
            options = SurveyOptions.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.exit(2);
            return;
        }
        // The core announces every run's start and end; a survey starts hundreds.
        LogLevel.QUIET.applyToCore();

        List<ScenarioResult> results = run(options);
        String report = SurveyReport.markdown(results, options, source());
        System.out.print(report);
        write(report, options.out());
        System.out.println();
        System.out.println("written to " + options.out().toAbsolutePath());
    }

    /** Every requested scenario on every requested seed. */
    public static List<ScenarioResult> run(SurveyOptions options) {
        ExecutorService pool = Executors.newFixedThreadPool(options.threads());
        try {
            List<ScenarioResult> results = new ArrayList<>();
            for (Scenario scenario : options.scenarios()) {
                long ticks = options.ticksFor(scenario);
                List<Callable<SeedResult>> runs = new ArrayList<>();
                for (long seed : options.seeds()) {
                    runs.add(() -> runOnce(scenario, seed, ticks));
                }
                results.add(new ScenarioResult(scenario, ticks, gather(pool.invokeAll(runs))));
            }
            return results;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("survey interrupted", e);
        } finally {
            pool.shutdownNow();
        }
    }

    /** One scenario on one seed, watched from start to finish. */
    public static SeedResult runOnce(Scenario scenario, long seed, long ticks) {
        Simulation simulation = scenario.build(seed);
        SurveyObserver observer = new SurveyObserver(seed);
        simulation.addObserver(observer);
        simulation.run(ticks);
        return observer.result();
    }

    private static List<SeedResult> gather(List<Future<SeedResult>> futures) throws InterruptedException {
        List<SeedResult> results = new ArrayList<>(futures.size());
        for (Future<SeedResult> future : futures) {
            try {
                results.add(future.get());
            } catch (ExecutionException e) {
                throw new IllegalStateException("a survey run failed", e.getCause());
            }
        }
        return results;
    }

    private static void write(String report, Path out) {
        try {
            Path parent = out.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(out, report, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("could not write " + out, e);
        }
    }

    /** Which code produced the numbers: the commit, and whether it had local changes. */
    private static String source() {
        String commit = git("rev-parse", "--short", "HEAD");
        if (commit.isEmpty()) {
            return "an unknown commit";
        }
        String changes = git("status", "--porcelain", "--untracked-files=no");
        return "commit " + commit + (changes.isEmpty() ? "" : " with uncommitted changes");
    }

    private static String git(String... args) {
        List<String> command = new ArrayList<>(List.of("git"));
        command.addAll(List.of(args));
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return process.waitFor() == 0 ? output : "";
        } catch (IOException e) {
            return "";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        }
    }
}
