import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import javax.swing.plaf.basic.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.print.PrinterException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

// ============================================================================
// 6. ANIMATION ENGINE (pure Swing timers - no external libraries)
// ============================================================================
enum Easing {
    LINEAR { float apply(float t) { return t; } },
    OUT_CUBIC { float apply(float t) { float u = 1f - t; return 1f - u * u * u; } },
    IN_OUT_CUBIC {
        float apply(float t) {
            return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
        }
    };
    abstract float apply(float t);
}

/** A float that can glide toward a target value; repaints its owner on every frame. */
final class AnimatedFloat {
    private float value;
    private Timer timer;
    private final Runnable onChange;

    AnimatedFloat(float initial, Runnable onChange) {
        this.value = initial;
        this.onChange = onChange;
    }

    float get() { return value; }

    void set(float v) {
        stop();
        value = v;
        onChange.run();
    }

    void animateTo(float target, int ms, Easing easing) {
        stop();
        final float from = value;
        if (ms <= 0 || from == target) {
            value = target;
            onChange.run();
            return;
        }
        final long start = System.nanoTime();
        timer = new Timer(15, null);
        timer.addActionListener(e -> {
            float t = Math.min(1f, (System.nanoTime() - start) / (ms * 1_000_000f));
            value = from + (target - from) * easing.apply(t);
            onChange.run();
            if (t >= 1f) {
                value = target;
                ((Timer) e.getSource()).stop();
            }
        });
        timer.start();
    }

    void stop() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
    }
}

/** One-shot animation helper (supports a start delay). */
final class Animator {
    private Animator() {}

    static Timer run(int delayMs, int durationMs, Easing easing, Consumer<Float> tick, Runnable done) {
        final long[] start = {-1};
        Timer timer = new Timer(15, null);
        timer.setInitialDelay(Math.max(0, delayMs));
        timer.addActionListener(e -> {
            long now = System.nanoTime();
            if (start[0] < 0) start[0] = now;
            float t = Math.min(1f, (now - start[0]) / (durationMs * 1_000_000f));
            tick.accept(easing.apply(t));
            if (t >= 1f) {
                ((Timer) e.getSource()).stop();
                if (done != null) done.run();
            }
        });
        timer.start();
        return timer;
    }
}

// ============================================================================
// 7. MODERN UI THEME (Consistent Colors, Fonts, Vector Icons & Helpers)
// ============================================================================
public class UITheme {
    public static final Color SIDEBAR_BG = new Color(15, 23, 42);       // Deep Slate
    public static final Color SIDEBAR_ACTIVE = new Color(30, 41, 59);
    public static final Color BG_LIGHT = new Color(241, 245, 249);
    public static final Color CARD_BG = Color.WHITE;
    public static final Color TEXT_DARK = new Color(15, 23, 42);
    public static final Color TEXT_MUTED = new Color(100, 116, 139);
    public static final Color SLATE_300 = new Color(148, 163, 184);
    public static final Color BORDER = new Color(226, 232, 240);
    public static final Color FIELD_BORDER = new Color(203, 213, 225);
    public static final Color SURFACE_ALT = new Color(248, 250, 252);

    public static final Color PRIMARY = new Color(37, 99, 235);
    public static final Color PRIMARY_DARK = new Color(29, 78, 216);
    public static final Color SUCCESS = new Color(16, 185, 129);
    public static final Color WARNING = new Color(245, 158, 11);
    public static final Color DANGER = new Color(239, 68, 68);
    public static final Color DANGER_DARK = new Color(185, 28, 28);
    public static final Color PURPLE = new Color(124, 58, 237);
    public static final Color INDIGO = new Color(79, 70, 229);
    public static final Color TEAL = new Color(13, 148, 136);
    public static final Color ORANGE = new Color(234, 88, 12);

    private static final String FAMILY = pickFamily();

    public static final Font FONT_TITLE = font(Font.BOLD, 22f);
    public static final Font FONT_SUBTITLE = font(Font.BOLD, 15f);
    public static final Font FONT_REGULAR = font(Font.PLAIN, 13f);
    public static final Font FONT_BOLD = font(Font.BOLD, 13f);
    public static final Font FONT_SMALL = font(Font.PLAIN, 12f);
    public static final Font FONT_MONO = new Font("Monospaced", Font.PLAIN, 12);

    private static String pickFamily() {
        String[] preferred = {"Segoe UI", "Inter", "SF Pro Text", "Helvetica Neue", "Roboto", "Ubuntu", "Noto Sans", "SansSerif"};
        try {
            java.util.Set<String> have = new java.util.HashSet<>(java.util.Arrays.asList(
                    GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
            for (String f : preferred) if (have.contains(f)) return f;
        } catch (Throwable ignored) {}
        return "SansSerif";
    }

    static Font font(int style, float size) {
        return new Font(FAMILY, Font.PLAIN, 12).deriveFont(style, size);
    }

    static void installDefaults() {
        UIManager.put("ToolTip.background", new javax.swing.plaf.ColorUIResource(SIDEBAR_BG));
        UIManager.put("ToolTip.foreground", new javax.swing.plaf.ColorUIResource(Color.WHITE));
        UIManager.put("ToolTip.border", new EmptyBorder(6, 10, 6, 10));
        UIManager.put("ToolTip.font", font(Font.PLAIN, 12f));
        UIManager.put("Label.font", FONT_REGULAR);
        UIManager.put("Label.foreground", TEXT_DARK);
        UIManager.put("Panel.background", BG_LIGHT);
        ToolTipManager.sharedInstance().setInitialDelay(350);
    }

    // ---------------- color + painting helpers ----------------
    static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }

    static Color mix(Color a, Color b, float t) {
        t = clamp01(t);
        return new Color(
                Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a)));
    }

    static Graphics2D aa(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g2;
    }

    static void paintShadow(Graphics2D g2, float x, float y, float w, float h, float arc, int spread, float strength) {
        for (int i = spread; i >= 1; i--) {
            float f = 1f - (float) i / (spread + 1);
            int a = (int) (strength * 255 * f * f * 2f / spread);
            g2.setColor(new Color(15, 23, 42, Math.max(0, Math.min(255, a))));
            g2.fill(new RoundRectangle2D.Float(x - i, y - i + spread / 3f, w + 2 * i, h + 2 * i, arc + i * 2, arc + i * 2));
        }
    }

    static String ellipsize(String s, FontMetrics fm, int maxW) {
        if (fm.stringWidth(s) <= maxW) return s;
        String dots = "\u2026";
        int end = s.length();
        while (end > 1 && fm.stringWidth(s.substring(0, end) + dots) > maxW) end--;
        return s.substring(0, end) + dots;
    }

    // ---------------- factories (kept API-compatible with the original UITheme) ----------------
    public static JButton createButton(String text, Color bg, Color fg) {
        return new RoundedButton(text, bg, fg);
    }

    static JLabel label(String text, Font f, Color c) {
        JLabel l = new JLabel(text);
        l.setFont(f);
        l.setForeground(c);
        return l;
    }

    static JLabel fieldLabel(String text) {
        return label(text, font(Font.BOLD, 12f), new Color(51, 65, 85));
    }

    static JScrollPane scroll(JComponent view) {
        JScrollPane sp = new JScrollPane(view);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setBackground(Color.WHITE);
        sp.getVerticalScrollBar().setUI(new ModernScrollBarUI());
        sp.getHorizontalScrollBar().setUI(new ModernScrollBarUI());
        sp.getVerticalScrollBar().setUnitIncrement(18);
        sp.getVerticalScrollBar().setOpaque(false);
        sp.getHorizontalScrollBar().setOpaque(false);
        return sp;
    }
}

/** Notice categories shared by toasts and dialogs. */
enum NoticeKind {
    INFO(UITheme.PRIMARY), SUCCESS(UITheme.SUCCESS), WARNING(UITheme.WARNING), ERROR(UITheme.DANGER);

    final Color color;
    NoticeKind(Color color) { this.color = color; }

    void paintIcon(Graphics2D g, double cx, double cy, double s, Color c) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.translate(cx, cy);
        g2.setColor(c);
        g2.setStroke(new BasicStroke((float) Math.max(1.8, s * 0.13), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (this) {
            case SUCCESS: Glyph.CHECK.paint(g2, 0, 0, s, c); break;
            case ERROR:
                g2.draw(new Line2D.Double(-.32 * s, -.32 * s, .32 * s, .32 * s));
                g2.draw(new Line2D.Double(.32 * s, -.32 * s, -.32 * s, .32 * s));
                break;
            case WARNING:
                g2.draw(new Line2D.Double(0, -.38 * s, 0, .1 * s));
                g2.fill(new Ellipse2D.Double(-.07 * s, .26 * s, .14 * s, .14 * s));
                break;
            default:
                g2.fill(new Ellipse2D.Double(-.07 * s, -.4 * s, .14 * s, .14 * s));
                g2.draw(new Line2D.Double(0, -.12 * s, 0, .38 * s));
        }
        g2.dispose();
    }
}

/** Small vector icon set (drawn with Java2D - no image files needed). */
enum Glyph {
    CHECK, ARROW, CLOCK, WRENCH, BOX, SWAP, USERS;

