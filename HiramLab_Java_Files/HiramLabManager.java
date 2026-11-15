import java.io.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// ============================================================================
// 5. CORE BACKEND CONTROLLER & LOCAL FILE PERSISTENCE (HiramLabManager)
// ============================================================================
public class HiramLabManager {
    private final List<Equipment> equipmentList = new ArrayList<>();
    private final List<Borrower> borrowerList = new ArrayList<>();
    private final List<BorrowTransaction> transactionList = new ArrayList<>();

    private static final String EQUIP_FILE = "hiramlab_equipment.csv";
    private static final String BORROWER_FILE = "hiramlab_borrowers.csv";
    private static final String TX_FILE = "hiramlab_transactions.csv";

    // Feature #12: Staff Login Authentication
    public String authenticateStaff(String username, String password) {
        if ("admin".equalsIgnoreCase(username) && "hiram123".equals(password)) {
            return "Lab Administrator (admin)";
        }
        if ("custodian".equalsIgnoreCase(username) && "lab2026".equals(password)) {
            return "Lab Custodian (custodian)";
        }
        return null;
    }

    // Feature #1: Equipment Management (Add, Edit, View, Delete)
    public boolean addEquipment(Equipment eq) {
        if (findEquipment(eq.getEquipmentId()) != null) return false;
        equipmentList.add(eq);
        saveAllData();
        return true;
    }

    public boolean deleteEquipment(String eqId) {
        Equipment eq = findEquipment(eqId);
        if (eq == null || "BORROWED".equals(eq.getStatus())) return false;
        equipmentList.remove(eq);
        saveAllData();
        return true;
    }

    // Feature #2: Borrower Registration
    public boolean addBorrower(Borrower br) {
        if (findBorrower(br.getBorrowerId()) != null) return false;
        borrowerList.add(br);
        saveAllData();
        return true;
    }

    public boolean deleteBorrower(String borrowerId) {
        Borrower br = findBorrower(borrowerId);
        if (br == null || br.getActiveBorrowedCount() > 0) return false;
        borrowerList.remove(br);
        saveAllData();
        return true;
    }

    // Feature #3: Equipment Release (Borrowing)
    public String releaseEquipment(String eqId, String borrowerId, String roomLocation, boolean allAccessoriesChecked) {
        if (!allAccessoriesChecked) {
            return "ERROR: You must verify and check ALL item-specific accessories before releasing this unit.";
        }
        if (roomLocation == null || roomLocation.trim().isEmpty()) {
            return "ERROR: Please enter the target Classroom / Laboratory Location (e.g., CCL 302).";
        }

        Equipment eq = findEquipment(eqId);
        Borrower br = findBorrower(borrowerId);

        if (eq == null || br == null) return "ERROR: Selected Equipment or Borrower was not found.";
        if (!"AVAILABLE".equals(eq.getStatus())) {
            return "ERROR: Equipment [" + eq.getEquipmentId() + "] is currently " + eq.getStatus() + ".";
        }
        if (br.isSuspended()) {
            return "ERROR: Borrower " + br.getFullName() + " is SUSPENDED (3 Strikes reached). Clear strikes first.";
        }
        if (!br.canBorrowMore()) {
            return "ERROR: " + br.getRole() + " Borrowing Limit Reached! (" + br.getRole()
                    + "s can only borrow up to " + br.getMaxBorrowLimit() + " active items at a time).";
        }

        eq.markAsBorrowed();
        br.incrementActiveBorrow();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime due = now.plusHours(br.getMaxAllowedHours());
        String txId = String.format("TX-%04d", transactionList.size() + 1001);

        BorrowTransaction tx = new BorrowTransaction(txId, eq.getEquipmentId(), br.getBorrowerId(),
                roomLocation.trim(), now, due, null, "ACTIVE");
        transactionList.add(tx);

        String slipText = tx.generateDigitalSlip(eq, br);
        saveSlipToFile(txId, slipText);
        saveAllData();
        return slipText;
    }

