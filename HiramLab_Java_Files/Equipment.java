// ============================================================================
// 1. ABSTRACTION & ENCAPSULATION: ABSTRACT EQUIPMENT PARENT CLASS
// ============================================================================
public abstract class Equipment {
    private String equipmentId;
    private String name;
    private String brand;
    private String status; // "AVAILABLE", "BORROWED", "UNDER_MAINTENANCE"
    private String conditionNote;

    public Equipment(String equipmentId, String name, String brand, String status, String conditionNote) {
        this.equipmentId = equipmentId;
        this.name = name;
        this.brand = brand;
        this.status = status;
        this.conditionNote = conditionNote;
    }

    // POLYMORPHIC ABSTRACT METHODS: Child classes must provide their own implementation
    public abstract String getCategory();
    public abstract String[] getAccessoryChecklist();
    public abstract String getSpecificDetails();
    public abstract String getSpecField1();
    public abstract String getSpecField2();
    public abstract void updateSpecs(String spec1, String spec2);
    public abstract String toCsvRow();

    // State Transition Methods (Automatic Maintenance Sorting)
    public void markAsBorrowed() {
        this.status = "BORROWED";
    }

    public void markAsReturnedGood() {
        this.status = "AVAILABLE";
        this.conditionNote = "Good Condition - Verified";
    }

    public void lockForMaintenance(String damageReason) {
        this.status = "UNDER_MAINTENANCE";
        this.conditionNote = "DEFECTIVE: " + (damageReason.isEmpty() ? "Needs Inspection" : damageReason);
    }

    public void markAsRepaired(String repairNote) {
        this.status = "AVAILABLE";
        this.conditionNote = "Repaired (" + repairNote + ")";
    }

    protected String cleanCsv(String input) {
        return input == null ? "" : input.replace(",", ";").replace("\n", " ").trim();
    }

    // Encapsulated Getters & Setters
    public String getEquipmentId() { return equipmentId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getConditionNote() { return conditionNote; }
    public void setConditionNote(String conditionNote) { this.conditionNote = conditionNote; }

    @Override
    public String toString() {
        return "[" + equipmentId + "] " + name + " (" + getCategory() + " - " + getSpecificDetails() + ")";
    }
}