    void paint(Graphics2D g, double cx, double cy, double s, Color color) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.translate(cx, cy);
        g2.setColor(color);
        g2.setStroke(new BasicStroke((float) Math.max(1.6, s * 0.11), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (this) {
            case CHECK: {
                Path2D p = new Path2D.Double();
                p.moveTo(-.42 * s, .02 * s); p.lineTo(-.1 * s, .34 * s); p.lineTo(.46 * s, -.3 * s);
                g2.draw(p);
                break;
            }
            case ARROW: {
                g2.draw(new Line2D.Double(-.4 * s, .4 * s, .4 * s, -.4 * s));
                Path2D p = new Path2D.Double();
                p.moveTo(-.05 * s, -.42 * s); p.lineTo(.42 * s, -.42 * s); p.lineTo(.42 * s, .05 * s);
                g2.draw(p);
                break;
            }
            case CLOCK:
                g2.draw(new Ellipse2D.Double(-.5 * s, -.5 * s, s, s));
                g2.draw(new Line2D.Double(0, 0, 0, -.28 * s));
                g2.draw(new Line2D.Double(0, 0, .2 * s, .12 * s));
                break;
            case WRENCH:
                g2.draw(new Line2D.Double(-.42 * s, .42 * s, .1 * s, -.1 * s));
                g2.draw(new Ellipse2D.Double(.02 * s, -.5 * s, .46 * s, .46 * s));
                break;
            case BOX:
                g2.draw(new RoundRectangle2D.Double(-.46 * s, -.36 * s, .92 * s, .76 * s, .2 * s, .2 * s));
                g2.draw(new Line2D.Double(-.46 * s, -.06 * s, .46 * s, -.06 * s));
                g2.draw(new Line2D.Double(-.12 * s, .12 * s, .12 * s, .12 * s));
                break;
            case SWAP: {
                g2.draw(new Line2D.Double(-.45 * s, -.2 * s, .45 * s, -.2 * s));
                Path2D a = new Path2D.Double();
                a.moveTo(.22 * s, -.42 * s); a.lineTo(.45 * s, -.2 * s); a.lineTo(.22 * s, .02 * s);
                g2.draw(a);
                g2.draw(new Line2D.Double(.45 * s, .22 * s, -.45 * s, .22 * s));
                Path2D b = new Path2D.Double();
                b.moveTo(-.22 * s, 0); b.lineTo(-.45 * s, .22 * s); b.lineTo(-.22 * s, .44 * s);
                g2.draw(b);
                break;
            }
            default: // USERS
                g2.draw(new Ellipse2D.Double(-.2 * s, -.46 * s, .4 * s, .4 * s));
                g2.draw(new Arc2D.Double(-.42 * s, .08 * s, .84 * s, .84 * s, 0, 180, Arc2D.OPEN));
        }
        g2.dispose();
    }
}

// ============================================================================
// 8. REUSABLE ANIMATED / CUSTOM-PAINTED SWING COMPONENTS
// ============================================================================

/** Panel that can fade + slide its content in (used for page transitions and staggered entrances). */
class FadePanel extends JPanel {
    private float a = 1f, dy = 0f;
    private Timer timer;
    private int autoDelay = -1;

    FadePanel(LayoutManager lm) {
        super(lm);
        setOpaque(false);
    }

    /** Start hidden and play the entrance automatically once the panel is first shown. */
    FadePanel autoPlay(int delayMs) {
        autoDelay = delayMs;
        a = 0f;
        return this;
    }

    @Override public void addNotify() {
        super.addNotify();
        if (autoDelay >= 0) {
            int d = autoDelay;
            autoDelay = -1;
            playIn(d + 80, 460);
        }
    }

    void playIn(int delay, int ms) {
        if (timer != null) timer.stop();
        a = 0f;
        dy = 18f;
        repaint();
        timer = Animator.run(delay, ms, Easing.OUT_CUBIC, v -> {
            a = v;
            dy = (1f - v) * 18f;
            repaint();
        }, () -> { a = 1f; dy = 0f; repaint(); });
    }

    @Override public void paint(Graphics g) {
        if (a >= 1f && dy == 0f) {
            super.paint(g);
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setComposite(AlphaComposite.SrcOver.derive(UITheme.clamp01(a)));
        g2.translate(0, dy);
        super.paint(g2);
        g2.dispose();
    }
}

/** Flat rounded button with animated hover color, press depth and a click ripple. */
class RoundedButton extends JButton {
    private final Color base;
    private final AnimatedFloat hover = new AnimatedFloat(0f, this::repaint);
    private final AnimatedFloat press = new AnimatedFloat(0f, this::repaint);
    private final AnimatedFloat ripple = new AnimatedFloat(1f, this::repaint);
    private Point ripplePoint = new Point(0, 0);

    RoundedButton(String text, Color bg, Color fg) {
        super(text);
        this.base = bg;
        setForeground(fg);
        setFont(UITheme.FONT_BOLD);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setRolloverEnabled(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setBorder(new EmptyBorder(10, 18, 10, 18));
        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hover.animateTo(1f, 160, Easing.OUT_CUBIC); }
            @Override public void mouseExited(MouseEvent e) {
                hover.animateTo(0f, 220, Easing.OUT_CUBIC);
                press.animateTo(0f, 120, Easing.OUT_CUBIC);
            }
            @Override public void mousePressed(MouseEvent e) {
                if (!isEnabled()) return;
                press.animateTo(1f, 80, Easing.OUT_CUBIC);
                ripplePoint = e.getPoint();
                ripple.set(0f);
                ripple.animateTo(1f, 520, Easing.OUT_CUBIC);
            }
            @Override public void mouseReleased(MouseEvent e) { press.animateTo(0f, 160, Easing.OUT_CUBIC); }
        });
    }

    @Override public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        return new Dimension(d.width, Math.max(d.height + 4, 40));
    }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = UITheme.aa(g);
        int w = getWidth(), h = getHeight();
        float hv = hover.get(), pr = press.get();
        float bodyH = h - 4f;
        float y = 2f - 1.5f * hv + 1.2f * pr;
        Color fill = isEnabled() ? UITheme.mix(base, Color.BLACK, 0.10f * hv + 0.08f * pr)
                                 : UITheme.mix(base, Color.WHITE, 0.6f);
        if (hv > 0.01f && isEnabled()) {
            g2.setColor(UITheme.alpha(base, (int) (70 * hv)));
            g2.fill(new RoundRectangle2D.Float(3, y + 3.5f, w - 6, bodyH, 14, 14));
        }
        RoundRectangle2D body = new RoundRectangle2D.Float(0, y, w, bodyH, 14, 14);
        g2.setColor(fill);
        g2.fill(body);
        float rp = ripple.get();
        if (rp < 1f) {
            Shape old = g2.getClip();
            g2.clip(body);
            int r = (int) (Math.max(w, h) * 1.3f * rp);
            g2.setColor(new Color(255, 255, 255, (int) (70 * (1 - rp))));
            g2.fillOval(ripplePoint.x - r, ripplePoint.y - r, 2 * r, 2 * r);
            g2.setClip(old);
        }
        g2.dispose();
        super.paintComponent(g);
    }
}

/** Shared drawing for rounded text inputs (glow on focus). */
final class FieldChrome {
    private FieldChrome() {}

    static void paint(Graphics g, JComponent c, float focus) {
        Graphics2D g2 = UITheme.aa(g);
        int w = c.getWidth(), h = c.getHeight();
        if (focus > 0.01f) {
            g2.setColor(UITheme.alpha(UITheme.PRIMARY, (int) (55 * focus)));
            g2.setStroke(new BasicStroke(4f));
            g2.draw(new RoundRectangle2D.Float(2, 2, w - 4, h - 4, 14, 14));
        }
        g2.setColor(Color.WHITE);
        g2.fill(new RoundRectangle2D.Float(1, 1, w - 2, h - 2, 12, 12));
        g2.setColor(UITheme.mix(UITheme.FIELD_BORDER, UITheme.PRIMARY, focus));
        g2.setStroke(new BasicStroke(1.2f + 0.6f * focus));
        g2.draw(new RoundRectangle2D.Float(1.5f, 1.5f, w - 3, h - 3, 12, 12));
        g2.dispose();
    }

    static void placeholder(Graphics g, javax.swing.text.JTextComponent c, String hint) {
        if (hint == null || hint.isEmpty() || !c.getText().isEmpty()) return;
        Graphics2D g2 = UITheme.aa(g);
        g2.setFont(c.getFont());
        g2.setColor(UITheme.SLATE_300);
        FontMetrics fm = g2.getFontMetrics();
        Insets in = c.getInsets();
        g2.drawString(hint, in.left, (c.getHeight() + fm.getAscent() - fm.getDescent()) / 2f);
        g2.dispose();
    }
}

