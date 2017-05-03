package prng.config;

import java.awt.Component;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;

public class Screen {
    public static void main(String[] args) {
        ConfigTable ct = new ConfigTable();
        JFrame frame = new JFrame(ct.resources.getString("PRNG_CONFIG_WINDOW"));
        JTable table = new JTable(ct);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        
        // pre-size columns of table
        TableColumnModel columnModel = table.getColumnModel();
        for (int column = 0; column < table.getColumnCount(); column++) {
            int width = 15; // Min width
            for (int row = 0; row < table.getRowCount(); row++) {
                TableCellRenderer renderer = table.getCellRenderer(row, column);
                Component comp = table.prepareRenderer(renderer, row, column);
                width = Math.max(comp.getPreferredSize().width +10 , width);
            }
            columnModel.getColumn(column).setPreferredWidth(width);
        }
        
        JScrollPane scrollPane = new JScrollPane(table);
        frame.getContentPane().add(scrollPane);
        frame.setSize(640, 512);
        frame.setVisible(true);
    }
}
