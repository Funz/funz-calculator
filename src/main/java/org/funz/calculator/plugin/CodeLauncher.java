package org.funz.calculator.plugin;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

/**
 * Used by Calculator to launch codes. Created from the plgin each time a new
 * case is treated.
 */
public abstract class CodeLauncher extends Thread {

    /**
     * Says whether the run failed or not. If yes getReason() will called.
     *
     * @see #getReason()
     */
    public abstract boolean failed();

    /**
     * Called whenever failed() return true.
     */
    public abstract String getReason();

    /**
     * Offers the launcher the possibility to register the execution parameters
     * prior each run.
     *
     * @param command command line content coming from the configuration xml
     * file
     * @param dir working temporary directory
     * @param files input files
     */
    public abstract void setExecutionParameters(String command, File dir, List files);

    public void setExecutionParameters(String command, File dir, LinkedList files) {
        setExecutionParameters(command, dir, (List)files);
    }

    /**
     * Used to stop running code.
     */
    public abstract void stopRunning();

}