class RoundedTextField extends JTextField {
    private final AnimatedFloat focus = new AnimatedFloat(0f, this::repaint);
    private String hint = "";

    RoundedTextField(String text) {
        super(text);
        setOpaque(false);
        setFont(UITheme.FONT_REGULAR);
        setForeground(UITheme.TEXT_DARK);
        setCaretColor(UITheme.PRIMARY);
        setSelectionColor(new Color(191, 219, 254));
        setBorder(new EmptyBorder(10, 13, 10, 13));
        addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { focus.animateTo(1f, 180, Easing.OUT_CUBIC); }
            @Override public void focusLost(FocusEvent e) { focus.animateTo(0f, 220, Easing.OUT_CUBIC); }
        });
    }

    RoundedTextField setHint(String h) { hint = h; return this; }

    @Override protected void paintComponent(Graphics g) {
        FieldChrome.paint(g, this, focus.get());
        super.paintComponent(g);
        FieldChrome.placeholder(g, this, hint);
    }
}

class RoundedPasswordField extends JPasswordField {
    private final AnimatedFloat focus = new AnimatedFloat(0f, this::repaint);

    RoundedPasswordField(String text) {
        super(text);
        setOpaque(false);
        setFont(UITheme.FONT_REGULAR);
        setForeground(UITheme.TEXT_DARK);
        setCaretColor(UITheme.PRIMARY);
        setSelectionColor(new Color(191, 219, 254));
        setEchoChar('\u2022');
        setBorder(new EmptyBorder(10, 13, 10, 13));
        addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { focus.animateTo(1f, 180, Easing.OUT_CUBIC); }
            @Override public void focusLost(FocusEvent e) { focus.animateTo(0f, 220, Easing.OUT_CUBIC); }
        });
    }

    void setRevealed(boolean revealed) { setEchoChar(revealed ? (char) 0 : '\u2022'); }

    @Override protected void paintComponent(Graphics g) {
        FieldChrome.paint(g, this, focus.get());
        super.paintComponent(g);
    }
}

/** Slim rounded scrollbar. */
class ModernScrollBarUI extends BasicScrollBarUI {
    private JButton zero() {
        JButton b = new JButton();
        Dimension d = new Dimension(0, 0);
        b.setPreferredSize(d); b.setMinimumSize(d); b.setMaximumSize(d);
        return b;
    }
    @Override protected JButton createDecreaseButton(int o) { return zero(); }
    @Override protected JButton createIncreaseButton(int o) { return zero(); }
    @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) {}
    @Override protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
        if (r.isEmpty() || !scrollbar.isEnabled()) return;
        Graphics2D g2 = UITheme.aa(g);
        g2.setColor(isThumbRollover() || isDragging ? UITheme.SLATE_300 : UITheme.FIELD_BORDER);
        g2.fill(new RoundRectangle2D.Float(r.x + 2, r.y + 2, r.width - 4, r.height - 4, 8, 8));
        g2.dispose();
    }
    @Override public Dimension getPreferredSize(JComponent c) { return new Dimension(11, 11); }
}

/** Combo box with rounded chrome, hover/focus feedback and a clean popup. */
class RoundedComboBox<E> extends JComboBox<E> {
    private final AnimatedFloat hover = new AnimatedFloat(0f, this::repaint);

    RoundedComboBox() { super(); init(); }
    RoundedComboBox(E[] items) { super(items); init(); }

    private void init() {
        setOpaque(false);
        setFont(UITheme.FONT_REGULAR);
        setForeground(UITheme.TEXT_DARK);
        setBorder(new EmptyBorder(2, 12, 2, 2));
        setRenderer(new ModernComboRenderer());
        setMaximumRowCount(8);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        MouseAdapter ma = new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hover.animateTo(1f, 150, Easing.OUT_CUBIC); }
            @Override public void mouseExited(MouseEvent e) { hover.animateTo(0f, 200, Easing.OUT_CUBIC); }
        };
        addMouseListener(ma);
        for (Component c : getComponents()) c.addMouseListener(ma);
    }

    float hoverValue() { return hover.get(); }

    @Override public void updateUI() { setUI(new ModernComboUI()); }

    @Override public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        return new Dimension(Math.min(d.width, 300), Math.max(d.height, 38));
    }
    @Override public Dimension getMinimumSize() { return new Dimension(120, 38); }
}

class ModernComboUI extends BasicComboBoxUI {
    @Override protected JButton createArrowButton() {
        JButton b = new JButton() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = UITheme.aa(g);
                int cx = getWidth() / 2, cy = getHeight() / 2;
                g2.setColor(UITheme.TEXT_MUTED);
                g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                Path2D p = new Path2D.Float();
                p.moveTo(cx - 4, cy - 2); p.lineTo(cx, cy + 2); p.lineTo(cx + 4, cy - 2);
                g2.draw(p);
                g2.dispose();
            }
        };
        b.setBorder(BorderFactory.createEmptyBorder());
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        b.setOpaque(false);
        b.setPreferredSize(new Dimension(32, 32));
        return b;
    }

    @Override public void paint(Graphics g, JComponent c) {
        float hv = c instanceof RoundedComboBox ? ((RoundedComboBox<?>) c).hoverValue() : 0f;
        boolean active = c.hasFocus() || comboBox.isPopupVisible();
        Graphics2D g2 = UITheme.aa(g);
        int w = c.getWidth(), h = c.getHeight();
        if (active) {
            g2.setColor(UITheme.alpha(UITheme.PRIMARY, 55));
            g2.setStroke(new BasicStroke(4f));
            g2.draw(new RoundRectangle2D.Float(2, 2, w - 4, h - 4, 14, 14));
        }
        g2.setColor(Color.WHITE);
        g2.fill(new RoundRectangle2D.Float(1, 1, w - 2, h - 2, 12, 12));
        Color border = active ? UITheme.PRIMARY : UITheme.mix(UITheme.FIELD_BORDER, UITheme.SLATE_300, hv);
        g2.setColor(border);
        g2.setStroke(new BasicStroke(active ? 1.8f : 1.2f));
        g2.draw(new RoundRectangle2D.Float(1.5f, 1.5f, w - 3, h - 3, 12, 12));
        g2.dispose();
        super.paint(g, c);
    }

    @Override public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {}

    @Override protected ComboPopup createPopup() {
        BasicComboPopup p = new BasicComboPopup(comboBox) {
            @Override protected JScrollPane createScroller() {
                JScrollPane sp = super.createScroller();
                sp.setBorder(BorderFactory.createEmptyBorder());
                sp.getVerticalScrollBar().setUI(new ModernScrollBarUI());
                return sp;
            }
        };
        p.setBorder(BorderFactory.createLineBorder(UITheme.FIELD_BORDER));
        return p;
    }
}

/** Two-line cell renderer: knows how to present Equipment, Borrower and BorrowTransaction nicely. */
class ModernComboRenderer extends JPanel implements ListCellRenderer<Object> {
    private final JLabel primary = new JLabel();
    private final JLabel secondary = new JLabel();
    private boolean selected, inList;

    ModernComboRenderer() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
        primary.setFont(UITheme.FONT_BOLD);
        secondary.setFont(UITheme.font(Font.PLAIN, 11f));
        primary.setAlignmentX(Component.LEFT_ALIGNMENT);
        secondary.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(Box.createVerticalGlue());
        add(primary);
        add(secondary);
        add(Box.createVerticalGlue());
    }

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        String p, s = null;
        if (value instanceof Equipment) {
            Equipment e = (Equipment) value;
            p = e.getEquipmentId() + "  \u00B7  " + e.getName();
            s = e.getCategory() + " \u2014 " + e.getSpecificDetails();
        } else if (value instanceof Borrower) {
            Borrower b = (Borrower) value;
            p = b.getBorrowerId() + "  \u00B7  " + b.getFullName();
            s = b.getRole() + "  |  Active " + b.getActiveBorrowedCount() + "/" + b.getMaxBorrowLimit()
                    + "  |  Strikes " + b.getStrikeCount() + (b.isSuspended() ? "  |  SUSPENDED" : "");
        } else if (value instanceof BorrowTransaction) {
            BorrowTransaction t = (BorrowTransaction) value;
            p = t.getTransactionId() + "  \u00B7  " + t.getEquipmentId() + " \u2192 " + t.getBorrowerId();
            s = "Room " + t.getRoomLocation() + (t.isOverdue() ? "  |  OVERDUE" : "  |  Due " + t.getDueTime().format(BorrowTransaction.FMT));
        } else {
            p = value == null ? "" : String.valueOf(value);
        }
        inList = index >= 0;
        selected = isSelected && inList;
        primary.setText(p);
        primary.setForeground(UITheme.TEXT_DARK);
        secondary.setText(s == null ? " " : s);
        secondary.setForeground(selected ? UITheme.PRIMARY_DARK : UITheme.TEXT_MUTED);
        secondary.setVisible(inList && s != null);
        setBorder(inList ? new EmptyBorder(7, 12, 7, 12) : new EmptyBorder(0, 0, 0, 0));
        setToolTipText(s == null ? p : p + "  \u2014  " + s);
        return this;
    }

    @Override protected void paintComponent(Graphics g) {
        if (inList) {
            g.setColor(selected ? new Color(239, 246, 255) : Color.WHITE);
            g.fillRect(0, 0, getWidth(), getHeight());
        }
        super.paintComponent(g);
    }
}

