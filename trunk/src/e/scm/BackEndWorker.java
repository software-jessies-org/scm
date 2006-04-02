/**
 * Similar to BlockingWorker. FIXME: replace both classes with SwingWorker for Java 6.
 */
public abstract class BackEndWorker implements Runnable {
  private String message;
  private Exception caughtException;
  
  ArrayList<String> lines = new ArrayList<String>();
  ArrayList<String> errors = new ArrayList<String>();
  String[] command;
  int status = 0;
  
  private int thisWorkerModCount;
  
  public BackEndWorker(final String message) {
    this.message = message;
    setStatus(message);
    thisWorkerModCount = ++expectedResultModCount;
  }
  
  public final void run() {
    try {
      progressIndicator.startAnimation();
      work();
    } catch (Exception ex) {
      caughtException = ex;
    } finally {
      if (expectedResultModCount != thisWorkerModCount) {
        // This result is outdated. Forget it.
        // Note that startAnimation and stopAnimation don't nest, so it's correct that we don't call progressIndicator.stopAnimation in this case.
        return;
      }
      progressIndicator.stopAnimation();
      clearStatus();
    }
    EventQueue.invokeLater(new Runnable() {
      public void run() {
        if (caughtException != null) {
          reportException(caughtException);
        } else {
          finish();
        }
      }
    });
  }
  
  /**
   * Override this to do the potentially time-consuming work.
   */
  public abstract void work();
  
  /**
   * Override this to do the finishing up that needs to be done back
   * on the event dispatch thread. This will only be invoked if your
   * 'work' method didn't throw an exception (if an exception was
   * thrown, 'reportException' will be invoked instead).
   */
  public abstract void finish();
  
  /**
   * Invoked if 'work' threw an exception. The default implementation
   * simply prints the stack trace. This method is run on the event
   * dispatch thread, so you can safely modify the UI here.
   */
  public void reportException(Exception ex) {
    SimpleDialog.showDetails(RevisionView.this, message, ex);
  }
}
