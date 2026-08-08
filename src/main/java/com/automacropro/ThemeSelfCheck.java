package com.automacropro;

import com.automacropro.engine.FailsafeMonitor;
import com.automacropro.engine.WindowManager;
import com.automacropro.model.ActionType;
import com.automacropro.model.AutoClickerSettings;
import com.automacropro.model.MacroProject;
import com.automacropro.model.MacroStep;
import com.automacropro.persistence.ProfileManager;
import com.automacropro.ui.GlassPanel;
import com.automacropro.ui.MainFrame;
import com.automacropro.ui.UiTheme;

import com.automacropro.ui.StepListTransferHandler;
import com.automacropro.util.I18n;
import com.automacropro.util.KeyCodeUtil;
import com.automacropro.util.ScreenCoords;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;

import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.TitledBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Smoke check for the Phase 2 theme + Window Manager plumbing. Not a unit-test
 * framework - just the smallest thing that fails loudly if the dark theme stops
 * being applied, the glass stops compositing, or the Win32 layer throws.
 *
 * Run: mvn compile exec:java -Dexec.mainClass=com.automacropro.ThemeSelfCheck
 */
public final class ThemeSelfCheck {

    private ThemeSelfCheck() {
    }

    public static void main(String[] args) throws Exception {
        // The EDT and the JNativeHook hook thread are non-daemon, so an
        // assertion failure would otherwise leave the JVM hanging forever
        // instead of reporting the failure and exiting.
        try {
            runChecks();
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.out.println("SELF-CHECK FAILED");
            System.exit(1);
        }
        System.out.println("OK - theme, glass, Win32, DnD, failsafe and frame all check out.");
        System.exit(0);
    }