/** Check box with an animated "draw-on" tick, hover glow and optional staggered entrance. */
class ModernCheckBox extends JCheckBox {
    private final AnimatedFloat tick;
    private final AnimatedFloat hover = new AnimatedFloat(0f, this::repaint);
    private float introAlpha = 1f, introDy = 0f;
    private Timer introTimer;

    ModernCheckBox(String text, boolean selected) {
        super(text, selected);
        tick = new AnimatedFloat(selected ? 1f : 0f, this::repaint);
        setOpaque(false);
        setFocusPainted(false);
        setFont(UITheme.FONT_REGULAR);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setBorder(new EmptyBorder(4, 2, 4, 2));
        addItemListener(e -> tick.animateTo(isSelected() ? 1f : 0f, 220, Easing.OUT_CUBIC));
        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hover.animateTo(1f, 140, Easing.OUT_CUBIC); }
            @Override public void mouseExited(MouseEvent e) { hover.animateTo(0f, 180, Easing.OUT_CUBIC); }
        });
    }

    void playIntro(int delay) {
        introAlpha = 0f;
        introDy = 10f;
        if (introTimer != null) introTimer.stop();
        introTimer = Animator.run(delay, 300, Easing.OUT_CUBIC, t -> {
            introAlpha = t;
            introDy = (1f - t) * 10f;
            repaint();
        }, () -> { introAlpha = 1f; introDy = 0f; repaint(); });
    }

    @Override public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        Insets in = getInsets();
        return new Dimension(in.left + 20 + 10 + fm.stringWidth(getText()) + in.right + 4, 28);
    }
    @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = UITheme.aa(g);
        g2.setComposite(AlphaComposite.SrcOver.derive(UITheme.clamp01(introAlpha)));
        Insets in = getInsets();
        int size = 20;
        float x = in.left, y = (getHeight() - size) / 2f + introDy;
        float tk = UITheme.clamp01(tick.get()), hv = hover.get();
        g2.setColor(UITheme.mix(Color.WHITE, UITheme.SUCCESS, tk));
        g2.fill(new RoundRectangle2D.Float(x, y, size, size, 7, 7));
        g2.setColor(UITheme.mix(UITheme.mix(UITheme.FIELD_BORDER, UITheme.PRIMARY, hv), UITheme.SUCCESS, tk));
        g2.setStroke(new BasicStroke(1.6f));
        g2.draw(new RoundRectangle2D.Float(x + .8f, y + .8f, size - 1.6f, size - 1.6f, 7, 7));
        if (tk > 0.02f) {
            float split = 0.38f;
            Path2D p = new Path2D.Float();
            float ax = x + 5.2f, ay = y + 10.6f, bx = x + 8.6f, by = y + 14f, cx = x + 15f, cy = y + 6.4f;
            p.moveTo(ax, ay);
            if (tk <= split) {
                float q = tk / split;
                p.lineTo(ax + (bx - ax) * q, ay + (by - ay) * q);
            } else {
                float q = (tk - split) / (1f - split);
                p.lineTo(bx, by);
                p.lineTo(bx + (cx - bx) * q, by + (cy - by) * q);
            }
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(p);
        }
        g2.setFont(getFont());
        g2.setColor(UITheme.mix(UITheme.DANGER_DARK, UITheme.TEXT_DARK, tk));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(getText(), x + size + 10, (getHeight() + fm.getAscent() - fm.getDescent()) / 2f + introDy);
        g2.dispose();
    }
}

/** White rounded "card" with soft drop shadow, a title and optional subtitle. */
class CardPanel extends JPanel {
    static final int PAD = 8;

    CardPanel(String title) { this(title, null); }

    CardPanel(String title, String subtitle) {
        super(new BorderLayout(10, 10));
        setOpaque(false);
        setBorder(new EmptyBorder(PAD + 14, PAD + 20, PAD + 14, PAD + 20));
        if (title != null && !title.isEmpty()) {
            JPanel head = new JPanel();
            head.setOpaque(false);
            head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
            JLabel t = UITheme.label(title, UITheme.FONT_SUBTITLE, UITheme.TEXT_DARK);
            t.setAlignmentX(Component.LEFT_ALIGNMENT);
            head.add(t);
            if (subtitle != null) {
                JLabel s = UITheme.label(subtitle, UITheme.font(Font.PLAIN, 12f), UITheme.TEXT_MUTED);
                s.setAlignmentX(Component.LEFT_ALIGNMENT);
                head.add(Box.createVerticalStrut(2));
                head.add(s);
            }
            head.setBorder(new EmptyBorder(0, 0, 0, 0));
            add(head, BorderLayout.NORTH);
        }
    }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = UITheme.aa(g);
        float w = getWidth() - 2 * PAD, h = getHeight() - 2 * PAD;
        UITheme.paintShadow(g2, PAD, PAD, w, h, 18, 8, 0.30f);
        g2.setColor(Color.WHITE);
        g2.fill(new RoundRectangle2D.Float(PAD, PAD, w, h, 18, 18));
        g2.dispose();
    }
}

/** Soft gray rounded container (used behind checklists). */
class SoftPanel extends JPanel {
    SoftPanel(LayoutManager lm) {
        super(lm);
        setOpaque(false);
        setBorder(new EmptyBorder(10, 14, 8, 14));
    }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = UITheme.aa(g);
        g2.setColor(UITheme.SURFACE_ALT);
        g2.fill(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, 14, 14));
        g2.setColor(UITheme.BORDER);
        g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 2, getHeight() - 2, 14, 14));
        g2.dispose();
    }
}

/** Form container that scrolls when the window is short but stretches when there is room. */
class ScrollableForm extends JPanel implements Scrollable {
    ScrollableForm(LayoutManager lm) {
        super(lm);
        setOpaque(false);
    }
    @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 16; }
    @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return Math.max(40, r.height - 40); }
    @Override public boolean getScrollableTracksViewportWidth() { return true; }
    @Override public boolean getScrollableTracksViewportHeight() {
        return getParent() instanceof JViewport && ((JViewport) getParent()).getHeight() > getPreferredSize().height;
    }
}

/** Label-above-field form layout used by every dialog. */
class FormPanel extends JPanel {
    private final GridBagConstraints gbc = new GridBagConstraints();
    private int row = 0;

    FormPanel() {
        super(new GridBagLayout());
        setOpaque(false);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        gbc.gridx = 0;
    }

    JLabel row(String label, JComponent field) {
        JLabel l = UITheme.fieldLabel(label);
        gbc.gridy = row++;
        gbc.insets = new Insets(row == 1 ? 0 : 12, 0, 5, 0);
        add(l, gbc);
        gbc.gridy = row++;
        gbc.insets = new Insets(0, 0, 0, 0);
        add(field, gbc);
        return l;
    }

    @Override public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        return new Dimension(Math.max(d.width, 400), d.height);
    }
}

// ============================================================================
// 9. TABLES: hover rows, zebra stripes, status badges, strike dots, usage bars
// ============================================================================
class ModernTable extends JTable {
    int hoverRow = -1;

    ModernTable(DefaultTableModel model, int rowHeight) {
        super(model);
        setRowHeight(rowHeight);
        setShowGrid(false);
        setIntercellSpacing(new Dimension(0, 0));
        setFillsViewportHeight(true);
        setFont(UITheme.FONT_REGULAR);
        setForeground(UITheme.TEXT_DARK);
        setSelectionBackground(new Color(219, 234, 254));
        setSelectionForeground(UITheme.TEXT_DARK);
        setAutoCreateRowSorter(true); // click a header to sort
        setDefaultRenderer(Object.class, new BaseCellRenderer());

        JTableHeader header = getTableHeader();
        header.setReorderingAllowed(false);
        header.setDefaultRenderer(new HeaderRenderer());
        header.setPreferredSize(new Dimension(100, 40));
        header.setBackground(UITheme.SURFACE_ALT);

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                int r = rowAtPoint(e.getPoint());
                if (r != hoverRow) { hoverRow = r; repaint(); }
            }
        });
        addMouseListener(new MouseAdapter() {
            @Override public void mouseExited(MouseEvent e) { hoverRow = -1; repaint(); }
        });
    }

    static Color rowColor(JTable t, int row, boolean selected) {
        if (selected) return new Color(219, 234, 254);
        if (t instanceof ModernTable && ((ModernTable) t).hoverRow == row) return new Color(241, 245, 249);
        return row % 2 == 0 ? Color.WHITE : new Color(250, 251, 253);
    }

    void setColumnWidths(int... widths) {
        for (int i = 0; i < widths.length && i < getColumnModel().getColumnCount(); i++) {
            getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (getRowCount() == 0) {
            Graphics2D g2 = UITheme.aa(g);
            g2.setFont(UITheme.FONT_REGULAR);
            g2.setColor(UITheme.SLATE_300);
            String msg = "No records to display";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(msg, (getWidth() - fm.stringWidth(msg)) / 2, Math.max(60, getVisibleRect().height / 2));
            g2.dispose();
        }
    }
}

