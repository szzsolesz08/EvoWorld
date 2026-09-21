package dev.evoworld.core.save;

import dev.evoworld.core.knowledge.BeliefKind;

/** One belief, with the count behind its confidence. */
public record SavedBelief(BeliefKind kind, String subject, double estimate, int observations) {
}
