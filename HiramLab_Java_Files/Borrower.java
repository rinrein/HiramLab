// ============================================================================
// 3. ABSTRACTION, INHERITANCE & POLYMORPHISM: BORROWER HIERARCHY
// ============================================================================
public abstract class Borrower {
    private String borrowerId;
    private String fullName;
    private String departmentOrSection;
    private int strikeCount;
    private int activeBorrowedCount;

    public Borrower(String borrowerId, String fullName, String departmentOrSection, int strikeCount, int activeBorrowedCount) {
        this.borrowerId = borrowerId;
        this.fullName = fullName;
        this.departmentOrSection = departmentOrSection;
        this.strikeCount = strikeCount;
        this.activeBorrowedCount = activeBorrowedCount;
    }

    // POLYMORPHIC ROLE-BASED METHODS
    public abstract String getRole();
    public abstract int getMaxBorrowLimit();
    public abstract int getMaxAllowedHours();
    public abstract String getRequiredCollateral();

    public boolean isSuspended() {
        return strikeCount >= 3;
    }

    public boolean canBorrowMore() {
        return !isSuspended() && (activeBorrowedCount < getMaxBorrowLimit());
    }

    public void addStrike() { this.strikeCount++; }
    public void clearStrikes() { this.strikeCount = 0; }
    public void incrementActiveBorrow() { this.activeBorrowedCount++; }
    public void decrementActiveBorrow() {
        if (this.activeBorrowedCount > 0) this.activeBorrowedCount--;
    }

    protected String cleanCsv(String s) {
        return s == null ? "" : s.replace(",", ";").trim();
    }

    public String toCsvRow() {
        return String.join(",", getRole().toUpperCase(), cleanCsv(borrowerId), cleanCsv(fullName),
                cleanCsv(departmentOrSection), String.valueOf(strikeCount), String.valueOf(activeBorrowedCount));
    }

    public String getBorrowerId() { return borrowerId; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getDepartmentOrSection() { return departmentOrSection; }
    public void setDepartmentOrSection(String departmentOrSection) { this.departmentOrSection = departmentOrSection; }
    public int getStrikeCount() { return strikeCount; }
    public int getActiveBorrowedCount() { return activeBorrowedCount; }
    public void setActiveBorrowedCount(int count) { this.activeBorrowedCount = count; }

    @Override
    public String toString() {
        return "[" + borrowerId + "] " + fullName + " (" + getRole() + " | Active: "
                + activeBorrowedCount + "/" + getMaxBorrowLimit() + " | Strikes: " + strikeCount + ")";
    }
}
