package dev.evoworld.core.agent;

import java.util.Locale;

/**
 * The internal bodily state of an agent. Immutable: every change produces a new value.
 *
 * <p>Hunger and thirst are <em>pressures</em>: {@code 0} means satisfied and
 * {@link #MAX} means critical. Energy and health are <em>reserves</em>: {@link #MAX}
 * means full and {@code 0} means depleted. All values are clamped to [0, 100],
 * so no sequence of operations can put a body into an impossible state.
 *
 * <p>Changes go through named operations - {@link #eat}, {@link #drink},
 * {@link #exert}, {@link #rest}, {@link #heal}, {@link #harm} - rather than
 * generic setters. Every one of them takes a positive amount and moves the
 * value in the direction its name implies, so a caller cannot accidentally
 * feed an agent by passing a negative number to a setter.
 */
public record Needs(double hunger, double thirst, double energy, double health) {

    public static final double MIN = 0.0;
    public static final double MAX = 100.0;

    /** Above this, a pressure starts damaging health. */
    public static final double CRITICAL_PRESSURE = 95.0;

    /**
     * Below this, hunger or thirst is not felt at all: nobody eats or drinks,
     * or walks anywhere to, for a need they cannot yet notice. About two and a
     * half hours after a drink, or seven after a meal.
     */
    public static final double NOTICEABLE = 5.0;

    /** At or below this, hunger and thirst leave a body free to heal. */
    public static final double WELL_PRESSURE = 50.0;

    /** At or above this, a body has the energy to spare for healing. */
    public static final double WELL_ENERGY = 25.0;

    public Needs {
        hunger = clamp(hunger);
        thirst = clamp(thirst);
        energy = clamp(energy);
        health = clamp(health);
    }

    public static Needs healthy() {
        return new Needs(MIN, MIN, MAX, MAX);
    }

    public static double clamp(double value) {
        return Math.min(MAX, Math.max(MIN, value));
    }

    // --- state ------------------------------------------------------------

    public boolean isAlive() {
        return health > MIN;
    }

    public boolean isStarving() {
        return hunger >= CRITICAL_PRESSURE;
    }

    public boolean isDehydrated() {
        return thirst >= CRITICAL_PRESSURE;
    }

    public boolean isExhausted() {
        return energy <= MIN;
    }

    /**
     * Whether this body is looked after well enough to mend: alive, neither
     * hungry nor thirsty beyond half way, and not run down. Far from every
     * condition that does damage, so a body is never hurt and healed at once.
     */
    public boolean isWellEnoughToHeal() {
        return isAlive()
                && hunger <= WELL_PRESSURE
                && thirst <= WELL_PRESSURE
                && energy >= WELL_ENERGY;
    }

    public boolean isFullyHealthy() {
        return health >= MAX;
    }

    // --- changes ----------------------------------------------------------

    /** Food relieves hunger. */
    public Needs eat(double nourishment) {
        return new Needs(hunger - positive(nourishment, "nourishment"), thirst, energy, health);
    }

    /** Water relieves thirst. */
    public Needs drink(double hydration) {
        return new Needs(hunger, thirst - positive(hydration, "hydration"), energy, health);
    }

    /** Effort spends energy. */
    public Needs exert(double effort) {
        return new Needs(hunger, thirst, energy - positive(effort, "effort"), health);
    }

    /** Rest recovers energy. */
    public Needs rest(double recovery) {
        return new Needs(hunger, thirst, energy + positive(recovery, "recovery"), health);
    }

    public Needs heal(double amount) {
        return new Needs(hunger, thirst, energy, health + positive(amount, "amount"));
    }

    public Needs harm(double amount) {
        return new Needs(hunger, thirst, energy, health - positive(amount, "amount"));
    }

    /**
     * Applies one tick of metabolism: pressures rise, energy drains or comes
     * back depending on what the tick was spent doing, unmet needs take their
     * toll on health, and a body that is well looked after mends a little -
     * most while asleep.
     *
     * <p>{@code rates} are one tick's worth, and comfort scales only what comes
     * back: the energy recovered and the healing, never the cost.
     *
     * <p>A pure function of the current state, which keeps the rule
     * deterministic and directly testable.
     */
    public Needs afterTick(MetabolismRates rates, Exertion exertion) {
        double nextHunger = hunger + rates.hunger();
        double nextThirst = thirst + rates.thirst();
        double energyChange = rates.energyChangeFor(exertion.level());
        if (energyChange > 0.0) {
            energyChange *= exertion.comfort();
        }

        Needs drained = new Needs(nextHunger, nextThirst, energy + energyChange, health);
        Needs harmed = drained.harm(damageFrom(drained, rates));
        return harmed.isWellEnoughToHeal()
                ? harmed.heal(rates.healingFor(exertion.level()) * exertion.comfort())
                : harmed;
    }

    /** One tick at this level of activity, with no help. */
    public Needs afterTick(MetabolismRates rates, ActivityLevel activity) {
        return afterTick(rates, Exertion.of(activity));
    }

    /** Health lost this tick to needs that have gone unmet. */
    private static double damageFrom(Needs needs, MetabolismRates rates) {
        double damage = 0.0;
        if (needs.isStarving()) {
            damage += rates.starvation();
        }
        if (needs.isDehydrated()) {
            damage += rates.dehydration();
        }
        if (needs.isExhausted()) {
            damage += rates.exhaustion();
        }
        return damage;
    }

    private static double positive(double amount, String what) {
        if (amount < 0.0) {
            throw new IllegalArgumentException(what + " must not be negative, was " + amount);
        }
        return amount;
    }

    @Override
    public String toString() {
        return String.format(
                Locale.ROOT,
                "hunger=%.1f thirst=%.1f energy=%.1f health=%.1f",
                hunger, thirst, energy, health);
    }
}
