package dev.evoworld.viewer;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;
import dev.evoworld.core.Simulation;
import dev.evoworld.core.action.SleepAction;
import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.event.SimulationEvent;
import dev.evoworld.core.intent.Intent;
import dev.evoworld.core.resource.ResourceSnapshot;
import dev.evoworld.core.resource.ResourceType;
import dev.evoworld.core.status.AgentStatus;
import dev.evoworld.core.world.AgentSnapshot;
import dev.evoworld.core.world.Location;
import dev.evoworld.core.world.WorldState;
import dev.evoworld.persistence.SaveFile;
import dev.evoworld.persistence.SaveFileException;
import dev.evoworld.runner.EventLog;
import dev.evoworld.runner.StatusReport;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.zip.Deflater;

/**
 * Draws the model, and turns keys into what the model should do.
 *
 * <p>Nothing here decides anything about the world. The map, the panel and the
 * feed are all read from the model's latest values each frame, so what is on
 * screen is always one consistent moment.
 */
final class ViewerScreen extends ApplicationAdapter {

    static final Path SAVE_TO = Path.of("saves", "viewer.json");

    private static final Color BACKGROUND = new Color(0.08f, 0.09f, 0.11f, 1f);
    private static final Color GROUND = new Color(0.14f, 0.16f, 0.18f, 1f);
    private static final Color IN_SIGHT = new Color(0.20f, 0.23f, 0.24f, 1f);
    private static final Color TEXT = new Color(0.86f, 0.88f, 0.90f, 1f);
    private static final Color DIM = new Color(0.55f, 0.58f, 0.62f, 1f);
    private static final Color ATLAS = new Color(0.98f, 0.84f, 0.30f, 1f);
    private static final Color ASLEEP = new Color(0.55f, 0.50f, 0.32f, 1f);
    private static final Color INTENT = new Color(0.98f, 0.84f, 0.30f, 0.55f);
    private static final Color GOOD = new Color(0.36f, 0.75f, 0.45f, 1f);
    private static final Color WARN = new Color(0.93f, 0.62f, 0.25f, 1f);
    private static final Color BAD = new Color(0.90f, 0.30f, 0.28f, 1f);
    private static final Color TRACK = new Color(0.22f, 0.24f, 0.27f, 1f);

    private final Simulation simulation;
    private final int startingSpeed;
    private final Optional<Path> screenshot;
    private final long screenshotAfter;
    private int framesDrawn;

    private ViewerModel model;
    private OrthographicCamera camera;
    private ShapeRenderer shapes;
    private SpriteBatch batch;
    private BitmapFont font;
    private final GlyphLayout measure = new GlyphLayout();
    private String flash = "";
    private float flashFor;

    ViewerScreen(Simulation simulation, int startingSpeed, Optional<Path> screenshot, long screenshotAfter) {
        this.simulation = simulation;
        this.startingSpeed = startingSpeed;
        this.screenshot = screenshot;
        this.screenshotAfter = screenshotAfter;
    }

    @Override
    public void create() {
        model = new ViewerModel(simulation, startingSpeed);
        camera = new OrthographicCamera();
        shapes = new ShapeRenderer();
        batch = new SpriteBatch();
        font = new BitmapFont();
        font.setUseIntegerPositions(true);
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        if (screenshot.isPresent()) {
            for (long i = 0; i < screenshotAfter; i++) {
                model.stepOnce();
            }
        }
    }

    @Override
    public void resize(int width, int height) {
        camera.setToOrtho(false, width, height);
        camera.update();
        shapes.setProjectionMatrix(camera.combined);
        batch.setProjectionMatrix(camera.combined);
    }

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();
        handleKeys();
        if (screenshot.isEmpty()) {
            // A screenshot shows exactly the tick it was asked for.
            model.advance(delta);
        }
        flashFor = Math.max(0f, flashFor - delta);

        ScreenUtils.clear(BACKGROUND);
        float width = camera.viewportWidth;
        float height = camera.viewportHeight;
        WorldState state = model.state();
        MapLayout layout = MapLayout.fit(state.bounds(), width, height);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        drawMap(layout, state);
        drawBars(width, height);
        if (model.isOver()) {
            drawShade(layout);
        }
        batch.begin();
        drawPanelText(width, height);
        drawFeed(layout);
        if (model.isOver()) {
            drawDeathNotice(layout);
        }
        batch.end();

