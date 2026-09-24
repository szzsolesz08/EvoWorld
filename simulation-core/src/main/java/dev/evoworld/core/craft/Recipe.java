package dev.evoworld.core.craft;

import dev.evoworld.core.item.ItemKind;
import dev.evoworld.core.resource.ResourceType;
import java.time.Duration;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * How one thing is made out of others.
 *
 * <p>Data rather than code, so that making something is one action with a
 * recipe in it rather than a special case per item. Times are in simulated
 * minutes, like everything else that takes a while, and turned into ticks for
 * the run they are used in.
 *
 * @param makes what comes out
 * @param needs what goes in, by resource and amount
 * @param minutes how long the work takes
 * @param energyCost what the work costs
 */
public record Recipe(
        ItemKind makes, Map<ResourceType, Double> needs, long minutes, double energyCost) {

    public Recipe {
        Objects.requireNonNull(makes, "makes");
        if (needs.isEmpty()) {
            throw new IllegalArgumentException("something has to go into " + makes.label());
        }
        if (minutes < 1) {
            throw new IllegalArgumentException("making something takes time, was " + minutes);
        }
        if (energyCost < 0.0) {
            throw new IllegalArgumentException("work cannot give energy back, was " + energyCost);
        }
        // Declaration order, so a recipe reads and saves the same way every run.
        Map<ResourceType, Double> copy = new EnumMap<>(ResourceType.class);
        needs.forEach((type, amount) -> {
            if (amount <= 0.0) {
                throw new IllegalArgumentException("a recipe cannot need " + amount + " " + type);
            }
            copy.put(type, amount);
        });
        needs = Collections.unmodifiableMap(copy);
    }

    /** How many ticks of {@code tick} the work occupies; never fewer than one. */
    public int ticks(Duration tick) {
        long seconds = Math.max(1L, tick.getSeconds());
        return (int) Math.max(1L, (minutes * 60L + seconds - 1) / seconds);
    }

    /** How much of everything this needs, in all - what it costs in pack space. */
    public double totalInput() {
        return needs.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    @Override
    public String toString() {
        return makes.label() + " from " + needs.entrySet().stream()
                .map(entry -> String.format(Locale.ROOT, "%.0f %s", entry.getValue(), entry.getKey().label()))
                .collect(Collectors.joining(" and "));
    }
}