class HeaderRenderer extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean sel, boolean foc, int row, int column) {
        super.getTableCellRendererComponent(table, value, false, false, row, column);
        String t = value == null ? "" : value.toString().toUpperCase();
        RowSorter<?> rs = table.getRowSorter();
        if (rs != null && !rs.getSortKeys().isEmpty()) {
            RowSorter.SortKey k = rs.getSortKeys().get(0);
            if (k.getColumn() == table.convertColumnIndexToModel(column) && k.getSortOrder() != SortOrder.UNSORTED) {
                t += k.getSortOrder() == SortOrder.ASCENDING ? "  \u25B2" : "  \u25BC";
            }
        }
        setText(t);
        setFont(UITheme.font(Font.BOLD, 11f));
        setForeground(UITheme.TEXT_MUTED);
        setBackground(UITheme.SURFACE_ALT);
        setOpaque(true);
        setHorizontalAlignment(LEFT);
        setBorder(new CompoundBorder(new MatteBorder(0, 0, 1, 0, UITheme.BORDER), new EmptyBorder(0, 12, 0, 8)));
        return this;
    }
}

class BaseCellRenderer extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
        super.getTableCellRendererComponent(t, v, sel, false, r, c);
        setBackground(ModernTable.rowColor(t, r, sel));
        setForeground(UITheme.TEXT_DARK);
        setBorder(new EmptyBorder(0, 12, 0, 8));
        setToolTipText(v == null ? null : v.toString());
        return this;
    }
}

/** Overdue-aware date cell: turns red when the row's status column says OVERDUE. */
class DueRenderer extends BaseCellRenderer {
    private final int statusCol;
    DueRenderer(int statusCol) { this.statusCol = statusCol; }

    @Override
    public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
        super.getTableCellRendererComponent(t, v, sel, foc, r, c);
        Object st = t.getValueAt(r, statusCol);
        if (st != null && st.toString().contains("OVERDUE")) {
            setForeground(UITheme.DANGER_DARK);
            setFont(UITheme.FONT_BOLD);
        } else {
            setFont(UITheme.FONT_REGULAR);
        }
        return this;
    }
}

/** Colored pill used for Status / Category / Role columns. */
class BadgeRenderer extends JLabel implements TableCellRenderer {
    private Color color = UITheme.TEXT_MUTED, rowBg = Color.WHITE;
    private String text = "";

    BadgeRenderer() { setOpaque(false); }

    static Color badgeColor(String s) {
        String u = s == null ? "" : s.toUpperCase();
        if (u.contains("OVERDUE") || u.startsWith("SUSPENDED") || u.equals("RETURNED_DAMAGED")) return UITheme.DANGER;
        if (u.equals("UNDER_MAINTENANCE") || u.equals("RETURNED_LATE")) return UITheme.WARNING;
        if (u.equals("AVAILABLE") || u.equals("ELIGIBLE") || u.equals("RETURNED_GOOD")) return UITheme.SUCCESS;
        if (u.equals("BORROWED") || u.equals("ACTIVE") || u.equals("STUDENT")) return UITheme.PRIMARY;
        if (u.equals("LAPTOP")) return UITheme.INDIGO;
        if (u.equals("PROJECTOR")) return UITheme.TEAL;
        if (u.equals("TOOL")) return UITheme.ORANGE;
        if (u.equals("FACULTY")) return UITheme.PURPLE;
        return UITheme.TEXT_MUTED;
    }

    @Override
    public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
        String s = v == null ? "" : v.toString();
        text = s.replace('_', ' ');
        color = badgeColor(s);
        rowBg = ModernTable.rowColor(t, r, sel);
        setToolTipText(s);
        return this;
    }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = UITheme.aa(g);
        g2.setColor(rowBg);
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.setFont(UITheme.font(Font.BOLD, 11f));
        FontMetrics fm = g2.getFontMetrics();
        String shown = UITheme.ellipsize(text, fm, Math.max(20, getWidth() - 36));
        int pw = fm.stringWidth(shown) + 20, ph = 22, x = 10, y = (getHeight() - ph) / 2;
        g2.setColor(UITheme.alpha(color, 36));
        g2.fill(new RoundRectangle2D.Float(x, y, pw, ph, ph, ph));
        g2.setColor(UITheme.mix(color, Color.BLACK, 0.25f));
        g2.drawString(shown, x + 10, y + (ph + fm.getAscent() - fm.getDescent()) / 2f);
        g2.dispose();
    }
}

/** 3 dots = 3 strikes. Filled red dots show how close a borrower is to suspension. */
class StrikeRenderer extends JLabel implements TableCellRenderer {
    private int strikes;
    private Color rowBg = Color.WHITE;

    StrikeRenderer() { setOpaque(false); }

    @Override
    public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
        strikes = v instanceof Number ? ((Number) v).intValue() : 0;
        rowBg = ModernTable.rowColor(t, r, sel);
        setToolTipText(strikes + " / 3 strikes");
        return this;
    }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = UITheme.aa(g);
        g2.setColor(rowBg);
        g2.fillRect(0, 0, getWidth(), getHeight());
        int d = 11, gap = 6, x = 12, y = (getHeight() - d) / 2;
        for (int i = 0; i < 3; i++) {
            if (i < strikes) {
                g2.setColor(strikes >= 3 ? UITheme.DANGER : UITheme.WARNING);
                g2.fill(new Ellipse2D.Float(x, y, d, d));
            } else {
                g2.setColor(UITheme.FIELD_BORDER);
                g2.setStroke(new BasicStroke(1.5f));
                g2.draw(new Ellipse2D.Float(x + .75f, y + .75f, d - 1.5f, d - 1.5f));
            }
            x += d + gap;
        }
        g2.setFont(UITheme.font(Font.BOLD, 12f));
        g2.setColor(strikes >= 3 ? UITheme.DANGER_DARK : UITheme.TEXT_MUTED);
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(strikes + "/3", x + 4, (getHeight() + fm.getAscent() - fm.getDescent()) / 2f);
        g2.dispose();
    }
}

/** "1 / 2" -> mini progress bar + text. */
class UsageRenderer extends JLabel implements TableCellRenderer {
    private String text = "";
    private Color rowBg = Color.WHITE;

    UsageRenderer() { setOpaque(false); }

    @Override
    public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
        text = v == null ? "" : v.toString();
        rowBg = ModernTable.rowColor(t, r, sel);
        return this;
    }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = UITheme.aa(g);
        g2.setColor(rowBg);
        g2.fillRect(0, 0, getWidth(), getHeight());
        float frac = 0f;
        try {
            String[] p = text.split("/");
            float a = Float.parseFloat(p[0].trim()), b = Float.parseFloat(p[1].trim());
            frac = b <= 0 ? 0 : UITheme.clamp01(a / b);
        } catch (Exception ignored) {}
        int bw = 54, bh = 6, x = 12, y = (getHeight() - bh) / 2;
        g2.setColor(UITheme.BORDER);
        g2.fill(new RoundRectangle2D.Float(x, y, bw, bh, bh, bh));
        if (frac > 0) {
            g2.setColor(frac >= 1f ? UITheme.DANGER : UITheme.PRIMARY);
            g2.fill(new RoundRectangle2D.Float(x, y, bw * frac, bh, bh, bh));
        }
        g2.setFont(UITheme.FONT_REGULAR);
        g2.setColor(UITheme.TEXT_DARK);
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(text, x + bw + 10, (getHeight() + fm.getAscent() - fm.getDescent()) / 2f);
        g2.dispose();
    }
}

// ============================================================================
// 10. DASHBOARD WIDGETS: KPI cards, animated sidebar nav, toasts
// ============================================================================

/** Dashboard Summary Counter card: count-up animation, hover lift, pulsing alert when overdue. */
class KpiCard extends JPanel {
    private final String label, subtext;
    private final Color accent;
    private final Glyph glyph;
    private final AnimatedFloat shown = new AnimatedFloat(0f, this::repaint);
    private final AnimatedFloat hover = new AnimatedFloat(0f, this::repaint);
    private int target = 0;
    private float pulse = 0f;
    private Timer pulseTimer;

