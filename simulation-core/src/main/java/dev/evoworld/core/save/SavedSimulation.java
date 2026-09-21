package dev.evoworld.core.save;

import java.util.List;
import java.util.Objects;

/**
 * Everything needed to carry a simulation on from exactly where it stopped.
 *
 * <p>The save format, deliberately separate from the domain. Every record in
 * this package holds only numbers, strings, enums and other records from this
 * package - no {@code Agent}, no {@code Needs}, no {@code Duration} - so a
 * domain class can be refactored without quietly changing what an old save
 * file means, and a serializer needs no knowledge of the domain to write one.
 *
 * <p>What is saved is what the next tick depends on and nothing more:
 * the configuration, the clock, the random generator's exact state, the
 * world's resources, and each agent's body, mind and unfinished business.
 * Goals are not saved because they are not state - they are recomputed from
 * the agent's needs every time it decides, and would come out the same.
 *
 * @param formatVersion which shape of save this is, so an old file is refused
 *     rather than misread
 * @param randomState the generator's full state, not just its seed
 * @param announced whether the run has already reported its agents' arrival
 */
public record SavedSimulation(
        int formatVersion,
        SavedConfig config,
        long tick,
        long randomState,
        long randomDraws,
        boolean announced,
        List<SavedResource> resources,
        List<SavedAgent> agents) {

    /** The shape of save this version of EvoWorld writes and reads. */
    public static final int FORMAT_VERSION = 2;

    public SavedSimulation {
        Objects.requireNonNull(config, "config");
        resources = List.copyOf(resources);
        agents = List.copyOf(agents);
    }
}
