package e.scm;

import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.*;

public class StatusesTableModel extends AbstractTableModel {
    private Boolean[] isIncluded;
    private List<FileStatus> statuses;
    
    public StatusesTableModel(List<FileStatus> statuses) {
        this.statuses = statuses;
        this.isIncluded = new Boolean[statuses.size()];
        for (int i = 0; i < isIncluded.length; ++i) {
            isIncluded[i] = Boolean.FALSE;
        }
    }
    
    public int getRowCount() {
        return statuses.size();
    }
    
    public int getColumnCount() {
        return 3;
    }
    
    public String getColumnName(int column) {
        switch (column) {
            case 0: return null;
            case 1: return "State";
            case 2: return "Name";
        }
        return "(no column " + column + ")";
    }
    
    public Object getValueAt(int row, int column) {
        switch (column) {
            case 0: return isIncluded[row];
            case 1: return getFileStatus(row).getStateString();
            case 2: return getFileStatus(row).getName();
        }
        return "(" + row + "," + column + ")";
    }
    
    public FileStatus getFileStatus(int row) {
        return statuses.get(row);
    }
    
    public Object getPrototypeFor(int column) {
        switch (column) {
            case 0: return Boolean.FALSE;
            case 1: return "M ";
            case 2: return "SomeClass.java";
        }
        return "(no column " + column + ")";
    }
    
    public Class getColumnClass(int column) {
        return (column == 0) ? Boolean.class : String.class;
    }
    
    public boolean isCellEditable(int row, int column) {
        return (column == 0);
    }
    
    public void setValueAt(Object value, int row, int column) {
        assert(column == 0);
        isIncluded[row] = (Boolean) value;
        fireTableCellUpdated(row, column);
    }
    
    public void includeFiles(List<FileStatus> fileStatuses) {
        includeFilenames(fileStatusListToFilenameList(fileStatuses));
    }
    
    public void includeFilenames(List<String> names) {
        for (int i = 0; i < getRowCount(); ++i) {
            String name = getFileStatus(i).getName();
            if (names.contains(name)) {
                setValueAt(Boolean.TRUE, i, 0);
            }
        }
    }
    
    public List<FileStatus> getIncludedFiles() {
        ArrayList<FileStatus> result = new ArrayList<FileStatus>();
        for (int i = 0; i < getRowCount(); ++i) {
            if (isIncluded(i)) {
                result.add(getFileStatus(i));
            }
        }
        return result;
    }
    
    public List<String> getIncludedFilenames() {
        return fileStatusListToFilenameList(getIncludedFiles());
    }
    
    public int getIncludedFileCount() {
        int result = 0;
        for (int i = 0; i < getRowCount(); ++i) {
            if (isIncluded(i)) {
                ++result;
            }
        }
        return result;
    }
    
    /**
     * Converts a list of FileStatus instances to a list of String instances
     * by invoking getName on each FileStatus.
     */
    private List<String> fileStatusListToFilenameList(List<FileStatus> files) {
        ArrayList<String> names = new ArrayList<String>();
        for (FileStatus fileStatus : files) {
            names.add(fileStatus.getName());
        }
        return names;
    }
    
    /**
     * Tests whether the check-in from the currently included files would
     * be non-empty. Used to enable and disable the commit button.
     */
    public boolean isAtLeastOneFileIncluded() {
        for (int i = 0; i < getRowCount(); ++i) {
            if (isIncluded(i)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Returns the index of the first row whose file is included, 0 if no
     * files are currently included.
     */
    public int chooseDefaultSelectedRow() {
        for (int i = 0; i < getRowCount(); ++i) {
            if (isIncluded(i)) {
                return i;
            }
        }
        return 0;
    }
    
    /**
     * Tests whether the file represented by the given row is to be included
     * in the check-in.
     */
    public boolean isIncluded(int row) {
        return isIncluded[row].booleanValue();
    }
    
    private int getRendererWidth(JTable table, TableCellRenderer renderer, int columnIndex, Object value) {
        Component component = renderer.getTableCellRendererComponent(table, value, false, false, 0, columnIndex);
        return component.getPreferredSize().width;
    }
    
    public void initColumnWidths(JTable table) {
        TableColumnModel columns = table.getColumnModel();
        for (int i = 0; i < columns.getColumnCount() - 1; ++i) {
            TableColumn column = columns.getColumn(i);
            int columnIndex = column.getModelIndex();
            
            // Find the renderers for the first cell in the column, and the column header.
            TableCellRenderer cellRenderer = table.getCellRenderer(0, i);
            TableCellRenderer headerRenderer = column.getHeaderRenderer();
            if (headerRenderer == null) {
                headerRenderer = table.getTableHeader().getDefaultRenderer();
            }
            
            // Work out a width wide enough to comfortably contain the header and a typical cell.
            int w1 = getRendererWidth(table, headerRenderer, columnIndex, getColumnName(columnIndex));
            int w2 = getRendererWidth(table, cellRenderer, columnIndex, getPrototypeFor(columnIndex));
            final int columnWidth = (120 * Math.max(w1, w2) / 100) + columns.getColumnMargin();
            
            // Constrain the column such that its initial width will be the desired width, but such that it will be possible to widen the column.
            if (columnWidth >= 0) {
                column.setMinWidth(columnWidth);
                // If we don't set the max width, the column will be too wide.
                column.setMaxWidth(columnWidth);
                // If we don't widen the column again, we won't be able to resize the column.
                // If we make this "too big", the column will be too wide. An overflow in the implementation, I assume.
                column.setMaxWidth(Short.MAX_VALUE);
                // The preferred width must come last.
                column.setPreferredWidth(columnWidth);
            }
            
            // We want the checkbox column tight around the checkbox.
            if (getColumnClass(columnIndex) == Boolean.class) {
                column.setResizable(false);
            }
        }
    }
}