    KpiCard(String label, String subtext, Color accent, Glyph glyph) {
        this.label = label;
        this.subtext = subtext;
        this.accent = accent;
        this.glyph = glyph;
        setOpaque(false);
        setPreferredSize(new Dimension(220, 112));
        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hover.animateTo(1f, 180, Easing.OUT_CUBIC); }
            @Override public void mouseExited(MouseEvent e) { hover.animateTo(0f, 240, Easing.OUT_CUBIC); }
        });
    }

    void setValue(int v) {
        if (v == target) return;
        target = v;
        shown.animateTo(v, 750, Easing.OUT_CUBIC);
        updatePulse();
    }

    private void updatePulse() {
        boolean need = accent == UITheme.DANGER && target > 0 && isDisplayable();
        if (need && pulseTimer == null) {
            pulseTimer = new Timer(45, e -> { pulse = (pulse + 0.035f) % 1f; repaint(); });
            pulseTimer.start();
        } else if (!need && pulseTimer != null) {
            pulseTimer.stop();
            pulseTimer = null;
        }
    }

    @Override public void addNotify() {
        super.addNotify();
        shown.set(0f);
        shown.animateTo(target, 900, Easing.OUT_CUBIC); // replay count-up when first shown
        updatePulse();
    }

    @Override public void removeNotify() {
        if (pulseTimer != null) { pulseTimer.stop(); pulseTimer = null; }
        shown.stop();
        super.removeNotify();
    }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = UITheme.aa(g);
        int w = getWidth(), h = getHeight();
        float hv = hover.get();
        float m = 8, x = m, y = m - 3 * hv, cw = w - 2 * m, ch = h - 2 * m;
        UITheme.paintShadow(g2, x, y, cw, ch, 16, 8, 0.30f + 0.25f * hv);
        RoundRectangle2D card = new RoundRectangle2D.Float(x, y, cw, ch, 16, 16);
        g2.setColor(Color.WHITE);
        g2.fill(card);

        Shape old = g2.getClip();
        g2.clip(card);
        g2.setColor(accent);
        g2.fillRect((int) x, (int) y, 5, (int) ch);
        g2.setPaint(new RadialGradientPaint(new Point2D.Float(x + cw - 24, y + 22), 90f,
                new float[]{0f, 1f}, new Color[]{UITheme.alpha(accent, 40), UITheme.alpha(accent, 0)}));
        g2.fillRect((int) x, (int) y, (int) cw, (int) ch);
        g2.setClip(old);

        // icon bubble
        double icx = x + cw - 36, icy = y + 34;
        if (accent == UITheme.DANGER && target > 0) {
            g2.setColor(UITheme.alpha(accent, (int) (90 * (1f - pulse))));
            double r = 18 + 12 * pulse;
            g2.fill(new Ellipse2D.Double(icx - r, icy - r, 2 * r, 2 * r));
        }
        g2.setColor(UITheme.alpha(accent, 34));
        g2.fill(new Ellipse2D.Double(icx - 20, icy - 20, 40, 40));
        glyph.paint(g2, icx, icy, 20, accent);

        // texts
        g2.setFont(UITheme.font(Font.BOLD, 11f));
        g2.setColor(UITheme.TEXT_MUTED);
        g2.drawString(UITheme.ellipsize(label, g2.getFontMetrics(), (int) cw - 22 - 64), x + 22, y + 28);
        g2.setFont(UITheme.font(Font.BOLD, 36f));
        g2.setColor(accent);
        g2.drawString(String.valueOf(Math.round(shown.get())), x + 22, y + 68);
        g2.setFont(UITheme.font(Font.PLAIN, 11f));
        g2.setColor(UITheme.TEXT_MUTED);
        g2.drawString(subtext, x + 22, y + ch - 14);
        g2.dispose();
    }
}

/** One entry in the sidebar. */
class NavItem extends JComponent {
    private final String text;
    private final Glyph glyph;
    private final AnimatedFloat hover = new AnimatedFloat(0f, this::repaint);
    private final AnimatedFloat active = new AnimatedFloat(0f, this::repaint);
    private int badge = 0;
    private Color badgeColor = UITheme.DANGER;

    NavItem(String text, Glyph glyph, Runnable onClick) {
        this.text = text;
        this.glyph = glyph;
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hover.animateTo(1f, 150, Easing.OUT_CUBIC); }
            @Override public void mouseExited(MouseEvent e) { hover.animateTo(0f, 200, Easing.OUT_CUBIC); }
            @Override public void mouseClicked(MouseEvent e) { onClick.run(); }
        });
    }

    void setActive(boolean on) { active.animateTo(on ? 1f : 0f, 300, Easing.OUT_CUBIC); }
    void setBadge(int n, Color c) { badge = n; badgeColor = c; repaint(); }

    @Override public Dimension getPreferredSize() { return new Dimension(200, 48); }
    @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, 48); }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = UITheme.aa(g);
        int w = getWidth(), h = getHeight();
        float hv = hover.get(), act = active.get();
        if (hv > 0.01f && act < 1f) {
            g2.setColor(new Color(255, 255, 255, (int) (20 * hv * (1 - act))));
            g2.fill(new RoundRectangle2D.Float(0, 0, w, h, 14, 14));
        }
        Color fg = UITheme.mix(UITheme.SLATE_300, Color.WHITE, Math.max(act, hv * 0.6f));
        glyph.paint(g2, 26 + 3 * hv, h / 2.0, 18, fg);
        g2.setFont(UITheme.FONT_BOLD);
        g2.setColor(fg);
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(text, 50 + 3 * hv, (h + fm.getAscent() - fm.getDescent()) / 2f);
        if (badge > 0) {
            g2.setFont(UITheme.font(Font.BOLD, 11f));
            FontMetrics bf = g2.getFontMetrics();
            String s = String.valueOf(badge);
            int bw = Math.max(22, bf.stringWidth(s) + 14), bh = 20;
            int bx = w - bw - 12, by = (h - bh) / 2;
            g2.setColor(badgeColor);
            g2.fill(new RoundRectangle2D.Float(bx, by, bw, bh, bh, bh));
            g2.setColor(Color.WHITE);
            g2.drawString(s, bx + (bw - bf.stringWidth(s)) / 2f, by + (bh + bf.getAscent() - bf.getDescent()) / 2f);
        }
        g2.dispose();
    }
}

/** Sidebar menu with a highlight pill that slides between items. */
class NavMenu extends JPanel {
    private final List<NavItem> items = new ArrayList<>();
    private final AnimatedFloat highlightY = new AnimatedFloat(0f, this::repaint);
    private final Consumer<Integer> onSelect;
    private int selected = 0;
    private boolean positioned = false;

    NavMenu(String[] labels, Glyph[] glyphs, Consumer<Integer> onSelect) {
        this.onSelect = onSelect;
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            NavItem item = new NavItem(labels[i], glyphs[i], () -> select(idx, true));
            item.setAlignmentX(Component.LEFT_ALIGNMENT);
            items.add(item);
            add(item);
            if (i < labels.length - 1) add(Box.createVerticalStrut(8));
        }
        items.get(0).setActive(true);
    }

    void select(int idx, boolean fire) {
        selected = idx;
        for (int i = 0; i < items.size(); i++) items.get(i).setActive(i == idx);
        highlightY.animateTo(items.get(idx).getY(), 340, Easing.OUT_CUBIC);
        if (fire && onSelect != null) onSelect.accept(idx);
    }

    void setBadge(int index, int count, Color color) { items.get(index).setBadge(count, color); }

    @Override public void doLayout() {
        super.doLayout();
        if (!positioned && items.get(selected).getHeight() > 0) {
            positioned = true;
            highlightY.set(items.get(selected).getY());
        }
    }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = UITheme.aa(g);
        float y = highlightY.get();
        int w = getWidth(), h = 48;
        g2.setColor(UITheme.alpha(UITheme.PRIMARY, 55));
        g2.fill(new RoundRectangle2D.Float(-2, y + 3, w + 4, h + 2, 18, 18));
        g2.setPaint(new GradientPaint(0, y, UITheme.PRIMARY, w, y + h, UITheme.INDIGO));
        g2.fill(new RoundRectangle2D.Float(0, y, w, h, 14, 14));
        g2.dispose();
    }
}

/** Slide-up toast notifications anchored to the bottom-right of the window. */
final class Toast extends JComponent {
    static final int M = 12;
    private final NoticeKind kind;
    private final String text;
    private final int cardW, cardH = 52;
    private float alpha = 0f, dy = 20f;

    Toast(NoticeKind kind, String message) {
        this.kind = kind;
        FontMetrics fm = getFontMetrics(UITheme.FONT_BOLD);
        this.text = UITheme.ellipsize(message, fm, 440);
        this.cardW = 16 + 28 + 12 + fm.stringWidth(this.text) + 22;
        setOpaque(false);
    }

    @Override public Dimension getPreferredSize() { return new Dimension(cardW + 2 * M, cardH + 2 * M + 14); }

    void playIn() {
        Animator.run(0, 320, Easing.OUT_CUBIC, t -> { alpha = t; dy = (1f - t) * 20f; repaint(); }, null);
    }

