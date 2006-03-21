package e.scm;

import e.util.*;
import java.awt.*;
import javax.swing.*;
import javax.swing.border.*;

public class RevisionListCellRenderer extends JPanel implements ListCellRenderer {
    private JLabel topLabel = new JLabel(" ");
    private JLabel bottomLabel = new JLabel(" ");
    private MutableMatteBorder matteBorder;
    
    public RevisionListCellRenderer() {
        super(new BorderLayout());
        setBackground(UIManager.getColor("List.background"));
        
        matteBorder = new MutableMatteBorder(0, 0, 1, 0, Color.RED);
        Border margin = new EmptyBorder(2, 2, 4, 8);
        setBorder(new CompoundBorder(matteBorder, margin));
        
        setOpaque(true);
        add(topLabel, BorderLayout.NORTH);
        add(bottomLabel, BorderLayout.SOUTH);
        
        String fontName = UIManager.getFont("Label.font").getFontName();
        topLabel.setFont(new Font(fontName, Font.BOLD, 12));
        bottomLabel.setFont(new Font(fontName, Font.PLAIN, 10));
    }
    
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        setEnabled(list.isEnabled());
        
        Revision revision = (Revision) value;
        
        topLabel.setText(revision.date + " " + revision.time);
        bottomLabel.setText("  " + revision.author + " (" + revision.number + ")");
        
        setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
        topLabel.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
        bottomLabel.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
        
        if (isSelected) {
            // FIXME: this isn't the right color, but there doesn't seem to be an appropriate ui default, even on Mac OS.
            matteBorder.setColor(UIManager.getColor("Table.focusCellForeground"));
        } else {
            matteBorder.setColor(UIManager.getColor("List.background"));
        }
        if (isSelected == false && (index % 2 == 0)) {
            setBackground(GuiUtilities.ALTERNATE_ROW_COLOR);
        }
        
        return this;
    }
    
    private static class MutableMatteBorder extends MatteBorder {
        public MutableMatteBorder(int top, int left, int bottom, int right, Color matteColor) {
            super(top, left, bottom, right, matteColor);
        }
        
        public void setColor(Color newColor) {
            this.color = newColor;
        }
    }
}
