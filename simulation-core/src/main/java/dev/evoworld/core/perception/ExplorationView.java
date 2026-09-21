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
        int regionCount) {

    public static final ExplorationView NOTHING_SEEN =
            new ExplorationView(List.of(), 0.0, 0, 0);

    public ExplorationView {
        frontier = List.copyOf(frontier);
    }

    /** How much of the world the agent has lost track of, or never had. */
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
