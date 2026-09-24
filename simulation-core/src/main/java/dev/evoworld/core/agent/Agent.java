package dev.evoworld.core.agent;

import dev.evoworld.core.exploration.ExplorationMap;
import dev.evoworld.core.intent.Intent;
import dev.evoworld.core.item.Belongings;
import dev.evoworld.core.item.Item;
import dev.evoworld.core.item.ItemKind;
import dev.evoworld.core.knowledge.Knowledge;
import dev.evoworld.core.memory.MemoryStore;
import dev.evoworld.core.resource.ResourceType;
import dev.evoworld.core.skill.Skills;
import dev.evoworld.core.world.AgentSnapshot;
import dev.evoworld.core.world.Location;
import java.util.Objects;
import java.util.Optional;

/**
 * A living entity in the world.
 *
 * <p>The agent is the only deliberately mutable part of the domain: it is an
 * identity that persists while its state changes. Everything it holds - its
 * {@link Needs}, its {@link Inventory}, its {@link Location} - is an immutable
 * value, so a snapshot is always safe to hand out.
 *
 * <p>State changes go through named domain methods. There is no
 * {@code setNeeds}: an action says {@link #exert(double)} or {@link #rest}, and
 * the passage of time says {@link #live}. That keeps every way an agent can
 * change to a short, readable list.
 */
public final class Agent {

    private final AgentId id;
    private final String name;
    private Location location;
    private Needs needs;
    private Inventory inventory;
    private Belongings belongings = Belongings.nothing();
    /** What the pack holds with nothing in the kit; the kit takes its room out of this. */
    private double packSize;
    private Knowledge knowledge = Knowledge.innocent();
    private Skills skills = Skills.untrained();
    private final MemoryStore memory;
    private final ExplorationMap exploration;
    private long ticksAlive;
    private Optional<Intent> intent = Optional.empty();

    public Agent(AgentId id, String name, Location location, Needs needs, Inventory inventory) {
        this(id, name, location, needs, inventory, new MemoryStore(), new ExplorationMap());
    }