    private static void runChecks() throws Exception {
        UiTheme.installLookAndFeel();

        // 1. The dark theme really reached the UIManager defaults, i.e. every
        //    stock component (not just our hand-painted ones) is now dark.
        Color panelBg = UIManager.getColor("Panel.background");
        assertTrue(panelBg.equals(UiTheme.BG_DEEP),
                "Panel.background should be BG_DEEP, was " + panelBg);
        assertTrue(UIManager.getColor("Label.foreground").getRed() > 128,
                "Label.foreground should be light-on-dark");

        // 2. Fonts resolve (bundled or fallback) and are never null.
        assertTrue(UiTheme.FONT_TITLE != null && UiTheme.FONT_BODY != null, "fonts must resolve");
        System.out.println("Title font  : " + UiTheme.FONT_TITLE.getFontName());
        System.out.println("Body font   : " + UiTheme.FONT_BODY.getFontName());

        // 3. Glass actually composites: paint a GlassPanel over BG_DEEP and
        //    confirm the result is lighter than the raw background. This is the
        //    check that fails if someone makes GlassPanel opaque again.
        BufferedImage img = new BufferedImage(120, 80, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        g.setColor(UiTheme.BG_DEEP);
        g.fillRect(0, 0, 120, 80);
        JPanel host = new JPanel(new BorderLayout());
        host.setSize(120, 80);
        GlassPanel glass = new GlassPanel(new BorderLayout());
        host.add(glass, BorderLayout.CENTER);
        host.doLayout();
        glass.setSize(120, 80);
        glass.paint(g);
        g.dispose();
        Color centre = new Color(img.getRGB(60, 40));
        assertTrue(centre.getRed() > UiTheme.BG_DEEP.getRed(),
                "glass should lighten the backdrop, got " + centre);
        System.out.println("Glass blend : " + UiTheme.BG_DEEP + " -> " + centre);

        // 4. Win32 layer answers without throwing, and never lists our own window.
        if (WindowManager.isSupported()) {
            var windows = WindowManager.listWindows();
            System.out.println("Windows     : " + windows.size() + " enumerated");
            int self = (int) ProcessHandle.current().pid();
            assertTrue(windows.stream().noneMatch(w -> w.pid == self),
                    "own process must be filtered out of the window list");
        } else {
            System.out.println("Windows     : unsupported platform (skipped)");
        }

        // 5. The real frame builds end-to-end with all three tabs.
        SwingUtilities.invokeAndWait(() -> {
            MainFrame frame = new MainFrame();
            frame.pack();
            JTabbedPane tabs = findTabs(frame.getContentPane());
            assertTrue(tabs != null && tabs.getTabCount() == 3, "expected 3 tabs");
            frame.dispose();
        });

        checkDragAndDropReorder();
        checkNoFalseCornerPositives();
        checkCoordinateRoundTrip();
        checkTopRowFitsOnStartup();
        checkFailsafeDwellAndLatch();
        checkTranslationsComplete();
        checkKeyCodeMapping();
        checkIntervalJitterRange();
        checkProfileRoundTrip();
        checkPreDelayRoundTrip();
        checkStepDeepCopy();
        checkScrollRoundTrip();
        checkMultiRowDragReorder();
    }

    /**
     * Pre-delay must survive a save/load cycle, and a file written before the
     * field existed must load as 0 rather than throwing.
     */
    private static void checkPreDelayRoundTrip() {
        MacroProject project = new MacroProject();
        MacroStep mouse = new MacroStep(ActionType.MOUSE);
        mouse.setPreDelayMs(200);
        MacroStep delay = new MacroStep(ActionType.DELAY);
        delay.setDelayMs(500);
        delay.setPreDelayMs(75);
        project.setSteps(new ArrayList<>(List.of(mouse, delay)));

        MacroProject reloaded = MacroProject.fromMap(project.toMap());
        assertTrue(reloaded.getSteps().size() == 2, "expected 2 steps back");
        assertTrue(reloaded.getSteps().get(0).getPreDelayMs() == 200,
                "mouse pre-delay lost: " + reloaded.getSteps().get(0).getPreDelayMs());
        assertTrue(reloaded.getSteps().get(1).getPreDelayMs() == 75, "delay pre-delay lost");
        // The standalone DELAY duration must not be confused with the pre-delay.
        assertTrue(reloaded.getSteps().get(1).getDelayMs() == 500, "delay duration lost");

        // Simulate a pre-v3 file: same payload with the key removed.
        var legacy = mouse.toMap();
        legacy.remove("preDelayMs");
        assertTrue(MacroStep.fromMap(legacy).getPreDelayMs() == 0,
                "a file without preDelayMs must default to 0");

        // describe() shows the suffix only when set.
        assertTrue(mouse.describe().contains("200"), "describe() should mention the pre-delay");
        MacroStep plain = new MacroStep(ActionType.MOUSE);
        assertTrue(!plain.describe().contains("Pre-delay") && !plain.describe().contains("Jeda"),
                "describe() must stay clean at 0: " + plain.describe());
        System.out.println("Pre-delay   : round trip exact, legacy files default to 0, describe() conditional");
    }

    /**
     * Copy/paste must deep-copy. Sharing one instance between two rows would
     * make editing the original silently rewrite the pasted copy.
     */
    private static void checkStepDeepCopy() {
        MacroStep original = new MacroStep(ActionType.MOUSE);
        original.setPreDelayMs(50);
        original.getMouseConfig().setX(111);

        MacroStep copy = MacroStep.fromMap(original.toMap());
        assertTrue(copy != original, "copy must be a distinct object");
        assertTrue(copy.getMouseConfig() != original.getMouseConfig(),
                "nested config must be copied too, not shared");

        original.getMouseConfig().setX(999);
        original.setPreDelayMs(1);
        assertTrue(copy.getMouseConfig().getX() == 111, "editing the original changed the copy");
        assertTrue(copy.getPreDelayMs() == 50, "editing the original changed the copy's pre-delay");
        System.out.println("Deep copy   : copies are independent of their source");
    }

    /**
     * A SCROLL step must survive save/load with its direction and notches, and
     * the signed wheel amount must match Robot's convention (up negative).
     */
    private static void checkScrollRoundTrip() {
        MacroStep up = new MacroStep(ActionType.SCROLL);
        up.getScrollConfig().setDirection(com.automacropro.model.ScrollActionConfig.ScrollDirection.UP);
        up.getScrollConfig().setNotches(5);

        MacroStep back = MacroStep.fromMap(up.toMap());
        assertTrue(back.getType() == ActionType.SCROLL, "type lost on scroll round trip");
        assertTrue(back.getScrollConfig().getNotches() == 5, "scroll notches lost");
        assertTrue(back.getScrollConfig().getDirection()
                == com.automacropro.model.ScrollActionConfig.ScrollDirection.UP, "scroll direction lost");
        assertTrue(back.getScrollConfig().getWheelAmount() == -5, "up must be negative for Robot.mouseWheel");

        MacroStep down = new MacroStep(ActionType.SCROLL);
        assertTrue(down.getScrollConfig().getWheelAmount() == 3, "default down x3 must be +3");
        assertTrue(down.describe().contains("Scroll"), "describe() should name the scroll");
        System.out.println("Scroll      : round trip exact, up negative / down positive");
    }

    /**
     * Multi-row drag reorder. The index arithmetic differs from the single-row
     * case (rows above the drop point shift it up), so it gets its own check.
     */
    private static void checkMultiRowDragReorder() {
        // Calls the handler's own reorder(), so a regression there fails here.
        List<String> abcd = List.of("A", "B", "C", "D");

        // Drag {A,B} to the end -> C D A B
        assertTrue(StepListTransferHandler.reorder(abcd, new int[]{0, 1}, 4).equals(List.of("C", "D", "A", "B")),
                "drag {A,B} to end gave " + StepListTransferHandler.reorder(abcd, new int[]{0, 1}, 4));

        // Drag {C,D} to the front -> C D A B
        assertTrue(StepListTransferHandler.reorder(abcd, new int[]{2, 3}, 0).equals(List.of("C", "D", "A", "B")),
                "drag {C,D} to front gave " + StepListTransferHandler.reorder(abcd, new int[]{2, 3}, 0));

        // A non-contiguous selection keeps its relative order.
        assertTrue(StepListTransferHandler.reorder(abcd, new int[]{0, 2}, 4).equals(List.of("B", "D", "A", "C")),
                "non-contiguous drag gave " + StepListTransferHandler.reorder(abcd, new int[]{0, 2}, 4));

        // Single-row path still agrees with resolveTarget.
        assertTrue(StepListTransferHandler.reorder(abcd, new int[]{0}, 3).equals(List.of("B", "C", "A", "D")),
                "single-row drag gave " + StepListTransferHandler.reorder(abcd, new int[]{0}, 3));
        System.out.println("Multi-drag  : block reorder correct for 4 selection shapes");
    }

    /** The tabbed pane is no longer the content pane itself (a language bar sits above it). */
    private static JTabbedPane findTabs(Container root) {
        for (Component child : root.getComponents()) {
            if (child instanceof JTabbedPane) {
                return (JTabbedPane) child;
            }
            if (child instanceof Container) {
                JTabbedPane found = findTabs((Container) child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * Every key in the English (fallback) bundle must exist in the Indonesian
     * one. A missing key silently falls back to English, so half-translated UI
     * is invisible without a check like this - and the fallback direction means
     * it would never throw to alert anyone.
     */
    private static void checkTranslationsComplete() throws Exception {
        // Read the .properties files directly rather than through ResourceBundle:
        // a bundle transparently inherits missing keys from its parent, which is
        // exactly the fallback being tested for, so getBundle("id") would report
        // every English key as present.
        Properties en = loadBundleFile("/i18n/messages.properties");
        Properties id = loadBundleFile("/i18n/messages_id.properties");
        List<String> missingId = new ArrayList<>();
        for (String key : en.stringPropertyNames()) {
            if (!id.containsKey(key)) {
                missingId.add(key);
            }
        }
        List<String> orphanId = new ArrayList<>();
        for (String key : id.stringPropertyNames()) {
            if (!en.containsKey(key)) {
                orphanId.add(key);
            }
        }
        assertTrue(missingId.isEmpty(), "Indonesian bundle missing " + missingId.size() + " key(s): " + missingId);
        // An Indonesian-only key is dead weight - nothing can ever look it up,
        // since lookups are driven by what the code asks for.
        assertTrue(orphanId.isEmpty(), "Indonesian bundle has " + orphanId.size() + " key(s) absent from English: " + orphanId);
        assertTrue(!en.isEmpty(), "English bundle is empty - resources not on the classpath?");
        System.out.println("i18n        : " + en.size() + " keys, both languages complete");
    }

    private static Properties loadBundleFile(String resource) throws Exception {
        Properties properties = new Properties();
        try (java.io.InputStream in = ThemeSelfCheck.class.getResourceAsStream(resource)) {
            assertTrue(in != null, "missing bundle resource " + resource);
            properties.load(in);
        }
        return properties;
    }

    /**
     * The recorder maps JNativeHook VC codes to AWT VK codes. They are unrelated
     * integer spaces, so a wrong entry records one key and replays a different
     * one - subtle enough to survive casual testing.
     */
    private static void checkKeyCodeMapping() {
        int[][] pairs = {
                {NativeKeyEvent.VC_A, KeyEvent.VK_A},
                {NativeKeyEvent.VC_Z, KeyEvent.VK_Z},
                {NativeKeyEvent.VC_0, KeyEvent.VK_0},
                {NativeKeyEvent.VC_F5, KeyEvent.VK_F5},
                {NativeKeyEvent.VC_ENTER, KeyEvent.VK_ENTER},
                {NativeKeyEvent.VC_SPACE, KeyEvent.VK_SPACE},
                {NativeKeyEvent.VC_BACKSPACE, KeyEvent.VK_BACK_SPACE},
                {NativeKeyEvent.VC_SHIFT, KeyEvent.VK_SHIFT},
                {NativeKeyEvent.VC_CONTROL, KeyEvent.VK_CONTROL},
                {NativeKeyEvent.VC_ESCAPE, KeyEvent.VK_ESCAPE},
        };
        for (int[] pair : pairs) {
            int got = KeyCodeUtil.vcToVk(pair[0]);
            assertTrue(got == pair[1], "VC " + pair[0] + " should map to VK " + pair[1] + ", got " + got);
        }
        System.out.println("Keymap      : " + pairs.length + " VC->VK pairs correct");
    }

    /**
     * Jitter must stay inside the advertised range and never yield a delay below
     * 1ms - a zero or negative sleep would turn a humanized 1ms interval into a
     * busy spin.
     */
    private static void checkIntervalJitterRange() {
        Random random = new Random(1234); // fixed seed: this assertion must not flake
        AutoClickerSettings s = new AutoClickerSettings();
        s.setIntervalMs(100);
        s.setIntervalJitterMs(20);
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (int i = 0; i < 100_000; i++) {
            long v = s.nextIntervalMs(random);
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        assertTrue(min >= 80 && max <= 120, "100+/-20 drew outside 80..120: " + min + ".." + max);
        assertTrue(min < max, "jitter produced a constant value");

        // Spread wider than the interval must clamp at 1, not go to zero.
        AutoClickerSettings tight = new AutoClickerSettings();
        tight.setIntervalMs(5);
        tight.setIntervalJitterMs(50);
        long floor = Long.MAX_VALUE;
        for (int i = 0; i < 100_000; i++) {
            floor = Math.min(floor, tight.nextIntervalMs(random));
        }
        assertTrue(floor >= 1, "jitter produced a sub-1ms delay: " + floor);

        // Disabled jitter must be exact, not "randomly the same".
        AutoClickerSettings off = new AutoClickerSettings();
        off.setIntervalMs(42);
        assertTrue(off.nextIntervalMs(random) == 42, "jitter off should return the exact interval");
        System.out.println("Jitter      : range " + min + ".." + max + " ms, floor respected, off = exact");
    }

    /** A saved profile must come back with the same values, including the new humanizer fields. */
    private static void checkProfileRoundTrip() {
        String name = "__selfcheck_tmp";
        AutoClickerSettings original = new AutoClickerSettings();
        original.setIntervalMs(137);
        original.setIntervalJitterMs(19);
        original.setFixedClickCount(4242);
        original.getMouseConfig().setPositionJitterPx(7);

        assertTrue(ProfileManager.save(name, original), "profile save failed");
        try {
            AutoClickerSettings loaded = ProfileManager.load(name);
            assertTrue(loaded != null, "profile did not load back");
            assertTrue(loaded.getIntervalMs() == 137, "interval lost: " + loaded.getIntervalMs());
            assertTrue(loaded.getIntervalJitterMs() == 19, "jitter lost: " + loaded.getIntervalJitterMs());
            assertTrue(loaded.getFixedClickCount() == 4242, "count lost");
            assertTrue(loaded.getMouseConfig().getPositionJitterPx() == 7, "position jitter lost");
            assertTrue(ProfileManager.list().contains(name), "profile missing from list()");

            // Path traversal in a profile name must not escape the profiles dir.
            assertTrue(!ProfileManager.isValidName("../../evil"), "path traversal name should be rejected");
            assertTrue(!ProfileManager.isValidName("   "), "blank name should be rejected");
        } finally {
            ProfileManager.delete(name);
        }
        assertTrue(!ProfileManager.list().contains(name), "profile not deleted");
        System.out.println("Profiles    : round trip exact, traversal names rejected");
    }

    /**
     * Guards the mixed-DPI false-positive bug: the corner test must never fire
     * for a point in the interior of the screen it is on. The old code tested
     * the cursor against every screen's edges without checking which screen it
     * was on, so 71 of 242 sampled points triggered - most of the bottom third
     * of the primary matched the scaled secondary's "bottom-right corner".
     */
    private static void checkNoFalseCornerPositives() {
        int checked = 0;
        int falsePositives = 0;
        for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            Rectangle b = device.getDefaultConfiguration().getBounds();
            for (int gx = 1; gx < 10; gx++) {
                for (int gy = 1; gy < 10; gy++) {
                    Point p = new Point(b.x + b.width * gx / 10, b.y + b.height * gy / 10);
                    Rectangle owner = ScreenCoords.logicalBoundsContaining(p);
                    assertTrue(owner != null, "interior point " + p.x + "," + p.y + " belongs to no screen");
                    checked++;
                    if (isCornerOf(p, owner)) {
                        falsePositives++;
                    }
                }
            }
        }
        assertTrue(falsePositives == 0,
                falsePositives + " of " + checked + " interior points still read as a corner");
        System.out.println("Corners     : 0 false positives across " + checked + " interior points");
    }

    /** Mirrors FailsafeMonitor's edge test against the screen that owns the point. */
    private static boolean isCornerOf(Point p, Rectangle b) {
        boolean nearX = p.x <= b.x + 5 || p.x >= b.x + b.width - 6;
        boolean nearY = p.y <= b.y + 5 || p.y >= b.y + b.height - 6;
        return nearX && nearY;
    }

    /**
     * Physical -> logical -> physical must be the identity, otherwise a picked
     * coordinate drifts every time it is replayed. Measured error before the
     * fix was 240x135 px on the 125% monitor.
     */
    private static void checkCoordinateRoundTrip() {
        int worst = 0;
        int samples = 0;
        for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            GraphicsConfiguration gc = device.getDefaultConfiguration();
            Rectangle b = gc.getBounds();
            double sx = gc.getDefaultTransform().getScaleX();
            double sy = gc.getDefaultTransform().getScaleY();
            int physW = (int) Math.round(b.width * sx);
            int physH = (int) Math.round(b.height * sy);
            for (int gx = 0; gx < 10; gx++) {
                for (int gy = 0; gy < 10; gy++) {
                    int px = b.x + physW * gx / 10;
                    int py = b.y + physH * gy / 10;
                    Point logical = ScreenCoords.toRobotSpace(px, py);
                    Point back = ScreenCoords.toStoredSpace(logical);
                    worst = Math.max(worst, Math.max(Math.abs(back.x - px), Math.abs(back.y - py)));
                    samples++;
                }
            }
        }
        // 1px is the rounding floor of an integer round trip through a fractional scale.
        assertTrue(worst <= 1, "coordinate round trip drifted by " + worst + "px");
        System.out.println("Coords      : round trip exact within " + worst + "px over " + samples + " points");
    }

    /**
     * The sequencer's top row must fit at the startup width, or a control gets
     * silently hidden - the "Import Project... only appears after I widen the
     * window" bug. Checks the real frame, so it fails if a future label, font,
     * or translated string pushes the row past the window again.
     */
    private static void checkTopRowFitsOnStartup() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
            frame.validate();
            Squeeze worst = new Squeeze();
            measureSqueeze(frame.getContentPane(), worst);
            frame.dispose();
            assertTrue(worst.slack >= 0,
                    "'" + worst.where + "' needs " + (-worst.slack) + "px more than it is given - "
                            + "its trailing controls would be wrapped out of sight");
            System.out.println("Layout      : tightest row has " + worst.slack + "px spare ("
                    + worst.where + ")");
        });
    }

    private static final class Squeeze {
        int slack = Integer.MAX_VALUE;
        String where = "?";
    }

    /**
     * Finds the container with the least room to spare, i.e. the smallest
     * {@code width - preferredWidth}. A negative value is the exact condition
     * that made FlowLayout wrap the "Import Project..." button out of sight, so
     * this fails if a future font, label, or translated string reintroduces it.
     *
     * Scroll panes are skipped: overflowing is what they are for, and their
     * content is reachable via the scrollbar rather than lost.
     */
    private static void measureSqueeze(Container parent, Squeeze worst) {
        for (Component child : parent.getComponents()) {
            if (!child.isVisible() || child instanceof JScrollPane) {
                continue;
            }
            if (!(child instanceof Container) || child.getWidth() <= 0) {
                continue;
            }
            Container container = (Container) child;
            // Only our own layout rows can be squeezed in the way that hides a
            // control. Every Swing widget is technically a Container, and the
            // composite ones (JSpinner, JComboBox, JTextField) have children
            // while still always receiving exactly their preferred width - so
            // measuring them reported a vacuous "0px spare (JSpinner)" and
            // checked nothing. The wrap bug lives in panels, not in widgets.
            if (container instanceof JPanel && container.getComponentCount() > 0) {
                int slack = container.getWidth() - container.getPreferredSize().width;
                if (slack < worst.slack) {
                    worst.slack = slack;
                    worst.where = describeContainer(container);
                }
            }
            measureSqueeze(container, worst);
        }
    }

    /** Best-effort human label for a container: its titled border, else its class. */
    private static String describeContainer(Container c) {
        if (c instanceof JComponent) {
            Border border = ((JComponent) c).getBorder();
            String title = titleOf(border);
            if (title != null) {
                return title;
            }
        }
        return c.getClass().getSimpleName();
    }

    private static String titleOf(Border border) {
        if (border instanceof TitledBorder) {
            return ((TitledBorder) border).getTitle();
        }
        if (border instanceof CompoundBorder) {
            String outer = titleOf(((CompoundBorder) border).getOutsideBorder());
            return outer != null ? outer : titleOf(((CompoundBorder) border).getInsideBorder());
        }
        return null;
    }

    /**
     * The drop-index adjustment is the one piece of real arithmetic in the DnD
     * path and it is off-by-one in the obvious implementation, so it gets a
     * check. Calls the handler's own {@code resolveTarget} rather than
     * restating the maths, so a regression there fails here.
     */
    private static void checkDragAndDropReorder() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            DefaultListModel<MacroStep> model = new DefaultListModel<>();
            MacroStep a = new MacroStep(ActionType.MOUSE);
            MacroStep b = new MacroStep(ActionType.KEYBOARD);
            MacroStep c = new MacroStep(ActionType.DELAY);
            model.addElement(a);
            model.addElement(b);
            model.addElement(c);

            // Drag row 0 down past row 2: drop index 3 is in before-removal
            // coordinates and must land it last, not out of bounds.
            moveForTest(model, 0, 3);
            assertTrue(model.get(0) == b && model.get(1) == c && model.get(2) == a,
                    "downward drag should put A last, got " + describe(model));

            // Drag it back to the top.
            moveForTest(model, 2, 0);
            assertTrue(model.get(0) == a && model.get(1) == b && model.get(2) == c,
                    "upward drag should restore A first, got " + describe(model));

            // A drop back onto its own position is a no-op, not a move.
            assertTrue(StepListTransferHandler.resolveTarget(1, 1, 3) < 0, "self-drop should be rejected");
            assertTrue(StepListTransferHandler.resolveTarget(1, 2, 3) < 0, "drop just below self is a no-op");
            System.out.println("DnD         : reorder arithmetic correct both directions");
        });
    }

    private static void moveForTest(DefaultListModel<MacroStep> model, int from, int dropIndex) {
        int target = StepListTransferHandler.resolveTarget(from, dropIndex, model.size());
        assertTrue(target >= 0, "expected a valid move for " + from + " -> " + dropIndex);
        model.add(target, model.remove(from));
    }

    private static String describe(DefaultListModel<MacroStep> model) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < model.size(); i++) {
            sb.append(model.get(i).getType()).append(' ');
        }
        return sb.toString().trim();
    }

    /**
     * Exercises the dwell + one-shot-latch state machine.
     *
     * Drives {@code isCursorInCornerAt} with synthetic positions rather than
     * parking the real cursor with Robot: the earlier version did the latter and
     * was flaky for a good reason - it commandeered the user's mouse for two
     * seconds, and one physical nudge during the dwell window moved the cursor
     * out of the corner, so the check failed while the failsafe was working
     * correctly. A trace confirmed exactly that (parked at 0,1439 then found at
     * -919,754 after the sleep).
     */
    private static void checkFailsafeDwellAndLatch() throws Exception {
        AtomicInteger alerts = new AtomicInteger();
        FailsafeMonitor.addAlertListener(alerts::incrementAndGet);
        FailsafeMonitor.setEnabled(true);
        FailsafeMonitor.noteEngineStarted();

        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration().getBounds();
        Point corner = new Point(screen.x, screen.y);
        Point middle = new Point(screen.x + screen.width / 2, screen.y + screen.height / 2);

        // Inside the 800ms grace period nothing may fire, however long we dwell.
        assertTrue(!FailsafeMonitor.isCursorInCornerAt(corner), "must not trigger during the startup grace period");
        Thread.sleep(850);

        // First sighting only starts the dwell clock - it must not trigger yet.
        assertTrue(!FailsafeMonitor.isCursorInCornerAt(corner), "first corner sighting should only arm the dwell");
        assertTrue(!FailsafeMonitor.isCursorInCornerAt(corner), "must not trigger before the dwell time elapses");
        Thread.sleep(250); // exceed DWELL_MS

        int triggers = 0;
        for (int i = 0; i < 50_000; i++) {
            if (FailsafeMonitor.isCursorInCornerAt(corner)) {
                triggers++;
            }
        }
        SwingUtilities.invokeAndWait(() -> { });

        assertTrue(triggers == 50_000, "a parked corner should keep reporting true, got " + triggers);
        assertTrue(alerts.get() == 1,
                "alert must fire exactly once per run despite " + triggers + " triggers, fired " + alerts.get());

        // Screen middle must never trigger - the mixed-DPI false positive.
        assertTrue(!FailsafeMonitor.isCursorInCornerAt(middle), "screen centre must never read as a corner");
        System.out.println("Failsafe    : dwell honoured, " + triggers + " triggers -> "
                + alerts.get() + " alert (latched), centre ignored");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
