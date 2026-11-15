import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

// ============================================================================
// 4. TRANSACTION MODEL, OVERDUE TIME TRACKER & DIGITAL BORROWER'S SLIP
// ============================================================================
public class BorrowTransaction {
    private String transactionId;
    private String equipmentId;
    private String borrowerId;
    private String roomLocation;
    private LocalDateTime borrowTime;
    private LocalDateTime dueTime;
    private LocalDateTime returnTime;
    private String returnStatus; // "ACTIVE", "RETURNED_GOOD", "RETURNED_DAMAGED", "RETURNED_LATE"

    public static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public BorrowTransaction(String transactionId, String equipmentId, String borrowerId,
                             String roomLocation, LocalDateTime borrowTime, LocalDateTime dueTime,
                             LocalDateTime returnTime, String returnStatus) {
        this.transactionId = transactionId;
        this.equipmentId = equipmentId;
        this.borrowerId = borrowerId;
        this.roomLocation = roomLocation;
        this.borrowTime = borrowTime;
        this.dueTime = dueTime;
        this.returnTime = returnTime;
        this.returnStatus = returnStatus;
    }

    // Overdue Time Tracker
    public boolean isOverdue() {
        return "ACTIVE".equals(returnStatus) && LocalDateTime.now().isAfter(dueTime);
    }

    public long getMinutesOverdue() {
        if (!isOverdue()) return 0;
        return Duration.between(dueTime, LocalDateTime.now()).toMinutes();
    }

    public void simulateOverdueForDemo() {
        this.borrowTime = LocalDateTime.now().minusHours(6);
        this.dueTime = LocalDateTime.now().minusHours(2);
    }

    public void completeReturn(String finalStatus) {
        this.returnTime = LocalDateTime.now();
        this.returnStatus = finalStatus;
    }

    // Digital Borrower's Slip Generator
    public String generateDigitalSlip(Equipment eq, Borrower br) {
        String eqName = (eq != null) ? eq.getName() : equipmentId;
        String eqCat = (eq != null) ? eq.getCategory() + " (" + eq.getSpecificDetails() + ")" : "N/A";
        String parts = (eq != null) ? String.join(", ", eq.getAccessoryChecklist()) : "Standard Set";
        String brName = (br != null) ? br.getFullName() + " (" + br.getRole() + ")" : borrowerId;
        String brDept = (br != null) ? br.getDepartmentOrSection() : "N/A";
        String col = (br != null) ? br.getRequiredCollateral() : "N/A";

        StringBuilder sb = new StringBuilder();
        sb.append("========================================================\n");
        sb.append("           HIRAMLAB DIGITAL BORROWER'S SLIP             \n");
        sb.append("   Automated Borrowing & Maintenance Sorting System     \n");
        sb.append("========================================================\n");
        sb.append("Transaction ID   : ").append(transactionId).append("\n");
        sb.append("Status           : ").append(isOverdue() ? "ACTIVE (OVERDUE)" : returnStatus).append("\n");
        sb.append("--------------------------------------------------------\n");
        sb.append("BORROWER DETAILS\n");
        sb.append("  Borrower ID    : ").append(borrowerId).append("\n");
        sb.append("  Full Name      : ").append(brName).append("\n");
        sb.append("  Dept / Section : ").append(brDept).append("\n");
        sb.append("  Collateral     : ").append(col).append("\n");
        sb.append("--------------------------------------------------------\n");
        sb.append("EQUIPMENT & INSPECTION DETAILS\n");
        sb.append("  Asset Tag ID   : ").append(equipmentId).append("\n");
        sb.append("  Equipment Name : ").append(eqName).append("\n");
        sb.append("  Specs / Type   : ").append(eqCat).append("\n");
        sb.append("  Room Location  : ").append(roomLocation).append("\n");
        sb.append("  Verified Parts : ").append(parts).append("\n");
        sb.append("--------------------------------------------------------\n");
        sb.append("TIME ACCOUNTABILITY\n");
        sb.append("  Released At    : ").append(borrowTime.format(FMT)).append("\n");
        sb.append("  Due Back By    : ").append(dueTime.format(FMT)).append("\n");
        sb.append("  Returned At    : ").append(returnTime == null ? "STILL BORROWED" : returnTime.format(FMT)).append("\n");
        sb.append("========================================================\n");
        sb.append("POLICY: Returning equipment past the due time or with\n");
        sb.append("missing/damaged parts incurs +1 Strike (3 Strikes = Lock).\n");
        sb.append("========================================================\n");
        return sb.toString();
    }

    public String toCsvRow() {
        String retStr = (returnTime == null) ? "NONE" : returnTime.format(FMT);
        String cleanRoom = roomLocation == null ? "" : roomLocation.replace(",", ";").trim();
        return String.join(",", transactionId, equipmentId, borrowerId, cleanRoom,
                borrowTime.format(FMT), dueTime.format(FMT), retStr, returnStatus);
    }

    public String getTransactionId() { return transactionId; }
    public String getEquipmentId() { return equipmentId; }
    public String getBorrowerId() { return borrowerId; }
    public String getRoomLocation() { return roomLocation; }
    public LocalDateTime getBorrowTime() { return borrowTime; }
    public LocalDateTime getDueTime() { return dueTime; }
    public LocalDateTime getReturnTime() { return returnTime; }
    public String getReturnStatus() { return returnStatus; }

    @Override
    public String toString() {
        String tag = isOverdue() ? " [OVERDUE!]" : "";
        return transactionId + " | Equip: " + equipmentId + " | Borrower: " + borrowerId + " | Room: " + roomLocation + tag;
    }
}
