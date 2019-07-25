package org.funz.calculator;

import java.io.File;
import org.junit.After;
import org.junit.Test;
import org.funz.Project;
import org.funz.ProjectController;
import org.funz.api.BatchRun_v1;
import org.funz.api.Funz_v1;
import org.funz.calculator.network.Session;
import org.funz.conf.Configuration;
import static org.funz.doeplugin.DesignConstants.NODESIGNER_ID;
import org.funz.ioplugin.IOPluginInterface;
import org.funz.ioplugin.IOPluginsLoader;
import org.funz.log.Alert;
import org.funz.log.AlertCollector;
import org.funz.log.Log;
import org.funz.log.LogCollector;
import org.funz.log.LogConsole;
import org.funz.parameter.Variable;
import org.funz.parameter.VariableMethods;
import org.funz.script.RMathExpression;
import org.funz.util.Disk;
import org.funz.util.Format;
import static org.funz.util.Format.ArrayMapToMDString;


/**
 *
 * @author richet
 */
public class CalculatorTest {

    static {
        System.setProperty("java.awt.headless", "true");
        System.setProperty("app.home", "../funz-client/distest");
        Alert.setCollector(new AlertCollector() {

            @Override
            public void showInformation(String string) {
                System.err.println("\nIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII\n" + string + "\nIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII\n");
            }

            @Override
            public void showError(String string) {
                System.err.println("\nEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE\n" + string + "\nEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE\n");
            }

            @Override
            public void showException(Exception i) {
                System.err.println("\nXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX\n" + i.getMessage() + "\nXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX\n");
            }
            
            @Override
            public String askInformation(String q) {
                System.err.println("\n????????????????????????????????????????????????\n" + q + "\n????????????????????????????????????????????????\n");
                return "???";
            }
            
            @Override
            public File askPath(String q) {
                System.err.println("\n????????????????????????????????????????????????\n" + q + "\n????????????????????????????????????????????????\n");
                return new File(".");
            }
            
            @Override
            public boolean askYesNo(String q) {
                System.err.println("\n????????????????????????????????????????????????\n" + q + "\n????????????????????????????????????????????????\n");
                return false;
            }
        });
    }

    public static void main(String args[]) {
        org.junit.runner.JUnitCore.main(CalculatorTest.class.getName());
    }
    Thread loop;

    @After
    public void tearDown() throws Exception {
        if (loop != null) {
            loop.join();
        }
    }

    @Test
    public void test1CalculatorNCalculations() throws Exception {
        System.err.println("+++++++++++++++++++++++++++ test1CalculatorNCalculations");
        Session.RESERVE_TIMEOUT = 5000;

        final Calculator calculator = new Calculator("file:calculator.xml", new LogConsole(), new LogConsole());

        loop = new Thread(new Runnable() {

            public void run() {
                try {
                    calculator.runloop();
                } catch (InterruptedException ex) {
                }
            }
        });
        loop.start();

        while (!calculator.isAvailable()) {
            System.err.print(".");
            Thread.sleep(1000);
        }

        runCalculations(4);

        Thread.sleep(5000);

        calculator.askToStop("end of test1CalculatorNCalculations");
        
        Thread.sleep(5000);

        loop.join();
    }