    // Feature #4, #7, #13, #14: Equipment Return + Auto Maintenance Sorting + Strikes
    public String returnEquipment(String txId, boolean allAccessoriesComplete, boolean reportedDamaged, String notes) {
        BorrowTransaction tx = findActiveTransaction(txId);
        if (tx == null) return "ERROR: Active transaction not found.";

        Equipment eq = findEquipment(tx.getEquipmentId());
        Borrower br = findBorrower(tx.getBorrowerId());
        boolean wasOverdue = tx.isOverdue();
        long minsLate = tx.getMinutesOverdue();

        if (br != null) br.decrementActiveBorrow();

        StringBuilder report = new StringBuilder();
        report.append("RETURN PROCESSED FOR TRANSACTION: ").append(txId).append("\n");
        report.append("--------------------------------------------------\n");

        // Automatic Maintenance Sorting (Feature #7)
        if (reportedDamaged || !allAccessoriesComplete) {
            String reason = notes.trim();
            if (!allAccessoriesComplete && reason.isEmpty()) {
                reason = "Incomplete/Missing Accessories upon check-in";
            } else if (reportedDamaged && reason.isEmpty()) {
                reason = "Reported Damaged/Defective upon return";
            }

            if (eq != null) eq.lockForMaintenance(reason);
            tx.completeReturn("RETURNED_DAMAGED");

            if (br != null) {
                br.addStrike();
                report.append("• AUTO-MAINTENANCE SORTING: Equipment [").append(eq != null ? eq.getEquipmentId() : "")
                      .append("] is now LOCKED under UNDER_MAINTENANCE.\n");
                report.append("• REASON LOGGED: ").append(reason).append("\n");
                report.append("• PENALTY APPLIED: +1 Strike added to ").append(br.getFullName())
                      .append(" (Current Strikes: ").append(br.getStrikeCount()).append("/3).\n");
            }
        } else {
            if (eq != null) eq.markAsReturnedGood();
            tx.completeReturn(wasOverdue ? "RETURNED_LATE" : "RETURNED_GOOD");
            report.append("• CONDITION VERIFIED: All accessories complete. Equipment restored to AVAILABLE.\n");
        }

        // Overdue Strike Check (Feature #13 & #14)
        if (wasOverdue) {
            if (br != null && !reportedDamaged && allAccessoriesComplete) {
                br.addStrike();
                report.append("• OVERDUE PENALTY: Item was returned ").append(minsLate)
                      .append(" mins past due time. +1 Strike added to ").append(br.getFullName())
                      .append(" (Current Strikes: ").append(br.getStrikeCount()).append("/3).\n");
            } else {
                report.append("• OVERDUE LOGGED: Item was also ").append(minsLate).append(" mins overdue.\n");
            }
        }

        if (br != null && br.isSuspended()) {
            report.append("\nWARNING: ").append(br.getFullName())
                  .append(" has reached 3 Strikes and is now SUSPENDED from borrowing!");
        }

        saveAllData();
        return report.toString();
    }

    // Feature #5: Search and Filter Equipment
    public List<Equipment> searchEquipment(String keyword, String categoryFilter, String statusFilter) {
        List<Equipment> result = new ArrayList<>();
        String key = (keyword == null) ? "" : keyword.toLowerCase().trim();
        for (Equipment eq : equipmentList) {
            boolean matchKey = key.isEmpty()
                    || eq.getEquipmentId().toLowerCase().contains(key)
                    || eq.getName().toLowerCase().contains(key)
                    || eq.getBrand().toLowerCase().contains(key)
                    || eq.getSpecificDetails().toLowerCase().contains(key);
            boolean matchCat = "ALL".equalsIgnoreCase(categoryFilter) || eq.getCategory().equalsIgnoreCase(categoryFilter);
            boolean matchStat = "ALL".equalsIgnoreCase(statusFilter) || eq.getStatus().equalsIgnoreCase(statusFilter);
            if (matchKey && matchCat && matchStat) {
                result.add(eq);
            }
        }
        return result;
    }

