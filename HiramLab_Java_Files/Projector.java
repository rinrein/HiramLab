// ============================================================================
// 2. INHERITANCE & POLYMORPHISM: PROJECTOR CHILD CLASS
// ============================================================================
public class Projector extends Equipment {
    private int lumens;
    private String portType;

    public Projector(String equipmentId, String name, String brand, String status, String conditionNote, int lumens, String portType) {
        super(equipmentId, name, brand, status, conditionNote);
        this.lumens = lumens;
        this.portType = portType;
    }

    @Override
    public String getCategory() { return "Projector"; }

    @Override
    public String[] getAccessoryChecklist() {
        return new String[]{
            "AC Power Cord",
            "HDMI Display Cable",
            "VGA Adapter / Dongle",
            "Working Remote Control",
            "Protective Lens Cap"
        };
    }

    @Override
    public String getSpecificDetails() { return lumens + " Lumens | Ports: " + portType; }

    @Override
    public String getSpecField1() { return String.valueOf(lumens); }

    @Override
    public String getSpecField2() { return portType; }

    @Override
    public void updateSpecs(String spec1, String spec2) {
        try { this.lumens = Integer.parseInt(spec1.trim()); } catch (NumberFormatException ignored) {}
        this.portType = spec2;
    }

    @Override
    public String toCsvRow() {
        return String.join(",", "PROJECTOR", cleanCsv(getEquipmentId()), cleanCsv(getName()),
                cleanCsv(getBrand()), cleanCsv(getStatus()), cleanCsv(getConditionNote()),
                String.valueOf(lumens), cleanCsv(portType));
    }
}
