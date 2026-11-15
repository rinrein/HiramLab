// ============================================================================
// 3. INHERITANCE & POLYMORPHISM: STUDENT BORROWER CHILD CLASS
// ============================================================================
public class StudentBorrower extends Borrower {
    public StudentBorrower(String borrowerId, String fullName, String section, int strikeCount, int activeBorrowedCount) {
        super(borrowerId, fullName, section, strikeCount, activeBorrowedCount);
    }

    @Override
    public String getRole() { return "Student"; }

    @Override
    public int getMaxBorrowLimit() { return 2; }

    @Override
    public int getMaxAllowedHours() { return 4; }

    @Override
    public String getRequiredCollateral() { return "Surrendered Validated Student ID"; }
}
