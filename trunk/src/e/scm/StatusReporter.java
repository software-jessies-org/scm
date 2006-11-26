package e.scm;

import e.gui.*;
import java.awt.*;
import javax.swing.*;

public class StatusReporter {
    private Component owner;
    private JAsynchronousProgressIndicator progressIndicator = new JAsynchronousProgressIndicator();
    private JPanel progressPanel;
    private JLabel statusLine = new JLabel();
    private int currentTaskHandle = 0;
    
    public StatusReporter(Component owner) {
        this.owner = owner;
        setMessage("");
        
        JPanel spacer = new JPanel();
        spacer.setSize(new Dimension(8, 1));
        progressPanel = new JPanel(new BorderLayout());
        progressPanel.add(progressIndicator, BorderLayout.CENTER);
        progressPanel.add(spacer, BorderLayout.EAST);
        
        progressPanel.setVisible(false);
    }
    
    public void addToPanel(JPanel statusPanel) {
        statusPanel.add(progressPanel, BorderLayout.WEST);
        statusPanel.add(statusLine, BorderLayout.CENTER);
    }
    
    public void setMessage(String message) {
        if (message.length() == 0) {
            message = " ";
        }
        statusLine.setText(message);
    }
    
    public void finishTask() {
        progressIndicator.stopAnimation();
        progressPanel.setVisible(false);
        setMessage("");
    }
    
    public int startTask(String message) {
        setMessage(message);
        progressPanel.setVisible(true);
        progressIndicator.startAnimation();
        return ++currentTaskHandle;
    }
    
    public boolean isTaskOutdated(int taskHandle) {
        return taskHandle != currentTaskHandle;
    }
    
    public void setProgressValue(final int value, final int maximum) {
        // FIXME!
        //progressBar.setValue(value);
        //progressBar.setMaximum(maximum);
        //progressBar.setIndeterminate(false);
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
