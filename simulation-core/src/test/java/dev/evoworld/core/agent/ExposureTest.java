package dev.evoworld.core.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.evoworld.core.world.Sky;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** What the cold and the dark do to a body. */
class ExposureTest {

    private static final MetabolismRates PER_HOUR = MetabolismRates.defaults();

    @Test
    @DisplayName("a mild day asks nothing: no shivering, ordinary rest")
    void mildIsFree() {
        Exposure day = Exposure.of(Exposure.MILD, 1.0);

        assertFalse(day.isCold());
        assertEquals(0.0, day.chill(), 1e-9);
        assertEquals(1.0, day.comfort(), 1e-9);
        assertEquals(Exposure.none(), new Exposure(0.0, 1.0));
    }

    @Test
    @DisplayName("the colder it is, the more a body burns and the less a night gives back")
    void coldCostsTwice() {
        Exposure cool = Exposure.of(10.0, 0.0);
        Exposure cold = Exposure.of(0.0, 0.0);

        assertTrue(cool.isCold());
        assertTrue(cold.chill() > cool.chill(), "colder means more shivering");
        assertTrue(cold.comfort() < cool.comfort(), "and a worse night");
    }

    @Test
    @DisplayName("darkness is the one thing about night that helps")
    void darknessHelpsRest() {
        Exposure sameTemperatureByDay = Exposure.of(10.0, 1.0);
        Exposure byNight = Exposure.of(10.0, 0.0);

        assertTrue(byNight.comfort() > sameTemperatureByDay.comfort());
        assertEquals(byNight.chill(), sameTemperatureByDay.chill(), 1e-9, "the dark is not cold by itself");
    }

    @Test
    @DisplayName("shivering costs energy, and only when there is energy going out")
    void chillIsChargedOnWhatGoesOut() {
        MetabolismRates perTick = PER_HOUR.perTick(java.time.Duration.ofHours(1));
        Needs rested = new Needs(0.0, 0.0, 80.0, Needs.MAX);

        Needs warmHour = rested.afterTick(perTick, new Exertion(ActivityLevel.ACTIVE, 1.0, 0.0));
        Needs coldHour = rested.afterTick(perTick, new Exertion(ActivityLevel.ACTIVE, 1.0, 0.25));

        assertTrue(coldHour.energy() < warmHour.energy(), "a cold hour awake costs more");
    }

    @Test
    @DisplayName("a cold night gives back less sleep than a warm one, and a fire makes up the difference")
    void restIsScaledByComfort() {
        MetabolismRates perTick = PER_HOUR.perTick(java.time.Duration.ofHours(1));
        Needs spent = new Needs(0.0, 0.0, 20.0, Needs.MAX);
        double coldNight = Exposure.of(Sky.COLDEST, 0.0).comfort();
        double byTheFire = Exposure.of(Sky.COLDEST + 12.0, 0.0).comfort();

        Needs cold = spent.afterTick(perTick, new Exertion(ActivityLevel.SLEEPING, coldNight));
        Needs warm = spent.afterTick(perTick, new Exertion(ActivityLevel.SLEEPING, byTheFire));

        assertTrue(warm.energy() > cold.energy(), "the fire is worth an hour's sleep: " + warm + " vs " + cold);
    }

    @Test
    @DisplayName("this spring is uncomfortable, not dangerous")
    void springIsSurvivable() {
        Exposure worstNight = Exposure.of(Sky.COLDEST, 0.0);

        assertTrue(worstNight.chill() <= 0.15, "a tenth or so more fuel burnt: " + worstNight);
        assertTrue(worstNight.comfort() >= 0.9, "and most of a night's rest still had: " + worstNight);
    }
}
