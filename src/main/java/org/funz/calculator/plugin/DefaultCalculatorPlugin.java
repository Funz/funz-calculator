package org.funz.calculator.plugin;

import java.io.File;
import java.util.LinkedList;
import java.util.Properties;

/**
 * Abstract plugin implementation with default behavior.
 */
public class DefaultCalculatorPlugin implements CalculatorPlugin {

    DataChannel _channel;
    private LinkedList _tvalues;
    Properties _variables;
    public boolean debug = System.getProperty("debug") != null && (System.getProperty("debug").equals("true") || System.getProperty("debug").equals("yes"));
    String code, user;
    boolean _secured = false;

    public DefaultCalculatorPlugin() {
    }

    public void setSecure(boolean sec) {
        _secured = sec;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public void setUser(String user) {
        this.user = user;
    }
    CodeLauncher launcher;

    public CodeLauncher createCodeLauncher(Properties variables, DataChannel channel) {
        _variables = variables;
        _channel = channel;
        return new DefaultCodeLauncher(this);
    }

    public DataChannel getDataChannel() {
        return _channel;
    }

    protected void setDataChannel(DataChannel dc) {
        _channel = dc;
    }

    protected LinkedList getTValues() {
        return _tvalues;
    }

    protected Properties getVariables() {
        return _variables;
    }

    protected void setVariables(Properties vars) {
        _variables = vars;
    }

    public void prepareNewProject(LinkedList tvalues) {
        _tvalues = tvalues;
    }
}
