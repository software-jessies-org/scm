package e.scm;

import java.awt.*;
import javax.swing.*;

public abstract class BlockingWorker extends Thread {
    private Component component;
    private String message;
    private Exception caughtException;
    
    public BlockingWorker(Component component, String message) {
        this.component = component;
        this.message = message;
        start();
    }
    
    public final void run() {
        WaitCursor waitCursor = new WaitCursor(component, message);
        try {
            waitCursor.start();
            work();
        } catch (Exception ex) {
            caughtException = ex;
        } finally {
            waitCursor.stop();
        }
        SwingUtilities.invokeLater(new Runnable() {
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
        ex.printStackTrace();
    }
}
