import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import javax.swing.table.*;

// ============================================================================
// 14. MAIN DASHBOARD FRAME (Animated Sidebar + Live KPI Cards + 4 Functional Views)
// ============================================================================
public class MainDashboardFrame extends JFrame {
    private static final String[] CARDS = {"EQUIP", "COUNTER", "BORROWERS", "MAINTENANCE"};
    private static final String[] TITLES = {"Equipment Inventory", "Release & Return Counter", "Borrower Registry & Strikes", "Maintenance & Logs"};
    private static final String[] SUBTITLES = {
        "Add, edit, search and track every asset (Abstraction, Inheritance & Polymorphism)",
        "Check equipment out and in with item-specific accessory verification",
        "Role-based limits, collateral rules and the 3-strike penalty system",
        "Automatic maintenance quarantine queue and the digital slip archive"};
    private static final DateTimeFormatter CLOCK_FMT = DateTimeFormatter.ofPattern("EEE, MMM d  \u2022  HH:mm:ss");

    private final HiramLabManager manager;
    private final String staffRole;

    // Dashboard Counters (Feature #10)
    private final KpiCard kpiAvailable = new KpiCard("AVAILABLE EQUIPMENT", "Ready for release", UITheme.SUCCESS, Glyph.CHECK);
    private final KpiCard kpiBorrowed = new KpiCard("CURRENTLY BORROWED", "Active in classrooms", UITheme.PRIMARY, Glyph.ARROW);
    private final KpiCard kpiOverdue = new KpiCard("OVERDUE ITEMS NOW", "Past allowed return time", UITheme.DANGER, Glyph.CLOCK);
    private final KpiCard kpiMaintenance = new KpiCard("UNDER MAINTENANCE", "Auto-locked for repair", UITheme.WARNING, Glyph.WRENCH);

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel mainContentPanel = new JPanel(cardLayout);
    private final Map<String, FadePanel> viewPanels = new HashMap<>();
    private NavMenu navMenu;
    private JLabel lblViewTitle, lblViewSubtitle, lblClock, lblEquipCount;
    private ToastManager toasts;
    private Timer clockTimer;

    // View 1
    private RoundedTextField txtSearchEquip;
    private RoundedComboBox<String> cmbCategoryFilter;
    private RoundedComboBox<String> cmbStatusFilter;
    private DefaultTableModel equipTableModel;
    private ModernTable equipTable;

    // View 2: borrowers
    private DefaultTableModel borrowerTableModel;
    private ModernTable borrowerTable;

    // View 3: Release & Return
    private RoundedComboBox<Equipment> cmbReleaseEquip;
    private RoundedComboBox<Borrower> cmbReleaseBorrower;
    private RoundedTextField txtRoomLocation;
    private PolicyBanner policyBanner;
    private JPanel releaseChecklistPanel;
    private final List<ModernCheckBox> releaseCheckBoxes = new ArrayList<>();

    private RoundedComboBox<BorrowTransaction> cmbActiveTransactions;
    private TimeTrackerBar timeTracker;
    private JPanel returnChecklistPanel;
    private final List<ModernCheckBox> returnCheckBoxes = new ArrayList<>();
    private RoundedComboBox<String> cmbReturnCondition;
    private RoundedTextField txtReturnDamageNote;

    // View 4
    private DefaultTableModel maintenanceTableModel;
    private ModernTable maintenanceTable;
    private DefaultTableModel historyTableModel;
    private ModernTable historyTable;

    public MainDashboardFrame(HiramLabManager manager, String staffRole) {
        this.manager = manager;
        this.staffRole = staffRole;

        setTitle("HiramLab \u2014 Automated Campus Equipment Borrowing & Maintenance Sorting System");
        setSize(1340, 820);
        setMinimumSize(new Dimension(1180, 740));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(UITheme.BG_LIGHT);
        root.add(buildSidebar(), BorderLayout.WEST);

        JPanel centerWrapper = new JPanel(new BorderLayout(0, 6));
        centerWrapper.setBackground(UITheme.BG_LIGHT);
        centerWrapper.setBorder(new EmptyBorder(18, 22, 12, 22));

        JPanel top = new JPanel(new BorderLayout(0, 4));
        top.setOpaque(false);
        top.add(buildHeader(), BorderLayout.NORTH);
        top.add(buildTopKpiHeader(), BorderLayout.CENTER);
        centerWrapper.add(top, BorderLayout.NORTH);

        mainContentPanel.setOpaque(false);
        addView("EQUIP", buildEquipmentView());
        addView("COUNTER", buildReleaseReturnView());
        addView("BORROWERS", buildBorrowerView());
        addView("MAINTENANCE", buildMaintenanceAndLogsView());
        centerWrapper.add(mainContentPanel, BorderLayout.CENTER);
        root.add(centerWrapper, BorderLayout.CENTER);

        setContentPane(root);
        toasts = new ToastManager(getLayeredPane());
        installShortcuts();
        refreshAllViews();

        clockTimer = new Timer(1000, e -> onClockTick());
        clockTimer.setInitialDelay(0);
        clockTimer.start();
    }

    @Override public void dispose() {
        if (clockTimer != null) clockTimer.stop();
        super.dispose();
    }

    private void addView(String key, JComponent view) {
        FadePanel f = new FadePanel(new BorderLayout());
        f.add(view, BorderLayout.CENTER);
        viewPanels.put(key, f);
        mainContentPanel.add(f, key);
    }

