package dev.evoworld.core.skill;

/**
 * A name for how good an agent has become at something.
 *
 * <p>Purely a label over a continuous proficiency - the arithmetic never uses
 * it. It exists so that "got better at gathering" can be an event an observer
 * notices, rather than a number creeping up unremarked in the fourth decimal
 * place.
 */
public enum SkillLevel {

    NOVICE(0.0),
    APPRENTICE(0.25),
    COMPETENT(0.5),
    SKILLED(0.75);

    private final double threshold;

    SkillLevel(double threshold) {
        this.threshold = threshold;
    }

    public double threshold() {
        return threshold;
    }

    /** The highest level a proficiency has reached. */
    public static SkillLevel of(double proficiency) {
        SkillLevel reached = NOVICE;
        for (SkillLevel level : values()) {
            if (proficiency >= level.threshold) {
                reached = level;
            }
        }
        return reached;
    }

    public String label() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