    public static boolean runCalculations(int n) throws Exception {
        System.err.println("++++++++++++++++++++ runCalculations " + n);

        startFunzClient();

        File tmp_in = new File("tmp/branin.R");
        if (tmp_in.exists()) {
            tmp_in.delete();
        }
        Disk.copyFile(new File("../funz-client/src/test/samples/branin.R"), tmp_in);

        IOPluginInterface plugin = IOPluginsLoader.newInstance("R", tmp_in);
        Project prj = ProjectController.createProject(tmp_in.getName(), tmp_in, "R", plugin);

        assert prj.getVariableByName("x1") != null : "Variable x1 not detected";
        assert prj.getVariableByName("x2") != null : "Variable x2 not detected";
        assert prj.getVariableByName("x1").getDefaultValue() == null : "Variable x1 default value not null.";
        assert prj.getVariableByName("x2").getDefaultValue().equals(".5") : "Variable x2 default value not detected.";

        plugin.setFormulaInterpreter(new RMathExpression(tmp_in.getName() + "_" + Configuration.timeDigest(), Configuration.isLog("R") ? new File(prj.getLogDir(), tmp_in.getName() + ".Rlog") : null));
        prj.setMainOutputFunction(plugin.suggestOutputFunctions().get(0));
        prj.setDesignerId(NODESIGNER_ID);

        Variable x1 = prj.getVariableByName("x1");
        x1.setType(Variable.TYPE_REAL);
        String[] x1s = new String[n];
        for (int j = 0; j < x1s.length; j++) {
            x1s[j] = "" + Math.random();
        }
        x1.setValues(VariableMethods.Value.asValueList(x1s));

        Variable x2 = prj.getVariableByName("x2");
        x2.setType(Variable.TYPE_REAL);
        x2.setValues(VariableMethods.Value.asValueList(".1"));

        prj.buildParameterList();

        prj.resetDiscreteCases(null);

        prj.setCases(prj.getDiscreteCases(), null);

        prj.useCache = false;

        BatchRun_v1 batchRun = new BatchRun_v1(null, prj, new File("tmp")) {

            @Override
            public void out(String string, int i) {
                System.err.println("     ooooo " + string);
            }

            @Override
            public void err(String msg, int i) {
                System.err.println("     eeeee " + msg);
            }

            @Override
            public void err(Exception ex, int i) {
                System.err.println("     xxxxx " + ex);
            }
        };

        assert batchRun.runBatch() : "Failed to run batch";

        System.err.println(Format.ArrayMapToMDString(batchRun.getResultsStringArrayMap()));

        stopFunzClient();

        System.err.println("++++++++++++++++++++ /runCalculations");

        return ArrayMapToMDString(batchRun.getResultsStringArrayMap()).length() > 0;
    }

    public static void startFunzClient() {
        System.err.println("+++++++++++++++++++++++++ startFunzClient ");

        System.setProperty("verbosity", "10");
        if (!new File(System.getProperty("app.home"), "quotas.hex").exists()) {
            assert false : "No conf file";
        }
        Configuration c = new Configuration(new File(System.getProperty("app.home"), "quotas.hex"), new LogConsole() {

            @Override
            public void logMessage(LogCollector.SeverityLevel severity, boolean sync, String message) {
                super.logMessage(severity, sync, "                                   " + message); //To change body of generated methods, choose Tools | Templates.
            }

        });
        Funz_v1.init(c, null, null);
        Log.level = 10;
        System.err.println("+++++++++++++++++++++++++ /startFunzClient ");
    }

    public static void stopFunzClient() throws InterruptedException {
        System.err.println("+++++++++++++++++++++++++ stopFunzClient ");
        if (Funz_v1.POOL != null) {
            Funz_v1.POOL.shutdown();
        }
        Thread.sleep(2000);
        System.err.println("+++++++++++++++++++++++++ /stopFunzClient ");
    }

    @Test
    public void testStartStopCalc() throws Exception {
        System.err.println("+++++++++++++++++++++++++++++ testStartStopCalc");
        Session.RESERVE_TIMEOUT = 2000;

        final Calculator calculator = new Calculator("file:calculator.xml", new LogConsole(),new LogConsole());

        loop = new Thread(new Runnable() {

            public void run() {
                try {
                    calculator.runloop();
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }
        });
        loop.start();

        while (!calculator.isAvailable()) {
            System.err.print(".");
            Thread.sleep(1000);
        }

        System.err.println("Calculator available now.");

        Thread.sleep(3000);

        System.err.println("============================== ASK TO STOP ===================================");
        calculator.askToStop("end testStartStopCalc");
    }
}
