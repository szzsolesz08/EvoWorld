package dev.evoworld.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.evoworld.core.Simulation;
import dev.evoworld.core.SimulationConfig;
import dev.evoworld.core.agent.Agent;
import dev.evoworld.core.decision.DeliberativeDecisionMaker;
import dev.evoworld.core.event.SimulationEvent;
import dev.evoworld.core.observer.SimulationObserver;
import dev.evoworld.core.save.SavedSimulation;
import dev.evoworld.core.world.World;
import dev.evoworld.core.world.WorldGenerator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Save to disk, load from disk, carry on - through real JSON and a real file. */
class SaveFileTest {

    @TempDir
    Path directory;

    private static Simulation fresh(long seed) {
        SimulationConfig config = SimulationConfig.defaults(seed);
        World world = WorldGenerator.forConfig(config);
        world.addAgent(Agent.spawn("agent-1", "Atlas", config.bounds().center()));
        return new Simulation(world, DeliberativeDecisionMaker.from(config), config);
    }

    private static final class Events implements SimulationObserver {
        final List<String> seen = new ArrayList<>();

        @Override
        public void onEvent(SimulationEvent event) {
            seen.add(event.tick() + " " + event.describe());
        }
    }

    @ParameterizedTest(name = "seed {0}")
    @ValueSource(longs = {1L, 5L, 7L, 10L})
    @DisplayName("save to a file, load it back, carry on: the same run as never stopping")
    void aSavedRunCarriesOnExactly(long seed) {
        Simulation unbroken = fresh(seed);
        Events wholeRun = new Events();
        unbroken.addObserver(wholeRun);
        unbroken.run(1500);

        Simulation firstHalf = fresh(seed);
        firstHalf.run(700);
        Path file = directory.resolve("atlas.json");
        SaveFile.write(firstHalf, file);

        Simulation secondHalf = SaveFile.load(file);
        Events continuation = new Events();
        secondHalf.addObserver(continuation);
        secondHalf.run(800);

        assertEquals(unbroken.snapshot(), secondHalf.snapshot());
        assertEquals(unbroken.save(), secondHalf.save());
        assertEquals(
                wholeRun.seen.stream()
                        .filter(line -> Long.parseLong(line.substring(0, line.indexOf(' '))) > 700)
                        .toList(),
                continuation.seen);
    }

    @Test
    @DisplayName("a run can be saved and resumed many times over without drifting")
    void repeatedSavesDoNotDrift() {
        Simulation unbroken = fresh(7L);
        unbroken.run(1000);

        Path file = directory.resolve("chain.json");
        Simulation relay = fresh(7L);
        for (int leg = 0; leg < 10; leg++) {
            relay.run(100);
            SaveFile.write(relay, file);
            relay = SaveFile.load(file);
        }

        assertEquals(unbroken.save(), relay.save());
    }

    @Test
    @DisplayName("JSON carries every number across exactly")
    void jsonRoundTripIsExact() {
        Simulation simulation = fresh(3L);
        simulation.run(900);
        SavedSimulation saved = simulation.save();

        assertEquals(saved, SaveFile.fromJson(SaveFile.toJson(saved)));
    }

    @Test
    @DisplayName("the same state is always written out the same way")
    void savesAreByteStable() {
        Simulation simulation = fresh(3L);
        simulation.run(300);

        assertEquals(SaveFile.toJson(simulation.save()), SaveFile.toJson(simulation.save()));
        assertEquals(
                SaveFile.toJson(simulation.save()),
                SaveFile.toJson(SaveFile.fromJson(SaveFile.toJson(simulation.save()))));
    }

    @Test
    @DisplayName("a save file is readable by a person, not just by the program")
    void savesAreHumanReadable() throws IOException {
        Simulation simulation = fresh(7L);
        simulation.run(200);
        Path file = directory.resolve("readable.json");

        SaveFile.write(simulation, file);
        String json = Files.readString(file);

        for (String field : List.of(
                "\"formatVersion\" : " + SavedSimulation.FORMAT_VERSION, "\"name\" : \"Atlas\"", "\"beliefs\"",
                "\"memories\"", "\"explored\"", "\"randomState\"", "\"tickDuration\" : \"PT10M\"")) {
            assertTrue(json.contains(field), "expected " + field + " in the save");
        }
    }

    @Test
    @DisplayName("saving over an existing save leaves nothing half-written or left over")
    void writesReplaceCleanly() throws IOException {
        Path file = directory.resolve("atlas.json");
        Simulation simulation = fresh(7L);
        simulation.run(50);
        SaveFile.write(simulation, file);
        simulation.run(50);
        SaveFile.write(simulation, file);

        try (Stream<Path> files = Files.list(directory)) {
            assertEquals(List.of(file.getFileName()), files.map(Path::getFileName).toList(),
                    "no temporary files should be left behind");
        }
        assertEquals(100L, SaveFile.read(file).tick());
    }

    @Test
    @DisplayName("saving into a folder that does not exist yet creates it")
    void createsMissingFolders() {
        Path nested = directory.resolve("saves").resolve("week-one").resolve("atlas.json");

        SaveFile.write(fresh(1L), nested);

        assertTrue(Files.exists(nested));
    }

    @Test
    @DisplayName("an unknown field is refused, not ignored")
    void rejectsUnknownFields() {
        String json = SaveFile.toJson(fresh(1L).save())
                .replaceFirst("\\{", "{ \"somethingNew\" : 1,");

        assertThrows(SaveFileException.class, () -> SaveFile.fromJson(json));
    }

    @Test
    @DisplayName("a missing number is refused rather than read as zero")
    void rejectsMissingNumbers() {
        String json = SaveFile.toJson(fresh(1L).save())
                .replaceFirst("\"randomState\" : [0-9]+,", "");

        assertThrows(SaveFileException.class, () -> SaveFile.fromJson(json));
    }

    @Test
    @DisplayName("something that is not a save at all gives a clear error")
    void rejectsGarbage() throws IOException {
        Path file = directory.resolve("not-a-save.json");
        Files.writeString(file, "this is not JSON");

        SaveFileException error = assertThrows(SaveFileException.class, () -> SaveFile.read(file));
        assertTrue(error.getMessage().contains("not a readable EvoWorld save"), error.getMessage());
    }

    @Test
    @DisplayName("a file that is not there gives a clear error")
    void rejectsAMissingFile() {
        SaveFileException error = assertThrows(
                SaveFileException.class, () -> SaveFile.read(directory.resolve("nowhere.json")));
        assertTrue(error.getMessage().contains("could not read save"), error.getMessage());
    }
}
