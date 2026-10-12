package dev.evoworld.core.goal;

import dev.evoworld.core.perception.Perception;
import dev.evoworld.core.world.Fire;
import dev.evoworld.core.resource.ResourceType;
import java.util.Locale;

/**
 * The want to have something with you before you need it.
 *
 * <p>Every other goal answers a need the body is feeling now. This one answers
 * a need the body will feel later, and it is the only honest way to make
 * carrying worth anything: a skin of water relieves no thirst while the agent
 * is standing in a spring, so an agent that valued everything against its
 * current thirst would never fill one, walk away, and be glad of it.
 *
 * <p>It is a drive, not a plan. It says "I would rather have water on me than
 * not", the way curiosity says "I would rather see somewhere new"; it does not
 * know where water is, when thirst will come, or what a waterskin is. What
 * satisfies it is left entirely to the proposers: filling a skin at a spring
 * and making a skin in the first place both serve it, and the evaluator picks
 * between them on the usual terms.
 *
 * <p>An agent with nothing to carry water in is as unprovisioned as an agent
 * with an empty skin - which is what gives it a reason to make one, without
 * anybody telling it that a waterskin is a good idea.
 */
public final class StayProvisionedGoal extends Goal {

    public static final String NAME = "STAY_PROVISIONED";

    /**
     * What a person will do about tomorrow's thirst while today's is quiet.
     *
     * <p>Small on purpose. Provisioning is a background want, not a rival to
     * eating: at a third it outbid hunger often enough that Atlas spent his
     * days fetching wood and making flasks, and starved with a full one on his
     * belt. A want that answers a need you do not yet have should lose every
     * argument with a need you do.
     */
    public static final double DEFAULT_PRUDENCE = 0.30;

    /** What the agent likes to have with it. */
    public static final ResourceType PROVISION = ResourceType.WATER;

    /**
     * And a little firewood, which is the other thing a night asks for.
     *
     * <p>Added when fire arrived. Wood fetched only once the cold had already
     * bitten was wood fetched in the dark, a handful at a time, and all of it
     * went straight on the fire - so nothing was ever left to make a flask
     * out of. Keeping a night's worth about you is what a person does, and it
     * is the same want as carrying water: having what tonight will need.
     */
    public static final double FIREWOOD = KeepWarmGoal.A_NIGHT * Fire.WOOD_PER_HOUR;

    /**
     * How much of it counts as being provisioned: about a day's drinking.
     *
     * <p>A target rather than "as much as I can hold", and the difference
     * matters. Measured against capacity, an agent with an empty second flask
     * looks worse off than one with a single full one, so it makes flask after
     * flask and is never satisfied. Measured against what a day needs, a full
     * flask answers the want and a second one answers nothing.
     */
    public static final double ENOUGH = 4.0;

    private final double prudence;

    public StayProvisionedGoal() {
        this(DEFAULT_PRUDENCE);
    }

    public StayProvisionedGoal(double prudence) {
        if (prudence < MIN_PRIORITY || prudence > MAX_PRIORITY) {
            throw new IllegalArgumentException("prudence must be within [0, 1], was " + prudence);
        }
        this.prudence = prudence;
    }

    public double prudence() {
        return prudence;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    protected double computePriority(Perception perception) {
        // Quiet while something is actually wrong, exactly as curiosity is.
        // Providing for tomorrow is a thing you do when today is in hand; an
        // agent that fetched wood for a flask while its hunger climbed died
        // with a full flask on its belt, which is a very silly way to die.
        return prudence * shortfall(perception) * ExploreGoal.calm(perception.needs());
    }

    /**
     * How far short of a day's supply the agent is, from 0 to 1.
     *
     * <p>An agent with nothing to carry water in is as short as one with an
     * empty flask - which is right, and is what gives it a reason to make one
     * without anybody telling it that a flask is a good idea.
     */
    public static double shortfall(Perception perception) {
        return Math.max(
                shortfallOf(perception, PROVISION, ENOUGH),
                WOOD_MATTERS_LESS * firewoodShortfall(perception));
    }

    /**
     * How much less an empty woodpile weighs than an empty flask.
     *
     * <p>A night without a fire is uncomfortable; a day without water is
     * dangerous. Counted equally, an agent in hard country - where wood is
     * thin - spent its days hunting for firewood it could not find, and one
     * of them died of thirst doing it.
     */
    private static final double WOOD_MATTERS_LESS = 0.6;

    /** How far short of a night's firewood the agent is. */
    public static double firewoodShortfall(Perception perception) {
        return shortfallOf(perception, ResourceType.WOOD, FIREWOOD);
    }

    private static double shortfallOf(Perception perception, ResourceType type, double enough) {
        double carried = perception.inventory().quantityOf(type);
        return Math.max(0.0, 1.0 - Math.min(carried, enough) / enough);
    }

    @Override
    protected String reasonFrom(Perception perception) {
        if (perception.belongings().roomFor(PROVISION) <= 0.0) {
            return "nothing to carry " + PROVISION.label() + " in";
        }
        return String.format(
                Locale.ROOT,
                "carrying %.1f %s of a day's %.1f",
                perception.inventory().quantityOf(PROVISION),
                PROVISION.label(),
                ENOUGH);
    }
}
