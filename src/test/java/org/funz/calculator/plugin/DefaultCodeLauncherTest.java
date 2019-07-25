package org.funz.calculator.plugin;

import java.io.File;
import java.util.LinkedList;
import org.junit.Before;
import org.junit.Test;
import org.funz.util.Disk;
import static org.funz.util.ParserUtils.getASCIIFileContent;

/**
 *
 * @author richet
 */
public class DefaultCodeLauncherTest {

    DefaultCodeLauncher launcher;

    public static void main(String args[]) {
        org.junit.runner.JUnitCore.main(DefaultCodeLauncherTest.class.getName());
    }

    @Before
    public void setUp() {
        launcher = new DefaultCodeLauncher(new DefaultCalculatorPlugin());
    }

    @Test
    public void testExecuteOK() throws Exception {
        System.err.println("++++++++++++++++++++++++++++++ testExecuteOK");
        String name = "ok";
        File src = new File("./src/test/resources/" + name + ".R");
        File Rin = new File("tmp", name + ".R");
        Disk.copyFile(src, Rin);
        File Rout = new File("tmp", name + ".Rout");
        File out = new File("tmp","out.txt");
        File err = new File("tmp","err.txt");
        if (out.exists()) {
            out.delete();
        }
        if (err.exists()) {
            err.delete();
        }
        if (Rout.exists()) {
            Rout.delete();
        }

        LinkedList<File> files = new LinkedList<File>();
        files.add(Rin);
        launcher.setExecutionParameters("R CMD BATCH", new File("tmp"), files);

        int ret = launcher.execute();

        assert ret == 0 : "Failed valid execution: return code=" + ret;
        assert out.exists() : "output stream file not found";
        assert err.exists() : "output stream file not found";

        assert Rout.exists() : "R output file not found";
        String contentRout = getASCIIFileContent(Rout);
        assert contentRout.contains(getASCIIFileContent(Rin)) : "bad execution";
    }

    @Test
    public void testExecuteFAIL() throws Exception {
        System.err.println("++++++++++++++++++++++++++++++ testExecuteFAIL");
        String name = "fail";
        File src = new File("./src/test/resources/" + name + ".R");
        File Rin = new File("tmp", name + ".R");
        Disk.copyFile(src, Rin);
        File Rout = new File("tmp", name + ".Rout");
        File out = new File("tmp","out.txt");
        File err = new File("tmp","err.txt");
        if (out.exists()) {
            out.delete();
        }
        if (err.exists()) {
            err.delete();
        }
        if (Rout.exists()) {
            Rout.delete();
        }

        LinkedList<File> files = new LinkedList<File>();
        files.add(Rin);
        launcher.setExecutionParameters("R CMD BATCH", new File("tmp"), files);

        int ret = launcher.execute();
        assert ret != 0 : "Failed bad execution: return code=" + ret;
        assert out.exists() : "output stream file not found";
        assert err.exists() : "output stream file not found";

        assert Rout.exists() : "R output file not found";
        String contentRout = getASCIIFileContent(Rout);
        assert contentRout.contains(getASCIIFileContent(Rin)) : "bad execution";
    }

    @Test
    public void testExecuteERROR() throws Exception {
        System.err.println("++++++++++++++++++++++++++++++ testExecuteERROR");
        String name = "error";
        File src = new File("./src/test/resources/" + name + ".R");
        File Rin = new File("tmp", name + ".R");
        Disk.copyFile(src, Rin);
        File Rout = new File("tmp", name + ".Rout");
        File out = new File("tmp","out.txt");
        File err = new File("tmp","err.txt");
        if (out.exists()) {
            out.delete();
        }
        if (err.exists()) {
            err.delete();
        }
        if (Rout.exists()) {
            Rout.delete();
        }

        LinkedList<File> files = new LinkedList<File>();
        files.add(Rin);
        launcher.setExecutionParameters("R CMD BATCH", new File("tmp"), files);

        int ret = launcher.execute();
        assert ret != 0 : "Failed bad execution: return code=" + ret;
        assert out.exists() : "output stream file not found";
        assert err.exists() : "output stream file not found";

        assert Rout.exists() : "R output file not found";
        String contentRout = getASCIIFileContent(Rout);
        assert contentRout.contains(getASCIIFileContent(Rin)) : "bad execution";
    }
}
