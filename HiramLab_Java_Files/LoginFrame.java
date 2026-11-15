import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;

// ============================================================================
// 13. FEATURE #12: STAFF LOGIN WINDOW (LoginFrame) - animated brand panel + shake on error
// ============================================================================
class BrandPanel extends JPanel {
    private static final double[][] ORBS = {{0, 0.35, 260}, {2, 0.28, 230}, {4, 0.42, 200}, {1, 0.22, 250}};
    private static final Color[] ORB_COLORS = {UITheme.PRIMARY, UITheme.PURPLE, UITheme.SUCCESS, UITheme.INDIGO};
    private static final String[] FEATURES = {
        "Polymorphic Accessory Checklists", "Automatic Maintenance Quarantine",
        "Role-Based Borrowing Limits", "Overdue & 3-Strike Accountability"};
    private static final Glyph[] FEATURE_ICONS = {Glyph.BOX, Glyph.WRENCH, Glyph.USERS, Glyph.CLOCK};

    private double time = 0;
    private long born = System.nanoTime();
    private Timer timer;

    BrandPanel() { setOpaque(true); }

    @Override public void addNotify() {
        super.addNotify();
        born = System.nanoTime();
        timer = new Timer(33, e -> { time += 0.033; repaint(); });
        timer.start();
    }

    @Override public void removeNotify() {
        if (timer != null) timer.stop();
        super.removeNotify();
    }

    private float reveal(double elapsed, double delay, double dur) {
        float t = UITheme.clamp01((float) ((elapsed - delay) / dur));
        return Easing.OUT_CUBIC.apply(t);
    }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = UITheme.aa(g);
        int w = getWidth(), h = getHeight();
        g2.setPaint(new GradientPaint(0, 0, new Color(15, 23, 42), w, h, new Color(30, 41, 89)));
        g2.fillRect(0, 0, w, h);

        for (int i = 0; i < ORBS.length; i++) {
            double cx = w * (0.5 + 0.38 * Math.sin(time * ORBS[i][1] + ORBS[i][0]));
            double cy = h * (0.5 + 0.40 * Math.cos(time * ORBS[i][1] * 0.8 + ORBS[i][0] * 1.7));
            float r = (float) ORBS[i][2];
            g2.setPaint(new RadialGradientPaint(new Point2D.Double(cx, cy), r, new float[]{0f, 1f},
                    new Color[]{UITheme.alpha(ORB_COLORS[i], 80), UITheme.alpha(ORB_COLORS[i], 0)}));
            g2.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
        }
        g2.setColor(new Color(255, 255, 255, 14));
        for (int x = 18; x < w; x += 30) for (int y = 18; y < h; y += 30) g2.fillRect(x, y, 2, 2);

        double e = (System.nanoTime() - born) / 1e9;
        int px = 48;
        float r;

        r = reveal(e, 0.15, 0.6);
        Graphics2D a = (Graphics2D) g2.create();
        a.setComposite(AlphaComposite.SrcOver.derive(r));
        a.translate(0, (1 - r) * 16);
        a.setColor(UITheme.alpha(Color.WHITE, 28));
        a.fill(new RoundRectangle2D.Float(px, 62, 52, 52, 16, 16));
        Glyph.BOX.paint(a, px + 26, 88, 26, Color.WHITE);
        a.setFont(UITheme.font(Font.BOLD, 11f));
        a.setColor(UITheme.SUCCESS);
        a.drawString("DCIT 50  \u2022  JAVA OOP SYSTEM", px + 66, 92);
        a.dispose();

        r = reveal(e, 0.35, 0.7);
        a = (Graphics2D) g2.create();
        a.setComposite(AlphaComposite.SrcOver.derive(r));
        a.translate(0, (1 - r) * 20);
        a.setFont(UITheme.font(Font.BOLD, 46f));
        a.setColor(Color.WHITE);
        a.drawString("HiramLab", px, 182);
        a.setFont(UITheme.font(Font.PLAIN, 14f));
        a.setColor(new Color(148, 163, 184));
        a.drawString("An Automated Campus Equipment Borrowing", px, 214);
        a.drawString("and Maintenance Sorting System", px, 234);
        a.dispose();

        for (int i = 0; i < FEATURES.length; i++) {
            r = reveal(e, 0.7 + i * 0.14, 0.6);
            a = (Graphics2D) g2.create();
            a.setComposite(AlphaComposite.SrcOver.derive(r));
            a.translate((1 - r) * -24, 0);
            int y = 296 + i * 56;
            a.setColor(new Color(255, 255, 255, 18));
            a.fill(new RoundRectangle2D.Float(px, y, w - 2 * px, 44, 14, 14));
            a.setColor(UITheme.alpha(UITheme.SUCCESS, 50));
            a.fill(new Ellipse2D.Float(px + 8, y + 7, 30, 30));
            Glyph.valueOf(FEATURE_ICONS[i].name()).paint(a, px + 23, y + 22, 15, UITheme.SUCCESS);
            a.setFont(UITheme.font(Font.PLAIN, 13f));
            a.setColor(new Color(226, 232, 240));
            FontMetrics fm = a.getFontMetrics();
            a.drawString(FEATURES[i], px + 50, y + (44 + fm.getAscent() - fm.getDescent()) / 2f);
            a.dispose();
        }
        g2.dispose();
    }
}

