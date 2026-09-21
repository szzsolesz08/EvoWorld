package dev.evoworld.core.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NeedsTest {

    private static final double EPSILON = 1e-9;
    private final MetabolismRates rates = MetabolismRates.defaults();

    @Nested
    @DisplayName("valid ranges")
    class ValidRanges {

        @Test
        @DisplayName("values out of range are clamped rather than rejected")
        void clampsOutOfRangeValues() {
            Needs needs = new Needs(-10.0, 250.0, -1.0, 101.0);

            assertEquals(Needs.MIN, needs.hunger(), EPSILON);
            assertEquals(Needs.MAX, needs.thirst(), EPSILON);
            assertEquals(Needs.MIN, needs.energy(), EPSILON);
            assertEquals(Needs.MAX, needs.health(), EPSILON);
        }

        @Test
        @DisplayName("no amount of eating drops hunger below zero")
        void cannotOverfeed() {
            Needs needs = new Needs(2.0, 0.0, 50.0, 50.0).eat(1000.0);

            assertEquals(Needs.MIN, needs.hunger(), EPSILON);
        }

        @Test
        @DisplayName("no amount of resting pushes energy past full")
        void cannotOverRest() {
            Needs needs = Needs.healthy().rest(1000.0);

            assertEquals(Needs.MAX, needs.energy(), EPSILON);
        }

        @Test
        @DisplayName("no amount of healing pushes health past full")
        void cannotOverheal() {
            Needs needs = new Needs(0.0, 0.0, 50.0, 90.0).heal(1000.0);

            assertEquals(Needs.MAX, needs.health(), EPSILON);
        }

        @Test
        @DisplayName("exhaustion and injury stop at zero, not below")
        void cannotGoBelowEmpty() {
            Needs drained = Needs.healthy().exert(1000.0).harm(1000.0);

            assertEquals(Needs.MIN, drained.energy(), EPSILON);
            assertEquals(Needs.MIN, drained.health(), EPSILON);
        }

        @Test
        @DisplayName("hundreds of random-looking changes never leave the legal range")
        void staysInRangeUnderRepeatedChange() {
            Needs needs = Needs.healthy();

            for (int i = 0; i < 200; i++) {
                needs = needs.afterTick(rates, ActivityLevel.ACTIVE).eat(i % 7).drink(i % 5).rest(i % 3);
                assertInRange(needs);
            }
        }

        private void assertInRange(Needs needs) {
            assertTrue(needs.hunger() >= Needs.MIN && needs.hunger() <= Needs.MAX, "hunger " + needs);
            assertTrue(needs.thirst() >= Needs.MIN && needs.thirst() <= Needs.MAX, "thirst " + needs);
            assertTrue(needs.energy() >= Needs.MIN && needs.energy() <= Needs.MAX, "energy " + needs);
            assertTrue(needs.health() >= Needs.MIN && needs.health() <= Needs.MAX, "health " + needs);
        }

        @Test
        @DisplayName("domain methods refuse negative amounts rather than working in reverse")
        void rejectsNegativeAmounts() {
            Needs needs = new Needs(50.0, 50.0, 50.0, 50.0);

            assertThrows(IllegalArgumentException.class, () -> needs.eat(-1.0));
            assertThrows(IllegalArgumentException.class, () -> needs.drink(-1.0));
            assertThrows(IllegalArgumentException.class, () -> needs.exert(-1.0));
            assertThrows(IllegalArgumentException.class, () -> needs.rest(-1.0));
            assertThrows(IllegalArgumentException.class, () -> needs.heal(-1.0));
            assertThrows(IllegalArgumentException.class, () -> needs.harm(-1.0));
        }
    }

    @Nested
    @DisplayName("individual transitions")
    class Transitions {

        private final Needs middling = new Needs(50.0, 50.0, 50.0, 50.0);

        @Test
        void eatingRelievesHungerAndNothingElse() {
            Needs after = middling.eat(20.0);

            assertEquals(30.0, after.hunger(), EPSILON);
            assertEquals(50.0, after.thirst(), EPSILON);
            assertEquals(50.0, after.energy(), EPSILON);
            assertEquals(50.0, after.health(), EPSILON);
        }

        @Test
        void drinkingRelievesThirstAndNothingElse() {
            Needs after = middling.drink(20.0);

            assertEquals(50.0, after.hunger(), EPSILON);
            assertEquals(30.0, after.thirst(), EPSILON);
            assertEquals(50.0, after.energy(), EPSILON);
        }

        @Test
        void exertionSpendsEnergyAndNothingElse() {
            Needs after = middling.exert(15.0);

            assertEquals(35.0, after.energy(), EPSILON);
            assertEquals(50.0, after.hunger(), EPSILON);
            assertEquals(50.0, after.health(), EPSILON);
        }

        @Test
        void restRecoversEnergyAndNothingElse() {
            Needs after = middling.rest(15.0);

            assertEquals(65.0, after.energy(), EPSILON);
            assertEquals(50.0, after.hunger(), EPSILON);
            assertEquals(50.0, after.thirst(), EPSILON);
        }

        @Test
        void healingAndHarmMoveHealthOnly() {
            assertEquals(60.0, middling.heal(10.0).health(), EPSILON);
            assertEquals(40.0, middling.harm(10.0).health(), EPSILON);
            assertEquals(50.0, middling.harm(10.0).energy(), EPSILON);
        }

        @Test
        @DisplayName("a change produces a new value and leaves the original alone")
        void changesDoNotMutate() {
            Needs original = Needs.healthy();

            Needs spent = original.exert(10.0);

            assertEquals(Needs.MAX, original.energy(), EPSILON);
            assertEquals(Needs.MAX - 10.0, spent.energy(), EPSILON);
        }

        @Test
        @DisplayName("a zero-sized change is a no-op, not an error")
        void zeroSizedChangesDoNothing() {
            assertEquals(middling, middling.eat(0.0));
            assertEquals(middling, middling.exert(0.0));
            assertEquals(middling, middling.harm(0.0));
        }
    }

    @Nested
    @DisplayName("starting state and thresholds")
    class State {

        @Test
        @DisplayName("a healthy agent starts satisfied and at full reserves")
        void healthyStartingState() {
            Needs needs = Needs.healthy();

            assertEquals(Needs.MIN, needs.hunger(), EPSILON);
            assertEquals(Needs.MIN, needs.thirst(), EPSILON);
            assertEquals(Needs.MAX, needs.energy(), EPSILON);
            assertEquals(Needs.MAX, needs.health(), EPSILON);
            assertTrue(needs.isAlive());
            assertFalse(needs.isStarving());
            assertFalse(needs.isDehydrated());
            assertFalse(needs.isExhausted());
        }

        @Test
        @DisplayName("starving and dehydrated begin at the critical pressure")
        void criticalThresholds() {
            assertTrue(new Needs(Needs.CRITICAL_PRESSURE, 0, 50, 50).isStarving());
            assertFalse(new Needs(Needs.CRITICAL_PRESSURE - 0.1, 0, 50, 50).isStarving());
            assertTrue(new Needs(0, Needs.CRITICAL_PRESSURE, 50, 50).isDehydrated());
            assertFalse(new Needs(0, Needs.CRITICAL_PRESSURE - 0.1, 50, 50).isDehydrated());
        }

        @Test
        @DisplayName("exhaustion means no energy at all")
        void exhaustionIsEmptyEnergy() {
            assertTrue(new Needs(0, 0, 0.0, 50).isExhausted());
            assertFalse(new Needs(0, 0, 0.1, 50).isExhausted());
        }

        @Test
        @DisplayName("an agent is dead once health reaches zero")
        void deathAtZeroHealth() {
            assertFalse(new Needs(0.0, 0.0, 50.0, 0.0).isAlive());
            assertTrue(new Needs(0.0, 0.0, 50.0, 0.1).isAlive());
        }
    }

    @Nested
    @DisplayName("metabolism over a tick")
    class Metabolism {

        @Test
        @DisplayName("a tick makes the agent hungrier and thirstier")
        void pressuresRiseEveryTick() {
            Needs after = Needs.healthy().afterTick(rates, ActivityLevel.ACTIVE);

            assertEquals(rates.hungerPerTick(), after.hunger(), EPSILON);
            assertEquals(rates.thirstPerTick(), after.thirst(), EPSILON);
            assertEquals(Needs.MAX, after.health(), EPSILON, "health is untouched while needs are met");
        }

        @Test
        @DisplayName("hunger and thirst rise at the same rate whatever the agent is doing")
        void activityDoesNotChangeHungerOrThirst() {
            Needs active = Needs.healthy().afterTick(rates, ActivityLevel.ACTIVE);
            Needs resting = Needs.healthy().afterTick(rates, ActivityLevel.RESTING);

            assertEquals(active.hunger(), resting.hunger(), EPSILON);
            assertEquals(active.thirst(), resting.thirst(), EPSILON);
        }

        @Test
        @DisplayName("being active costs more energy than resting")
        void activityDecidesTheEnergyCost() {
            Needs active = Needs.healthy().afterTick(rates, ActivityLevel.ACTIVE);
            Needs resting = Needs.healthy().afterTick(rates, ActivityLevel.RESTING);

            assertEquals(Needs.MAX - rates.activeEnergyPerTick(), active.energy(), EPSILON);
            assertEquals(Needs.MAX - rates.restingEnergyPerTick(), resting.energy(), EPSILON);
            assertTrue(resting.energy() > active.energy(), "resting must be the cheaper option");
        }

        @Test
        @DisplayName("resting recovers more than a tick of rest costs, so it is worth doing")
        void restingIsANetGain() {
            double restGain = 6.0;
            Needs tired = new Needs(0.0, 0.0, 50.0, 100.0);

            Needs after = tired.rest(restGain).afterTick(rates, ActivityLevel.RESTING);

            assertTrue(after.energy() > tired.energy(), "a tick spent resting should leave more energy");
        }

        @Test
        @DisplayName("critical hunger and thirst both cost health in the same tick")
        void unmetNeedsDamageHealth() {
            Needs starving = new Needs(Needs.MAX, Needs.MAX, 50.0, Needs.MAX);

            Needs after = starving.afterTick(rates, ActivityLevel.ACTIVE);

            double expectedDamage = rates.starvationDamage() + rates.dehydrationDamage();
            assertEquals(Needs.MAX - expectedDamage, after.health(), EPSILON);
        }

        @Test
        @DisplayName("running out of energy costs health too")
        void exhaustionDamagesHealth() {
            Needs drained = new Needs(0.0, 0.0, 0.2, Needs.MAX);

            Needs after = drained.afterTick(rates, ActivityLevel.ACTIVE);

            assertEquals(Needs.MIN, after.energy(), EPSILON);
            assertEquals(Needs.MAX - rates.exhaustionDamage(), after.health(), EPSILON);
        }

        @Test
        @DisplayName("all three sources of damage stack")
        void damageSourcesStack() {
            Needs wretched = new Needs(Needs.MAX, Needs.MAX, 0.0, Needs.MAX);

            Needs after = wretched.afterTick(rates, ActivityLevel.ACTIVE);

            double expected = rates.starvationDamage() + rates.dehydrationDamage()
                    + rates.exhaustionDamage();
            assertEquals(Needs.MAX - expected, after.health(), EPSILON);
        }

        @Test
        @DisplayName("damage is judged on the state the tick produced, not the one it started from")
        void damageUsesThePostTickState() {
            // One tick below the threshold; the tick itself pushes it over.
            Needs almost = new Needs(Needs.CRITICAL_PRESSURE - rates.hungerPerTick(), 0.0, 50.0, 100.0);

            Needs after = almost.afterTick(rates, ActivityLevel.ACTIVE);

            assertTrue(after.isStarving());
            assertEquals(Needs.MAX - rates.starvationDamage(), after.health(), EPSILON);
        }

        @Test
        @DisplayName("left alone long enough, an agent starves to death")
        void unattendedNeedsAreEventuallyFatal() {
            Needs needs = Needs.healthy();

            long ticks = 0;
            while (needs.isAlive() && ticks < 10_000) {
                needs = needs.afterTick(rates, ActivityLevel.ACTIVE);
                ticks++;
            }

            assertFalse(needs.isAlive(), "an agent that never eats or drinks must not survive");
            assertTrue(ticks < 500, "death took an implausible " + ticks + " ticks");
        }

        @Test
        @DisplayName("metabolism is a pure function: same input, same output")
        void metabolismIsDeterministic() {
            Needs start = new Needs(11.0, 22.0, 33.0, 44.0);

            assertEquals(
                    start.afterTick(rates, ActivityLevel.ACTIVE),
                    start.afterTick(rates, ActivityLevel.ACTIVE));
        }
    }

    @Nested
    @DisplayName("healing")
    class Healing {

        @Test
        @DisplayName("a body that is looked after mends a little every tick")
        void aWellKeptBodyHeals() {
            Needs hurt = new Needs(10.0, 10.0, 80.0, 60.0);

            Needs after = hurt.afterTick(rates, ActivityLevel.ACTIVE);

            assertEquals(60.0 + rates.healingPerTick(), after.health(), EPSILON);
        }

        @Test
        @DisplayName("and faster at rest")
        void restHealsFaster() {
            Needs hurt = new Needs(10.0, 10.0, 80.0, 60.0);

            double active = hurt.afterTick(rates, ActivityLevel.ACTIVE).health();
            double resting = hurt.afterTick(rates, ActivityLevel.RESTING).health();

            assertEquals(60.0 + rates.restingHealingPerTick(), resting, EPSILON);
            assertTrue(resting > active);
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({
            "too hungry, 60, 10, 80",
            "too thirsty, 10, 60, 80",
            "too tired, 10, 10, 20"})
        @DisplayName("a body that is hungry, thirsty or run down does not heal")
        void neglectStopsHealing(String why, double hunger, double thirst, double energy) {
            Needs hurt = new Needs(hunger, thirst, energy, 60.0);

            assertEquals(60.0, hurt.afterTick(rates, ActivityLevel.RESTING).health(), EPSILON);
        }

        @Test
        @DisplayName("being well is judged on the state the tick produced")
        void wellnessUsesThePostTickState() {
            // Just well enough going in; the tick's thirst pushes it over.
            Needs edge = new Needs(0.0, Needs.WELL_PRESSURE, 80.0, 60.0);

            assertEquals(60.0, edge.afterTick(rates, ActivityLevel.RESTING).health(), EPSILON);
        }

        @Test
        @DisplayName("healing stops at full health")
        void neverAboveFull() {
            Needs nearly = new Needs(0.0, 0.0, 80.0, Needs.MAX - 0.05);

            assertEquals(Needs.MAX, nearly.afterTick(rates, ActivityLevel.RESTING).health(), EPSILON);
        }

        @Test
        @DisplayName("no tick both hurts and heals")
        void neverHurtAndHealedAtOnce() {
            for (double hunger = 0; hunger <= 100; hunger += 5) {
                for (double energy = 0; energy <= 100; energy += 5) {
                    Needs before = new Needs(hunger, 0.0, energy, 60.0);
                    Needs after = before.afterTick(rates, ActivityLevel.RESTING);
                    boolean hurt = after.isStarving() || after.isDehydrated() || after.isExhausted();
                    assertTrue(!hurt || after.health() < 60.0, "hurt but healed: " + after);
                    assertTrue(!after.isWellEnoughToHeal() || after.health() > 60.0,
                            "well but not healed: " + after);
                }
            }
        }

        @Test
        @DisplayName("the dead do not heal")
        void theDeadStayDead() {
            Needs dead = new Needs(0.0, 0.0, 80.0, Needs.MIN);

            assertEquals(Needs.MIN, dead.afterTick(rates, ActivityLevel.RESTING).health(), EPSILON);
        }

        @Test
        @DisplayName("resting may not heal less than being active, and nothing heals backwards")
        void healingRatesAreSensible() {
            assertThrows(IllegalArgumentException.class,
                    () -> new MetabolismRates(0.8, 1.2, 0.5, 0.1, 1.5, 2.0, 1.0, 0.5, 0.1));
            assertThrows(IllegalArgumentException.class,
                    () -> new MetabolismRates(0.8, 1.2, 0.5, 0.1, 1.5, 2.0, 1.0, -0.1, 0.5));
        }
    }
}
