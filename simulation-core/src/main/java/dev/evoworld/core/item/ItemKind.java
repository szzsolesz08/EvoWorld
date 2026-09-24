package dev.evoworld.core.item;

import dev.evoworld.core.resource.ResourceType;
import java.util.Optional;

/**
 * A kind of thing an agent can make and keep.
 *
 * <p>The difference between an item and a {@link ResourceType} is that a
 * resource is measured out - nine berries, four units of water - while an item
 * is one object that either you have or you do not, and that wears out with
 * use. Water is a resource; the skin you carry it in is an item.
 *
 * <p>An item earns its place by changing what the agent can do, and there are
 * only two ways of doing that here:
 *
 * <ul>
 *   <li>it <strong>holds</strong> something, which is what lets a resource be
 *       carried at all - you cannot take a spring with you, but you can take a
 *       skin of water;
 *   <li>it <strong>works on</strong> something, which makes gathering that
 *       thing cheaper and more rewarding than doing it with your hands.
 * </ul>
 *
 * <p>Nothing here says an item is worth having. What one is worth is something
 * the agent finds out by having one, the same way it finds out what berries
 * are worth by eating one.
 *
 * @param label what it is called
 * @param bulk how much room it takes in a pack, in the same units a pack holds
 * @param uses how many times it can be used before it is worn out
 */
public enum ItemKind {

    /** A carved flask: the only way to take water away from where water is. */
    FLASK("flask", 2.0, 200, ResourceType.WATER, 8.0, null, 1.0, 1.0),

    /** An axe: wood for less effort, and more of it per swing. */
    AXE("axe", 3.0, 40, null, 0.0, ResourceType.WOOD, 0.5, 1.6),

    /** A pick: the same bargain for stone. */
    PICKAXE("pickaxe", 3.0, 40, null, 0.0, ResourceType.STONE, 0.5, 1.6);

    private final String label;
    private final double bulk;
    private final int uses;
    /** Null rather than Optional: an enum constant cannot hold a generic well. */
    private final ResourceType holds;
    private final double holdsUpTo;
    private final ResourceType worksOn;
    private final double effortMultiplier;
    private final double yieldMultiplier;

    ItemKind(
            String label,
            double bulk,
            int uses,
            ResourceType holds,
            double holdsUpTo,
            ResourceType worksOn,
            double effortMultiplier,
            double yieldMultiplier) {
        this.label = label;
        this.bulk = bulk;
        this.uses = uses;
        this.holds = holds;
        this.holdsUpTo = holdsUpTo;
        this.worksOn = worksOn;
        this.effortMultiplier = effortMultiplier;
        this.yieldMultiplier = yieldMultiplier;
    }

    public String label() {
        return label;
    }

    public double bulk() {
        return bulk;
    }

    /** How many uses a new one has in it. */
    public int uses() {
        return uses;
    }

    /** What this can carry, if it carries anything. */
    public Optional<ResourceType> holds() {
        return Optional.ofNullable(holds);
    }

    /** How much of it one of these holds. */
    public double holdsUpTo() {
        return holdsUpTo;
    }

    /** What this is a tool for, if it is a tool. */
    public Optional<ResourceType> worksOn() {
        return Optional.ofNullable(worksOn);
    }

    /** What a gather costs with this in hand, as a share of what it costs without. */
    public double effortMultiplier() {
        return effortMultiplier;
    }

    /** How much more a gather yields with this in hand. */
    public double yieldMultiplier() {
        return yieldMultiplier;
    }

    /** The kind of thing made for carrying {@code type}, if there is one. */
    public static Optional<ItemKind> carrying(ResourceType type) {
        for (ItemKind kind : values()) {
            if (kind.carries(type)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }

    /** Whether this holds {@code type}. */
    public boolean carries(ResourceType type) {
        return holds == type;
    }

    /** Whether this helps with gathering {@code type}. */
    public boolean helpsWith(ResourceType type) {
        return worksOn == type;
    }

    @Override
    public String toString() {
        return label;
    }
}
