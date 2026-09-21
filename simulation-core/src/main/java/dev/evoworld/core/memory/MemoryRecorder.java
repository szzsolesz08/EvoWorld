package dev.evoworld.core.memory;

import dev.evoworld.core.SimulationTime;
import dev.evoworld.core.action.Action;
import dev.evoworld.core.action.ActionOutcome;
import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.exploration.Discovery;
import dev.evoworld.core.perception.PerceivedResource;
import dev.evoworld.core.perception.Perception;
import dev.evoworld.core.resource.ResourceType;
import dev.evoworld.core.world.Location;
import java.util.Locale;

/**
 * Turns what happens to an agent into things it remembers.
 *
 * <p>Kept apart from the store so that what is worth recording and how it is
 * kept stay separate questions. An agent that learns to remember different
 * things changes this class; one that forgets differently changes the store.
 *
 * <p>Nothing here is a judgement call at runtime: every memory it writes is a
 * direct consequence of something the agent perceived or did, with an
 * importance derived from the event rather than assigned by a rule.
 */
public final class MemoryRecorder {

    private MemoryRecorder() {
    }

    /**
     * Notices everything in sight.
     *
     * <p>Each resource yields two memories, and the split is the point: where
     * it is barely fades, while how much was in it goes stale within a day.
     * An agent ends up sure where the spring is and unsure whether it still
     * has water - which is exactly the state of knowledge that makes going to
     * look worthwhile.
     */
    public static void rememberSightings(MemoryStore memory, Perception perception) {
        SimulationTime now = perception.time();
        for (PerceivedResource seen : perception.resources()) {
            String subject = RecallQuery.subjectFor(seen.type());

            memory.remember(Memory.formed(
                            MemoryKind.RESOURCE_LOCATION,
                            now,
                            seen.location(),
                            subject,
                            seen.type().label() + " here")
                    .withImportance(importanceOf(seen.type())));

            memory.remember(Memory.formed(
                    MemoryKind.OBSERVED_RESOURCE,
                    now,
                    seen.location(),
                    subject,
                    String.format(Locale.ROOT, "%.1f %s", seen.quantity(), seen.type().label())));

            if (!seen.isAvailable()) {
                memory.remember(Memory.formed(
                        MemoryKind.WORLD_EVENT,
                        now,
                        seen.location(),
                        subject,
                        "the " + seen.type().label() + " here ran out"));
            }
        }
    }

    /** Remembers finding something for the first time. */
    public static void rememberDiscovery(MemoryStore memory, Discovery discovery) {
        memory.remember(Memory.formed(
                MemoryKind.DISCOVERY,
                discovery.when(),
                discovery.where(),
                RecallQuery.subjectFor(discovery.what()),
                discovery.describe()));
    }

    /**
     * Remembers how an attempt went, and where - if it is worth remembering.
     *
     * <p>Every failure is kept: being refused is information. A success is kept
     * only when it concerns something - a meal here, a drink there. A step
     * taken successfully is not a memory, it is walking, and recording one per
     * tile filled a bounded store with footsteps until there was no room left
     * for what the agent had discovered.
     */
    public static void rememberOutcome(
            MemoryStore memory,
            Action action,
            ActionOutcome outcome,
            Location where,
            SimulationTime now) {
        if (outcome.succeeded() && action.concerns().isEmpty()) {
            return;
        }
        MemoryKind kind =
                outcome.succeeded() ? MemoryKind.SUCCESSFUL_ACTION : MemoryKind.FAILED_ACTION;
        memory.remember(
                Memory.formed(kind, now, where, outcome.actionName(), outcome.detail()));
    }

    /**
     * Remembers a scare, weighted by how bad it was.
     *
     * <p>Importance rises with the share of health lost, so a scratch is filed
     * and nearly dying is unforgettable - which is also what keeps it in the
     * store when something has to be dropped.
     */
    public static void rememberHarm(
            MemoryStore memory,
            Location where,
            SimulationTime now,
            Needs needs,
            double healthLost) {
        if (healthLost <= 0.0) {
            return;
        }
        double severity = Math.min(1.0, healthLost / Math.max(needs.health() + healthLost, 1.0));
        memory.remember(Memory.formed(
                        MemoryKind.DANGER,
                        now,
                        where,
                        causeOf(needs),
                        String.format(
                                Locale.ROOT,
                                "lost %.1f health to %s, down to %.1f",
                                healthLost,
                                causeOf(needs).toLowerCase(Locale.ROOT),
                                needs.health()))
                .withImportance(
                        Math.min(1.0, MemoryKind.DANGER.baselineImportance() + severity / 2.0)));
    }

    /** What is currently hurting, as a stable subject an agent can be asked about. */
    private static String causeOf(Needs needs) {
        if (needs.isDehydrated()) {
            return "THIRST";
        }
        if (needs.isStarving()) {
            return "HUNGER";
        }
        if (needs.isExhausted()) {
            return "EXHAUSTION";
        }
        return "HARM";
    }

    /** Something an agent can eat or drink matters more than something it cannot yet use. */
    private static double importanceOf(ResourceType type) {
        double baseline = MemoryKind.RESOURCE_LOCATION.baselineImportance();
        return type.isEdible() || type.isDrinkable() ? baseline : baseline / 2.0;
    }
}
