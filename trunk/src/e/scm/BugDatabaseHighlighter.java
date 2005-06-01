package e.scm;

import e.gui.*;
import e.ptextarea.*;
import e.util.*;
import java.io.*;
import java.util.regex.*;

/**
 * Links to a bug database from check-in comments.
 */
public class BugDatabaseHighlighter extends PHyperlinkTextStyler {
    public BugDatabaseHighlighter(PTextArea textArea) {
        super(textArea, "(?i)\\b((?:(Sun |bug |D)([0-9]+)))");
    }
    
    public void hyperlinkClicked(CharSequence linkText, Matcher matcher) {
        String url = urlForMatcher(matcher);
        try {
            BrowserLauncher.openURL(url);
        } catch (IOException ex) {
            SimpleDialog.showDetails(null, "Bug Database Link", ex);
        }
    }
    
    public boolean isAcceptableMatch(CharSequence line, Matcher matcher) {
        return true;
    }
    
    public String makeToolTip(Matcher matcher) {
        return urlForMatcher(matcher);
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
    
    public void addKeywordsTo(java.util.Collection<String> collection) {
    }
}