    private void installShortcuts() {
        for (int i = 0; i < CARDS.length; i++) {
            final int idx = i;
            String name = "nav" + i;
            getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                    .put(KeyStroke.getKeyStroke(KeyEvent.VK_1 + i, InputEvent.CTRL_DOWN_MASK), name);
            getRootPane().getActionMap().put(name, new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) { navMenu.select(idx, true); }
            });
        }
    }

    private void onClockTick() {
        lblClock.setText(LocalDateTime.now().format(CLOCK_FMT));
        updateKpiCounters();
        if (timeTracker != null) timeTracker.tick();
    }

    private void switchNav(int index) {
        String key = CARDS[index];
        refreshAllViews();
        cardLayout.show(mainContentPanel, key);
        viewPanels.get(key).playIn(0, 380);
        lblViewTitle.setText(TITLES[index]);
        lblViewSubtitle.setText(SUBTITLES[index]);
    }

    // ------------------------------------------------------------------------
    // SIDEBAR + HEADER
    // ------------------------------------------------------------------------
    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(UITheme.SIDEBAR_BG);
        sidebar.setPreferredSize(new Dimension(260, 800));
        sidebar.setBorder(new EmptyBorder(26, 18, 22, 18));

        JPanel brandBox = new JPanel(new BorderLayout(12, 0)) {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        brandBox.setOpaque(false);
        brandBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        JComponent logo = new JComponent() {
            @Override public Dimension getPreferredSize() { return new Dimension(46, 46); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = UITheme.aa(g);
                g2.setPaint(new GradientPaint(0, 0, UITheme.PRIMARY, 46, 46, UITheme.PURPLE));
                g2.fill(new RoundRectangle2D.Float(0, 0, 46, 46, 14, 14));
                Glyph.BOX.paint(g2, 23, 23, 24, Color.WHITE);
                g2.dispose();
            }
        };
        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.add(UITheme.label("HiramLab", UITheme.font(Font.BOLD, 24f), Color.WHITE));
        titles.add(UITheme.label("Asset & Maintenance Triage", UITheme.font(Font.PLAIN, 12f), UITheme.SLATE_300));
        brandBox.add(logo, BorderLayout.WEST);
        brandBox.add(titles, BorderLayout.CENTER);

        JLabel staffBadge = UITheme.label("\u25CF  " + staffRole, UITheme.font(Font.BOLD, 12f), UITheme.SUCCESS);
        staffBadge.setBorder(new EmptyBorder(14, 2, 24, 0));
        staffBadge.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel north = new JPanel();
        north.setOpaque(false);
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.add(brandBox);
        north.add(staffBadge);
        sidebar.add(north, BorderLayout.NORTH);

        navMenu = new NavMenu(
                new String[]{"Equipment Inventory", "Release & Return", "Borrowers & Strikes", "Maintenance & Logs"},
                new Glyph[]{Glyph.BOX, Glyph.SWAP, Glyph.USERS, Glyph.WRENCH},
                this::switchNav);
        JPanel navWrapper = new JPanel(new BorderLayout());
        navWrapper.setOpaque(false);
        navWrapper.add(navMenu, BorderLayout.NORTH);
        sidebar.add(navWrapper, BorderLayout.CENTER);

        JPanel bottomBox = new JPanel(new GridLayout(3, 1, 0, 8));
        bottomBox.setOpaque(false);
        JLabel hintKeys = UITheme.label("Shortcuts: Ctrl + 1 \u2013 4", UITheme.font(Font.PLAIN, 11f), new Color(100, 116, 139));
        JButton btnSave = UITheme.createButton("Save CSV Data Now", UITheme.SUCCESS, Color.WHITE);
        JButton btnLogout = UITheme.createButton("Sign Out", new Color(51, 65, 85), Color.WHITE);

        btnSave.addActionListener(e -> {
            manager.saveAllData();
            toasts.show(NoticeKind.SUCCESS, "Equipment, borrowers & transactions saved to CSV.");
        });
        btnLogout.addActionListener(e -> {
            manager.saveAllData();
            dispose();
            new LoginFrame(manager).setVisible(true);
        });
        bottomBox.add(hintKeys);
        bottomBox.add(btnSave);
        bottomBox.add(btnLogout);
        sidebar.add(bottomBox, BorderLayout.SOUTH);
        return sidebar;
    }

    private JComponent buildHeader() {
        FadePanel header = new FadePanel(new BorderLayout(10, 0)).autoPlay(0);
        header.setBorder(new EmptyBorder(0, 4, 4, 4));

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        lblViewTitle = new JLabel(TITLES[0]) {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, super.getMaximumSize().height); }
        };
        lblViewTitle.setFont(UITheme.font(Font.BOLD, 26f));
        lblViewTitle.setForeground(UITheme.TEXT_DARK);
        lblViewSubtitle = new JLabel(SUBTITLES[0]) {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, super.getMaximumSize().height); }
        };
        lblViewSubtitle.setFont(UITheme.font(Font.PLAIN, 13f));
        lblViewSubtitle.setForeground(UITheme.TEXT_MUTED);
        left.add(lblViewTitle);
        left.add(Box.createVerticalStrut(2));
        left.add(lblViewSubtitle);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 6));
        right.setOpaque(false);
        lblClock = UITheme.label(LocalDateTime.now().format(CLOCK_FMT), UITheme.font(Font.BOLD, 13f), UITheme.TEXT_MUTED);
        right.add(lblClock);

        header.add(left, BorderLayout.CENTER);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    // ------------------------------------------------------------------------
    // FEATURE #10: DASHBOARD SUMMARY COUNTERS (TOP KPI HEADER)
    // ------------------------------------------------------------------------
    private JPanel buildTopKpiHeader() {
        JPanel kpiGrid = new JPanel(new GridLayout(1, 4, 6, 0));
        kpiGrid.setOpaque(false);
        KpiCard[] cards = {kpiAvailable, kpiBorrowed, kpiOverdue, kpiMaintenance};
        for (int i = 0; i < cards.length; i++) {
            FadePanel wrap = new FadePanel(new BorderLayout()).autoPlay(120 + i * 110);
            wrap.add(cards[i], BorderLayout.CENTER);
            kpiGrid.add(wrap);
        }
        return kpiGrid;
    }

    private void updateKpiCounters() {
        kpiAvailable.setValue(manager.getAvailableCount());
        kpiBorrowed.setValue(manager.getBorrowedCount());
        kpiOverdue.setValue(manager.getOverdueCount());
        kpiMaintenance.setValue(manager.getMaintenanceCount());
        if (navMenu != null) {
            navMenu.setBadge(1, manager.getOverdueCount(), UITheme.DANGER);
            navMenu.setBadge(3, manager.getMaintenanceCount(), UITheme.WARNING);
        }
    }

    // ------------------------------------------------------------------------
    // VIEW 1: EQUIPMENT MANAGEMENT & SEARCH/FILTER TABLE (Features #1, #5, #8)
    // ------------------------------------------------------------------------
    private JPanel buildEquipmentView() {
        CardPanel panel = new CardPanel("Equipment Inventory Registry",
                "Click a column header to sort \u2022 type to filter instantly");

        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        filterBar.setOpaque(false);

        txtSearchEquip = new RoundedTextField("").setHint("Search by ID, name, brand or specs\u2026");
        txtSearchEquip.setPreferredSize(new Dimension(250, 40));
        cmbCategoryFilter = new RoundedComboBox<>(new String[]{"ALL", "Laptop", "Projector", "Tool"});
        cmbStatusFilter = new RoundedComboBox<>(new String[]{"ALL", "AVAILABLE", "BORROWED", "UNDER_MAINTENANCE"});
        cmbCategoryFilter.setPreferredSize(new Dimension(120, 40));
        cmbStatusFilter.setPreferredSize(new Dimension(180, 40));

        JButton btnFilter = UITheme.createButton("Search / Filter", UITheme.PRIMARY, Color.WHITE);
        JButton btnReset = UITheme.createButton("Reset", new Color(226, 232, 240), UITheme.TEXT_DARK);

        filterBar.add(txtSearchEquip);
        filterBar.add(UITheme.fieldLabel("Category"));
        filterBar.add(cmbCategoryFilter);
        filterBar.add(UITheme.fieldLabel("Status"));
        filterBar.add(cmbStatusFilter);
        filterBar.add(btnFilter);
        filterBar.add(btnReset);

        lblEquipCount = UITheme.label(" ", UITheme.font(Font.BOLD, 12f), UITheme.TEXT_MUTED);

        String[] cols = {"Asset ID", "Category", "Equipment Name", "Brand", "Technical Specs", "Status", "Condition / Triage Note"};
        equipTableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        equipTable = new ModernTable(equipTableModel, 40);
        equipTable.getColumnModel().getColumn(1).setCellRenderer(new BadgeRenderer());
        equipTable.getColumnModel().getColumn(5).setCellRenderer(new BadgeRenderer());
        equipTable.setColumnWidths(80, 95, 190, 75, 240, 185, 215);

        JPanel actionBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actionBar.setOpaque(false);
        JButton btnAdd = UITheme.createButton("+ Add New Equipment", UITheme.SUCCESS, Color.WHITE);
        JButton btnEdit = UITheme.createButton("Edit Selected", UITheme.PRIMARY, Color.WHITE);
        JButton btnInspect = UITheme.createButton("View Polymorphic Checklist", UITheme.PURPLE, Color.WHITE);
        JButton btnDelete = UITheme.createButton("Delete Selected", UITheme.DANGER, Color.WHITE);
        actionBar.add(btnAdd);
        actionBar.add(btnEdit);
        actionBar.add(btnInspect);
        actionBar.add(btnDelete);
        JPanel actionRow = new JPanel(new BorderLayout());
        actionRow.setOpaque(false);
        actionRow.add(actionBar, BorderLayout.WEST);
        actionRow.add(lblEquipCount, BorderLayout.EAST);

        ActionListener filterAction = e -> refreshEquipmentTable();
        btnFilter.addActionListener(filterAction);
        txtSearchEquip.addActionListener(filterAction);
        cmbCategoryFilter.addActionListener(filterAction);
        cmbStatusFilter.addActionListener(filterAction);
        txtSearchEquip.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { refreshEquipmentTable(); }
            @Override public void removeUpdate(DocumentEvent e) { refreshEquipmentTable(); }
            @Override public void changedUpdate(DocumentEvent e) { refreshEquipmentTable(); }
        });
        btnReset.addActionListener(e -> {
            txtSearchEquip.setText("");
            cmbCategoryFilter.setSelectedIndex(0);
            cmbStatusFilter.setSelectedIndex(0);
            refreshEquipmentTable();
        });
        btnAdd.addActionListener(e -> showAddEquipmentDialog());
        btnEdit.addActionListener(e -> showEditEquipmentDialog());
        btnInspect.addActionListener(e -> showPolymorphicChecklistDialog());
        btnDelete.addActionListener(e -> deleteSelectedEquipment());

        JScrollPane sp = UITheme.scroll(equipTable);
        sp.setBorder(new LineBorder(UITheme.BORDER));

        JPanel centerContainer = new JPanel(new BorderLayout(0, 12));
        centerContainer.setOpaque(false);
        centerContainer.add(filterBar, BorderLayout.NORTH);
        centerContainer.add(sp, BorderLayout.CENTER);
        centerContainer.add(actionRow, BorderLayout.SOUTH);
        panel.add(centerContainer, BorderLayout.CENTER);
        return panel;
    }

    // ------------------------------------------------------------------------
    // VIEW 2: RELEASE & RETURN COUNTER (Features #3, #4, #7, #8, #9, #11, #13)
    // ------------------------------------------------------------------------
    private SoftPanel buildChecklistBox(String heading, String sub, JPanel listPanel, JComponent action) {
        SoftPanel box = new SoftPanel(new BorderLayout(0, 6));
        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        JLabel h = UITheme.label(heading, UITheme.FONT_BOLD, UITheme.TEXT_DARK);
        JLabel s = UITheme.label(sub, UITheme.font(Font.PLAIN, 11f), UITheme.TEXT_MUTED);
        h.setAlignmentX(Component.LEFT_ALIGNMENT);
        s.setAlignmentX(Component.LEFT_ALIGNMENT);
        titles.add(h);
        titles.add(s);
        JPanel head = new JPanel(new BorderLayout(10, 0));
        head.setOpaque(false);
        head.add(titles, BorderLayout.CENTER);
        if (action != null) head.add(action, BorderLayout.EAST);
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setOpaque(false);
        box.add(head, BorderLayout.NORTH);
        box.add(listPanel, BorderLayout.CENTER);
        return box;
    }

    private JScrollPane formScroll(JComponent form) {
        JScrollPane fs = UITheme.scroll(form);
        fs.setOpaque(false);
        fs.getViewport().setOpaque(false);
        fs.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        return fs;
    }

    private JPanel buildReleaseReturnView() {
        JPanel splitGrid = new JPanel(new GridLayout(1, 2, 4, 0));
        splitGrid.setOpaque(false);

        // ================= LEFT CARD: EQUIPMENT RELEASE =================
        CardPanel releaseCard = new CardPanel("Release Equipment", "Check-out & accessory verification");
        JPanel releaseForm = new ScrollableForm(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.gridx = 0;
        gbc.insets = new Insets(2, 2, 2, 2);

        cmbReleaseEquip = new RoundedComboBox<>();
        cmbReleaseBorrower = new RoundedComboBox<>();
        policyBanner = new PolicyBanner();
        txtRoomLocation = new RoundedTextField("CCL 301");
        releaseChecklistPanel = new JPanel();
        JButton btnCheckAllRelease = UITheme.createButton("Tick All", new Color(226, 232, 240), UITheme.TEXT_DARK);
        SoftPanel releaseBox = buildChecklistBox("Item-Specific Accessory Checklist",
                "Polymorphism \u2014 every item must be ticked to release", releaseChecklistPanel, btnCheckAllRelease);

        JButton btnReleaseNow = UITheme.createButton("Release Equipment & Generate Digital Slip", UITheme.SUCCESS, Color.WHITE);

        gbc.gridy = 0; releaseForm.add(UITheme.fieldLabel("1. Select Available Equipment:"), gbc);
        gbc.gridy = 1; releaseForm.add(cmbReleaseEquip, gbc);
        gbc.gridy = 2; releaseForm.add(UITheme.fieldLabel("2. Select Registered Borrower:"), gbc);
        gbc.gridy = 3; releaseForm.add(cmbReleaseBorrower, gbc);
        gbc.gridy = 4; gbc.insets = new Insets(6, 2, 8, 2); releaseForm.add(policyBanner, gbc);
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.gridy = 5; releaseForm.add(UITheme.fieldLabel("3. Target Classroom / Lab Location:"), gbc);
        gbc.gridy = 6; releaseForm.add(txtRoomLocation, gbc);
        gbc.gridy = 7; gbc.weighty = 1.0; gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(8, 2, 8, 2);
        releaseForm.add(releaseBox, gbc);

        gbc.gridy = 8; gbc.weighty = 0; gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 2, 2, 2);
        releaseForm.add(btnReleaseNow, gbc);

        cmbReleaseEquip.addActionListener(e -> updateReleaseChecklistUI());
        cmbReleaseBorrower.addActionListener(e -> updateBorrowerPolicyBanner());
        btnCheckAllRelease.addActionListener(e -> releaseCheckBoxes.forEach(cb -> cb.setSelected(true)));
        btnReleaseNow.addActionListener(e -> processEquipmentRelease());
        releaseCard.add(formScroll(releaseForm), BorderLayout.CENTER);

        // ================= RIGHT CARD: EQUIPMENT RETURN & TRIAGE =================
        CardPanel returnCard = new CardPanel("Return Equipment", "Check-in & automatic maintenance sorting");
        JPanel returnForm = new ScrollableForm(new GridBagLayout());
        GridBagConstraints rgbc = new GridBagConstraints();
        rgbc.fill = GridBagConstraints.HORIZONTAL;
        rgbc.weightx = 1.0;
        rgbc.gridx = 0;
        rgbc.insets = new Insets(2, 2, 2, 2);

        cmbActiveTransactions = new RoundedComboBox<>();
        timeTracker = new TimeTrackerBar();
        returnChecklistPanel = new JPanel();
        SoftPanel returnBox = buildChecklistBox("Return Accessory Verification",
                "Unticked item = Auto-Maintenance & +1 Strike", returnChecklistPanel, null);
        cmbReturnCondition = new RoundedComboBox<>(new String[]{
            "Good Condition (Restore to AVAILABLE)",
            "Damaged / Defective (Auto-Lock to UNDER_MAINTENANCE)"
        });
        txtReturnDamageNote = new RoundedTextField("").setHint("e.g. cracked lens cap, missing HDMI cable\u2026");

        JButton btnSimulateOverdue = UITheme.createButton("Simulate Overdue (Demo)", UITheme.WARNING, Color.WHITE);
        JButton btnProcessReturn = UITheme.createButton("Complete Return & Run Triage", UITheme.PRIMARY, Color.WHITE);

        rgbc.gridy = 0; returnForm.add(UITheme.fieldLabel("1. Select Active Borrowed Transaction:"), rgbc);
        rgbc.gridy = 1; returnForm.add(cmbActiveTransactions, rgbc);
        rgbc.gridy = 2; returnForm.add(timeTracker, rgbc);
        rgbc.gridy = 3; rgbc.weighty = 1.0; rgbc.fill = GridBagConstraints.BOTH;
        rgbc.insets = new Insets(8, 2, 8, 2);
        returnForm.add(returnBox, rgbc);
        rgbc.insets = new Insets(2, 2, 2, 2);
        rgbc.gridy = 4; rgbc.weighty = 0; rgbc.fill = GridBagConstraints.HORIZONTAL;
        returnForm.add(UITheme.fieldLabel("2. Physical Condition Inspection Result:"), rgbc);
        rgbc.gridy = 5; returnForm.add(cmbReturnCondition, rgbc);
        rgbc.gridy = 6; returnForm.add(UITheme.fieldLabel("3. Damage / Missing Accessory Remarks (if any):"), rgbc);
        rgbc.gridy = 7; returnForm.add(txtReturnDamageNote, rgbc);

        JPanel returnBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        returnBtns.setOpaque(false);
        returnBtns.add(btnSimulateOverdue);
        returnBtns.add(btnProcessReturn);
        rgbc.gridy = 8; rgbc.insets = new Insets(10, 2, 4, 2);
        returnForm.add(returnBtns, rgbc);

        cmbActiveTransactions.addActionListener(e -> updateReturnChecklistUI());
        btnSimulateOverdue.addActionListener(e -> simulateOverdueTransaction());
        btnProcessReturn.addActionListener(e -> processEquipmentReturn());
        returnCard.add(formScroll(returnForm), BorderLayout.CENTER);

        splitGrid.add(releaseCard);
        splitGrid.add(returnCard);
        return splitGrid;
    }

    // ------------------------------------------------------------------------
    // VIEW 3: BORROWER REGISTRY & STRIKE SYSTEM (Features #2, #9, #14)
    // ------------------------------------------------------------------------
    private JPanel buildBorrowerView() {
        CardPanel panel = new CardPanel("Borrower Registry, Role-Based Limits & 3-Strike Accountability",
                "Three filled dots = suspended \u2022 click a header to sort");

        String[] cols = {"Borrower ID", "Full Name", "Role", "Dept / Section",
                         "Active / Limit", "Max Hours", "Collateral", "Strikes", "Account Status"};
        borrowerTableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        borrowerTable = new ModernTable(borrowerTableModel, 42);
        borrowerTable.getColumnModel().getColumn(2).setCellRenderer(new BadgeRenderer());
        borrowerTable.getColumnModel().getColumn(4).setCellRenderer(new UsageRenderer());
        borrowerTable.getColumnModel().getColumn(7).setCellRenderer(new StrikeRenderer());
        borrowerTable.getColumnModel().getColumn(8).setCellRenderer(new BadgeRenderer());
        borrowerTable.setColumnWidths(120, 140, 95, 120, 125, 85, 170, 100, 210);

        JPanel actionBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actionBar.setOpaque(false);
        JButton btnAddBorrower = UITheme.createButton("+ Register New Borrower", UITheme.SUCCESS, Color.WHITE);
        JButton btnEditBorrower = UITheme.createButton("Edit Selected", UITheme.PRIMARY, Color.WHITE);
        JButton btnAddStrike = UITheme.createButton("+1 Manual Strike", UITheme.WARNING, Color.WHITE);
        JButton btnClearStrikes = UITheme.createButton("Clear Strikes (Lift Suspension)", UITheme.PURPLE, Color.WHITE);
        JButton btnDeleteBorrower = UITheme.createButton("Delete Borrower", UITheme.DANGER, Color.WHITE);
        actionBar.add(btnAddBorrower);
        actionBar.add(btnEditBorrower);
        actionBar.add(btnAddStrike);
        actionBar.add(btnClearStrikes);
        actionBar.add(btnDeleteBorrower);

        btnAddBorrower.addActionListener(e -> showAddBorrowerDialog());
        btnEditBorrower.addActionListener(e -> showEditBorrowerDialog());
        btnAddStrike.addActionListener(e -> modifyBorrowerStrikes(true));
        btnClearStrikes.addActionListener(e -> modifyBorrowerStrikes(false));
        btnDeleteBorrower.addActionListener(e -> deleteSelectedBorrower());

        JScrollPane sp = UITheme.scroll(borrowerTable);
        sp.setBorder(new LineBorder(UITheme.BORDER));
        JPanel wrap = new JPanel(new BorderLayout(0, 12));
        wrap.setOpaque(false);
        wrap.add(sp, BorderLayout.CENTER);
        wrap.add(actionBar, BorderLayout.SOUTH);
        panel.add(wrap, BorderLayout.CENTER);
        return panel;
    }

    // ------------------------------------------------------------------------
    // VIEW 4: AUTOMATIC MAINTENANCE SORTING & TRANSACTION LOGS (#7, #11, #13)
    // ------------------------------------------------------------------------
    private JPanel buildMaintenanceAndLogsView() {
        JPanel split = new JPanel(new GridLayout(2, 1, 0, 0));
        split.setOpaque(false);

        CardPanel maintCard = new CardPanel("Automatic Maintenance Sorting Queue", "Quarantined equipment awaiting repair");
        String[] mCols = {"Asset ID", "Category", "Equipment Name", "Brand", "Technical Specs", "Recorded Damage / Missing Parts Note"};
        maintenanceTableModel = new DefaultTableModel(mCols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        maintenanceTable = new ModernTable(maintenanceTableModel, 36);
        maintenanceTable.getColumnModel().getColumn(1).setCellRenderer(new BadgeRenderer());
        maintenanceTable.setColumnWidths(90, 110, 220, 90, 220, 380);

        JPanel maintActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        maintActions.setOpaque(false);
        JButton btnRepair = UITheme.createButton("\u2713 Mark Selected as Repaired & Restore to AVAILABLE", UITheme.SUCCESS, Color.WHITE);
        btnRepair.addActionListener(e -> repairSelectedEquipment());
        maintActions.add(btnRepair);
        JScrollPane msp = UITheme.scroll(maintenanceTable);
        msp.setBorder(new LineBorder(UITheme.BORDER));
        JPanel mWrap = new JPanel(new BorderLayout(0, 10));
        mWrap.setOpaque(false);
        mWrap.add(msp, BorderLayout.CENTER);
        mWrap.add(maintActions, BorderLayout.SOUTH);
        maintCard.add(mWrap, BorderLayout.CENTER);

        CardPanel logCard = new CardPanel("Complete Borrowing Transaction History", "Digital Borrower's Slip archive");
        String[] tCols = {"Tx ID", "Equip ID", "Borrower ID", "Room Location", "Borrowed At", "Due By", "Returned At", "Final Status"};
        historyTableModel = new DefaultTableModel(tCols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        historyTable = new ModernTable(historyTableModel, 36);
        historyTable.getColumnModel().getColumn(5).setCellRenderer(new DueRenderer(7));
        historyTable.getColumnModel().getColumn(7).setCellRenderer(new BadgeRenderer());
        historyTable.setColumnWidths(80, 100, 110, 120, 150, 150, 150, 190);

        JPanel logActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        logActions.setOpaque(false);
        JButton btnViewSlip = UITheme.createButton("View / Print Digital Borrower's Slip", UITheme.PRIMARY, Color.WHITE);
        btnViewSlip.addActionListener(e -> viewSelectedTransactionSlip());
        logActions.add(btnViewSlip);
        JScrollPane lsp = UITheme.scroll(historyTable);
        lsp.setBorder(new LineBorder(UITheme.BORDER));
        JPanel lWrap = new JPanel(new BorderLayout(0, 10));
        lWrap.setOpaque(false);
        lWrap.add(lsp, BorderLayout.CENTER);
        lWrap.add(logActions, BorderLayout.SOUTH);
        logCard.add(lWrap, BorderLayout.CENTER);

        split.add(maintCard);
        split.add(logCard);
        return split;
    }

    // ========================================================================
    // UI REFRESH & SYNCHRONIZATION LOGIC
    // ========================================================================
    private void refreshAllViews() {
        updateKpiCounters();
        refreshEquipmentTable();
        refreshBorrowerTable();
        refreshMaintenanceAndHistoryTables();
        refreshReleaseAndReturnCombos();
    }

    private void refreshEquipmentTable() {
        if (equipTableModel == null) return;
        equipTableModel.setRowCount(0);
        String key = txtSearchEquip != null ? txtSearchEquip.getText() : "";
        String cat = cmbCategoryFilter != null ? (String) cmbCategoryFilter.getSelectedItem() : "ALL";
        String stat = cmbStatusFilter != null ? (String) cmbStatusFilter.getSelectedItem() : "ALL";

        List<Equipment> found = manager.searchEquipment(key, cat, stat);
        for (Equipment eq : found) {
            equipTableModel.addRow(new Object[]{
                eq.getEquipmentId(), eq.getCategory(), eq.getName(), eq.getBrand(),
                eq.getSpecificDetails(), eq.getStatus(), eq.getConditionNote()
            });
        }
        if (lblEquipCount != null) {
            lblEquipCount.setText("Showing " + found.size() + " of " + manager.getEquipmentList().size() + " items");
        }
    }

    private void refreshBorrowerTable() {
        if (borrowerTableModel == null) return;
        borrowerTableModel.setRowCount(0);
        for (Borrower b : manager.getBorrowerList()) {
            String status = b.isSuspended() ? "SUSPENDED (3+ Strikes)" : "ELIGIBLE";
            borrowerTableModel.addRow(new Object[]{
                b.getBorrowerId(), b.getFullName(), b.getRole(), b.getDepartmentOrSection(),
                b.getActiveBorrowedCount() + " / " + b.getMaxBorrowLimit(),
                b.getMaxAllowedHours() + " hrs",
                b.getRequiredCollateral(), b.getStrikeCount(), status
            });
        }
    }

    private void refreshMaintenanceAndHistoryTables() {
        if (maintenanceTableModel != null) {
            maintenanceTableModel.setRowCount(0);
            for (Equipment eq : manager.getEquipmentList()) {
                if ("UNDER_MAINTENANCE".equals(eq.getStatus())) {
                    maintenanceTableModel.addRow(new Object[]{
                        eq.getEquipmentId(), eq.getCategory(), eq.getName(),
                        eq.getBrand(), eq.getSpecificDetails(), eq.getConditionNote()
                    });
                }
            }
        }
        if (historyTableModel != null) {
            historyTableModel.setRowCount(0);
            for (BorrowTransaction tx : manager.getTransactionList()) {
                String retStr = tx.getReturnTime() == null ? "\u2014" : tx.getReturnTime().format(BorrowTransaction.FMT);
                String stat = tx.isOverdue() ? "ACTIVE (OVERDUE!)" : tx.getReturnStatus();
                historyTableModel.addRow(new Object[]{
                    tx.getTransactionId(), tx.getEquipmentId(), tx.getBorrowerId(),
                    tx.getRoomLocation(), tx.getBorrowTime().format(BorrowTransaction.FMT),
                    tx.getDueTime().format(BorrowTransaction.FMT), retStr, stat
                });
            }
        }
    }

    private void refreshReleaseAndReturnCombos() {
        if (cmbReleaseEquip == null) return;
        cmbReleaseEquip.removeAllItems();
        for (Equipment eq : manager.getEquipmentList()) {
            if ("AVAILABLE".equals(eq.getStatus())) cmbReleaseEquip.addItem(eq);
        }
        updateReleaseChecklistUI();

        cmbReleaseBorrower.removeAllItems();
        for (Borrower br : manager.getBorrowerList()) cmbReleaseBorrower.addItem(br);
        updateBorrowerPolicyBanner();

        cmbActiveTransactions.removeAllItems();
        for (BorrowTransaction tx : manager.getTransactionList()) {
            if ("ACTIVE".equals(tx.getReturnStatus())) cmbActiveTransactions.addItem(tx);
        }
        updateReturnChecklistUI();
    }

    private void fillChecklist(JPanel panel, List<ModernCheckBox> store, Equipment eq, String emptyText) {
        panel.removeAll();
        store.clear();
        if (eq != null) {
            String[] items = eq.getAccessoryChecklist();
            for (int i = 0; i < items.length; i++) {
                ModernCheckBox cb = new ModernCheckBox(items[i], true);
                cb.setAlignmentX(Component.LEFT_ALIGNMENT);
                cb.playIntro(i * 70);
                store.add(cb);
                panel.add(cb);
            }
        } else {
            panel.add(UITheme.label(emptyText, UITheme.FONT_REGULAR, UITheme.TEXT_MUTED));
        }
        panel.revalidate();
        panel.repaint();
    }

    // Dynamic Polymorphic Checkboxes on Release
    private void updateReleaseChecklistUI() {
        if (releaseChecklistPanel == null) return;
        fillChecklist(releaseChecklistPanel, releaseCheckBoxes,
                (Equipment) cmbReleaseEquip.getSelectedItem(), "No available equipment selected.");
    }

    private void updateBorrowerPolicyBanner() {
        if (policyBanner == null) return;
        policyBanner.showBorrower((Borrower) cmbReleaseBorrower.getSelectedItem());
    }

    // Dynamic Polymorphic Checkboxes on Return
    private void updateReturnChecklistUI() {
        if (returnChecklistPanel == null) return;
        BorrowTransaction tx = (BorrowTransaction) cmbActiveTransactions.getSelectedItem();
        Equipment eq = tx == null ? null : manager.findEquipment(tx.getEquipmentId());
        fillChecklist(returnChecklistPanel, returnCheckBoxes, eq, "No active borrowed transactions right now.");
        if (timeTracker != null) timeTracker.setTransaction(tx);
    }

    private String selectedId(ModernTable table) {
        int row = table.getSelectedRow();
        if (row < 0) return null;
        return (String) table.getModel().getValueAt(table.convertRowIndexToModel(row), 0);
    }

    // ========================================================================
    // INTERACTIVE DIALOGS & TRANSACTION HANDLERS (logic identical to the original)
    // ========================================================================
    private void processEquipmentRelease() {
        Equipment eq = (Equipment) cmbReleaseEquip.getSelectedItem();
        Borrower br = (Borrower) cmbReleaseBorrower.getSelectedItem();
        if (eq == null || br == null) {
            Dialogs.message(this, NoticeKind.WARNING, "Release Validation",
                    "Please select both an available Equipment and a Borrower.");
            return;
        }

        boolean allChecked = releaseCheckBoxes.stream().allMatch(JCheckBox::isSelected);
        String result = manager.releaseEquipment(eq.getEquipmentId(), br.getBorrowerId(), txtRoomLocation.getText(), allChecked);

        if (result.startsWith("ERROR:")) {
            Dialogs.message(this, NoticeKind.ERROR, "Cannot Release Equipment", result.substring(6).trim());
        } else {
            refreshAllViews();
            toasts.show(NoticeKind.SUCCESS, eq.getName() + " released to " + br.getFullName());
            Dialogs.slip(this, "Equipment Released \u2014 Digital Slip", result);
        }
    }

    private void processEquipmentReturn() {
        BorrowTransaction tx = (BorrowTransaction) cmbActiveTransactions.getSelectedItem();
        if (tx == null) {
            Dialogs.message(this, NoticeKind.WARNING, "Return Error", "No active borrowing transaction selected.");
            return;
        }

        boolean allAccessoriesComplete = returnCheckBoxes.stream().allMatch(JCheckBox::isSelected);
        boolean reportedDamaged = cmbReturnCondition.getSelectedIndex() == 1;
        String notes = txtReturnDamageNote.getText().trim();

        String summary = manager.returnEquipment(tx.getTransactionId(), allAccessoriesComplete, reportedDamaged, notes);
        txtReturnDamageNote.setText("");
        cmbReturnCondition.setSelectedIndex(0);
        refreshAllViews();

        boolean problem = summary.contains("AUTO-MAINTENANCE") || summary.contains("PENALTY") || summary.contains("WARNING");
        Dialogs.message(this, problem ? NoticeKind.WARNING : NoticeKind.SUCCESS,
                "Return & Maintenance Triage Complete", summary);
    }

    private void simulateOverdueTransaction() {
        BorrowTransaction tx = (BorrowTransaction) cmbActiveTransactions.getSelectedItem();
        if (tx == null) {
            toasts.show(NoticeKind.WARNING, "Select an active transaction first to simulate overdue time.");
            return;
        }
        tx.simulateOverdueForDemo();
        manager.saveAllData();
        refreshAllViews();
        Dialogs.message(this, NoticeKind.WARNING, "Overdue Simulation Activated",
                "Transaction " + tx.getTransactionId() + " has been fast-forwarded to 2 hours past its due time!\n"
                + "\u2022 Notice the red 'OVERDUE ITEMS NOW' counter at the top increased.\n"
                + "\u2022 Returning this item now will automatically trigger an Overdue Strike.");
    }

    private void showAddEquipmentDialog() {
        RoundedComboBox<String> cmbType = new RoundedComboBox<>(new String[]{"Laptop", "Projector", "Tool"});
        RoundedTextField txtId = new RoundedTextField("LAP-104");
        RoundedTextField txtName = new RoundedTextField("");
        RoundedTextField txtBrand = new RoundedTextField("");
        RoundedTextField txtSpec1 = new RoundedTextField("Intel Core i7");
        RoundedTextField txtSpec2 = new RoundedTextField("16");

        FormPanel form = new FormPanel();
        form.row("Equipment Subclass", cmbType);
        form.row("Asset Tag ID", txtId);
        form.row("Equipment Name", txtName);
        form.row("Brand", txtBrand);
        JLabel lblSpec1 = form.row("Processor:", txtSpec1);
        JLabel lblSpec2 = form.row("RAM (GB):", txtSpec2);

        cmbType.addActionListener(e -> {
            String t = (String) cmbType.getSelectedItem();
            if ("Laptop".equals(t)) {
                txtId.setText("LAP-10" + (manager.getEquipmentList().size() + 1));
                lblSpec1.setText("Processor:"); txtSpec1.setText("Intel Core i5");
                lblSpec2.setText("RAM (GB):"); txtSpec2.setText("16");
            } else if ("Projector".equals(t)) {
                txtId.setText("PRJ-20" + (manager.getEquipmentList().size() + 1));
                lblSpec1.setText("Brightness (Lumens):"); txtSpec1.setText("3800");
                lblSpec2.setText("Supported Ports:"); txtSpec2.setText("HDMI / VGA");
            } else {
                txtId.setText("TL-30" + (manager.getEquipmentList().size() + 1));
                lblSpec1.setText("Tool Category:"); txtSpec1.setText("Networking Kit");
                lblSpec2.setText("Piece Count:"); txtSpec2.setText("10");
            }
        });

        if (Dialogs.form(this, "Add New Equipment", NoticeKind.SUCCESS, form, "Add Equipment")) {
            String id = txtId.getText().trim();
            String name = txtName.getText().trim();
            String brand = txtBrand.getText().trim();
            if (id.isEmpty() || name.isEmpty() || brand.isEmpty()) {
                Dialogs.message(this, NoticeKind.ERROR, "Input Error", "ID, Name, and Brand are required.");
                return;
            }

            Equipment eq;
            String type = (String) cmbType.getSelectedItem();
            try {
                if ("Laptop".equals(type)) {
                    eq = new Laptop(id, name, brand, "AVAILABLE", "Good Condition", txtSpec1.getText().trim(), Integer.parseInt(txtSpec2.getText().trim()));
                } else if ("Projector".equals(type)) {
                    eq = new Projector(id, name, brand, "AVAILABLE", "Good Condition", Integer.parseInt(txtSpec1.getText().trim()), txtSpec2.getText().trim());
                } else {
                    eq = new Tool(id, name, brand, "AVAILABLE", "Good Condition", txtSpec1.getText().trim(), Integer.parseInt(txtSpec2.getText().trim()));
                }
            } catch (NumberFormatException ex) {
                Dialogs.message(this, NoticeKind.ERROR, "Format Error",
                        "Numeric specification field (RAM / Lumens / Pieces) must be a valid integer.");
                return;
            }

            if (!manager.addEquipment(eq)) {
                Dialogs.message(this, NoticeKind.ERROR, "Duplicate ID", "Asset ID already exists!");
            } else {
                refreshAllViews();
                toasts.show(NoticeKind.SUCCESS, "Equipment " + id + " added to inventory.");
            }
        }
    }

    private void showEditEquipmentDialog() {
        String id = selectedId(equipTable);
        if (id == null) {
            toasts.show(NoticeKind.WARNING, "Select an equipment row from the table first.");
            return;
        }
        Equipment eq = manager.findEquipment(id);
        if (eq == null) return;

        RoundedTextField txtName = new RoundedTextField(eq.getName());
        RoundedTextField txtBrand = new RoundedTextField(eq.getBrand());
        RoundedTextField txtSpec1 = new RoundedTextField(eq.getSpecField1());
        RoundedTextField txtSpec2 = new RoundedTextField(eq.getSpecField2());
        RoundedTextField txtNote = new RoundedTextField(eq.getConditionNote());

        FormPanel form = new FormPanel();
        form.row("Asset ID (Read-Only)", UITheme.label(eq.getEquipmentId() + "  (" + eq.getCategory() + ")", UITheme.FONT_BOLD, UITheme.TEXT_DARK));
        form.row("Equipment Name", txtName);
        form.row("Brand", txtBrand);
        form.row("Primary Spec", txtSpec1);
        form.row("Secondary Spec", txtSpec2);
        form.row("Condition Note", txtNote);

        if (Dialogs.form(this, "Edit Equipment Details", NoticeKind.INFO, form, "Save Changes")) {
            eq.setName(txtName.getText().trim());
            eq.setBrand(txtBrand.getText().trim());
            eq.updateSpecs(txtSpec1.getText().trim(), txtSpec2.getText().trim());
            eq.setConditionNote(txtNote.getText().trim());
            manager.saveAllData();
            refreshAllViews();
            toasts.show(NoticeKind.SUCCESS, "Equipment " + id + " updated.");
        }
    }

    private void showPolymorphicChecklistDialog() {
        String id = selectedId(equipTable);
        if (id == null) {
            toasts.show(NoticeKind.WARNING, "Select an item in the table to view its polymorphic checklist.");
            return;
        }
        Equipment eq = manager.findEquipment(id);
        if (eq == null) return;

        StringBuilder sb = new StringBuilder();
        sb.append("Equipment : [").append(eq.getEquipmentId()).append("] ").append(eq.getName()).append("\n");
        sb.append("Subclass  : ").append(eq.getCategory()).append(" extends Equipment\n");
        sb.append("Specs     : ").append(eq.getSpecificDetails()).append("\n\n");
        sb.append("Overridden getAccessoryChecklist() Items:\n");
        for (String item : eq.getAccessoryChecklist()) {
            sb.append("  \u2611 ").append(item).append("\n");
        }
        Dialogs.message(this, NoticeKind.INFO, "Polymorphic Inspection Checklist", sb.toString());
    }

    private void deleteSelectedEquipment() {
        String id = selectedId(equipTable);
        if (id == null) {
            toasts.show(NoticeKind.WARNING, "Select an equipment item to delete.");
            return;
        }
        if (!manager.deleteEquipment(id)) {
            Dialogs.message(this, NoticeKind.ERROR, "Delete Blocked", "Cannot delete an item that is currently BORROWED.");
        } else {
            refreshAllViews();
            toasts.show(NoticeKind.SUCCESS, "Equipment " + id + " deleted.");
        }
    }

    private void showAddBorrowerDialog() {
        RoundedComboBox<String> cmbRole = new RoundedComboBox<>(new String[]{"Student", "Faculty"});
        RoundedTextField txtId = new RoundedTextField("2025-11020");
        RoundedTextField txtName = new RoundedTextField("");
        RoundedTextField txtDept = new RoundedTextField("BSIT 2-1");

        cmbRole.addActionListener(e -> {
            if ("Faculty".equals(cmbRole.getSelectedItem())) {
                txtId.setText("FAC-90" + (manager.getBorrowerList().size() + 10));
                txtDept.setText("DIT Faculty");
            } else {
                txtId.setText("2025-110" + (manager.getBorrowerList().size() + 10));
                txtDept.setText("BSIT 2-1");
            }
        });

        FormPanel form = new FormPanel();
        form.row("Borrower Role (Subclass)", cmbRole);
        form.row("Student / Employee ID", txtId);
        form.row("Full Name", txtName);
        form.row("Section / Department", txtDept);

        if (Dialogs.form(this, "Register New Borrower", NoticeKind.SUCCESS, form, "Register")) {
            String id = txtId.getText().trim();
            String name = txtName.getText().trim();
            String dept = txtDept.getText().trim();
            if (id.isEmpty() || name.isEmpty()) {
                Dialogs.message(this, NoticeKind.ERROR, "Error", "ID and Full Name are required.");
                return;
            }
            Borrower b = "Faculty".equals(cmbRole.getSelectedItem())
                    ? new FacultyBorrower(id, name, dept, 0, 0)
                    : new StudentBorrower(id, name, dept, 0, 0);

            if (!manager.addBorrower(b)) {
                Dialogs.message(this, NoticeKind.ERROR, "Duplicate ID", "Borrower ID already exists!");
            } else {
                refreshAllViews();
                toasts.show(NoticeKind.SUCCESS, name + " registered as " + b.getRole() + ".");
            }
        }
    }

    private void showEditBorrowerDialog() {
        String id = selectedId(borrowerTable);
        if (id == null) {
            toasts.show(NoticeKind.WARNING, "Select a borrower from the table first.");
            return;
        }
        Borrower b = manager.findBorrower(id);
        if (b == null) return;

        RoundedTextField txtName = new RoundedTextField(b.getFullName());
        RoundedTextField txtDept = new RoundedTextField(b.getDepartmentOrSection());

        FormPanel form = new FormPanel();
        form.row("Borrower ID", UITheme.label(b.getBorrowerId() + "  (" + b.getRole() + ")", UITheme.FONT_BOLD, UITheme.TEXT_DARK));
        form.row("Full Name", txtName);
        form.row("Dept / Section", txtDept);

        if (Dialogs.form(this, "Edit Borrower Profile", NoticeKind.INFO, form, "Save Changes")) {
            b.setFullName(txtName.getText().trim());
            b.setDepartmentOrSection(txtDept.getText().trim());
            manager.saveAllData();
            refreshAllViews();
            toasts.show(NoticeKind.SUCCESS, "Borrower profile updated.");
        }
    }

    private void modifyBorrowerStrikes(boolean addStrike) {
        String id = selectedId(borrowerTable);
        if (id == null) {
            toasts.show(NoticeKind.WARNING, "Select a borrower row first.");
            return;
        }
        Borrower b = manager.findBorrower(id);
        if (b == null) return;

        if (addStrike) {
            b.addStrike();
        } else {
            b.clearStrikes();
        }
        manager.saveAllData();
        refreshAllViews();
        if (addStrike) {
            toasts.show(b.isSuspended() ? NoticeKind.ERROR : NoticeKind.WARNING,
                    b.getFullName() + ": strike " + b.getStrikeCount() + "/3" + (b.isSuspended() ? " \u2014 SUSPENDED" : ""));
        } else {
            toasts.show(NoticeKind.SUCCESS, "Strikes cleared for " + b.getFullName() + ".");
        }
    }

    private void deleteSelectedBorrower() {
        String id = selectedId(borrowerTable);
        if (id == null) {
            toasts.show(NoticeKind.WARNING, "Select a borrower to delete.");
            return;
        }
        if (!manager.deleteBorrower(id)) {
            Dialogs.message(this, NoticeKind.ERROR, "Delete Blocked",
                    "Cannot delete a borrower who currently has active borrowed equipment.");
        } else {
            refreshAllViews();
            toasts.show(NoticeKind.SUCCESS, "Borrower " + id + " deleted.");
        }
    }

    private void repairSelectedEquipment() {
        String eqId = selectedId(maintenanceTable);
        if (eqId == null) {
            toasts.show(NoticeKind.WARNING, "Select a quarantined item from the Maintenance table first.");
            return;
        }
        Equipment eq = manager.findEquipment(eqId);
        if (eq == null) return;

        String note = Dialogs.input(this, "Repair Triage",
                "Enter technician repair & verification note for [" + eq.getName() + "]:",
                "Replaced missing cable & passed diagnostics");
        if (note != null) {
            eq.markAsRepaired(note.trim().isEmpty() ? "Verified Working" : note.trim());
            manager.saveAllData();
            refreshAllViews();
            toasts.show(NoticeKind.SUCCESS, eq.getName() + " [" + eq.getEquipmentId() + "] restored to AVAILABLE.");
        }
    }

    private void viewSelectedTransactionSlip() {
        String txId = selectedId(historyTable);
        if (txId == null) {
            toasts.show(NoticeKind.WARNING, "Select a transaction row from the history table first.");
            return;
        }
        BorrowTransaction tx = manager.findTransactionById(txId);
        if (tx == null) return;

        Equipment eq = manager.findEquipment(tx.getEquipmentId());
        Borrower br = manager.findBorrower(tx.getBorrowerId());
        Dialogs.slip(this, "Digital Borrower's Slip \u2014 " + txId, tx.generateDigitalSlip(eq, br));
    }
}
