package org.funz.calculator.plugin;

import java.io.File;
import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import org.apache.ftpserver.ConnectionConfigFactory;
import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.UserManager;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.usermanager.PropertiesUserManagerFactory;
import org.apache.ftpserver.usermanager.impl.BaseUser;
import org.apache.mina.core.session.IoSession;
import org.funz.calculator.network.Session;

/**
 *
 * @author richet
 */
public class FTPCalculatorTunnel implements CalculatorTunnel, org.apache.ftpserver.ipfilter.SessionFilter {

    String uri;
    FtpServer server;
    String username, auth_host;
    boolean running = false;
    File homedir;
    File users;
    public boolean ip_filter = false;

    public FTPCalculatorTunnel(boolean ip_filter) {
        this.ip_filter = ip_filter;
    }

    public void setParameters(String username, String auth_host, File homedir) {
        this.username = username;
        try {
            this.auth_host = InetAddress.getByName(auth_host).getHostAddress();
        } catch (UnknownHostException ex) {
            this.auth_host = auth_host;
        }
        this.homedir = homedir;
    }

    public void log(String msg) {
        //System.err.println(msg);
    }

    public boolean accept(IoSession is) {
        if (!ip_filter) {
            return true;
        }
        log("IP filter: " + is.getRemoteAddress() + " (authorized=" + auth_host + ")");
        return is.getRemoteAddress().equals(auth_host);
    }

    public void start() {
        try {
            FtpServerFactory serverFactory = new FtpServerFactory();
            ListenerFactory factory = new ListenerFactory();

// set the port of the listener
            int port = 2100 + (int) (100 * Math.random());
            ServerSocket test = null;
            while (true) {
                try {
                    test = new ServerSocket(port);
                    factory.setPort(port);
                    break;
                } catch (BindException e) {
                    port++;
                } catch (IOException e) {
                    port++;
                } finally {
                    if (test!=null)
                        try {
                            test.close();
                        } catch (Exception e) {
                        }
                }
            }

            factory.setSessionFilter(this);
            serverFactory.addListener("default", factory.createListener());

            ConnectionConfigFactory connectionConfigFactory = new ConnectionConfigFactory();
            connectionConfigFactory.setAnonymousLoginEnabled(true);
            serverFactory.setConnectionConfig(connectionConfigFactory.createConnectionConfig());
            PropertiesUserManagerFactory userManagerFactory = new PropertiesUserManagerFactory();
            UserManager um = userManagerFactory.createUserManager();

            BaseUser user = new BaseUser();
            user.setName("anonymous");
            //user.setPassword("");
            user.setHomeDirectory(homedir.getAbsolutePath());
            um.save(user);

            serverFactory.setUserManager(um);

            server = serverFactory.createServer();
            
            server.start();
            uri = "ftp://" + Session.localIP + ":" + factory.getPort();
            log("Start FTP server: " + uri);
            running = true;
        } catch (Exception ex) {
            ex.printStackTrace(System.err);
            uri = null;
        }
    }

    public void stop() {
        try {
            if (running) {
                server.stop();
                server = null;
                log("Stop FTP server: " + uri);
            }
        } catch (Exception ex) {
            ex.printStackTrace(System.err);
        }
    }

    public String addTunnelInformation(String data) {
        return data + " " + uri;
    }
}