    void playOut(Runnable done) {
        Animator.run(0, 260, Easing.IN_OUT_CUBIC, t -> { alpha = 1f - t; dy = -t * 8f; repaint(); }, done);
    }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = UITheme.aa(g);
        g2.setComposite(AlphaComposite.SrcOver.derive(UITheme.clamp01(alpha)));
        float x = M, y = M + dy;
        UITheme.paintShadow(g2, x, y, cardW, cardH, 14, 10, 0.55f);
        RoundRectangle2D shape = new RoundRectangle2D.Float(x, y, cardW, cardH, 14, 14);
        g2.setColor(UITheme.SIDEBAR_BG);
        g2.fill(shape);
        Shape old = g2.getClip();
        g2.clip(shape);
        g2.setColor(kind.color);
        g2.fillRect((int) x, (int) y, 4, cardH);
        g2.setClip(old);
        g2.setColor(UITheme.alpha(kind.color, 55));
        g2.fill(new Ellipse2D.Float(x + 16, y + (cardH - 28) / 2f, 28, 28));
        kind.paintIcon(g2, x + 30, y + cardH / 2.0, 14, kind.color);
        g2.setFont(UITheme.FONT_BOLD);
        g2.setColor(Color.WHITE);
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(text, x + 56, y + cardH / 2f + (fm.getAscent() - fm.getDescent()) / 2f);
        g2.dispose();
    }
}

final class ToastManager {
    private final JLayeredPane layer;
    private final List<Toast> active = new ArrayList<>();

    ToastManager(JLayeredPane layer) {
        this.layer = layer;
        layer.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) { relayout(); }
        });
    }

    void show(NoticeKind kind, String message) {
        Toast t = new Toast(kind, message);
        active.add(t);
        layer.add(t, JLayeredPane.POPUP_LAYER);
        relayout();
        t.playIn();
        Timer hide = new Timer(3200, e -> t.playOut(() -> {
            active.remove(t);
            layer.remove(t);
            layer.repaint();
            relayout();
        }));
        hide.setRepeats(false);
        hide.start();
    }

    private void relayout() {
        int y = layer.getHeight() - 10;
        for (int i = active.size() - 1; i >= 0; i--) {
            Toast t = active.get(i);
            Dimension d = t.getPreferredSize();
            y -= d.height;
            t.setBounds(layer.getWidth() - d.width - 10, y, d.width, d.height);
            y += 8;
        }
    }
}

// ============================================================================
// 11. ANIMATED DIALOGS (replace JOptionPane) + DIGITAL SLIP "PRINTER" VIEWER
// ============================================================================
class ModernDialog extends JDialog {
    private final NoticeKind kind;
    private final String heading;
    private final JComponent body;
    private final String okText, cancelText;
    private final List<JButton> extras = new ArrayList<>();
    private boolean accepted = false, closing = false;
    private boolean perPixel = false, fade = false;
    private Point dragOffset;

    ModernDialog(Window owner, String heading, NoticeKind kind, JComponent body, String okText, String cancelText) {
        super(owner, ModalityType.APPLICATION_MODAL);
        this.heading = heading;
        this.kind = kind;
        this.body = body;
        this.okText = okText;
        this.cancelText = cancelText;
        setUndecorated(true);
        try {
            GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
            perPixel = gd.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT);
            fade = gd.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT);
            if (perPixel) setBackground(new Color(0, 0, 0, 0));
        } catch (Throwable t) {
            perPixel = false;
            fade = false;
        }
    }

    void addAction(JButton b) { extras.add(b); }

    boolean showDialog() {
        build();
        pack();
        setLocationRelativeTo(getOwner());
        if (fade) {
            try {
                setOpacity(0f);
                final int y0 = getY();
                setLocation(getX(), y0 + 16);
                Animator.run(0, 220, Easing.OUT_CUBIC, t -> {
                    try { setOpacity(Math.min(1f, Math.max(0f, t))); } catch (Exception ignored) {}
                    setLocation(getX(), y0 + Math.round((1f - t) * 16));
                }, null);
            } catch (Exception ignored) {}
        }
        setVisible(true);
        return accepted;
    }

    private void finish(boolean ok) {
        if (closing) return;
        closing = true;
        accepted = ok;
        if (fade) {
            Animator.run(0, 140, Easing.OUT_CUBIC, t -> {
                try { setOpacity(Math.max(0f, 1f - t)); } catch (Exception ignored) {}
            }, this::dispose);
        } else {
            dispose();
        }
    }

    private void build() {
        final int m = perPixel ? 14 : 0;
        JPanel root = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = UITheme.aa(g);
                float w = getWidth() - 2 * m, h = getHeight() - 2 * m;
                float arc = perPixel ? 22 : 0;
                if (perPixel) UITheme.paintShadow(g2, m, m, w, h, arc, 14, 0.6f);
                RoundRectangle2D shape = new RoundRectangle2D.Float(m, m, w, h, arc, arc);
                g2.setColor(Color.WHITE);
                g2.fill(shape);
                Shape old = g2.getClip();
                g2.clip(shape);
                g2.setColor(kind.color);
                g2.fillRect(m, m, (int) w, 5);
                g2.setClip(old);
                if (!perPixel) {
                    g2.setColor(UITheme.FIELD_BORDER);
                    g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
                }
                g2.dispose();
            }
        };
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(m + 24, m + 26, m + 20, m + 26));

        // header: icon bubble + title
        JPanel head = new JPanel(new BorderLayout(14, 0));
        head.setOpaque(false);
        JComponent bubble = new JComponent() {
            @Override public Dimension getPreferredSize() { return new Dimension(42, 42); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = UITheme.aa(g);
                g2.setColor(UITheme.alpha(kind.color, 36));
                g2.fill(new Ellipse2D.Float(0, 0, 42, 42));
                kind.paintIcon(g2, 21, 21, 20, kind.color);
                g2.dispose();
            }
        };
        head.add(bubble, BorderLayout.WEST);
        head.add(UITheme.label(heading, UITheme.font(Font.BOLD, 18f), UITheme.TEXT_DARK), BorderLayout.CENTER);
        root.add(head, BorderLayout.NORTH);

        JPanel bodyWrap = new JPanel(new BorderLayout());
        bodyWrap.setOpaque(false);
        bodyWrap.setBorder(new EmptyBorder(16, 0, 20, 0));
        bodyWrap.add(body, BorderLayout.CENTER);
        root.add(bodyWrap, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttons.setOpaque(false);
        for (JButton b : extras) buttons.add(b);
        if (cancelText != null) {
            JButton cancel = UITheme.createButton(cancelText, new Color(226, 232, 240), UITheme.TEXT_DARK);
            cancel.addActionListener(e -> finish(false));
            buttons.add(cancel);
        }
        Color okColor = kind == NoticeKind.INFO ? UITheme.PRIMARY : kind.color;
        JButton ok = UITheme.createButton(okText, okColor, Color.WHITE);
        ok.addActionListener(e -> finish(true));
        buttons.add(ok);
        root.add(buttons, BorderLayout.SOUTH);

        setContentPane(root);
        getRootPane().setDefaultButton(ok);
        getRootPane().registerKeyboardAction(e -> finish(false),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        MouseAdapter drag = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { dragOffset = e.getPoint(); }
            @Override public void mouseDragged(MouseEvent e) {
                if (dragOffset == null) return;
                Point p = e.getLocationOnScreen();
                setLocation(p.x - dragOffset.x - ((Component) e.getSource()).getX(), p.y - dragOffset.y - ((Component) e.getSource()).getY());
            }
        };
        root.addMouseListener(drag);
        root.addMouseMotionListener(drag);
    }
}

/** Facade used by the dashboard: message / form / input / receipt dialogs. */
final class Dialogs {
    private Dialogs() {}

    private static Window owner(Component c) {
        if (c == null) return null;
        return c instanceof Window ? (Window) c : SwingUtilities.getWindowAncestor(c);
    }

    static JTextArea textArea(String text, int width) {
        JTextArea a = new JTextArea(text);
        a.setEditable(false);
        a.setFocusable(false);
        a.setOpaque(false);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setBorder(null);
        a.setFont(UITheme.FONT_REGULAR);
        a.setForeground(new Color(51, 65, 85));
        a.setSize(new Dimension(width, Short.MAX_VALUE));
        a.setPreferredSize(new Dimension(width, a.getPreferredSize().height));
        return a;
    }

    static void message(Component parent, NoticeKind kind, String title, String text) {
        new ModernDialog(owner(parent), title, kind, textArea(text, 420), "OK", null).showDialog();
    }

    static boolean form(Component parent, String title, NoticeKind kind, JComponent body, String okText) {
        return new ModernDialog(owner(parent), title, kind, body, okText, "Cancel").showDialog();
    }

    /** Returns the entered text, or null when cancelled. */
    static String input(Component parent, String title, String prompt, String initial) {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setOpaque(false);
        p.add(textArea(prompt, 420), BorderLayout.NORTH);
        RoundedTextField field = new RoundedTextField(initial);
        p.add(field, BorderLayout.CENTER);
        field.addAncestorListener(new AncestorListener() {
            @Override public void ancestorAdded(AncestorEvent e) { field.requestFocusInWindow(); field.selectAll(); }
            @Override public void ancestorRemoved(AncestorEvent e) {}
            @Override public void ancestorMoved(AncestorEvent e) {}
        });
        boolean ok = new ModernDialog(owner(parent), title, NoticeKind.INFO, p, "Confirm", "Cancel").showDialog();
        return ok ? field.getText() : null;
    }

