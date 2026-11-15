// ============================================================================
// 3. INHERITANCE & POLYMORPHISM: FACULTY BORROWER CHILD CLASS
// ============================================================================
public class FacultyBorrower extends Borrower {
    public FacultyBorrower(String borrowerId, String fullName, String department, int strikeCount, int activeBorrowedCount) {
        super(borrowerId, fullName, department, strikeCount, activeBorrowedCount);
    }

    @Override
    public String getRole() { return "Faculty"; }

    @Override
    public int getMaxBorrowLimit() { return 5; }

    @Override
    public int getMaxAllowedHours() { return 12; }

    @Override
    public String getRequiredCollateral() { return "Faculty Signature (No ID Surrender)"; }
}
