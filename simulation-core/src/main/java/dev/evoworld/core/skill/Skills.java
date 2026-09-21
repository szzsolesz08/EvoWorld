package dev.evoworld.core.skill;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * How practised an agent is at the things it does.
 *
 * <p>Deliberately modest. A skill is a count of successful practice, turned
 * into a proficiency with the same shape knowledge uses for confidence -
 * {@code n / (n + 10)} - and proficiency does exactly one thing: it makes the
 * work cheaper. A skilled forager spends up to half the energy a novice does
 * on the same haul. It does not make the agent faster, luckier or wiser; those
 * would be claims about the world that nothing here models.
 *
 * <p>Only actions that declare themselves {@link
 * dev.evoworld.core.action.Action#trainable() trainable} build skill. Eating
 * and resting are not things you get better at.
 *
 * <p>Immutable, like knowledge: practising produces new skills, which is what
 * lets the simulation compare before and after and say when something improved.
 */
public final class Skills {

    /** Successful practices that buy half of full proficiency. */
    public static final int PRACTICE_HALF_LIFE = 10;

    /** The most effort a fully proficient agent saves. */
    public static final double MAX_SAVING = 0.5;

    private final Map<String, Integer> practice;

    private Skills(Map<String, Integer> practice) {
        this.practice = Collections.unmodifiableMap(practice);
    }

    public static Skills untrained() {
        return new Skills(new LinkedHashMap<>());
    }

    /** Exactly this much practice, in this order - how a saved agent gets it back. */
    public static Skills of(Map<String, Integer> practice) {
        Map<String, Integer> copy = new LinkedHashMap<>();
        practice.forEach((skill, done) -> {
            if (done < 0) {
                throw new IllegalArgumentException("negative practice at " + skill);
            }
            copy.put(Objects.requireNonNull(skill, "skill"), done);
        });
        return new Skills(copy);
    }

    /** Every skill practised, and how often, in the order first taken up. */
    public Map<String, Integer> practice() {
        return practice;
    }

    public int practiceAt(String skill) {
        return practice.getOrDefault(skill, 0);
    }

    /** How good the agent is, from nothing towards one. */
    public double proficiency(String skill) {
        int done = practiceAt(skill);
        return (double) done / (done + PRACTICE_HALF_LIFE);
    }

    public SkillLevel level(String skill) {
        return SkillLevel.of(proficiency(skill));
    }

    /** What a unit of effort at this costs, as a fraction of what a novice pays. */
    public double effortMultiplier(String skill) {
        return 1.0 - MAX_SAVING * proficiency(skill);
    }

    /** The skills after one more successful go. */
    public Skills practised(String skill) {
        Objects.requireNonNull(skill, "skill");
        Map<String, Integer> next = new LinkedHashMap<>(practice);
        next.merge(skill, 1, Integer::sum);
        return new Skills(next);
    }

    public boolean isEmpty() {
        return practice.isEmpty();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Skills skills && practice.equals(skills.practice);
    }

    @Override
    public int hashCode() {
        return practice.hashCode();
    }

    @Override
    public String toString() {
        if (practice.isEmpty()) {
            return "no skills yet";
        }
        return practice.keySet().stream()
                .map(skill -> String.format(
                        Locale.ROOT,
                        "%s %s (%d done, %.0f%% less effort)",
                        skill.toLowerCase(Locale.ROOT),
                        level(skill).label(),
                        practiceAt(skill),
                        (1.0 - effortMultiplier(skill)) * 100))
                .collect(Collectors.joining(", "));
    }
}