    /** Digital Borrower's Slip viewer: paper "prints" out of the top. */
    static void slip(Component parent, String title, String content) {
        ReceiptView view = new ReceiptView(content);
        ModernDialog d = new ModernDialog(owner(parent), title, NoticeKind.INFO, view, "Close", null);

        JButton copy = UITheme.createButton("Copy", new Color(226, 232, 240), UITheme.TEXT_DARK);
        copy.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(content), null);
            copy.setText("Copied \u2713");
            Timer t = new Timer(1400, ev -> copy.setText("Copy"));
            t.setRepeats(false);
            t.start();
        });
        JButton print = UITheme.createButton("Print", new Color(226, 232, 240), UITheme.TEXT_DARK);
        print.addActionListener(e -> {
            try {
                view.getTextArea().print();
            } catch (PrinterException ex) {
                message(d, NoticeKind.ERROR, "Print Failed", ex.getMessage() == null ? "Unable to print the slip." : ex.getMessage());
            }
        });
        d.addAction(copy);
        d.addAction(print);
        d.showDialog();
    }
}

class ReceiptView extends JPanel {
    private final JTextArea area;
    private float progress = 0f;

    ReceiptView(String text) {
        super(new BorderLayout());
        setOpaque(false);
        area = new JTextArea(text);
        area.setEditable(false);
        area.setFont(UITheme.FONT_MONO);
        area.setForeground(new Color(30, 41, 59));
        area.setBackground(new Color(253, 252, 248));
        area.setBorder(new EmptyBorder(14, 16, 14, 16));
        area.setLineWrap(true);
        area.setCaretPosition(0);
        JScrollPane sp = UITheme.scroll(area);
        sp.getViewport().setBackground(new Color(253, 252, 248));
        sp.setBorder(new LineBorder(UITheme.BORDER));
        sp.setPreferredSize(new Dimension(520, 400));
        add(sp, BorderLayout.CENTER);
    }

    JTextArea getTextArea() { return area; }

    @Override public void addNotify() {
        super.addNotify();
        Animator.run(160, 760, Easing.IN_OUT_CUBIC, t -> { progress = t; repaint(); }, () -> { progress = 1f; repaint(); });
    }

    @Override public void paint(Graphics g) {
        if (progress >= 1f) { super.paint(g); return; }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.clipRect(0, 0, getWidth(), (int) (getHeight() * progress));
        g2.translate(0, (progress - 1f) * 24);
        super.paint(g2);
        g2.dispose();
    }
}

// ============================================================================
// 12. BORROWER POLICY BANNER + OVERDUE TIME TRACKER BAR
// ============================================================================
class PolicyBanner extends JPanel {
    private final JLabel head = new JLabel();
    private final JLabel vLimit = new JLabel(), vHours = new JLabel(), vCollateral = new JLabel(), vStrikes = new JLabel();
    private final AnimatedFloat danger = new AnimatedFloat(0f, this::repaint);

    PolicyBanner() {
        setOpaque(false);
        setLayout(new GridBagLayout());
        setBorder(new EmptyBorder(8, 18, 8, 14));
        head.setFont(UITheme.FONT_BOLD);
        vStrikes.setFont(UITheme.font(Font.BOLD, 12f));
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0; c.gridy = 0; c.gridwidth = 3; c.weightx = 1; c.insets = new Insets(0, 0, 5, 0);
        add(head, c);
        c.gridx = 3; c.gridwidth = 1; c.weightx = 0; c.anchor = GridBagConstraints.EAST;
        add(vStrikes, c);
        addPair("Borrow limit", vLimit, 0, 1, 1);
        addPair("Max duration", vHours, 2, 1, 1);
        addPair("Collateral", vCollateral, 0, 2, 3);
        showBorrower(null);
    }

    private void addPair(String name, JLabel value, int gx, int gy, int valueSpan) {
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.gridy = gy; c.gridx = gx; c.insets = new Insets(1, 0, 1, 10);
        add(UITheme.label(name, UITheme.font(Font.PLAIN, 12f), UITheme.TEXT_MUTED), c);
        c.gridx = gx + 1; c.gridwidth = valueSpan; c.weightx = valueSpan > 1 ? 1 : 0; c.insets = new Insets(1, 0, 1, 14);
        value.setFont(UITheme.font(Font.BOLD, 12f));
        value.setForeground(UITheme.TEXT_DARK);
        add(value, c);
    }

    void showBorrower(Borrower b) {
        if (b == null) {
            head.setText("No borrower selected.");
            head.setForeground(UITheme.TEXT_MUTED);
            for (JLabel l : new JLabel[]{vLimit, vHours, vCollateral, vStrikes}) l.setText(" ");
            danger.animateTo(0f, 250, Easing.OUT_CUBIC);
            return;
        }
        boolean sus = b.isSuspended();
        head.setText(b.getRole() + " Policy" + (sus ? "  \u2014  SUSPENDED, CANNOT BORROW" : ""));
        vLimit.setText("Max " + b.getMaxBorrowLimit() + " items (active " + b.getActiveBorrowedCount() + ")");
        vHours.setText("Max " + b.getMaxAllowedHours() + " hrs");
        vCollateral.setText(b.getRequiredCollateral());
        vStrikes.setText("Strikes " + b.getStrikeCount() + " / 3");
        vStrikes.setForeground(sus ? UITheme.DANGER_DARK : UITheme.TEXT_MUTED);
        head.setForeground(sus ? UITheme.DANGER_DARK : UITheme.PRIMARY_DARK);
        danger.animateTo(sus ? 1f : 0f, 350, Easing.OUT_CUBIC);
    }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = UITheme.aa(g);
        Color tone = UITheme.mix(UITheme.PRIMARY, UITheme.DANGER, danger.get());
        RoundRectangle2D shape = new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
        g2.setColor(UITheme.alpha(tone, 20));
        g2.fill(shape);
        Shape old = g2.getClip();
        g2.clip(shape);
        g2.setColor(tone);
        g2.fillRect(0, 0, 5, getHeight());
        g2.setClip(old);
        g2.setColor(UITheme.alpha(tone, 70));
        g2.draw(shape);
        g2.dispose();
    }
}

/** Overdue Time Tracker: live countdown + progress bar for the selected active transaction. */
class TimeTrackerBar extends JPanel {
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    private BorrowTransaction tx;
    private final AnimatedFloat progress = new AnimatedFloat(0f, this::repaint);
    private String text = "No active transaction selected";
    private Color tone = UITheme.TEXT_MUTED;

    TimeTrackerBar() {
        setOpaque(false);
        setPreferredSize(new Dimension(100, 56));
        setMinimumSize(new Dimension(100, 56));
    }

    void setTransaction(BorrowTransaction t) { this.tx = t; update(true); }
    void tick() { update(false); }

    private void update(boolean animate) {
        if (tx == null) {
            text = "No active transaction selected";
            tone = UITheme.TEXT_MUTED;
            progress.animateTo(0f, 250, Easing.OUT_CUBIC);
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        long total = Math.max(1, Duration.between(tx.getBorrowTime(), tx.getDueTime()).getSeconds());
        long elapsed = Duration.between(tx.getBorrowTime(), now).getSeconds();
        float p = UITheme.clamp01(elapsed / (float) total);
        if (tx.isOverdue()) {
            text = "OVERDUE by " + tx.getMinutesOverdue() + " min \u2014 returning now adds +1 Strike";
            tone = UITheme.DANGER;
            p = 1f;
        } else {
            long left = Math.max(0, Duration.between(now, tx.getDueTime()).getSeconds());
            text = String.format("Due in %dh %02dm %02ds", left / 3600, (left % 3600) / 60, left % 60);
            tone = p > 0.75f ? UITheme.WARNING : UITheme.SUCCESS;
        }
        if (animate) progress.animateTo(p, 650, Easing.OUT_CUBIC); else progress.set(p);
    }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = UITheme.aa(g);
        int w = getWidth();
        g2.setFont(UITheme.font(Font.BOLD, 12f));
        g2.setColor(UITheme.mix(tone, Color.BLACK, 0.2f));
        g2.drawString(text, 2, 16);
        if (tx != null) {
            g2.setFont(UITheme.font(Font.PLAIN, 11f));
            g2.setColor(UITheme.TEXT_MUTED);
            String range = tx.getBorrowTime().format(HM) + "  \u2192  " + tx.getDueTime().format(HM);
            g2.drawString(range, w - g2.getFontMetrics().stringWidth(range) - 2, 16);
        }
        int bh = 10, by = 28;
        g2.setColor(UITheme.BORDER);
        g2.fill(new RoundRectangle2D.Float(0, by, w, bh, bh, bh));
        float fw = Math.max(bh, w * progress.get());
        if (tx != null) {
            g2.setPaint(new GradientPaint(0, 0, UITheme.alpha(tone, 150), w, 0, tone));
            g2.fill(new RoundRectangle2D.Float(0, by, fw, bh, bh, bh));
        }
        g2.dispose();
    }
}