        // Two frames in, so the window has settled at its real size.
        if (screenshot.isPresent() && ++framesDrawn == 2) {
            capture(screenshot.get());
            Gdx.app.exit();
        }
    }

    private static void capture(Path file) {
        int width = Gdx.graphics.getBackBufferWidth();
        int height = Gdx.graphics.getBackBufferHeight();
        Pixmap frame = Pixmap.createFromFrameBuffer(0, 0, width, height);
        try {
            FileHandle target = Gdx.files.absolute(file.toAbsolutePath().toString());
            PixmapIO.writePNG(target, frame, Deflater.DEFAULT_COMPRESSION, true);
        } finally {
            frame.dispose();
        }
    }

    // --- keys -------------------------------------------------------------

    private void handleKeys() {
        if (pressed(Input.Keys.SPACE)) {
            model.togglePause();
        }
        if (pressed(Input.Keys.RIGHT) && model.isPaused()) {
            model.stepOnce();
        }
        if (pressed(Input.Keys.PLUS) || pressed(Input.Keys.EQUALS) || pressed(Input.Keys.NUMPAD_ADD)) {
            model.faster();
        }
        if (pressed(Input.Keys.MINUS) || pressed(Input.Keys.NUMPAD_SUBTRACT)) {
            model.slower();
        }
        if (pressed(Input.Keys.S)) {
            save();
        }
        if (pressed(Input.Keys.ESCAPE)) {
            Gdx.app.exit();
        }
    }

    private static boolean pressed(int key) {
        return Gdx.input.isKeyJustPressed(key);
    }

    private void save() {
        try {
            SaveFile.write(model.simulation(), SAVE_TO);
            say("saved to " + SAVE_TO);
        } catch (SaveFileException e) {
            say("could not save: " + e.getMessage());
        }
    }

    private void say(String message) {
        flash = message;
        flashFor = 3f;
    }

    // --- the map ----------------------------------------------------------

    private void drawMap(MapLayout layout, WorldState state) {
        AgentSnapshot atlas = state.agents().get(0);
        int radius = model.simulation().config().perceptionRadius();
        float gap = Math.max(1f, layout.tile() * 0.06f);

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int y = 0; y < state.bounds().height(); y++) {
            for (int x = 0; x < state.bounds().width(); x++) {
                Location tile = new Location(x, y);
                boolean seen = atlas.alive() && tile.manhattanDistanceTo(atlas.location()) <= radius;
                shapes.setColor(seen ? IN_SIGHT : GROUND);
                shapes.rect(layout.left(tile) + gap / 2, layout.bottom(tile) + gap / 2,
                        layout.tile() - gap, layout.tile() - gap);
            }
        }
        for (ResourceSnapshot node : state.resources()) {
            if (node.isAvailable()) {
                // Area in proportion to what is left, so a half-picked bush looks half full.
                float side = layout.tile() * 0.72f * (float) Math.sqrt(node.availability());
                shapes.setColor(colourOf(node.type()));
                shapes.rect(layout.centreX(node.location()) - side / 2,
                        layout.centreY(node.location()) - side / 2, side, side);
            }
        }
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        for (ResourceSnapshot node : state.resources()) {
            float side = layout.tile() * 0.72f;
            Color edge = colourOf(node.type());
            shapes.setColor(edge.r, edge.g, edge.b, node.isAvailable() ? 0.9f : 0.5f);
            shapes.rect(layout.centreX(node.location()) - side / 2,
                    layout.centreY(node.location()) - side / 2, side, side);
        }
        atlas.intent().ifPresent(intent -> drawIntent(layout, atlas, intent));
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(!atlas.alive() ? DIM : isAsleep() ? ASLEEP : ATLAS);
        shapes.circle(layout.centreX(atlas.location()), layout.centreY(atlas.location()),
                layout.tile() * 0.30f, 24);
        shapes.setColor(BACKGROUND);
        shapes.circle(layout.centreX(atlas.location()), layout.centreY(atlas.location()),
                layout.tile() * 0.10f, 12);
        shapes.end();
    }

    /** Whether the latest decision was a sleep that is still going on. */
    private boolean isAsleep() {
        AgentStatus status = model.status();
        return status.busyFor() > 0
                && status.doing().filter(decision -> decision.action().name().equals(SleepAction.NAME)).isPresent();
    }

    private void drawIntent(MapLayout layout, AgentSnapshot atlas, Intent intent) {
        Location target = intent.target();
        shapes.setColor(INTENT);
        shapes.line(layout.centreX(atlas.location()), layout.centreY(atlas.location()),
                layout.centreX(target), layout.centreY(target));
        shapes.circle(layout.centreX(target), layout.centreY(target), layout.tile() * 0.42f, 24);
    }

    private static Color colourOf(ResourceType type) {
        return switch (type) {
            case WATER -> new Color(0.30f, 0.58f, 0.95f, 1f);
            case BERRIES -> new Color(0.86f, 0.27f, 0.42f, 1f);
            case WOOD -> new Color(0.62f, 0.44f, 0.26f, 1f);
            case STONE -> new Color(0.62f, 0.64f, 0.68f, 1f);
        };
    }

    private void drawShade(MapLayout layout) {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.55f);
        shapes.rect(layout.originX(), layout.originY(), layout.width(), layout.height());
        shapes.end();
    }

    private void drawDeathNotice(MapLayout layout) {
        String notice = model.death().map(SimulationEvent::describe).orElse("nobody is left alive");
        font.setColor(BAD);
        font.draw(batch, notice, layout.originX(), layout.originY() + layout.height() / 2,
                layout.width(), Align.center, true);
    }

    // --- the side panel ---------------------------------------------------

    private float panelLeft(float width) {
        return width - MapLayout.PANEL_WIDTH + MapLayout.MARGIN;
    }

    private float panelWidth() {
        return MapLayout.PANEL_WIDTH - 2 * MapLayout.MARGIN;
    }

    /** Where the need bars start, from the top; the text above them is fixed. */
    private static final float HEADER_HEIGHT = 86f;
    private static final float BAR_ROW = 24f;
    private static final float LABEL_WIDTH = 64f;
    private static final float VALUE_WIDTH = 46f;

    private void drawBars(float width, float height) {
        Needs needs = model.status().body().needs();
        float x = panelLeft(width) + LABEL_WIDTH;
        float barWidth = panelWidth() - LABEL_WIDTH - VALUE_WIDTH;
        float top = height - MapLayout.MARGIN - HEADER_HEIGHT;
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        List<Bar> bars = bars(needs);
        for (int i = 0; i < bars.size(); i++) {
            float y = top - (i + 1) * BAR_ROW + 6f;
            shapes.setColor(TRACK);
            shapes.rect(x, y, barWidth, BAR_ROW - 12f);
            shapes.setColor(bars.get(i).colour());
            shapes.rect(x, y, barWidth * (float) (bars.get(i).value() / Needs.MAX), BAR_ROW - 12f);
        }
        shapes.end();
    }

    private record Bar(String label, double value, Color colour) {
    }

    /** Health and energy are reserves, full when high; hunger and thirst are pressures. */
    private static List<Bar> bars(Needs needs) {
        return List.of(
                new Bar("health", needs.health(), needs.health() < 50 ? BAD : needs.isFullyHealthy() ? GOOD : WARN),
                new Bar("hunger", needs.hunger(), pressureColour(needs.hunger())),
                new Bar("thirst", needs.thirst(), pressureColour(needs.thirst())),
                new Bar("energy", needs.energy(), needs.energy() <= Needs.WELL_ENERGY ? BAD : GOOD));
    }

    private static Color pressureColour(double pressure) {
        if (pressure >= Needs.CRITICAL_PRESSURE) {
            return BAD;
        }
        return pressure > Needs.WELL_PRESSURE ? WARN : GOOD;
    }

    private void drawPanelText(float width, float height) {
        float x = panelLeft(width);
        float y = height - MapLayout.MARGIN;
        AgentStatus status = model.status();
        Simulation simulation = model.simulation();

        font.setColor(TEXT);
        font.draw(batch, "EvoWorld", x, y);
        font.setColor(DIM);
        font.draw(batch, "seed " + simulation.config().seed(), x, y, panelWidth(), Align.right, false);
        y -= 22f;
        font.setColor(TEXT);
        font.draw(batch, EventLog.when(status.time()), x, y);
        font.setColor(model.isPaused() ? WARN : DIM);
        font.draw(batch, model.isPaused()
                        ? "PAUSED"
                        : String.format(Locale.ROOT, "%.0f ticks/s", model.ticksPerSecond()),
                x, y, panelWidth(), Align.right, false);
        y -= 22f;
        AgentSnapshot body = status.body();
        font.setColor(body.alive() ? ATLAS : DIM);
        font.draw(batch, body.name() + (body.alive() ? "" : " (dead)") + "  at " + body.location(), x, y);

        float barsTop = height - MapLayout.MARGIN - HEADER_HEIGHT;
        List<Bar> bars = bars(body.needs());
        for (int i = 0; i < bars.size(); i++) {
            float rowY = barsTop - i * BAR_ROW - 5f;
            font.setColor(DIM);
            font.draw(batch, bars.get(i).label(), x, rowY);
            font.setColor(TEXT);
            font.draw(batch, String.format(Locale.ROOT, "%.0f", bars.get(i).value()),
                    x, rowY, panelWidth(), Align.right, false);
        }

        y = barsTop - bars.size() * BAR_ROW - 12f;
        for (String[] row : rows(status, simulation)) {
            font.setColor(DIM);
            font.draw(batch, row[0], x, y);
            font.setColor(TEXT);
            measure.setText(font, row[1], TEXT, panelWidth() - LABEL_WIDTH - 12f, Align.left, true);
            font.draw(batch, measure, x + LABEL_WIDTH + 12f, y);
            y -= measure.height + 10f;
        }

        font.setColor(flashFor > 0 ? WARN : DIM);
        String hint = flashFor > 0
                ? flash
                : "Space pause   Right step   +/- speed   S save   Esc quit";
        font.draw(batch, hint, x, MapLayout.MARGIN + 14f, panelWidth(), Align.left, true);
    }

    /**
     * The status rows worth reading while it runs, taken from the console's
     * own status report so the two can never disagree about what they say.
     */
    private static List<String[]> rows(AgentStatus status, Simulation simulation) {
        List<String> lines = StatusReport.lines(status, simulation.config().tickDuration());
        List<String> wanted = List.of(
                "wants", "doing", "last act", "intent", "carrying", "knows", "skills", "explored");
        return lines.stream()
                .skip(1)
                .filter(line -> line.length() > 14)
                .map(line -> new String[] {line.substring(3, 14).trim(), line.substring(14).trim()})
                .filter(row -> wanted.contains(row[0]))
                .map(row -> new String[] {row[0].equals("last act") ? "did" : row[0], row[1]})
                .toList();
    }

    // --- the feed ---------------------------------------------------------

    private void drawFeed(MapLayout layout) {
        float x = MapLayout.MARGIN;
        float width = layout.right() - x;
        float y = MapLayout.FEED_HEIGHT - 4f;
        font.setColor(DIM);
        font.draw(batch, "recent", x, y);
        y -= 22f;
        List<SimulationEvent> feed = model.feed();
        for (int i = feed.size() - 1; i >= 0 && y > MapLayout.MARGIN; i--) {
            SimulationEvent event = feed.get(i);
            font.setColor(colourOf(event));
            measure.setText(font, EventLog.when(event.time()) + "   " + event.describe(),
                    font.getColor(), width, Align.left, false);
            font.draw(batch, measure, x, y);
            y -= 18f;
        }
    }

    private static Color colourOf(SimulationEvent event) {
        return switch (event) {
            case SimulationEvent.DangerBegan danger -> BAD;
            case SimulationEvent.Died died -> BAD;
            case SimulationEvent.DangerPassed passed -> WARN;
            case SimulationEvent.Recovered recovered -> GOOD;
            default -> TEXT;
        };
    }

    @Override
    public void dispose() {
        shapes.dispose();
        batch.dispose();
        font.dispose();
    }
}