    private Agent(
            AgentId id,
            String name,
            Location location,
            Needs needs,
            Inventory inventory,
            MemoryStore memory,
            ExplorationMap exploration) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = requireName(name);
        this.location = Objects.requireNonNull(location, "location");
        this.needs = Objects.requireNonNull(needs, "needs");
        this.inventory = Objects.requireNonNull(inventory, "inventory");
        this.packSize = inventory.capacity();
        this.memory = Objects.requireNonNull(memory, "memory");
        this.exploration = Objects.requireNonNull(exploration, "exploration");
    }

    /**
     * An agent exactly as it was saved: body, age, mind and all.
     *
     * <p>The only way to set an agent's age, and the only way to hand it a
     * memory it did not form itself. Both are right for a saved agent coming
     * back and wrong for anything else, which is why this is a separate door.
     */
    public static Agent restored(
            AgentId id,
            String name,
            Location location,
            Needs needs,
            Inventory inventory,
            long ticksAlive,
            Knowledge knowledge,
            Skills skills,
            MemoryStore memory,
            ExplorationMap exploration,
            Optional<Intent> intent,
            Belongings belongings) {
        if (ticksAlive < 0) {
            throw new IllegalArgumentException("age must not be negative, was " + ticksAlive);
        }
        Agent agent = new Agent(id, name, location, needs, inventory, memory, exploration);
        agent.ticksAlive = ticksAlive;
        agent.belongings = Objects.requireNonNull(belongings, "belongings");
        // The saved pack was already short by what the kit takes up, so the
        // full size has to be put back before the kit is charged for again -
        // otherwise every save and load would shrink it a little further.
        agent.packSize = inventory.capacity() + belongings.bulk();
        agent.knowledge = Objects.requireNonNull(knowledge, "knowledge");
        agent.skills = Objects.requireNonNull(skills, "skills");
        agent.intent = Objects.requireNonNull(intent, "intent");
        return agent;
    }

    public Agent(AgentId id, String name, Location location, Needs needs) {
        this(id, name, location, needs, Inventory.empty());
    }

    /** A new, healthy, empty-handed agent. */
    public static Agent spawn(String id, String name, Location location) {
        return new Agent(AgentId.of(id), name, location, Needs.healthy(), Inventory.empty());
    }

    private static String requireName(String name) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("an agent needs a name");
        }
        return name;
    }

    // --- identity and state -----------------------------------------------

    public AgentId id() {
        return id;
    }

    /** What to call this agent in a log; the {@link #id()} is what identifies it. */
    public String name() {
        return name;
    }

    public Location location() {
        return location;
    }

    public Needs needs() {
        return needs;
    }

    /** What the agent has on it: containers, tools, whatever it has made. */
    public Belongings belongings() {
        return belongings;
    }

    /**
     * Takes something new into the kit, at the cost of room in the pack.
     *
     * <p>Anything that no longer fits is dropped rather than quietly vanishing:
     * a pack is a pack, and picking up an axe with your hands full means
     * putting something down.
     */
    public void acquire(Item item) {
        this.belongings = belongings.plus(Objects.requireNonNull(item, "item"));
        this.inventory = inventory.withCapacity(packCapacity());
    }

    /**
     * Wears one of these by a use, and throws it away if that finished it.
     *
     * <p>Losing a container can leave the agent carrying more than it has
     * anything to carry it in, so what will not fit is spilled here.
     */
    public void use(ItemKind kind) {
        Belongings used = belongings.after(kind);
        this.belongings = used.withoutWornOut();
        this.inventory = inventory.withCapacity(packCapacity());
        spillWhatCannotBeCarried();
    }

    /** What is left of the pack once the kit has taken its room. */
    private double packCapacity() {
        return Math.max(Inventory.MINIMUM_CAPACITY, packSize - belongings.bulk());
    }

    /** Pours away anything the agent no longer has a container for. */
    private void spillWhatCannotBeCarried() {
        for (ResourceType type : ResourceType.values()) {
            if (!type.needsContainer()) {
                continue;
            }
            double spare = inventory.quantityOf(type) - belongings.roomFor(type);
            if (spare > 0.0) {
                inventory = inventory.minus(type, spare);
            }
        }
    }

    public Inventory inventory() {
        return inventory;
    }

    /**
     * What this agent has worked out about how the world works.
     *
     * <p>Starts empty. An agent is born knowing nothing about what berries do,
     * and finds out the same way anyone would.
     */
    public Knowledge knowledge() {
        return knowledge;
    }

    /** How practised this agent is at the things it does. */
    public Skills skills() {
        return skills;
    }

    /** Replaces the agent's skills, having practised something. */
    public void updateSkills(Skills practised) {
        this.skills = Objects.requireNonNull(practised, "practised");
    }

    /** Replaces what the agent believes, having learned something. */
    public void updateKnowledge(Knowledge learned) {
        this.knowledge = Objects.requireNonNull(learned, "learned");
    }

    /**
     * How much of the world this agent has seen, and how recently.
     *
     * <p>Coverage rather than episodes or claims: the third standing thing an
     * agent carries, and what makes exploring different from wandering.
     */
    public ExplorationMap exploration() {
        return exploration;
    }

    /**
     * What this agent remembers.
     *
     * <p>Lives on the agent because it is the agent's, and dies with it. The
     * body and the mind are separate objects but the same creature.
     */
    public MemoryStore memory() {
        return memory;
    }

    /** Where this agent has set out for, if anywhere. */
    public Optional<Intent> intent() {
        return intent;
    }

    /** How many ticks this agent has been alive. Stops when it dies. */
    public long ticksAlive() {
        return ticksAlive;
    }

    public boolean isAlive() {
        return needs.isAlive();
    }

    // --- what an agent can do ---------------------------------------------

    public void moveTo(Location destination) {
        this.location = Objects.requireNonNull(destination, "destination");
    }

    /** Spends energy on effort. */
    public void exert(double effort) {
        this.needs = needs.exert(effort);
    }

    /** Recovers energy. */
    public void rest(double recovery) {
        this.needs = needs.rest(recovery);
    }

    /** Relieves hunger. */
    public void eat(double nourishment) {
        this.needs = needs.eat(nourishment);
    }

    /** Relieves thirst. */
    public void drink(double hydration) {
        this.needs = needs.drink(hydration);
    }

    /**
     * Puts what fits into the inventory.
     *
     * @return how much was actually stored, which is less than offered when the
     *     agent is already loaded
     */
    public double store(ResourceType type, double amount) {
        Inventory updated = inventory.plus(type, Math.min(amount, roomFor(type)));
        double stored = updated.total() - inventory.total();
        this.inventory = updated;
        return stored;
    }

    /**
     * How much more of something the agent could take.
     *
     * <p>Some things cannot be held in your hands: without a container there is
     * no room for water however empty the pack is, which is what makes a
     * waterskin worth having rather than a decoration.
     */
    public double roomFor(ResourceType type) {
        return snapshot().roomFor(type);
    }

    /**
     * Takes items back out of the inventory.
     *
     * @return how much was actually removed, which is less than asked for when
     *     the agent is not carrying that much
     */
    public double take(ResourceType type, double amount) {
        Inventory updated = inventory.minus(type, amount);
        double taken = inventory.total() - updated.total();
        this.inventory = updated;
        return taken;
    }

    /** Sets out for somewhere, putting aside whatever it was heading for before. */
    public void commitTo(Intent newIntent) {
        this.intent = Optional.of(Objects.requireNonNull(newIntent, "intent"));
    }

    /** Stops heading anywhere in particular: arrived, stuck, or dead. */
    public void giveUpIntent() {
        this.intent = Optional.empty();
    }

    // --- what time does to an agent ---------------------------------------

    /**
     * One tick of simply existing: metabolism runs at the cost of whatever the
     * agent was doing, and the agent gets a tick older.
     *
     * <p>A dead agent does not age further, so {@link #ticksAlive()} stays the
     * length of the life rather than the length of the run.
     */
    public void live(MetabolismRates rates, Exertion exertion) {
        Objects.requireNonNull(rates, "rates");
        Objects.requireNonNull(exertion, "exertion");
        if (!isAlive()) {
            return;
        }
        this.needs = needs.afterTick(rates, exertion);
        this.ticksAlive++;
    }

    /** An immutable copy of everything observable about this agent right now. */
    public AgentSnapshot snapshot() {
        return new AgentSnapshot(
                id, name, location, needs, inventory, belongings, ticksAlive, isAlive(), intent);
    }

    @Override
    public String toString() {
        return name + "[" + id + " at " + location + " " + needs + ", " + inventory
                + ", age " + ticksAlive + " ticks]";
    }
}
