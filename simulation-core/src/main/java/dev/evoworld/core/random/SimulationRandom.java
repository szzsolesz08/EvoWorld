package dev.evoworld.core.random;

/**
 * The one source of randomness in a simulation run.
 *
 * <p>Everything stochastic draws from here, which is what makes a run
 * reproducible rather than merely seeded. It is a single choke point, so
 * "nothing else invents randomness" is something you can check by looking for
 * constructor calls rather than a convention people have to remember.
 *
 * <p>The generator is the linear congruential generator specified for
 * {@link java.util.Random} - the same multiplier, increment and bit selection,
 * so it produces exactly the sequence {@code new Random(seed)} would, on any
 * JVM. It is written out here rather than delegated to for one reason: its
 * entire state is then a single {@code long} this class can hand over and take
 * back. A saved run needs the position in the stream, and a seed plus a count
 * of draws is not enough to recover it - {@code nextInt(bound)} sometimes
 * rejects a value and draws again, so replaying the count with the wrong bounds
 * would land somewhere else.
 *
 * <p>Deliberately not thread safe: the simulation is single threaded, and
 * concurrent draws would destroy reproducibility anyway.
 */
public final class SimulationRandom {

    private static final long MULTIPLIER = 0x5DEECE66DL;
    private static final long INCREMENT = 0xBL;
    private static final long MASK = (1L << 48) - 1;

    private final long seed;
    private long state;
    private long drawCount;

    private SimulationRandom(long seed, long state, long drawCount) {
        this.seed = seed;
        this.state = state;
        this.drawCount = drawCount;
    }

    public static SimulationRandom seededWith(long seed) {
        return new SimulationRandom(seed, scramble(seed), 0L);
    }

    /**
     * An independent stream derived from the same run seed.
     *
     * <p>Two subsystems drawing from one stream are coupled: change how many
     * numbers the agent uses and the world it lives in comes out different,
     * because every later draw shifts. Named streams break that - the world
     * generated from seed 42 stays the same world however agent behaviour
     * evolves - while keeping the whole run reproducible from a single seed.
     *
     * <p>The mix is the ordinary string hash recurrence, whose result is
     * specified by the platform, so a named stream is the same stream on any
     * JVM.
     */
    public static SimulationRandom seededWith(long seed, String stream) {
        return seededWith(mix(seed, stream));
    }

    /**
     * A stream picked up exactly where a saved one left off.
     *
     * @param state what {@link #state()} returned when it was saved
     */
    public static SimulationRandom resumedAt(long seed, long state, long drawCount) {
        if ((state & ~MASK) != 0L) {
            throw new IllegalArgumentException("not a generator state: " + state);
        }
        if (drawCount < 0) {
            throw new IllegalArgumentException("draw count must not be negative, was " + drawCount);
        }
        return new SimulationRandom(seed, state, drawCount);
    }

    private static long mix(long seed, String stream) {
        long mixed = seed;
        for (int i = 0; i < stream.length(); i++) {
            mixed = mixed * 31L + stream.charAt(i);
        }
        return mixed;
    }

    /** How {@code java.util.Random} turns a seed into its starting state. */
    private static long scramble(long seed) {
        return (seed ^ MULTIPLIER) & MASK;
    }

    /** The seed this stream actually runs on, after any name was mixed in. */
    public long seed() {
        return seed;
    }

    /**
     * Everything needed, with the seed, to carry on from exactly here.
     *
     * <p>Forty-eight bits, always non-negative.
     */
    public long state() {
        return state;
    }

    /**
     * How many values have been drawn so far.
     *
     * <p>Two runs that agree on the seed but not on this number have diverged,
     * which makes it a cheap fingerprint of a run.
     */
    public long drawCount() {
        return drawCount;
    }

    /**
     * A value in {@code [0, bound)}.
     *
     * <p>The algorithm specified for {@code java.util.Random.nextInt(int)}:
     * powers of two take the high bits, everything else rejects the values that
     * would make the result uneven and draws again.
     */
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive, was " + bound);
        }
        drawCount++;
        int value = next(31);
        int limit = bound - 1;
        if ((bound & limit) == 0) {
            return (int) ((bound * (long) value) >> 31);
        }
        for (int unbiased = value;
                unbiased - (value = unbiased % bound) + limit < 0;
                unbiased = next(31)) {
            // Rejected: this draw would favour the low results. Draw again.
        }
        return value;
    }

    private int next(int bits) {
        state = (state * MULTIPLIER + INCREMENT) & MASK;
        return (int) (state >>> (48 - bits));
    }

    @Override
    public String toString() {
        return "SimulationRandom[seed=" + seed + " draws=" + drawCount + "]";
    }
}
