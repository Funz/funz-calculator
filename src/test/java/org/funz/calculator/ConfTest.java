package org.funz.calculator;

import java.io.File;
import java.net.ServerSocket;
import java.util.HashMap;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.funz.Project;
import org.funz.calculator.network.Session;
import org.funz.log.LogConsole;
import org.funz.run.Client;
import org.funz.util.ASCII;
import static org.funz.util.ParserUtils.getASCIIFileContent;

/**
 *
 * @author richet
 */
public class ConfTest {

    public static void main(String args[]) {
        org.junit.runner.JUnitCore.main(ConfTest.class.getName());
    }
    Calculator calculator;
    Session calc_client;
    ServerSocket _socket;
    Client ui_client;
    int i = 0;
    Thread loop;

    @After
    public void tearDown() throws Exception {
        calculator.askToStop("tearDown");
        loop.join();
    }

    @Before
    public void setUp() throws Exception {
        Session.RESERVE_TIMEOUT = 2000;

        calculator = new Calculator("file:dist/calculator.xml",new LogConsole(),new LogConsole());

        loop = new Thread(new Runnable() {

            public void run() {
                    calculator.run();
            }
        });
        loop.start();

        System.err.println(calculator.isAvailable());
        while (!calculator.isAvailable()) {
            System.err.print(".");
            Thread.sleep(1000);
        }

        ui_client = new Client("localhost", calculator._port) {
            public void log(String string) {
                System.err.println("[client] " + string);
            }
        };
        System.err.print("Connection established: ");
        System.err.println(ui_client.isConnected());
    }

    @Test
    public void testNoConfUpdated() throws Exception {
        if (new File("results.zip").exists()) {
            assert new File("results.zip").delete() : "(!) Cannot cleanup results.zip";
        }

        Project prj = new Project("test");
        prj.setCode("R");

        assert ui_client.reserveTimeOut(5000,prj, new StringBuffer(), new StringBuffer()) : "Failed reserve";

        assert ui_client.newCase(new HashMap()) : "Failed newCase";

        assert ui_client.putFile(new File("./src/test/resources/long.R"), new File("./src/test/resources")) : "Failed putFile";

        assert ui_client.execute("R", new Client.DataListener() {

            public void informationLineArrived(String string) {
                System.err.println(">>> " + string);
            }
        }) : "Failed execute";

        assert ui_client.archiveResults() : "Failed archiveResults";

        assert ui_client.transferArchive(new File("."), null) : "Failed transferArchive";

        assert new File("results.zip").exists() : "(!) Failed results.zip";

        ui_client.disconnect();

    }

    @Test
    public void testConfUpdated() throws Exception {
        if (new File("results.zip").exists()) {
            assert new File("results.zip").delete() : "(!) Cannot cleanup results.zip";
        }

        Project prj = new Project("test");
        prj.setCode("R");

        assert ui_client.reserveTimeOut(5000, prj, new StringBuffer(), new StringBuffer()) : "Failed reserve";

        assert ui_client.newCase(new HashMap()) : "Failed newCase";

        assert ui_client.putFile(new File("./src/test/resources/long.R"), new File("./src/test/resources")) : "Failed putFile";

        Thread t = new Thread(new Runnable() {

            public void run() {
                try {
                    if (!ui_client.execute("R", new Client.DataListener() {

                        public void informationLineArrived(String string) {
                            System.err.println(">>> " + string);
                        }
                    })) {
                        System.err.println("Failed execute");
                    }

                    if (!ui_client.archiveResults()) {
                        System.err.println("Failed archiveResults");
                    }

                    if (!ui_client.transferArchive(new File("."), null)) {
                        System.err.println("Failed transferArchive");
                    }

                    if (!new File("results.zip").exists()) {
                        System.err.println("Failed create results.zip");
                    }

                    ui_client.disconnect();

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        t.start();

        Thread.sleep(1000);

        System.err.println("Update conf NOW !");
        ASCII.saveFile(new File("dist","calculator.xml"), getASCIIFileContent(new File("dist","calculator.xml")).replace("$$", "$$$"));

        Thread.sleep(15000);
        t.join();

        assert calculator.isAvailable() : "Calculator not avaible after update";
    }
}
