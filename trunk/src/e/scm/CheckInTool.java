package e.scm;

public class CheckInTool {
    public static void main(String[] arguments) {
        for (int i = 0; i < arguments.length; ++i) {
            System.err.println("Unknown argument '" + arguments[i] + "'.");
        }
        if (arguments.length > 0) {
            System.exit(1);
        }
        new CheckInWindow();
    }

    /**
     * Prevents instantiation. If you want a programmatic interface to
     * incorporate a check-in tool in your own Java program,
     * look at CheckInWindow instead. Or invoke CheckInTool.main
     * explicitly.
     */
    private CheckInTool() {
    }
}
