package dev.evoworld.runner.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The survey as a whole: what it accepts, and that its answer never wobbles. */
class SurveyTest {

    private static SurveyOptions small(int threads) {
        return new SurveyOptions(
                List.of(1L, 2L), Scenario.ALL, Optional.of(200L), true, Path.of("unused.md"), threads);
    }

    @Test
    @DisplayName("the same survey gives the same report, however many threads run it")
    void reportsAreDeterministic() {
        SurveyOptions sequential = small(1);
        SurveyOptions parallel = small(4);

        String once = SurveyReport.markdown(Survey.run(sequential), sequential, "test");
        String again = SurveyReport.markdown(Survey.run(parallel), parallel, "test");

        assertEquals(once, again);
    }

    @Test
    @DisplayName("a short survey of every scenario finishes and reports on every seed")
    void smokeRun() {
        List<ScenarioResult> results = Survey.run(small(2));

        assertEquals(Scenario.ALL.size(), results.size());
        for (ScenarioResult result : results) {
            assertEquals(2, result.runs());
            assertEquals(200L, result.ticks());
            result.seeds().forEach(seed -> assertEquals(200L, seed.ticksRun(), result.scenario().name()));
        }
        String report = SurveyReport.markdown(results, small(2), "test");
        for (Scenario scenario : Scenario.ALL) {
            assertTrue(report.contains("| " + scenario.name() + " |"), scenario.name());
            assertTrue(report.contains("## " + scenario.name() + ", seed by seed"), scenario.name());
        }
    }

    @Test
    @DisplayName("a run that starts hurt reports how many mended; one that starts whole does not")
    void mendedColumn() {
        List<ScenarioResult> results = Survey.run(new SurveyOptions(
                List.of(1L), List.of(Scenario.BASELINE, Scenario.WOUNDED),
                Optional.of(600L), false, Path.of("unused.md"), 1));
        String report = SurveyReport.markdown(results, small(1), "test");

        assertTrue(report.contains("| baseline | 600 | 1/1 |"), report);
        assertTrue(report.lines().anyMatch(line -> line.startsWith("| wounded |") && line.contains("| 1/1 |")),
                report);
    }

    @Nested
    @DisplayName("options")
    class Options {

        @Test
        @DisplayName("defaults: seeds 1 to 20, every scenario, each at its own length")
        void defaults() {
            SurveyOptions options = SurveyOptions.defaults();

            assertEquals(20, options.seeds().size());
            assertEquals(1L, options.seeds().get(0));
            assertEquals(Scenario.ALL, options.scenarios());
            assertEquals(Scenario.TWENTY_DAYS, options.ticksFor(Scenario.BASELINE));
            assertEquals(2000L, options.ticksFor(Scenario.HOURLY));
            assertEquals(SurveyOptions.DEFAULT_OUT, options.out());
        }

        @Test
        @DisplayName("seeds come as ranges, lists, or both")
        void seedSpecs() {
            assertEquals(List.of(3L, 4L, 5L), SurveyOptions.seeds("3-5"));
            assertEquals(List.of(3L, 7L, 10L), SurveyOptions.seeds("3,7,10"));
            assertEquals(List.of(1L, 2L, 3L, 9L), SurveyOptions.seeds("1-3, 9"));
            assertEquals(List.of(1L, 2L), SurveyOptions.seeds("1-2,2"), "repeats are dropped");
        }

        @Test
        @DisplayName("everything can be set from the command line")
        void parsesEverything() {
            SurveyOptions options = SurveyOptions.parse(new String[] {
                "--seeds", "5-6", "--scenarios", "wounded,sparse", "--ticks", "100",
                "--detail", "--out", "x.md", "--threads", "3"});

            assertEquals(List.of(5L, 6L), options.seeds());
            assertEquals(List.of(Scenario.WOUNDED, Scenario.SPARSE), options.scenarios());
            assertEquals(100L, options.ticksFor(Scenario.BASELINE));
            assertTrue(options.detail());
            assertEquals(Path.of("x.md"), options.out());
            assertEquals(3, options.threads());
        }

        @Test
        @DisplayName("nonsense is refused with a reason")
        void refusesNonsense() {
            assertThrows(IllegalArgumentException.class, () -> SurveyOptions.parse(new String[] {"--bogus"}));
            assertThrows(IllegalArgumentException.class, () -> SurveyOptions.parse(new String[] {"--seeds"}));
            assertThrows(IllegalArgumentException.class, () -> SurveyOptions.parse(new String[] {"--seeds", "9-1"}));
            assertThrows(IllegalArgumentException.class, () -> SurveyOptions.parse(new String[] {"--seeds", "x"}));
            assertThrows(IllegalArgumentException.class,
                    () -> SurveyOptions.parse(new String[] {"--scenarios", "nonsense"}));
            assertThrows(IllegalArgumentException.class, () -> SurveyOptions.parse(new String[] {"--ticks", "0"}));
        }

        @Test
        @DisplayName("consecutive seeds are written as a range")
        void describesSeeds() {
            assertEquals("1-20", SurveyReport.describeSeeds(SurveyOptions.seeds("1-20")));
            assertEquals("3, 7", SurveyReport.describeSeeds(List.of(3L, 7L)));
        }
    }
}