    // Feature #10: Dashboard Summary Counters
    public int getAvailableCount() {
        return (int) equipmentList.stream().filter(e -> "AVAILABLE".equals(e.getStatus())).count();
    }
    public int getBorrowedCount() {
        return (int) equipmentList.stream().filter(e -> "BORROWED".equals(e.getStatus())).count();
    }
    public int getMaintenanceCount() {
        return (int) equipmentList.stream().filter(e -> "UNDER_MAINTENANCE".equals(e.getStatus())).count();
    }
    public int getOverdueCount() {
        return (int) transactionList.stream().filter(BorrowTransaction::isOverdue).count();
    }

    // Feature #6: Local File Saving & Loading (CSV Persistence)
    public void saveAllData() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(EQUIP_FILE))) {
            for (Equipment eq : equipmentList) pw.println(eq.toCsvRow());
        } catch (IOException e) {
            System.err.println("Save Equipment Error: " + e.getMessage());
        }

        try (PrintWriter pw = new PrintWriter(new FileWriter(BORROWER_FILE))) {
            for (Borrower br : borrowerList) pw.println(br.toCsvRow());
        } catch (IOException e) {
            System.err.println("Save Borrower Error: " + e.getMessage());
        }

        try (PrintWriter pw = new PrintWriter(new FileWriter(TX_FILE))) {
            for (BorrowTransaction tx : transactionList) pw.println(tx.toCsvRow());
        } catch (IOException e) {
            System.err.println("Save Transaction Error: " + e.getMessage());
        }
    }

    public void loadAllData() {
        equipmentList.clear();
        borrowerList.clear();
        transactionList.clear();

        File eqFile = new File(EQUIP_FILE);
        File brFile = new File(BORROWER_FILE);
        File txFile = new File(TX_FILE);

        if (!eqFile.exists() && !brFile.exists()) {
            loadInitialSeedData();
            saveAllData();
            return;
        }

        if (eqFile.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(eqFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] p = line.split(",", -1);
                    if (p.length >= 8) {
                        String type = p[0];
                        if ("LAPTOP".equalsIgnoreCase(type)) {
                            equipmentList.add(new Laptop(p[1], p[2], p[3], p[4], p[5], p[6], parseIntSafe(p[7], 8)));
                        } else if ("PROJECTOR".equalsIgnoreCase(type)) {
                            equipmentList.add(new Projector(p[1], p[2], p[3], p[4], p[5], parseIntSafe(p[6], 3500), p[7]));
                        } else if ("TOOL".equalsIgnoreCase(type)) {
                            equipmentList.add(new Tool(p[1], p[2], p[3], p[4], p[5], p[6], parseIntSafe(p[7], 1)));
                        }
                    }
                }
            } catch (IOException ignored) {}
        }

        if (brFile.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(brFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] p = line.split(",", -1);
                    if (p.length >= 6) {
                        if ("STUDENT".equalsIgnoreCase(p[0])) {
                            borrowerList.add(new StudentBorrower(p[1], p[2], p[3], parseIntSafe(p[4], 0), parseIntSafe(p[5], 0)));
                        } else if ("FACULTY".equalsIgnoreCase(p[0])) {
                            borrowerList.add(new FacultyBorrower(p[1], p[2], p[3], parseIntSafe(p[4], 0), parseIntSafe(p[5], 0)));
                        }
                    }
                }
            } catch (IOException ignored) {}
        }

        if (txFile.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(txFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] p = line.split(",", -1);
                    if (p.length >= 8) {
                        LocalDateTime bTime = LocalDateTime.parse(p[4], BorrowTransaction.FMT);
                        LocalDateTime dTime = LocalDateTime.parse(p[5], BorrowTransaction.FMT);
                        LocalDateTime rTime = "NONE".equals(p[6]) ? null : LocalDateTime.parse(p[6], BorrowTransaction.FMT);
                        transactionList.add(new BorrowTransaction(p[0], p[1], p[2], p[3], bTime, dTime, rTime, p[7]));
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private void loadInitialSeedData() {
        // Pre-loaded Equipment (Laptops, Projectors, Tools)
        equipmentList.add(new Laptop("LAP-101", "ThinkPad E14 Gen 5", "Lenovo", "AVAILABLE", "Good Condition", "Intel Core i5-1335U", 16));
        equipmentList.add(new Laptop("LAP-102", "VivoBook Pro 15", "ASUS", "AVAILABLE", "Good Condition", "AMD Ryzen 7 5800H", 16));
        equipmentList.add(new Laptop("LAP-103", "Latitude 3420", "Dell", "BORROWED", "Good Condition", "Intel Core i5-1135G7", 8));
        equipmentList.add(new Projector("PRJ-201", "EB-X06 XGA Projector", "Epson", "AVAILABLE", "Good Condition", 3600, "HDMI / VGA"));
        equipmentList.add(new Projector("PRJ-202", "Smart Beam DLP", "BenQ", "UNDER_MAINTENANCE", "DEFECTIVE: Flickering bulb & missing HDMI cable", 4000, "HDMI / USB-C"));
        equipmentList.add(new Tool("TL-301", "RJ45 Network Crimping & Tester Kit", "Fluke", "AVAILABLE", "Good Condition", "Networking Kit", 12));
        equipmentList.add(new Tool("TL-302", "Digital Multimeter & Soldering Set", "UNI-T", "AVAILABLE", "Good Condition", "Electronics Kit", 8));

        // Pre-loaded Borrowers (Students & Faculty)
        borrowerList.add(new StudentBorrower("2025-10412", "Reniel Arcilla", "BSIT 2-1", 0, 1));
        borrowerList.add(new StudentBorrower("2025-10894", "Lee Santos", "BSIT 2-1", 2, 0));
        borrowerList.add(new StudentBorrower("2025-10999", "Mark Dela Cruz", "BSCS 3-2", 3, 0)); // Suspended sample
        borrowerList.add(new FacultyBorrower("FAC-9001", "Prof. Maria Reyes", "DIT Department", 0, 0));
        borrowerList.add(new FacultyBorrower("FAC-9004", "Engr. Paolo Mendoza", "CEIT Faculty", 0, 0));

        // Pre-loaded Active Transaction for LAP-103
        LocalDateTime now = LocalDateTime.now();
        transactionList.add(new BorrowTransaction(
                "TX-1001", "LAP-103", "2025-10412", "CCL 302",
                now.minusHours(1), now.plusHours(3), null, "ACTIVE"
        ));
    }

    private void saveSlipToFile(String txId, String slipContent) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(txId + "_BorrowSlip.txt"))) {
            pw.print(slipContent);
        } catch (IOException ignored) {}
    }

    private int parseIntSafe(String val, int fallback) {
        try { return Integer.parseInt(val.trim()); } catch (Exception e) { return fallback; }
    }

    public Equipment findEquipment(String id) {
        return equipmentList.stream().filter(e -> e.getEquipmentId().equalsIgnoreCase(id)).findFirst().orElse(null);
    }
    public Borrower findBorrower(String id) {
        return borrowerList.stream().filter(b -> b.getBorrowerId().equalsIgnoreCase(id)).findFirst().orElse(null);
    }
    public BorrowTransaction findActiveTransaction(String txId) {
        return transactionList.stream().filter(t -> t.getTransactionId().equalsIgnoreCase(txId) && "ACTIVE".equals(t.getReturnStatus())).findFirst().orElse(null);
    }
    public BorrowTransaction findTransactionById(String txId) {
        return transactionList.stream().filter(t -> t.getTransactionId().equalsIgnoreCase(txId)).findFirst().orElse(null);
    }

    public List<Equipment> getEquipmentList() { return equipmentList; }
    public List<Borrower> getBorrowerList() { return borrowerList; }
    public List<BorrowTransaction> getTransactionList() { return transactionList; }
}
