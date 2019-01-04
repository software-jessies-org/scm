package e.scm;

import e.gui.*;
import java.awt.*;

public abstract class BlockingWorker implements Runnable {
    private Component component;
    private String message;
    private Exception caughtException;
    private WaitCursor waitCursor;
    
    public BlockingWorker(Component component, String message, StatusReporter statusReporter) {
        this.component = component;
        this.message = message;
        waitCursor = new WaitCursor(component, message, statusReporter);
        waitCursor.start();
    }
    
    public final void run() {
        try {
            work();
        } catch (Exception ex) {
            caughtException = ex;
        }
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                waitCursor.stop();
                if (caughtException != null) {
                    reportException(caughtException);
                } else {
                    finish();
                }
            }
        });
    }
    
    public WaitCursor getWaitCursor() {
        return waitCursor;
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
        SimpleDialog.showDetails(null, message, ex);
    }
}
