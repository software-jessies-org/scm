package e.scm;

import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.*;

public class StatusesTableModel extends AbstractTableModel {
    private List statuses;
    
    public StatusesTableModel(List statuses) {
        this.statuses = statuses;
    }
    
    public int getRowCount() {
        return statuses.size();
    }
    
    public int getColumnCount() {
        return 2;
    }
    
    public String getColumnName(int column) {
        switch (column) {
            case 0: return "State";
            case 1: return "Name";
        }
        return "(no column " + column + ")";
    }
    
    public Object getValueAt(int row, int column) {
        switch (column) {
            case 0: return getFileStatus(row).getStateString();
            case 1: return getFileStatus(row).getName();
        }
        return "(" + row + "," + column + ")";
    }
    
    public FileStatus getFileStatus(int row) {
        return (FileStatus) statuses.get(row);
    }
    
    public Object getPrototypeFor(int column) {
        switch (column) {
            case 0: return "M ";
            case 1: return "SomeClass.java";
        }
        return "(no column " + column + ")";
    }
    
    public void initColumnWidths(JTable table) {
        TableColumnModel columns = table.getColumnModel();
        for (int i = 0; i < columns.getColumnCount() - 1; ++i) {
            TableColumn column = columns.getColumn(i);
            int columnIndex = column.getModelIndex();
            
            TableCellRenderer renderer = table.getCellRenderer(0, i);
            Component c = renderer.getTableCellRendererComponent(table, getPrototypeFor(columnIndex), false, false, 0, i);
            final int columnWidth = c.getPreferredSize().width + columns.getColumnMargin();
            if (columnWidth >= 0) {
                column.setPreferredWidth(columnWidth);
                column.setMinWidth(columnWidth);
                column.setMaxWidth(columnWidth);
            }
        }
    }
}
