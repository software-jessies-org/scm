package e.scm;

import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.*;

public class StatusesTableModel extends AbstractTableModel {
    private Boolean[] isIncluded;
    private List statuses;
    
    public StatusesTableModel(List statuses) {
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
            case 0: return "Included";
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
        return (FileStatus) statuses.get(row);
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
    
    public void includeFiles(List/*<FileStatus>*/ fileStatuses) {
        includeFilenames(fileStatusListToFilenameList(fileStatuses));
    }
    
    public void includeFilenames(List/*<String>*/ names) {
        for (int i = 0; i < getRowCount(); ++i) {
            String name = getFileStatus(i).getName();
            if (names.contains(name)) {
                setValueAt(Boolean.TRUE, i, 0);
            }
        }
    }
    
    public List/*<FileStatus>*/ getIncludedFiles() {
        ArrayList result = new ArrayList();
        for (int i = 0; i < getRowCount(); ++i) {
            if (isIncluded[i].booleanValue()) {
                result.add(getFileStatus(i));
            }
        }
        return result;
    }
    
    public List/*<String>*/ getIncludedFilenames() {
        return fileStatusListToFilenameList(getIncludedFiles());
    }
    
    /**
     * Converts a list of FileStatus instances to a list of String instances
     * by invoking getName on each FileStatus.
     */
    private List/*<String>*/ fileStatusListToFilenameList(List/*<FileStatus>*/ files) {
        ArrayList names = new ArrayList();
        for (int i = 0; i < files.size(); ++i) {
            FileStatus fileStatus = (FileStatus) files.get(i);
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
