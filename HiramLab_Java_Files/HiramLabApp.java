import javax.swing.*;

/**
 * HIRAMLAB: An Automated Campus Equipment Borrowing and Maintenance Sorting System
 * Complete Java OOP + Swing GUI Application (DCIT 50 Final Project)
 *
 * Compile : javac -encoding UTF-8 *.java
 * Run     : java HiramLabApp
 */
public class HiramLabApp {
    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            } catch (Exception ignored) {}
            UITheme.installDefaults();

            HiramLabManager manager = new HiramLabManager();
            manager.loadAllData(); // Loads saved CSV files or initializes seed data on first run
            new LoginFrame(manager).setVisible(true);
        });
    }
}
