public class RevisionTool {
    public static void main(String[] args) {
        for (int i = 0; i < args.length; ++i) {
            String filename = args[i];
            new RevisionWindow(filename);
        }
    }
    private RevisionTool() { }
}
