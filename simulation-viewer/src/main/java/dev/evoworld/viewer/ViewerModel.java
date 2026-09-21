package dev.evoworld.viewer;

import dev.evoworld.core.Simulation;
import dev.evoworld.core.event.SimulationEvent;
import dev.evoworld.core.observer.SimulationObserver;
import dev.evoworld.core.status.AgentStatus;
import dev.evoworld.core.world.WorldState;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Everything the window shows, and the clock that moves it - with no drawing.
 *
 * <p>The simulation runs here, on whatever thread calls {@link #advance}, which
 * in the viewer is the render thread. The simulation is not thread-safe and
 * does not need to be: one frame asks for however many ticks the elapsed time
 * is worth at the current speed, and draws the result. The seed still decides
 * every tick; frame timing only decides how fast they arrive.
 *
 * <p>Kept free of LibGDX so the parts that can be wrong - pacing, pausing,
 * stepping, what the feed keeps - are tested without a window.
 */
public final class ViewerModel {

    /** Ticks per second, slowest to fastest. */
    static final double[] SPEEDS = {1, 2, 4, 8, 15, 30, 60, 120};

    static final int DEFAULT_SPEED_INDEX = 2;

    /** Events the feed keeps. */
    static final int FEED_SIZE = 10;

    /**
     * The most ticks one frame may run. A frame that took a second - the
     * window dragged, the machine busy - does not get to make up for it all
     * at once, or the next frame would be slower still.
     */
    static final int MAX_TICKS_PER_FRAME = 20;

    private final Simulation simulation;
    private final Deque<SimulationEvent> feed = new ArrayDeque<>();
    private Optional<SimulationEvent.Died> death = Optional.empty();
    private WorldState state;
    private AgentStatus status;
    private int speedIndex;
    private boolean paused;
    private double owed;

    public ViewerModel(Simulation simulation, int speedIndex) {
        this.simulation = Objects.requireNonNull(simulation, "simulation");
        this.speedIndex = speedIndex;
        simulation.addObserver(new SimulationObserver() {
            @Override
            public void onEvent(SimulationEvent event) {
                remember(event);
            }
        });
        simulation.start();
        refresh();
    }

    /**
     * Lets {@code seconds} of real time pass.
     *
     * @return how many ticks that ran
     */
    public int advance(double seconds) {
        if (paused || isOver()) {
            return 0;
        }
        owed += seconds * ticksPerSecond();
        int due = (int) Math.min(Math.floor(owed), MAX_TICKS_PER_FRAME);
        // Whatever could not be run this frame is dropped, not carried: falling
        // behind should slow the picture down, not make it lurch to catch up.
        owed = Math.min(owed - due, 1.0);
        int ran = 0;
        while (ran < due && !isOver()) {
            simulation.tick();
            ran++;
        }
        if (ran > 0) {
            refresh();
        }
        return ran;
    }

    /** One tick, whether paused or not. */
    public void stepOnce() {
        if (!isOver()) {
            simulation.tick();
            refresh();
        }
    }

    public void togglePause() {
        paused = !paused;
        owed = 0.0;
    }

    public void faster() {
        speedIndex = Math.min(speedIndex + 1, SPEEDS.length - 1);
    }

    public void slower() {
        speedIndex = Math.max(speedIndex - 1, 0);
    }

    public boolean isPaused() {
        return paused;
    }

    public boolean isOver() {
        return !simulation.anyoneAlive();
    }

    public double ticksPerSecond() {
        return SPEEDS[speedIndex];
    }

    public Simulation simulation() {
        return simulation;
    }

    public WorldState state() {
        return state;
    }

    public AgentStatus status() {
        return status;
    }

    /** The latest events, newest last. */
    public List<SimulationEvent> feed() {
        return List.copyOf(feed);
    }

    public Optional<SimulationEvent.Died> death() {
        return death;
    }

    private void remember(SimulationEvent event) {
        if (event instanceof SimulationEvent.DayPassed) {
            return;
        }
        if (event instanceof SimulationEvent.Died died) {
            death = Optional.of(died);
        }
        feed.addLast(event);
        while (feed.size() > FEED_SIZE) {
            feed.removeFirst();
        }
    }

    private void refresh() {
        state = simulation.snapshot();
        status = simulation.status().get(0);
    }
}
