// ============================================================================
// 2. INHERITANCE & POLYMORPHISM: EQUIPMENT CHILD CLASSES
// ============================================================================
public class Laptop extends Equipment {
    private String processor;
    private int ramGb;

    public Laptop(String equipmentId, String name, String brand, String status, String conditionNote, String processor, int ramGb) {
        super(equipmentId, name, brand, status, conditionNote);
        this.processor = processor;
        this.ramGb = ramGb;
    }

    @Override
    public String getCategory() { return "Laptop"; }

    @Override
    public String[] getAccessoryChecklist() {
        return new String[]{
            "Original Charger & Power Brick",
            "USB / Wireless Optical Mouse",
            "Padded Laptop Sleeve / Bag",
            "Screen & Keyboard Undamaged"
        };
    }

    @Override
    public String getSpecificDetails() { return processor + " | " + ramGb + "GB RAM"; }

    @Override
    public String getSpecField1() { return processor; }

    @Override
    public String getSpecField2() { return String.valueOf(ramGb); }

    @Override
    public void updateSpecs(String spec1, String spec2) {
        this.processor = spec1;
        try { this.ramGb = Integer.parseInt(spec2.trim()); } catch (NumberFormatException ignored) {}
    }

    @Override
    public String toCsvRow() {
        return String.join(",", "LAPTOP", cleanCsv(getEquipmentId()), cleanCsv(getName()),
                cleanCsv(getBrand()), cleanCsv(getStatus()), cleanCsv(getConditionNote()),
                cleanCsv(processor), String.valueOf(ramGb));
    }
}
