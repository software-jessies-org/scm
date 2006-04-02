package e.scm;

public class BackEndTask {
  private String message;
  private StatusReporter statusReporter;
  private int taskHandle;
  private int workers;
  
  public BackEndTask(String message, StatusReporter statusReporter) {
    this.message = message;
    this.statusReporter = statusReporter;
    taskHandle = statusReporter.startTask(message);
    workers = 0;
  }
  
  public synchronized void attachWorker() {
    ++workers;
  }
  
  public synchronized void detachWorker() {
    --workers;
    if (workers != 0) {
      return;
    }
    if (isOutdated()) {
      // This result is outdated. Forget it.
      // Note that startAnimation and stopAnimation don't nest, so it's correct that we don't call progressIndicator.stopAnimation in this case.
      return;
    }
    statusReporter.finishTask();
  }
  
  public boolean isOutdated() {
    return statusReporter.isTaskOutdated(taskHandle);
  }
  
  public void reportException(Exception ex) {
    statusReporter.reportException(message, ex);
  }
}
