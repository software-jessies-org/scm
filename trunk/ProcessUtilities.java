//package e.util;

import java.io.*;
import java.util.*;

public class ProcessUtilities {
    /**
     * Returns the lines output to standard output by 'command' when run.
     * Lines written to standard error are appended to 'errors'.
     * FIXME: should errors *we* detect go in the return value, or in errors?
     */
    public static String[] backQuote(String[] command, ArrayList errors) {
        ArrayList result = new ArrayList();
        try {
            Process p = Runtime.getRuntime().exec(command);
            p.getOutputStream().close();
            readLinesFromStream(result, p.getInputStream());
            readLinesFromStream(errors, p.getErrorStream());
        } catch (Exception ex) {
            ex.printStackTrace();
            result.add(ex.getMessage());
        } finally {
            return (String[]) result.toArray(new String[result.size()]);
        }
    }

    private static void readLinesFromStream(ArrayList result, InputStream stream) {
        BufferedReader in = null;
        try {
            in = new BufferedReader(new InputStreamReader(stream));
            String line;
            while ((line = in.readLine()) != null) {
                result.add(line);
            }
            in.close();
        } catch (Exception ex) {
            ex.printStackTrace();
            result.add(ex.getMessage());
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ex) {
                    ex.printStackTrace();
                    result.add(ex.getMessage());
                }
            }
        }
    }
    
    /** Prevents instantiation. */
    private ProcessUtilities() {
    }
}
