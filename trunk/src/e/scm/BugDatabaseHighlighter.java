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
 * 
 * Examples:
 *   Sun Java bug parade: Sun 6227617.
 *   RFCs: RFC2229.
 */
public class BugDatabaseHighlighter extends RegularExpressionStyleApplicator {
    public BugDatabaseHighlighter(PTextArea textArea) {
        // Group 1 - the text to be underlined.
        // Group 2 - the vendor, used to choose a URL template.
        // Group 3 - the id, inserted into the template.
        super(textArea, "(?i)\\b((?:(Sun |bug |RFC ?|D)([0-9]+)))", PStyle.HYPERLINK);
    }
    
    @Override
    public boolean canApplyStylingTo(PStyle style) {
        return (style == PStyle.NORMAL || style == PStyle.COMMENT);
    }
    
    private String urlForMatcher(Matcher matcher) {
        String id = matcher.group(3);
        String vendor = matcher.group(2);
        if (vendor != null) {
            if (vendor.equals("Sun ")) {
                return "http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=" + id;
            } else if (vendor.equals("bug ")) {
                return "http://fogbugz.jessies.org/fogbugz/default.php?pg=pgEditBug&command=view&ixBug=" + id;
            } else if (vendor.equals("RFC")) {
                return "http://ftp.rfc-editor.org/in-notes/rfc" + id + ".txt";
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
