package org.funz.run;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.funz.Project;
import org.funz.calculator.Calculator;
import org.funz.calculator.network.Session;
import org.funz.log.LogConsole;
import org.funz.run.Client;
import org.funz.run.Client.DataListener;
import org.funz.util.Disk;
import org.funz.util.TimeOut;

/**
 *
 * @author richet
 */
public class NetworkTest {

    public static void main(String args[]) {
        org.junit.runner.JUnitCore.main(NetworkTest.class.getName());
    }
    Calculator calculator;
    List<Session> sessions = new LinkedList<Session>();
    ServerSocket _socket;
    Client ui_client1, ui_client2;
    int i = 0;
    Thread tc;
    volatile boolean running = false;

    @After
    public void tearDown() throws Exception {
        System.err.println("########################################### tearDown ###########################################");
        calculator.askToStop("tearDown");
        for (Session s : sessions) {
            if (s != null) {
                s.askToStop(true, "tearDown");
            }
        }
        tc.join();

        ui_client1.force_disconnect();
        ui_client2.force_disconnect();
        Thread.sleep(Session.RESERVE_TIMEOUT / 2);

        System.err.println("########################################### /tearDown ###########################################");
    }

    @Before
    public void setUp() throws Exception {
        System.err.println("########################################### setUp ###########################################");

        Session.RESERVE_TIMEOUT = 2000;
        Session.REQUEST_TIMEOUT = 10000;

        calculator = new Calculator("file:./dist/calculator.xml", new LogConsole(), new LogConsole());
        calculator.checkAvailability();
        sessions.clear();

        tc = new Thread(new Runnable() {//mimic Calculator NetworkListener/Thread

            public void run() {
                while (!calculator.isAskToStop()) {//mimic Calculator.buildClient
                    try {
                        Session c = null;
                        try {
                            c = new Session(calculator, calculator._serversocket.accept()) {
                                @Override
                                public void log(String message) {
                                    super.log("                                   " + message);
                                }
                            };
                            sessions.add(c);
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                        if (c!=null)
                            c.start();

                        synchronized (calculator) {
                            calculator.wait(5000);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }, "tc" + i);
        tc.start();

        Thread.sleep(Session.RESERVE_TIMEOUT);

        System.err.print("[client-1] Connection 1 starting");
        ui_client1 = new Client("localhost", calculator._port) {
            public void log(String string) {
                System.err.println("[client-1] " + string);
            }

            @Override
            public synchronized String getActivity() throws Exception {
                System.err.println("111111111111111111111111111111111 getActivity");
                return super.getActivity(); //To change body of generated methods, choose Tools | Templates.
            }

        };
        System.err.print("[client-1] Connection 1 established: ");
        System.err.println(ui_client1.isConnected());

        System.err.print("[client-2] Connection 2 starting");
        ui_client2 = new Client("localhost", calculator._port) {
            public void log(String string) {
                System.err.println("[client-2] " + string);
            }

            @Override
            public synchronized String getActivity() throws Exception {
                System.err.println("2222222222222222222222222222222222 getActivity");
                return super.getActivity(); //To change body of generated methods, choose Tools | Templates.
            }
        };
        System.err.print("[client-2] Connection 2 established: ");
        System.err.println(ui_client2.isConnected());

        System.err.println("########################################### /setUp ###########################################");

    }

    @Test
    public void testListening() throws Exception {

        for (int j = 0; j < 10; j++) {
            Thread.sleep(1000);

            System.err.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> UI1: " + ui_client1.getActivity());
            Client.CalculatorInfo info1 = new Client.CalculatorInfo();
            assert ui_client1.getInfo(info1);
            System.err.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> UI1: " + info1);
            System.err.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> UI2: " + ui_client2.getActivity());
            Client.CalculatorInfo info2 = new Client.CalculatorInfo();
            assert ui_client2.getInfo(info2);
            System.err.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> UI2: " + info2);
            System.err.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> C1: " + calculator.getActivity());

        }

        // no more remaining thread
    }

    @Test
    public void testListeningUnplug() throws Exception, Throwable {

        for (int j = 0; j < 10; j++) {
            Thread.sleep(1000);

            System.err.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> UI1: " + ui_client1.getActivity());
            Client.CalculatorInfo info1 = new Client.CalculatorInfo();
            assert ui_client1.getInfo(info1) : "Failed getInfo at " + j;
            System.err.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> UI1: " + info1);
            System.err.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> UI2: " + ui_client2.getActivity());
            Client.CalculatorInfo info2 = new Client.CalculatorInfo();
            assert ui_client2.getInfo(info2) : "Failed getInfo at " + j;
            System.err.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> UI2: " + info2);
            System.err.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> C1: " + calculator.getActivity());

        }

        ui_client2.finalize();

        for (int j = 0; j < 20; j++) {
            Thread.sleep(1000);

            System.err.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> UI1: " + ui_client1.getActivity());
            Client.CalculatorInfo info1 = new Client.CalculatorInfo();
            assert ui_client1.getInfo(info1) : "Failed getInfo at " + j;
            System.err.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> UI1: " + info1);
        }

        // no more remaining thread
    }

    // @Test no longer working because of readRequest time limit
    public void testReserved() throws Exception {
        Project prj = new Project("test");
        prj.setCode("R");

        if (!ui_client1.reserveTimeOut(Session.RESERVE_TIMEOUT, prj, new StringBuffer(), new StringBuffer())) {
            throw new Exception("!!! Impossible to first reserve: " + ui_client1.getReason());
        }
        for (int j = 0; j < 10; j++) {
            Thread.sleep(1000);

            System.err.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> UI1: " + ui_client1.getActivity());
            Client.CalculatorInfo info1 = new Client.CalculatorInfo();
            assert ui_client1.getInfo(info1) : "Failed getInfo at " + j;
            System.err.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> UI1: " + info1);
            System.err.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> UI2: " + ui_client2.getActivity());
            Client.CalculatorInfo info2 = new Client.CalculatorInfo();
            assert ui_client2.getInfo(info2) : "Failed getInfo at " + j;
            System.err.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> UI2: " + info2);
            System.err.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> C1: " + calculator.getActivity());

        }

        // no more remaining thread
    }

    @Test
    public void testCase() throws Exception {
        Project prj = new Project("test");
        prj.setCode("R");

        System.err.println("------------------------------------------------[CLIENT]>reserve " + ui_client1.reserve(prj, new StringBuffer(), new StringBuffer()));

        assert ui_client1.isConnected() : "Client not connected !";
        assert ui_client1.isReserved() : "Client not reserved !";

        System.err.println("------------------------------------------------[CLIENT]>newCase " + ui_client1.newCase(new HashMap()));

        System.err.println("------------------------------------------------[CLIENT]>putFile " + ui_client1.putFile(new File("src/test/samples/novar.R"), new File("./src/test/samples/")));

        System.err.println("------------------------------------------------[CLIENT]>execute " + ui_client1.execute("R", new DataListener() {
            public void informationLineArrived(String str) {
                System.err.println("[CLIENT]>execute>info " + str);
            }
        }));

        System.err.println("------------------------------------------------[CLIENT]>archiveResults " + ui_client1.archiveResults());

        System.err.println("------------------------------------------------[CLIENT]>transferArchive " + ui_client1.transferArchive(new File("."), new Disk.ProgressObserver() {
            public void newDataBlock(int i) {
                System.err.println("------------------------------------------------[CLIENT]>transferArchive>newDataBlock " + i);
            }

            public void setTotalSize(long l) {
                System.err.println("------------------------------------------------[CLIENT]>transferArchive>setTotalSize " + l);
            }
        }));

        System.err.println("------------------------------------------------[CLIENT]>unreserve " + ui_client1.unreserve());

        assert !ui_client1.isReserved() : "Client still reserved: "+ui_client1.getReason();

        File results = new File("results.zip");
        assert results.exists();
        results.delete();

        for (int j = 0; j < 10; j++) {
            Thread.sleep(1000);

            System.err.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> UI1: " + ui_client1.getActivity());
            Client.CalculatorInfo info1 = new Client.CalculatorInfo();
//            assert ui_client1.getInfo(info1);
//            System.err.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> UI1: " + info1);
            System.err.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> UI2: " + ui_client2.getActivity());
            Client.CalculatorInfo info2 = new Client.CalculatorInfo();
//            assert ui_client2.getInfo(info2);
//            System.err.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> UI2: " + info2);
            System.err.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> C1: " + calculator.getActivity());

        }

        ui_client1.disconnect();

        assert !ui_client1.isConnected() : "Client still connected !";

        // no more remaining thread
    }

}
