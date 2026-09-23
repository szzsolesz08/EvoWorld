package dev.evoworld.core.perception;

import java.util.List;

/**
 * What the agent can tell about how much of the world it has seen.
 *
 * <p>The part of a perception that is neither sensed nor remembered but simply
 * kept: coverage. It is what turns wandering into exploring - an agent with
 * this can head for the corner it has not visited instead of a tile picked at
 * random.
 *
 * @param frontier the least familiar places, nearest-to-unknown first
 * @param worldFamiliarity how much of the world feels currently known, 0 to 1
 */
public record ExplorationView(
        List<UnexploredArea> frontier,
        double worldFamiliarity,
        int regionsVisited,
        int regionCount,
        List<KnownPlenty> plenty) {

    public static final ExplorationView NOTHING_SEEN =
            new ExplorationView(List.of(), 0.0, 0, 0, List.of());

    public ExplorationView {
        frontier = List.copyOf(frontier);
        plenty = List.copyOf(plenty);
    }

    /** Without any memory of plenty; for tests and for a mind that has seen nothing yet. */
    public ExplorationView(
            List<UnexploredArea> frontier, double worldFamiliarity, int regionsVisited, int regionCount) {
        this(frontier, worldFamiliarity, regionsVisited, regionCount, List.of());
    }

    /** How much of the country around it the agent has lost track of, or never had. */
    public double unexploredFraction() {
        return 1.0 - worldFamiliarity;
    }

    public boolean hasSomewhereToGo() {
        return !frontier.isEmpty();
    }

    @Override
    public String toString() {
        return String.format(
                java.util.Locale.ROOT,
                "knows %.0f%% of %d regions (%d visited)",
                worldFamiliarity * 100,
                regionCount,
                regionsVisited);
    }
}
