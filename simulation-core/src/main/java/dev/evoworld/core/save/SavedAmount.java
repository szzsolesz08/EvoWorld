package dev.evoworld.core.save;

import dev.evoworld.core.resource.ResourceType;

/** A quantity of one thing carried. */
public record SavedAmount(ResourceType type, double amount) {
}
