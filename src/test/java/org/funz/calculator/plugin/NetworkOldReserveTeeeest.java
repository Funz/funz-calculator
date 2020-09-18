package org.funz.calculator.plugin;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.LinkedList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.funz.Project;
import org.funz.calculator.Calculator;
import org.funz.calculator.OldClient;
import org.funz.calculator.network.Session;
import org.funz.log.LogConsole;
import org.funz.util.TimeOut;

/**
 *
 * @author richet
 */
public class NetworkOldReserveTeeeest {

    public static void main(String args[]) {
        org.junit.runner.JUnitCore.main(NetworkOldReserveTeeeest.class.getName());
    }
    Calculator calculator;
    List<Session> calc_clients = new LinkedList<Session>();
    ServerSocket _socket;
    OldClient ui_client1, ui_client2;
    int i = 0;
    Thread tc;
    volatile boolean running = false;

    @After
    public void tearDown() throws Exception {
        System.err.println("########################################### tearDown ###########################################");
        calculator.askToStop("tearDown");
        for (Session calc_client : calc_clients) {
            if (calc_client != null) {
                calc_client.askToStop(true,"tearDown");
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

        calculator = new Calculator("file:dist/calculator.xml", new LogConsole(),new LogConsole());
        calculator.checkAvailability();

        tc = new Thread(new Runnable() {//mimic Calculator NetworkListener/Thread

            public void run() {
                while (!calculator.isAskToStop()) {//mimic Calculator.buildClient
                    try {
                        try {
                            calc_clients.add(new Session(calculator, calculator._serversocket.accept()) {
                                @Override
                                public void log(String message) {
                                    super.log("                                   " + message);
                                }
                            });
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                        calc_clients.get(calc_clients.size() - 1).start();

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
        ui_client1 = new OldClient("localhost", calculator._port) {
            public void log(String string) {
                System.err.println("[client-1] " + string);
            }
        };
        System.err.print("[client-1] Connection 1 established: ");
        System.err.println(ui_client1.isConnected());

        System.err.print("[client-2] Connection 2 starting");
        ui_client2 = new OldClient("localhost", calculator._port) {
            public void log(String string) {
                System.err.println("[client-2] " + string);
            }
        };
        System.err.print("[client-2] Connection 2 established: ");
        System.err.println(ui_client2.isConnected());

        System.err.println("########################################### /setUp ###########################################");

    }

    // @Test
    public void testReserveUnreserve() throws Exception {
        System.err.println("+++++++++++++++++++++++++++++++++++++++++++++++++++ testReserveUnreserve");

        Project prj = new Project("test");
        prj.setCode("R");

        if (!ui_client1.reserve(prj, new StringBuffer(), new StringBuffer())) {
            throw new Exception("!!! Impossible to first reserve: " + ui_client1.getReason());
        }

        Thread.sleep(Session.RESERVE_TIMEOUT / 2);

        System.err.println("################################# unreserve " + Session.RESERVE_TIMEOUT / 2);
        if (!ui_client1.unreserve()) {
            throw new Exception("!!! Impossible to unreserve: " + ui_client1.getReason());
        }
        ui_client1.disconnect();
        //assert !ui_client1.isConnected() : "Failed to disconnect"; bug : scket is not null and remains taged as "connected" (before)
    }

    // @Test
    public void test2ReserveUnreserve() throws Exception {
        System.err.println("+++++++++++++++++++++++++++++++++++++++++++++++++++ test2ReserveUnreserve");

        Project prj = new Project("test");
        prj.setCode("R");

        if (!ui_client1.reserve(prj, new StringBuffer(), new StringBuffer())) {
            throw new Exception("!!! Impossible to first reserve: " + ui_client1.getReason());
        }

        System.err.println("sleeping " + Session.RESERVE_TIMEOUT / 2);
        Thread.sleep(Session.RESERVE_TIMEOUT / 2);

        if (!ui_client1.unreserve()) {
            throw new Exception("!!! Impossible to unreserve: " + ui_client1.getReason());
        }

        Thread.sleep(Session.RESERVE_TIMEOUT);

        ui_client1.disconnect();

        Thread.sleep(Session.RESERVE_TIMEOUT);

        if (!ui_client2.reserve(prj, new StringBuffer(), new StringBuffer())) {
            throw new Exception("!!! Impossible to first reserve 2nd client: " + ui_client2.getReason());
        }

        System.err.println("sleeping " + Session.RESERVE_TIMEOUT / 2);
        Thread.sleep(Session.RESERVE_TIMEOUT / 2);

        if (!ui_client2.unreserve()) {
            throw new Exception("!!! Impossible to unreserve: " + ui_client2.getReason());
        }

        ui_client1.disconnect();
        assert !ui_client1.isConnected() : "Failed to disconnect";

        ui_client2.disconnect();
        assert !ui_client2.isConnected() : "Failed to disconnect";
    }

    // @Test
    public void testReserveNOUnreserve() throws Exception {
        System.err.println("+++++++++++++++++++++++++++++++++++++++++++++++++++ testReserveNOUnreserve");

        Project prj = new Project("test");
        prj.setCode("R");

        if (!ui_client1.reserve(prj, new StringBuffer(), new StringBuffer())) {
            throw new Exception("!!! Impossible to first reserve: " + ui_client1.getReason());
        }
        assert ui_client1.isReserved() : "Not reserved ! (1)";
        //assert ui_client1.getActivity().contains("reserved by") : "Not well reserved";

        System.err.println("sleeping 2x" + Session.RESERVE_TIMEOUT);
        Thread.sleep(2 * Session.RESERVE_TIMEOUT);

        //assert ui_client1.getActivity().contains("idle") : "Not well reserved";
        try {
            new TimeOut("ui_client1.unreserve") {

                @Override
                protected Object defaultResult() {
                    return null;
                }

                @Override
                protected Object command() {
                    try {
                        assert !ui_client1.unreserve() : "!!! Problem : still possible to unreserve: " + ui_client1.getReason();
                    } catch (Exception ex) {
                        assert false : ex.getMessage();
                    }
                    return null;
                }
            }.execute(5000);
            assert false : "No timeout";
        } catch (TimeOut.TimeOutException te) {
        }

        ui_client1.force_disconnect();
        assert !ui_client1.isConnected() : "Failed to disconnect";
    }

    // @Test
    public void testReserveNOUnreserveTimeout() throws Exception {
        System.err.println("+++++++++++++++++++++++++++++++++++++++++++++++++++ testReserveNOUnreserveTimeout");
        Project prj = new Project("test");
        prj.setCode("R");

        if (!ui_client1.reserve(prj, new StringBuffer(), new StringBuffer())) {
            throw new Exception("Impossible to first reserve: " + ui_client1.getReason());
        }
        assert ui_client1.isReserved() : "Not reserved ! (1)";
        //assert ui_client1.getActivity().contains("reserved by") : "Not well reserved";

        Thread.sleep(Session.RESERVE_TIMEOUT / 2);

        System.err.println("sleeping 2x" + Session.RESERVE_TIMEOUT);
        Thread.sleep(2 * Session.RESERVE_TIMEOUT);

        //assert ui_client1.getActivity().contains("idle") : "Not well reserved";
        if (!ui_client2.reserve( prj, new StringBuffer(), new StringBuffer())) {
            throw new Exception("!!! Impossible to next reserve: " + ui_client2.getReason());
        }

        assert ui_client2.isReserved() : "No reserved ! (2)";
        //assert ui_client2.getActivity().contains("reserved by") : "Not well reserved";

        Thread.sleep(Session.RESERVE_TIMEOUT / 2);

        if (ui_client1.unreserve()) {
            throw new Exception("!!! Problem : still possible to unreserve: " + ui_client1.getReason());
        }

        ui_client1.disconnect();
        assert !ui_client1.isConnected() : "Failed to disconnect (1)";

        ui_client2.disconnect();
        assert !ui_client2.isConnected() : "Failed to disconnect (2)";
    }

    // @Test
    public void testReserveNOUnreserveTimeoutFail() throws Exception {
        System.err.println("+++++++++++++++++++++++++++++++++++++++++++++++++++ testReserveNOUnreserveTimeoutFail");

        Project prj = new Project("test");
        prj.setCode("R");

        if (!ui_client1.reserve(prj, new StringBuffer(), new StringBuffer())) {
            throw new Exception("Impossible to first reserve: " + ui_client1.getReason());
        }

        System.err.println("sleeping .5x" + Session.RESERVE_TIMEOUT);
        Thread.sleep(Session.RESERVE_TIMEOUT / 2);

        if (ui_client2.reserve(prj, new StringBuffer(), new StringBuffer())) {
            throw new Exception("!!! Problem : possible to next reserve: " + ui_client2.getReason());
        }
        Thread.sleep(Session.RESERVE_TIMEOUT / 2);

        if (ui_client1.unreserve()) {
            throw new Exception("!!! Problem : still possible to unreserve: " + ui_client1.getReason());
        }

        ui_client1.force_disconnect();
        assert !ui_client1.isConnected() : "Failed to disconnect";
    }
}
