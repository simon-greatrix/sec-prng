package prng.config;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;

public class Screen {
    public static void main(String[] args) {
        ConfigTable ct = new ConfigTable();
        JFrame frame = new JFrame(ct.resources.getString("PRNG_CONFIG_WINDOW"));
        JTable table = new JTable(ct);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        JScrollPane scrollPane = new JScrollPane(table);
        frame.getContentPane().add(scrollPane);
        frame.setSize(640, 512);
        frame.setVisible(true);
    }
}
