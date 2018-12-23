package e.scm;

import e.util.*;
import java.awt.*;
import javax.swing.*;
import javax.swing.border.*;

public class RevisionListCellRenderer extends JPanel implements ListCellRenderer<Revision> {
    private JLabel topLabel = new JLabel(" ");
    private JLabel bottomLabel = new JLabel(" ");
    private MutableMatteBorder matteBorder;
    
    public RevisionListCellRenderer() {
        super(new BorderLayout());
        setBackground(UIManager.getColor("List.background"));
        
        matteBorder = new MutableMatteBorder(0, 0, 1, 0, Color.RED);
        Border margin = BorderFactory.createEmptyBorder(2, 2, 4, 8);
        setBorder(BorderFactory.createCompoundBorder(matteBorder, margin));
        
        setOpaque(true);
        add(topLabel, BorderLayout.NORTH);
        add(bottomLabel, BorderLayout.SOUTH);
        
        String fontName = UIManager.getFont("Label.font").getFontName();
        topLabel.setFont(new Font(fontName, Font.BOLD, 12));
        bottomLabel.setFont(new Font(fontName, Font.PLAIN, 10));
    }
    
    public Component getListCellRendererComponent(JList<? extends Revision> list, Revision revision, int index, boolean isSelected, boolean cellHasFocus) {
        setEnabled(list.isEnabled());
        
        topLabel.setText(revision.date + " " + revision.time);
        bottomLabel.setText("  " + revision.author + " (" + revision.number + ")");
        
        setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
        topLabel.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
        bottomLabel.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
        
        if (GuiUtilities.isMacOs() && isSelected) {
            // FIXME: this isn't the right color, but there doesn't seem to be an appropriate ui default, even on Mac OS.
            // FIXME: iTunes (which I think I was copying here) seems to behave differently to everything else, so I'm not even sure what color I'm looking for.
            // FIXME: is this actually supposed to be a single-pixel line of the particular table cell's non-selected background color? I think it might be.
            matteBorder.setColor(UIManager.getColor("Table.focusCellForeground"));
        } else {
            matteBorder.setColor(UIManager.getColor("List.background"));
        }
        
        if (isSelected == false) {
            setBackground(GuiUtilities.backgroundColorForRow(index));
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
