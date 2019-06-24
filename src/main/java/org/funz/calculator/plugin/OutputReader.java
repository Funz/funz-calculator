package org.funz.calculator.plugin;

/** Thread to follow progress of a calculation.
 * @author richet
 */
public abstract class OutputReader extends Thread {
    public String _information;
    public volatile boolean _stopMe = false;
    public DefaultCodeLauncher _launcher;
    
    final Object waiter = new Object();

    public OutputReader(DefaultCodeLauncher launcher) {
        _launcher=launcher;
    }

    void askToStop() {
        _stopMe = true;
        synchronized (this) {
            notify();
        }
        interrupt();
    }
}
