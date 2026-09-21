package dev.evoworld.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.evoworld.core.Simulation;
import dev.evoworld.core.save.SavedSimulation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * Writes a simulation to disk as JSON, and reads it back.
 *
 * <p>The only place Jackson appears. The core hands over a
 * {@link SavedSimulation} - plain records of numbers, strings and enums - and
 * this turns it into text; the core has no idea a file is involved, which is
 * how it stays free of I/O.
 *
 * <p>Reading is strict on purpose. A field the format does not know about, or a
 * number missing where one is required, is an error rather than a default: a
 * save that silently loaded with a zero where the agent's health should be
 * would be far worse than one that refused to load.
 *
 * <p>Writing is atomic where the file system allows. The save goes to a
 * temporary file first and is moved into place, so a run killed mid-save
 * leaves the previous save intact instead of half of a new one - which
 * matters for a simulation meant to run unattended for days.
 */
public final class SaveFile {

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES);

    private SaveFile() {
    }

    /** Saves a simulation to a file, replacing any save already there. */
    public static void write(Simulation simulation, Path file) {
        write(simulation.save(), file);
    }

    public static void write(SavedSimulation saved, Path file) {
        Objects.requireNonNull(file, "file");
        Path target = file.toAbsolutePath();
        try {
            Path directory = target.getParent();
            if (directory != null) {
                Files.createDirectories(directory);
            }
            Path temporary = Files.createTempFile(directory, target.getFileName().toString(), ".tmp");
            try {
                Files.writeString(temporary, toJson(saved), StandardCharsets.UTF_8);
                moveIntoPlace(temporary, target);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException e) {
            throw new SaveFileException("could not write save to " + target, e);
        }
    }

    /** Reads a save and rebuilds the simulation, ready to carry on. */
    public static Simulation load(Path file) {
        return Simulation.load(read(file));
    }

    public static SavedSimulation read(Path file) {
        Objects.requireNonNull(file, "file");
        try {
            return fromJson(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new SaveFileException("could not read save from " + file.toAbsolutePath(), e);
        }
    }

    public static String toJson(SavedSimulation saved) {
        try {
            return JSON.writeValueAsString(Objects.requireNonNull(saved, "saved"));
        } catch (JsonProcessingException e) {
            throw new SaveFileException("could not turn the simulation into JSON", e);
        }
    }

    public static SavedSimulation fromJson(String json) {
        try {
            return JSON.readValue(json, SavedSimulation.class);
        } catch (JsonProcessingException e) {
            throw new SaveFileException("not a readable EvoWorld save: " + e.getOriginalMessage(), e);
        }
    }

    private static void moveIntoPlace(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
