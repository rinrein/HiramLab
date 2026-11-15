// ============================================================================
// 2. INHERITANCE & POLYMORPHISM: TOOL CHILD CLASS
// ============================================================================
public class Tool extends Equipment {
    private String toolType;
    private int pieceCount;

    public Tool(String equipmentId, String name, String brand, String status, String conditionNote, String toolType, int pieceCount) {
        super(equipmentId, name, brand, status, conditionNote);
        this.toolType = toolType;
        this.pieceCount = pieceCount;
    }

    @Override
    public String getCategory() { return "Tool"; }

    @Override
    public String[] getAccessoryChecklist() {
        return new String[]{
            "All Kit Pieces Complete (" + pieceCount + " pcs)",
            "Hard Carrying Case Intact",
            "Blades / Pins / Probes Calibrated"
        };
    }

    @Override
    public String getSpecificDetails() { return toolType + " (" + pieceCount + " pcs)"; }

    @Override
    public String getSpecField1() { return toolType; }

    @Override
    public String getSpecField2() { return String.valueOf(pieceCount); }

    @Override
    public void updateSpecs(String spec1, String spec2) {
        this.toolType = spec1;
        try { this.pieceCount = Integer.parseInt(spec2.trim()); } catch (NumberFormatException ignored) {}
    }

    @Override
    public String toCsvRow() {
        return String.join(",", "TOOL", cleanCsv(getEquipmentId()), cleanCsv(getName()),
                cleanCsv(getBrand()), cleanCsv(getStatus()), cleanCsv(getConditionNote()),
                cleanCsv(toolType), String.valueOf(pieceCount));
    }
}
