public abstract class RevisionListWorker extends BackEndWorker {
  protected String filePath;
  private JList listForErrors;
  
  public RevisionListWorker(String filePath, JList listForErrors) {
    super("Getting list of revisions...");
    this.filePath = filePath;
    this.listForErrors = listForErrors;
  }
  
  public void work() {
    command = backEnd.getLogCommand(filePath);
    status = ProcessUtilities.backQuote(backEnd.getRoot(), command, lines, errors);
  }
  
  private RevisionListModel parseRevisions() {
    RevisionListModel revisions = backEnd.parseLog(lines);
    if (backEnd.isLocallyModified(filePath)) {
      revisions.addLocalRevision(Revision.LOCAL_REVISION);
    }
    return revisions;
  }
  
  public void finish() {
    if (status != 0 || errors.size() > 0) {
      ScmUtilities.showToolError(listForErrors, errors, command, status);
      return;
    }
    
    EventQueue.invokeLater(new Runnable() {
      public void run() {
        reportFileRevisions(parseRevisions());
      }
    });
  }
  
  public abstract void reportFileRevisions(RevisionListModel fileRevisions);
}
