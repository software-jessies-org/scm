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
        super(textArea, "(?i)\\b((?:(?:D(?:efect)?|(?:Bug[z ]?(?:ID)?)))\\s*[#]?([0-9]+))");
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
        return "http://try4.fogbugz.com/default.asp?pg=pgEditBug&command=view&ixBug=" + matcher.group(2);
    }
}
