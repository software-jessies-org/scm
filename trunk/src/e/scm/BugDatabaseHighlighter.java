package e.scm;

import e.ptextarea.*;
import java.io.*;
import java.util.regex.*;

/**
 * Links to a bug database from check-in comments.
 */
public class BugDatabaseHighlighter extends PHyperlinkTextStyler {
    public BugDatabaseHighlighter(PTextArea textArea) {
        super(textArea, "\\b((?:(?:D(?:efect)?|(?:Bug[z ]?(?:ID)?)))\\s*[#]?([0-9]+))");
    }
    
    public void hyperlinkClicked(CharSequence linkText, Matcher matcher) {
        String url = "http://try4.fogbugz.com/default.asp?pg=pgEditBug&command=view&ixBug=" + matcher.group(2);
        try {
            e.util.BrowserLauncher.openURL(url);
        } catch (IOException ex) {
            // FIXME: Bug 10.
            ex.printStackTrace();
        }
    }
    
    public boolean isAcceptableMatch(CharSequence line, Matcher matcher) {
        return true;
    }
}
