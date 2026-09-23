package dev.evoworld.runner.survey;

import dev.evoworld.core.Simulation;
import dev.evoworld.core.SimulationConfig;
import dev.evoworld.core.agent.Agent;
import dev.evoworld.core.agent.AgentId;
import dev.evoworld.core.agent.MetabolismRates;
import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.decision.DeliberativeDecisionMaker;
import dev.evoworld.core.world.ChunkMix;
import dev.evoworld.core.world.WorldBounds;
import dev.evoworld.core.world.World;
import dev.evoworld.core.world.WorldGenerator;
import dev.evoworld.core.world.WorldGenerator.ResourcePlan;
import dev.evoworld.runner.EvoWorldRunner;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongFunction;

/**
 * A named situation to put an agent in, the same way on every seed.
 *
 * <p>The baseline is the plain run the console gives you. The others each lean
 * on one thing - a wound, a hungrier body, a barer world - so a change that
 * helps in one place and hurts in another shows up as a row that moved.
 *
 * @param name how it is asked for on the command line
 * @param description what it stresses, for the report
 * @param defaultTicks how long it runs unless told otherwise
 * @param config the configuration for a given seed
 * @param startingAs the agent's body at the start
 * @param resources what the world is stocked with
 */
public record Scenario(
        String name,
        String description,
        long defaultTicks,
        LongFunction<SimulationConfig> config,
        Needs startingAs,
        List<ResourcePlan> resources) {

    /** Twenty simulated days at the default ten minutes a tick. */
    public static final long TWENTY_DAYS = 2880L;

    public static final Scenario BASELINE = new Scenario(
            "baseline",
            "the default run: 10-minute ticks, 20 days",
            TWENTY_DAYS,
            SimulationConfig::defaults,
            Needs.healthy(),
            WorldGenerator.DEFAULT_PLAN);

    public static final Scenario HOURLY = new Scenario(
            "hourly",
            "60-minute ticks, 2,000 of them (83 days)",
            2000L,
            seed -> SimulationConfig.defaults(seed).withTickDuration(Duration.ofMinutes(60)),
            Needs.healthy(),
            WorldGenerator.DEFAULT_PLAN);

    public static final Scenario WOUNDED = new Scenario(
            "wounded",
            "starts at health 30, fed and rested",
            TWENTY_DAYS,
            SimulationConfig::defaults,
            new Needs(Needs.MIN, Needs.MIN, Needs.MAX, 30.0),
            WorldGenerator.DEFAULT_PLAN);

    public static final Scenario HUNGRY_WORLD = new Scenario(
            "hungry-world",
            "hunger and thirst rise 1.5 times as fast",
            TWENTY_DAYS,
            seed -> withMetabolism(SimulationConfig.defaults(seed), hungrier(1.5)),
            Needs.healthy(),
            WorldGenerator.DEFAULT_PLAN);

    public static final Scenario SPARSE = new Scenario(
            "sparse",
            "hard country: chunks half as rich, and more variable",
            TWENTY_DAYS,
            seed -> SimulationConfig.defaults(seed)
                    .withWorld(WorldBounds.endless(), ChunkMix.lean()),
            Needs.healthy(),
            WorldGenerator.DEFAULT_PLAN);

    /** The old world: sixteen tiles square, with walls, generated all at once. */
    public static final Scenario WALLED = new Scenario(
            "walled",
            "a finite 16x16 world, generated whole",
            TWENTY_DAYS,
            seed -> SimulationConfig.defaults(seed)
                    .withWorld(WorldBounds.of(16, 16), ChunkMix.standard()),
            Needs.healthy(),
            WorldGenerator.DEFAULT_PLAN);

    /** Every scenario, in report order. */
    public static final List<Scenario> ALL =
            List.of(BASELINE, HOURLY, WOUNDED, HUNGRY_WORLD, SPARSE, WALLED);

    public Scenario {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(startingAs, "startingAs");
        resources = List.copyOf(resources);
        if (defaultTicks < 1) {
            throw new IllegalArgumentException("a scenario must run at least one tick");
        }
    }

    public static Optional<Scenario> named(String name) {
        return ALL.stream().filter(scenario -> scenario.name().equals(name)).findFirst();
    }

    /**
     * The run for one seed: the same world, agent and mind the console would
     * build, apart from what this scenario changes.
     */
    public Simulation build(long seed) {
        return build(config.apply(seed));
    }

    /**
     * The run for a configuration derived from this scenario's, such as the
     * viewer's with a different tick length.
     */
    public Simulation build(SimulationConfig chosen) {
        World world = chosen.bounds().isEndless()
                ? WorldGenerator.endless(chosen)
                : WorldGenerator.generate(chosen, resources);
        world.addAgent(new Agent(
                AgentId.of("agent-1"),
                EvoWorldRunner.FIRST_AGENT_NAME,
                chosen.bounds().center(),
                startingAs));
        return new Simulation(world, DeliberativeDecisionMaker.from(chosen), chosen);
    }

    static SimulationConfig withMetabolism(SimulationConfig config, MetabolismRates body) {
        return new SimulationConfig(
                config.seed(),
                config.tickDuration(),
                config.bounds(),
                config.chunks(),
                config.perceptionRadius(),
                body,
                config.actions());
    }

    /** The default body, with hunger and thirst rising {@code factor} times as fast. */
    static MetabolismRates hungrier(double factor) {
        MetabolismRates normal = MetabolismRates.defaults();
        return new MetabolismRates(
                normal.hunger() * factor,
                normal.thirst() * factor,
                normal.activeEnergy(),
                normal.restingEnergy(),
                normal.sleepingEnergy(),
                normal.starvation(),
                normal.dehydration(),
                normal.exhaustion(),
                normal.activeHealing(),
                normal.restingHealing(),
                normal.sleepingHealing());
    }

    /** Half of every kind of resource, but never none of one. */
    static List<ResourcePlan> halved(List<ResourcePlan> plan) {
        return plan.stream()
                .map(each -> new ResourcePlan(each.type(), Math.max(1, each.nodeCount() / 2)))
                .toList();
    }
}