public class LoginFrame extends JFrame {
    private final HiramLabManager manager;
    private RoundedTextField txtUser;
    private RoundedPasswordField txtPass;
    private JLabel lblError;
    private RoundedButton btnLogin;
    private boolean shaking = false;

    public LoginFrame(HiramLabManager manager) {
        this.manager = manager;
        setTitle("HiramLab \u2014 Laboratory Counter Authentication");
        setSize(920, 600);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        setBackground(UITheme.BG_LIGHT);

        FadePanel root = new FadePanel(new GridLayout(1, 2)).autoPlay(0);
        root.setOpaque(false);

        // ---------- right login form ----------
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBackground(Color.WHITE);
        formPanel.setBorder(new EmptyBorder(30, 52, 30, 52));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.gridx = 0;

        txtUser = new RoundedTextField("admin").setHint("Enter username");
        txtPass = new RoundedPasswordField("hiram123");

        JLabel formTitle = UITheme.label("Staff Counter Sign In", UITheme.font(Font.BOLD, 26f), UITheme.TEXT_DARK);
        JLabel formSub = UITheme.label("Enter laboratory custodian credentials to continue", UITheme.FONT_REGULAR, UITheme.TEXT_MUTED);

        JLabel showToggle = UITheme.label("Show", UITheme.font(Font.BOLD, 12f), UITheme.PRIMARY);
        showToggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        showToggle.addMouseListener(new MouseAdapter() {
            private boolean shown = false;
            @Override public void mouseClicked(MouseEvent e) {
                shown = !shown;
                txtPass.setRevealed(shown);
                showToggle.setText(shown ? "Hide" : "Show");
            }
        });
        JPanel passLabelRow = new JPanel(new BorderLayout());
        passLabelRow.setOpaque(false);
        passLabelRow.add(UITheme.fieldLabel("Password"), BorderLayout.WEST);
        passLabelRow.add(showToggle, BorderLayout.EAST);

        btnLogin = (RoundedButton) UITheme.createButton("Sign In to Dashboard", UITheme.PRIMARY, Color.WHITE);
        JButton btnDemo = UITheme.createButton("Auto-Fill Custodian Account", new Color(226, 232, 240), UITheme.TEXT_DARK);

        lblError = UITheme.label(" ", UITheme.font(Font.BOLD, 12f), UITheme.DANGER_DARK);
        JLabel hint = new JLabel("<html><center><font color='#64748B'>Accounts: <b>admin / hiram123</b> or <b>custodian / lab2026</b></font></center></html>");
        hint.setFont(UITheme.font(Font.PLAIN, 12f));
        hint.setHorizontalAlignment(SwingConstants.CENTER);

        JComponent[] rows = {formTitle, formSub, UITheme.fieldLabel("Username"), txtUser, passLabelRow, txtPass, lblError, btnLogin, btnDemo, hint};
        Insets[] gaps = {
            new Insets(0, 0, 0, 0), new Insets(4, 0, 26, 0), new Insets(0, 0, 6, 0), new Insets(0, 0, 16, 0),
            new Insets(0, 0, 6, 0), new Insets(0, 0, 8, 0), new Insets(0, 0, 10, 0), new Insets(0, 0, 10, 0),
            new Insets(0, 0, 18, 0), new Insets(0, 0, 0, 0)};
        for (int i = 0; i < rows.length; i++) {
            FadePanel wrap = new FadePanel(new BorderLayout()).autoPlay(250 + i * 70);
            wrap.add(rows[i], BorderLayout.CENTER);
            gbc.gridy = i;
            gbc.insets = gaps[i];
            formPanel.add(wrap, gbc);
        }

        btnLogin.addActionListener(e -> performLogin());
        txtPass.addActionListener(e -> performLogin());
        txtUser.addActionListener(e -> txtPass.requestFocusInWindow());
        btnDemo.addActionListener(e -> {
            txtUser.setText("custodian");
            txtPass.setText("lab2026");
            lblError.setText(" ");
        });

        root.add(new BrandPanel());
        root.add(formPanel);
        setContentPane(root);
        getRootPane().setDefaultButton(btnLogin);
    }

    private void performLogin() {
        String user = txtUser.getText().trim();
        String pass = new String(txtPass.getPassword());
        String roleTitle = manager.authenticateStaff(user, pass);
        if (roleTitle != null) {
            lblError.setText(" ");
            btnLogin.setText("Signing in\u2026");
            btnLogin.setEnabled(false);
            Timer t = new Timer(550, e -> {
                dispose();
                new MainDashboardFrame(manager, roleTitle).setVisible(true);
            });
            t.setRepeats(false);
            t.start();
        } else {
            lblError.setText("Invalid username or password.  Try: admin / hiram123");
            shake();
        }
    }

    private void shake() {
        if (shaking) return;
        shaking = true;
        final Point origin = getLocation();
        Animator.run(0, 450, Easing.LINEAR,
                t -> setLocation(origin.x + (int) Math.round(Math.sin(t * Math.PI * 7) * 11 * (1 - t)), origin.y),
                () -> { setLocation(origin); shaking = false; });
    }
}
