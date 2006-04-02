package e.scm;

import java.awt.*;
import java.util.*;

/**
 * Similar to BlockingWorker. FIXME: replace both classes with SwingWorker for Java 6.
 */
public abstract class BackEndWorker implements Runnable {
  private String message;
  private StatusReporter statusReporter;
  private Exception caughtException;
  
  ArrayList<String> lines = new ArrayList<String>();
  ArrayList<String> errors = new ArrayList<String>();
  String[] command;
  int status = 0;
  
  private int taskHandle;
  
  public BackEndWorker(final String message, StatusReporter statusReporter) {
    this.message = message;
    this.statusReporter = statusReporter;
    taskHandle = statusReporter.startTask(message);
  }
  
  public final void run() {
    try {
      work();
    } catch (Exception ex) {
      caughtException = ex;
    } finally {
      if (statusReporter.isTaskOutdated(taskHandle)) {
        // This result is outdated. Forget it.
        // Note that startAnimation and stopAnimation don't nest, so it's correct that we don't call progressIndicator.stopAnimation in this case.
        return;
      }
      statusReporter.finishTask();
    }
    EventQueue.invokeLater(new Runnable() {
      public void run() {
        if (caughtException != null) {
          statusReporter.reportException(caughtException);
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
}
