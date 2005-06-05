package e.scm;

import e.gui.*;
import e.ptextarea.*;
import e.util.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;

/**
 * Links to a bug database from check-in comments.
 */
public class BugDatabaseHighlighter extends RegularExpressionStyleApplicator {
    private static final EnumSet<PStyle> SOURCE_STYLES = EnumSet.of(PStyle.NORMAL, PStyle.COMMENT);
    
    public BugDatabaseHighlighter(PTextArea textArea) {
        // Group 1 - the text to be underlined.
        // Group 2 - the vendor, used to choose a URL template.
        // Group 3 - the id, inserted into the template.
        super(textArea, "(?i)\\b((?:(Sun |bug |D)([0-9]+)))", PStyle.HYPERLINK);
    }
    
    @Override
    public EnumSet<PStyle> getSourceStyles() {
        return SOURCE_STYLES;
    }
    
    private String urlForMatcher(Matcher matcher) {
        String id = matcher.group(3);
        String vendor = matcher.group(2);
        if (vendor != null) {
            if (vendor.equals("Sun ")) {
                return "http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=" + id;
            } else if (vendor.equals("bug ")) {
                return "http://fogbugz.local/fogbugz/default.php?pg=pgEditBug&command=view&ixBug=" + id;
            }
        }
        return "http://woggle/" + id;
    }
    
    @Override
    protected void configureSegment(PTextSegment segment, Matcher matcher) {
        String url = urlForMatcher(matcher);
        segment.setLinkAction(new BugLinkActionListener(url));
        segment.setToolTip(url);
    }
    
    private static class BugLinkActionListener implements ActionListener {
        private String url;
        
        public BugLinkActionListener(String url) {
            this.url = url;
        }
        
        public void actionPerformed(ActionEvent e) {
            try {
                BrowserLauncher.openURL(url);
            } catch (IOException ex) {
                SimpleDialog.showDetails(null, "Bug Database Link", ex);
            }
        }
    }
}
