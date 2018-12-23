package e.scm;

import java.awt.*;
import java.util.*;

/**
 * Similar to BlockingWorker. FIXME: replace both classes with SwingWorker for Java 6.
 */
public abstract class BackEndWorker implements Runnable {
  private BackEndTask task;
  // A field so that it is accessible to an inner class.
  private Exception caughtException;
  
  ArrayList<String> lines = new ArrayList<>();
  ArrayList<String> errors = new ArrayList<>();
  String[] command;
  int status = 0;
  
  public BackEndWorker(BackEndTask task) {
    this.task = task;
    task.attachWorker();
  }
  
  public final void run() {
    try {
      work();
    } catch (Exception ex) {
      caughtException = ex;
    } finally {
      task.detachWorker();
      if (task.isOutdated()) {
        // This result is outdated. Forget it.
        return;
      }
    }
    EventQueue.invokeLater(new Runnable() {
      public void run() {
        if (caughtException != null) {
          task.reportException(caughtException);
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
