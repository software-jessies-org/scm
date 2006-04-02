package e.scm;

import e.gui.*;
import java.awt.*;
import javax.swing.*;

public class StatusReporter {
  private Component owner;
  private JAsynchronousProgressIndicator progressIndicator = new JAsynchronousProgressIndicator();
  private JLabel statusLine = new JLabel();
  private int currentTaskHandle = 0;
  
  public StatusReporter(Component owner) {
    this.owner = owner;
    setMessage("");
  }
  
  public void addToPanel(JPanel statusPanel) {
    statusPanel.add(progressIndicator, BorderLayout.WEST);
    statusPanel.add(statusLine, BorderLayout.CENTER);
  }
  
  private void setMessage(String message) {
    if (message.length() == 0) {
      message = " ";
    }
    statusLine.setText(message);
  }
  
  public void finishTask() {
    progressIndicator.stopAnimation();
    setMessage ("");
  }
  
  public int startTask(String message) {
    setMessage (message);
    progressIndicator.startAnimation();
    return ++currentTaskHandle;
  }
  
  public boolean isTaskOutdated(int taskHandle) {
    return taskHandle != currentTaskHandle;
  }
  
  /**
   * Invoked if 'work' threw an exception. The default implementation
   * simply prints the stack trace. This method is run on the event
   * dispatch thread, so you can safely modify the UI here.
   */
  public void reportException(String message, Exception ex) {
    SimpleDialog.showDetails(owner, message, ex);
  }
}
