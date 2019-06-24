package org.funz.calculator.plugin;

import java.util.LinkedList;
import java.util.Properties;

/** Calculator plugin interface. */
public interface CalculatorPlugin {

    /** Called for each case.
     * @param variables variable set for current launch
     * @param channel data back sending channel
     * @see CodeLauncher
     */
    public CodeLauncher createCodeLauncher(Properties variables, DataChannel channel);

    /** Called whenever a new project starts to run.
     * @param tvalues TValue list comming from Project
     */
    public void prepareNewProject(LinkedList tvalues);

    public DataChannel getDataChannel();

    public void setCode(String code);

    public void setUser(String user);
}
