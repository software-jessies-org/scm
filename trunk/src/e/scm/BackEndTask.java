package e.scm;

public class BackEndTask {
  private String title;
  private StatusReporter statusReporter;
  private int taskHandle;
  private int workers;
  
  public BackEndTask(String title, StatusReporter statusReporter) {
    this.title = title;
    this.statusReporter = statusReporter;
    workers = 0;
  }
  
  public synchronized void attachWorker() {
    if (workers == 0) {
      taskHandle = statusReporter.startTask(title);
    }
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
    statusReporter.reportException(title, ex);
  }
  
  public void changeTitle(String title) {
    this.title = title;
    statusReporter.setMessage(title);
  }
}
