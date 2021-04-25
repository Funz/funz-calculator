package org.funz.calculator.network;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Properties;
import static org.funz.Protocol.*;
import org.funz.calculator.*;
import org.funz.calculator.Calculator.SocketWriter;
import org.funz.calculator.plugin.CalculatorPlugin;
import org.funz.calculator.plugin.DataChannel;
import org.funz.calculator.plugin.DefaultCalculatorPlugin;
import org.funz.log.LogCollector;
import org.funz.util.ASCII;
import org.funz.util.Disk;
import org.funz.util.ZipTool;

public class Session extends Thread implements DataChannel {

    volatile boolean _askedToStop = false;
    private String _codeName;
    private DataInputStream _dis;
    private DataOutputStream _dos;
    private LinkedList _files;
    private String _host, _ip;
    private boolean _killed = false;
    private String _method;
    private CalculatorPlugin _plugin;// = Calculator.DEFAULT_PLUGIN;
    private BufferedReader _reader;
    private ArrayList _request;
    private volatile Socket _sock;
    private LinkedList _tvalues;
    private SocketWriter _writer;
    public static String localIP = "0.0.0.0";
    private final Calculator calculator;

    @Override
    public String toString() {
        //log("<toString");
        if (_host != null) {
            return _host + ":" + calculator._port + "-" + hashCode() + " (" + (_sock != null ? _sock.toString() : "No socket") + ")";
        } else {
            return "?:?";
        }
    }

    public static String getProtocol() {
        return "METHOD_RESERVE = " + METHOD_RESERVE + ",\n"
                + "METHOD_UNRESERVE = " + METHOD_UNRESERVE + ",\n"
                + "METHOD_GET_CODES = " + METHOD_GET_CODES + ",\n"
                + "METHOD_NEW_CASE = " + METHOD_NEW_CASE + ",\n"
                + "METHOD_EXECUTE = " + METHOD_EXECUTE + ",\n"
                + "METHOD_INTERRUPT = " + METHOD_INTERRUPT + ", \n"
                + "METHOD_PUT_FILE = " + METHOD_PUT_FILE + ", \n"
                + "METHOD_ARCH_RES = " + METHOD_ARCH_RES + ",\n"
                + "METHOD_GET_ARCH = " + METHOD_GET_ARCH + ",\n"
                + "METHOD_KILL = " + METHOD_KILL + ",\n"
                + "METHOD_GET_INFO = " + METHOD_GET_INFO + ",\n"
                + "METHOD_GET_ACTIVITY = " + METHOD_GET_ACTIVITY + ",\n"
                + "RET_YES = " + RET_YES + ", \n"
                + "RET_ERROR = " + RET_ERROR + ",\n"
                + "RET_NO = " + RET_NO + ",\n"
                + "RET_SYNC = " + RET_SYNC + ",\n"
                + "RET_INFO = " + RET_INFO + ", \n"
                + "RET_FILE = " + RET_FILE + ", \n"
                + "RET_HEARTBEAT = " + RET_HEARTBEAT + ",\n"
                + "END_OF_REQ = " + END_OF_REQ + ", \n"
                + "ARCHIVE_FILE = " + ARCHIVE_FILE + ",\n"
                + "ARCHIVE_FILTER = " + ARCHIVE_FILTER + ",\n"
                + "UNAVAILABLE_STATE = " + UNAVAILABLE_STATE + ",\n"
                + "ALREADY_RESERVED = " + ALREADY_RESERVED + ",\n"
                + "IDLE_STATE = " + IDLE_STATE + ",\n"
                + "PRIVATE_KEY = " + PRIVATE_KEY + ",\n"
                + "SOCKET_BUFFER_SIZE = " + SOCKET_BUFFER_SIZE + ";";
    }

    public void log(String message) {
        //System.err.println("                         >>>>>>>>>>>>>>>>>>>>>>>>> " + message);
    }

    public void out(String message) {
        log("        " + message);
        this.calculator.out(":" + hashCode() + ": " + message);
    }

    public void err(String message) {
        log("[ERROR] " + message);
        this.calculator.err("!" + hashCode() + "! " + message);
    }

    public Session(Calculator calculator, Socket sock) throws IOException {
        super(calculator._name + ":" + calculator._port + " " + sock.toString());

        _sock = sock;
        this.calculator = calculator;

        _sock.setTcpNoDelay(true);
        _sock.setTrafficClass(0x04); // = IPTOS_RELIABILITY
        //_sock.setSendBufferSize(Calculator.SOCKET_BUFFER_SIZE);
        //_sock.setReceiveBufferSize(Calculator.SOCKET_BUFFER_SIZE);

        _dis = new DataInputStream(_sock.getInputStream());
        _reader = new BufferedReader(new InputStreamReader(_dis, ASCII.CHARSET)) {

            @Override
            public String readLine() throws IOException {
                String s = super.readLine();
                log("                                      > " + s);
                return s;
            }

            @Override
            public void close() throws IOException {
                log("                                      > " + "CLOSE");
                super.close();
            }

            public Object getLock() {
                log("                                      > " + "getLock");
                return lock;
            }
        };
        _dos = new DataOutputStream(_sock.getOutputStream());
        _writer = new Calculator.SocketWriter(_sock) {

            @Override
            public void println(Object obj) throws IOException {
                log("                                      < " + obj);
                super.println(obj);
            }

            @Override
            public void println(int i) throws IOException {
                log("                                      < " + i);
                super.println(i);
            }

            @Override
            public void flush() {
                log("                                      << ");
                super.flush();
            }

        };
        _host = _sock.getInetAddress().getCanonicalHostName();
        _ip = _sock.getInetAddress().getHostAddress();
        localIP = _sock.getLocalAddress().getHostAddress();
        _killed = false;
        log("connected to " + _host + "/" + _ip);
    }

    public void askToStop(boolean sync, String why) {
        out("askToStop ... " + this + " because " + why);
        if (_askedToStop) {
            this.calculator.removeSession(this);
            out("          ...askToStop already called.");
            return;
        }
        _askedToStop = true;
        try {
            if (_reader != null) {
                _reader.close();
                _reader = null;
            }
            if (_writer != null) {
                _writer.close();
                _writer = null;
            }
            if (_dis != null) {
                _dis.close();
                _dis = null;
            }
            if (_dos != null) {
                _dos.close();
                _dos = null;
            }
            if (_sock != null) {
                _sock.shutdownInput();
                _sock.shutdownOutput();
                _sock.close();
                _sock = null;
            }
        } catch (IOException ex) {
            //err(ex.getLocalizedMessage()); No need to report. We already know that socket will be close
        }

        if (sync) {
            sessionTimeoutStop(true, "sync askToStop");
            try {
                join();
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        } else {
            sessionTimeoutStop(false, "askToStop");
        }

        this.calculator.removeSession(this);
        out("          ...askToStop done.");
    }

    private void archiveResults() {
        out("archiveResults ...");
        File archive = new File(this.calculator._dir.getParent() + File.separator + Calculator.ARCHIVE_FILE);
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(archive);
            try {
                ZipTool.zipDirectory(this.calculator._dir, false, out, (LogCollector) null, (ZipTool.ProgressObserver) null/*new ZipTool.ProgressObserver(){
                        
                         public void nextEntry(String arg0) {
                         sendInfomationLineToConsole("  zipping "+arg0);
                         }
                         }*/, new ZipTool.Filter() {
                    public boolean isToBeIncluded(File file) {
                        if (file == null) {
                            calculator.err("  zip: Exclude null file");
                            return false;
                        }
                        if (!file.exists()) {
                            calculator.err("  zip: Exclude unknown file " + file.getName());
                            return false;
                        }
                        if (file.getName().startsWith(".nfs")) {
                            calculator.err("  zip: Exclude NFS file " + file.getName());
                            return false;
                        }
                        if (!file.canRead()) {
                            calculator.err("  zip: Exclude unreadable file " + file.getName());
                            return false;
                        }
                        if (Disk.isLink(file)) {
                            calculator.err("  zip: Exclude link file " + file.getName());
                            return false;
                        }
                        if (calculator._vars.containsKey(ARCHIVE_FILTER)) {
                            if (!file.getName().matches(calculator._vars.getProperty(ARCHIVE_FILTER))) {
                                calculator.out("  zip: Not matching filter regexp '" + calculator._vars.getProperty(ARCHIVE_FILTER) + "' file " + file.getName());
                                return false;
                            } else {
                                calculator.out("  zip:     Matching filter regexp '" + calculator._vars.getProperty(ARCHIVE_FILTER) + "' file " + file.getName());
                                return true;
                            }
                        }
                        calculator.out("  zip: Include file " + file.getName());
                        return true;
                    }
                });

            } catch (IOException ioe) {
                err("zip failed:" + ioe.getMessage());
                returnNO("zip failed:" + ioe.getMessage());
            }
            returnYES();
        } catch (IOException ioe) {
            err("archiving failed:" + ioe.getMessage());
            try {
                returnNO("archiving failed:" + ioe.getMessage());
            } catch (IOException ioe2) {
            }
        } finally {
            try {
                out.close();
                out = null;
            } catch (Exception ee) {
            }
        }

        out("archiving over");
        out("                  ... archiveResults done.");
    }

    void setActivity(String s, String from) {
        log("activity:" + s + " from " + from);
        this.calculator.setActivity(s, from);
        synchronized (reservetimeout_lock) {
            reservetimeout_lock.notify();
        }
        log("        ... activity done.");
    }

    private void execute() throws Exception {
        out("execute ...");
        if (this.calculator._reserver == this) {

            if (!this.calculator.isAvailable()) {
                err("Not available. Exec failed for " + _request.get(1));
                returnNO("Not available. Exec failed for " + _request.get(1));
                //this.calculator._reserver = null;
                askToStop(true, "Not available. Exec failed for " + _request.get(1));
                return;
            }

            String codeName = (String) _request.get(1);
            this.calculator.log("executing " + codeName);
            returnYES();
            setActivity("running " + codeName, "execute");

            String command = "command_not_set:" + codeName;
            for (int i = 0; i < calculator._codes.length; i++) {
                if (this.calculator._codes[i].name.equals(codeName)) {
                    command = this.calculator._codes[i].command;
                    break;
                }
            }

            try {
                this.calculator._launcher = _plugin.createCodeLauncher(this.calculator._vars, this);
            } catch (Exception e) { // in case something goes wrong on server side, release client so it will retry another server...
                err("Exception for " + _request.get(1) + ": " + e.getMessage());
                returnNO("Exception for " + _request.get(1) + ": " + e.getMessage());
                askToStop(true, "Exception for " + _request.get(1) + ": " + e.getMessage());
                return;
            } catch (Error e) { // in case something goes wrong on server side, release client so it will retry another server...
                err("Error for " + _request.get(1) + ": " + e.getMessage());
                returnNO("Error: " + e.getMessage());
                askToStop(true, "Error for " + _request.get(1) + ": " + e.getMessage());
                return;
            }

            this.calculator._lastLauncher = this.calculator._launcher;
            this.calculator._launcher.setExecutionParameters(command, this.calculator._dir, _files);
            this.calculator._launcher.start();
            Thread watcher = new Thread("NetworkClient.watcher") {
                public void run() {
                    while (Session.this.calculator._launcher == Session.this.calculator._lastLauncher) {
                        try {
                            sleep(org.funz.Protocol.PING_PERIOD/5);
                        } catch (Exception e) {
                        }
                        synchronized (Session.this.calculator._launcherLock) {
                            if (Session.this.calculator._launcher != Session.this.calculator._lastLauncher) {
                                return;
                            }
                            try {
                                if (_writer != null) {
                                    _writer.println(Calculator.RET_HEARTBEAT);
                                    _writer.flush();
                                }
                            } catch (IOException e) {
                                Session.this.err("killing after connection lost: " + e);
                                if (Session.this.calculator._launcher.isAlive()) {
                                    Session.this.calculator._launcher.stopRunning();
                                }
                                askToStop(false, "killing after connection lost: " + e);
                                return;
                            }
                        }
                    }
                }
            };

            watcher.start();

            this.calculator._launcher.join();
            synchronized (this.calculator._launcherLock) {
                this.calculator._launcher = null;
            }
            watcher.interrupt();
            watcher = null;

            if (this.calculator._lastLauncher.failed() && !_killed) {
                if (this.calculator._lastLauncher.getReason() == null) {
                    returnNO("run failed: null");
                    err("run failed: null");
                } else {
                    returnNO("run failed: " + this.calculator._lastLauncher.getReason());
                    err("run failed: " + this.calculator._lastLauncher.getReason());
                }

                /*
                 this.calculator._reserver = null;
                 }*/
                setActivity(this.calculator.isAvailable() ? Calculator.IDLE_STATE : Calculator.UNAVAILABLE_STATE, "launcher failed");
                //_askedToStop = true; NO! this might have just failed for one code, and not for others...
                //return;
            } else {
                if (!_killed) {
                    returnYES();
                    setActivity(this.calculator.isAvailable() ? Calculator.ALREADY_RESERVED + " by " + this.calculator._reserver : Calculator.UNAVAILABLE_STATE, "launcher succeded");
                    out("done!");
                }
                /*if exec was killed, do not say anything, because it will interact with the next protocol command*/
 /*else {
                 _writer.println(Calculator.RET_NO);
                 _writer.println("run killed");
                 _writer.println(Calculator.END_OF_REQ);
                 _writer.flush();
                 this.calculator.log("killed");
                 }*/

            }
        } else {
            err("execution failed");
            notOwner("execute");
        }
        out("           ... execute done.");
    }

    private void getInfo() throws IOException {
        out("getInfo ...");
        returnYES();

        _writer.println(System.getProperty("user.name"));
        _writer.println(this.calculator._spool);
        String comment = this.calculator._comment;
        comment = comment.replaceAll("\n", " ");
        comment = comment.replaceAll("\r", "");
        comment = comment.replaceAll("\t", " ");
        _writer.println(comment);

        _writer.println(calculator._codes.length);
        for (int i = 0; i < calculator._codes.length; i++) {
            _writer.println(this.calculator._codes[i].name);
            if (this.calculator._codes[i].pluginURL != null) {
                _writer.println(this.calculator._codes[i].pluginURL);
            } else {
                _writer.println("no plugin");
            }
            if (this.calculator._codes[i].command != null) {
                _writer.println(this.calculator._codes[i].command);
            } else {
                _writer.println("no command");
            }
        }

        _writer.println(this.calculator._plugins.size());
        for (Iterator it = this.calculator._plugins.keySet().iterator(); it.hasNext();) {
            CalculatorPlugin p = (CalculatorPlugin) this.calculator._plugins.get(it.next());
            _writer.println(p.getClass().getName());
        }

        if (this.calculator._unavailables == null) {
            _writer.println(0);
        } else {
            _writer.println(calculator._unavailables.length);
            for (int i = 0; i < calculator._unavailables.length; i++) {
                _writer.println(this.calculator._unavailables[i].getStartString());
                _writer.println(this.calculator._unavailables[i].getEndString());
            }
        }
        _writer.flush();
        out("           ... getInfo done.");
    }

    private void getActivity() throws IOException {
        out("getActivity ...");
        returnYES();

        _writer.println(this.calculator.getActivity());
        _writer.flush();
        out("           ... getActivity done.");
    }

    private void killRunningCode() throws Exception {
        out("killRunningCode ...");
        if (this.calculator._reserver != null && this.calculator._secretCode.equals(_request.get(1))) {
            out("killing...");

            out("killRunningCode: SYNC !");

            if (this.calculator._reserver != null) {
                calculator._reserver._killed = true;
                this.calculator._reserver.stopLauncher();
                // force idle state now
                this.calculator._reserver = null;
                setActivity(Calculator.IDLE_STATE, "killRunningCode");
            } else {
                err("   kill failed: reserver=null");
                notOwner("killRunningCode");
            }

            returnYES();
            out("   killed.");
        } else {
            err("   kill failed: reserver=" + this.calculator._reserver + " secret code=" + this.calculator._secretCode);
            notOwner("killRunningCode/secretCode");
        }
        out("                   ... killRunningCode done.");
    }
    String init_dir_name;

    private void newCase() throws Exception {
        out("newCase ...");
        if (this.calculator._reserver != this) {
            notOwner("newCase");
            return;
        }

        this.calculator._vars = new Properties();
        int nvars = Integer.parseInt(_reader.readLine());
        for (int _i = 0; _i < nvars; _i++) {
            String key = _reader.readLine();
            String value = _reader.readLine();
            this.calculator._vars.put(key, value);
            out("\t" + key + "\t = " + value);
        }
        log("         " + this.calculator._vars);

        if (this.calculator._dir == null) {
            this.calculator._dir = new File(this.calculator._spool + File.separator + this.calculator._name + File.separator + this.calculator._port + File.separator + "spool");
        }
        if (this.calculator._dir.isDirectory() || this.calculator._dir.mkdirs()) {
            try {
                Disk.emptyDir(this.calculator._dir);
            } catch (IOException e) {
                err(e.getMessage());
            }
            int tries = 0;
            while ((tries++)<10) {
                File[] in = Disk.listRecursiveFiles(this.calculator._dir);
                if (in.length==0) break;
                try {
                    Disk.emptyDir(this.calculator._dir);
                } catch (IOException e) {
                    err(e.getMessage());
                }
                sleep(1000);
            }
            // in the end, could not cleanup... (e.g. maybe because nfs latency)
            if (Disk.listRecursiveFiles(this.calculator._dir).length > 0) {
                err("Could not clean directory " + this.calculator._dir);
                //throw new IOException("Could not clean directory " + this.calculator._dir);
                this.calculator._dir = new File(this.calculator._dir.getParentFile(),"spool."+Math.round(Math.random()*9999));
                err("Trying to use new spool directory " + this.calculator._dir);
                if (!this.calculator._dir.mkdirs()) throw new IOException("Could not create new directory " + this.calculator._dir);
            }
            returnYES();
        } else {
            throw new IOException("could not create directory " + this.calculator._dir);
        }
        _files = new LinkedList();

        out("        ... newCase done.");
    }

    private void notOwner(String from) throws Exception {
        out("notOwner " + from + " ...");
        String msg = "";
        if (this.calculator == null) {
            msg = "null calculator";
        } else if (this.calculator._reserver == null) {
            msg = "no reserver";
        } else {
            msg = this.calculator._reserver.getName() + "!=" + this.getName();
        }
        returnNO("not owner (" + from + "): " + msg);
        out("         ... notOwner done.");
    }

    private void putFile() throws Exception {
        out("putFile ...");
        if (this.calculator._reserver != this) {
            notOwner("putFile");
            return;
        }
        returnYES();

        String name = ((String) _request.get(1));
        long size = Long.parseLong((String) _request.get(2));
        name = name.replace('/', File.separatorChar);
        out("receiving file " + name + " (" + size + " bytes)");
        File f = new File(this.calculator._dir, name);
        if (!f.getParentFile().exists() && !f.getParentFile().mkdirs()) {
            throw new IOException("Could not create directory " + f.getParentFile().getAbsolutePath());
        }

        if (!this.calculator._isSecure) {
            Disk.deserializeFile(_dis, f.getPath(), size, null);
        } else {
            Disk.deserializeEncryptedFile(_dis, f.getPath(), size, this.calculator._key, null);
        }

        _files.add(f);

        returnYES();
        out("         ... putFile done.");
    }

    private synchronized void readRequest() throws Exception {
        out("readRequest ...");
        //calculator.out.println("REQ ?");
        if (_request == null) {
            _request = new ArrayList();
        }
        _request.clear();

        log("readRequest ... 0");

        if (_reader == null) {
            log("readRequest ... _reader == null");
            throw new Exception("reader destroyed");
        }

        log("readRequest ... _reader.ready(): " + _reader.ready());
        int sleep = 0;
        try {
            while (!_askedToStop && _reader != null && !_reader.ready()) {
                Thread.sleep(100);
                if (sleep++ > 1000) {
                    log("readRequest ... ready waited too long.");
                    throw new IOException("ready waited too long");
                }
            }
        } catch (InterruptedException e) {
        }

        if (_reader == null) {
            log("readRequest zzz _reader == null");
            throw new Exception("reader destroyed");
        }

        String line;
        while ((line = _reader.readLine()) != null) {
            log("readRequest ... line:" + line);
            //calculator.out.println("  <" + line);
            if (line.equals(Calculator.END_OF_REQ)) {
                break;
            }
            _request.add(line);
        }
        _reader.mark(10);
        _reader.reset();

        log("readRequest ... request: " + _request);

        if (_request.size() == 0) {
            log("readRequest ... request.size() == 0");
            System.err.print("0");
            //calculator.out.println("  REQ> NULL");
            throw new IOException("no stream");
            //_request.add("no stream");
        }
        if (_request.get(0) == null) {
            log("readRequest ... request.get(0) == null");
            //calculator.out.println("  REQ> NULL");
            throw new IOException("empty stream");
        }
        if (!_sock.isConnected()) {
            log("readRequest ... !sock.isConnected()");
            //calculator.out.println("connection lost");
            throw new IOException("connection lost");
        }
        _method = (String) _request.get(0);
        out("          ... readRequest done: " + _method);
    }
    public static long RESERVE_TIMEOUT = 5 * 60 * 1000;//5 min
    private final Object reservetimeout_lock = new Object();
    Thread reservetimeout;

    private void reserve() throws Exception {
        _askedToStop = false;
        out("reserve ...");

        log("reserve: SYNC !");
        if (!this.calculator.isAvailable()) {
            err("calculator not available. (reserver=" + this.calculator._reserver + ")");
            returnNO(Calculator.UNAVAILABLE_STATE);
            askToStop(false, "calculator not available. (reserver=" + this.calculator._reserver + ")");
            return;
        }

        if (this.calculator._reserver == null) {
            log("reserve: _reserver == null");
            returnYES();

            log("reserve:_reserver = this");
            this.calculator._reserver = this;

            setActivity(Calculator.ALREADY_RESERVED + " by " + this.calculator._reserver, "reserve");
            final Calculator currentcalc = this.calculator;
            final String currenthost = this.calculator._reserver.toString();
            //assert reservetimeout == null : "Reserve timeout already exists...";
            if (reservetimeout != null && reservetimeout.getState() != State.TERMINATED) {
                log("reserve: reservetimeout != null && reservetimeout.isAlive()");
                reservetimeout.join();
                reservetimeout = null;
            }
            reservetimeout = new Thread(new Runnable() {
                public void run() {
                    log("reservetimeout.run");
                    try {
                        synchronized (reservetimeout_lock) {
                            reservetimeout_lock.wait(RESERVE_TIMEOUT);
                        }
                    } catch (InterruptedException ex) {
                        ex.printStackTrace(System.err);
                    }

                    //log(" [reserve TIMEOUT] activity =?= " + Calculator.ALREADY_RESERVED + " by " + currenthost);
                    if (!currentcalc.getActivity().startsWith(Calculator.ALREADY_RESERVED + " by " + currenthost)) {
                        reservetimeout = null;
                        return;
                    }

                    log(" [reserve TIMEOUT] force unreserve because status is still '" + currentcalc.getActivity() + "'");
                    try {
                        force_unreserve();
                    } catch (Exception ex) {
                    }
                    log("reservetimeout.run END");
                }
            }, "NetworkClient.reservetimeout");
            log("reserve: reservetimeout.start()");
            reservetimeout.start();

            _codeName = _reader.readLine();
            int nTValues = Integer.parseInt(_reader.readLine());
            _tvalues = new LinkedList();
            for (int i = 0; i < nTValues; i++) {
                String key = _reader.readLine();
                String value = _reader.readLine();
                _tvalues.add(new String[]{key, value});
            }
            log("reserve: tvalues=" + _tvalues);

            for (int i = 0; i < calculator._codes.length; i++) {
                //calculator.out.println(_codes[i].name + " =? " + _codeName);
                if (this.calculator._codes[i].name.equals(_codeName)) {
                    //calculator.out.println("> " + _codes[i].pluginFileName);
                    _plugin = (CalculatorPlugin) this.calculator._plugins.get(this.calculator._codes[i].pluginURL);
                    break;
                }
            }
            if (_plugin == null) {
                _plugin = new DefaultCalculatorPlugin();//Calculator.DEFAULT_PLUGIN;
                ((DefaultCalculatorPlugin) _plugin).setSecure(calculator._isSecure);
            }
            _plugin.setCode(_codeName);
            _plugin.setUser(_ip);

            log("+ using plugin " + _plugin.getClass().getName() + " for " + _codeName);

            try {
                out("reserve: prepareNewProject");
                _plugin.prepareNewProject(_tvalues);
            } catch (Exception e) {
                err("Plugin " + _plugin.getClass().getName() + " throwed exception " + e.toString());
                returnNO("plugin exception " + e.getMessage());
                askToStop(false, "Plugin " + _plugin.getClass().getName() + " throwed exception " + e.toString());
                return;
            }

            try {
                this.calculator._secretCode = "" + this.calculator._calendar.getTimeInMillis();
            } catch (Exception e) {
                this.calculator._secretCode = "0";
            }
            _writer.println(Calculator.RET_YES);
            _writer.println(this.calculator._secretCode);
            _writer.println(_ip);
            _writer.println(this.calculator._isSecure ? "Y" : "N");
            _writer.println(Calculator.END_OF_REQ);
            _writer.flush();

            _killed = false;
            if (this.calculator._isSecure) {
                try {
                    MessageDigest md = MessageDigest.getInstance("MD5");
                    md.update(Calculator.PRIVATE_KEY.getBytes());
                    md.update(this.calculator._secretCode.getBytes());
                    this.calculator._key = md.digest();
                } catch (Exception e) {
                    this.calculator._key = new byte[]{0};
                }
            }

        } else {
            err("reserve failed, already reserved by: " + this.calculator._reserver);
            returnNO(Calculator.ALREADY_RESERVED);
            askToStop(false, "reserve failed, already reserved by: " + this.calculator._reserver);
        }

        out("          ... reserve done.");
    }

    private void returnYES() throws IOException {
        _writer.println(Calculator.RET_YES);
        _writer.println(Calculator.END_OF_REQ);
        _writer.flush();
    }

    void returnNO(String why) throws IOException {
        _writer.println(Calculator.RET_NO);
        _writer.println(why);
        _writer.println(Calculator.END_OF_REQ);
        _writer.flush();
    }

    @Override
    public void run() {
        try {
            log("run.START");
            while (!_askedToStop) {
                log(" run.readRequest");

                try {
//                    sessionTimeoutStart("REQUEST");
                    readRequest();
                    log(" run.method= " + _method);

                    if (_method.equals(Calculator.METHOD_RESERVE)) {
                        sessionTimeoutStart(Calculator.METHOD_RESERVE);
                        reserve();
                    } else if (_method.equals(Calculator.METHOD_UNRESERVE)) {
                        sessionTimeoutStop(true, Calculator.METHOD_UNRESERVE); // no need here sessionTimeoutStart();
                        unreserve();
                    } else if (_method.equals(Calculator.METHOD_NEW_CASE)) {
                        sessionTimeoutStart(Calculator.METHOD_NEW_CASE);
                        newCase();
                    } else if (_method.equals(Calculator.METHOD_PUT_FILE)) {
                        sessionTimeoutStart(Calculator.METHOD_PUT_FILE);
                        putFile();
                    } else if (_method.equals(Calculator.METHOD_ARCH_RES)) {
                        sessionTimeoutStart(Calculator.METHOD_ARCH_RES);
                        archiveResults();
                    } else if (_method.equals(Calculator.METHOD_GET_ARCH)) {
                        sessionTimeout_reset_period = 1000; // reset timeout counter each 1000 ms (as long as data blocks will be sent), to avoid session timeout when tranfer huge files
                        sessionTimeoutStart(Calculator.METHOD_GET_ARCH);
                        transferArchive();
                        sessionTimeout_reset_period = REQUEST_TIMEOUT;
                    } else if (_method.equals(Calculator.METHOD_EXECUTE)) {
                        sessionTimeoutStop(true, Calculator.METHOD_EXECUTE); // _NO_ sessionTimeoutStart here, beccause we don't know how long it will take...
                        execute();
                        sessionTimeoutStart(Calculator.METHOD_EXECUTE); // To unplug if connection was lost during run
                    } else if (_method.equals(Calculator.METHOD_KILL)) {
                        sessionTimeoutStart(Calculator.METHOD_KILL);
                        killRunningCode();
                    } else if (_method.equals(Calculator.METHOD_GET_INFO)) {
                        sessionTimeoutStart(Calculator.METHOD_GET_INFO);
                        getInfo();
                    } else if (_method.equals(Calculator.METHOD_GET_ACTIVITY)) {
                        sessionTimeoutStart(Calculator.METHOD_GET_ACTIVITY);
                        getActivity();
                    } else {
                        sessionTimeoutStart("unknownRequest");
                        unknownRequest();
                    }

                } catch (IOException e) {
                    err("IOException: " + e.getMessage());
                    returnNO("IOException:" + e.getMessage());
                    force_unreserve();
                }
                log(" run.readRequest Processed");
            }
//            sessionTimeoutStop(false,"/REQUEST");
            log(" run._askedToStop");

            if (this.calculator._reserver == this) {
                this.calculator._reserver = null;
            }

            log(" run.closing socket " + _sock);
            try {
                if (_sock != null && !_sock.isClosed()) {
                    _sock.shutdownInput();
                    _sock.shutdownOutput();
                    _sock.close();
                }
            } catch (Exception ex) {
                err("Failed to close server & socket: " + ex.getMessage());
            }
            log(" run.NOException");
        } catch (Exception e) {
            //System.err.println("SYNC ? " + this.calculator._lock);
            if (this.calculator._reserver == this) {
                this.calculator._reserver = null;
                setActivity(Calculator.IDLE_STATE, "run/exception");
            }

            if (this.calculator._reserver == this) {
                err("synchronized (this.calculator._launcherLock)");
                synchronized (this.calculator._launcherLock) {
                    if (this.calculator._launcher != null && this.calculator._launcher.isAlive()) {
                        this.calculator._launcher.stopRunning();
                    }
                    this.calculator._launcher = null;
                }
            }
            try {
                if (_sock != null && !_sock.isClosed()) {
                    _sock.shutdownInput();
                    _sock.shutdownOutput();
                    _sock.close();
                }
            } catch (Exception ex) {
                err("Failed to shutdown socket: " + ex.getMessage());
            }
            log(" run.Exception END");
        }
        log(" run.END ");
    }

    public static long REQUEST_TIMEOUT = 60 * 1000;//1 min
    Thread requesttimeout;
    int i = 0;
    volatile boolean sessionTimeout = false;

    private void sessionTimeoutStop(boolean sync, String by) {
        log(".......................................................requesttimeout.STOP " + by);
        if (requesttimeout != null && requesttimeout.getState() != State.TERMINATED) {
            log(".......................................................  requesttimeout: !=null && requesttimeout.getState() " + requesttimeout.getState());
            sessionTimeout = false;
            if (requesttimeout != null) {
                requesttimeout.interrupt();
            }

            if (sync) { //most always join, otherwise sessionTimeout will return true before ending breaking previous requesttimeout thread...
                try {
                    if (requesttimeout != null) {
                        requesttimeout.join();
                    }
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }

            requesttimeout = null;
        }
    }

    private volatile long sessionTimeout_waited = 0; // cumulative waited time
    private long sessionTimeout_reset_period = REQUEST_TIMEOUT; // cumulative waited time
    private void sessionTimeoutStart(final String by) {
        sessionTimeoutStop(true, by);
        sessionTimeout_waited = 0;
        requesttimeout = new Thread(new Runnable() {
            public void run() {
                while (sessionTimeout_waited < REQUEST_TIMEOUT) {
                    log(".......................................................  requesttimeout.RUN " + by);
                    try {
                        sleep(sessionTimeout_reset_period);
                        sessionTimeout_waited += sessionTimeout_reset_period;
                    } catch (InterruptedException ex) {
                        log(".......................................................  requesttimeout.INTERRUPT " + by);
                    }
                    if (!sessionTimeout) {
                        log(".......................................................  requesttimeout.BROKEN " + by);
                        return;
                    } else {
                        log(".......................................................    [request TIMEOUT] by " + by + " force stop because no interaction with client in " + REQUEST_TIMEOUT / 1000 + " s.");
                    }
                }
                try {
                    askToStop(false, ".......................................................    [request TIMEOUT] by " + by + " force stop because no interaction with client in " + REQUEST_TIMEOUT / 1000 + " s.");
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }, "NetworkClient.requesttimeout " + (i++) + " by " + by);
        log(".......................................................requesttimeout.START " + by);
        sessionTimeout = true;
        requesttimeout.start();
    }

    public boolean sendFileToConsole(File file) {
        out("sendFileToConsole " + file);
        return false;
    }
    String lastinfo;

    public boolean sendInfomationLineToConsole(String info) {
        out("sendInfomationLineToConsole ... " + info);
        if (info == null) {
            return false;
        }
        if (info.equals(lastinfo)) {
            return true;
        }
        synchronized (this.calculator._launcherLock) {
            if (this.calculator._launcher != this.calculator._lastLauncher) {
                return false;
            }
            try {
                _writer.println(Calculator.RET_INFO);
                _writer.println(info);
                _writer.flush();
            } catch (IOException e) {
                err(e.getLocalizedMessage());
                lastinfo = null;
                return false;
            }
        }
        lastinfo = info;
        out("                              ... sendInfomationLineToConsole done.");
        return true;
    }

    private void stopLauncher() {
        out("stopLauncher ...");
        synchronized (this.calculator._launcherLock) {
            if (this.calculator._launcher != null && this.calculator._launcher.isAlive()) {
                this.calculator._launcher.stopRunning();
                this.calculator._launcher = null;
            }
        }
        out("            ... stopLauncher done.");
    }

    private void transferArchive() throws IOException {
        out("transferArchive ...");
        returnYES();
        File archive = new File(this.calculator._dir.getParent() + File.separator + Calculator.ARCHIVE_FILE);
        _writer.println("" + archive.length());
        _writer.flush();
        _reader.readLine();
        log("starting transfer of " + archive.length() + " bytes");
        Disk.ProgressObserver obs = new Disk.ProgressObserver() {
            long total, counter = 0, last = 0;

            public void newDataBlock(int size) {
                sessionTimeout_waited = 0; // Reset sessionTimeOut counter between data blocks.
                if (total == 0) {
                    return;
                }
                counter += size;
                long nbdots = counter * 10L / total;
                if (last < nbdots) {
                    log("transferred " + nbdots + "0 %");
                }
                last = nbdots;
            }

            public void setTotalSize(long t) {
                total = t;
            }
        };
        if (!this.calculator._isSecure) {
            Disk.serializeFile(_dos, archive, archive.length(), obs);
        } else {
            Disk.serializeEncryptedFile(_dos, archive, archive.length(), this.calculator._key, obs);
        }
        _writer.flush();
        archive.delete();
        if (this.calculator._dir != null) {
            try {
                out("cleaning - removing folder " + this.calculator._dir.getParentFile());
                Disk.removeDir(this.calculator._dir.getParentFile());
            } catch (Exception e) {
                err("Removing folder exception:" + e.toString());
                e.printStackTrace();
            }
        }
        log("transfer over");
        out("               ... transferArchive done.");
    }

    private void unknownRequest() throws IOException {
        out("unknownRequest ...");
        log("unknown request " + _method);
        _writer.println(Calculator.RET_ERROR);
        _writer.println("unknown request " + _method);
        _writer.println(Calculator.END_OF_REQ);
        _writer.flush();
        out("               ... unknownRequest done.");
    }

    private void unreserve() throws Exception {
        out("unreserve ...");

        log("unreserve: SYNC !");

        if (this.calculator._reserver == this) {
            this.calculator._reserver = null;
            this.calculator._secretCode = "";
            this.calculator.log("unreserved");
            returnYES();
            setActivity(Calculator.IDLE_STATE, "unreserved");
            //askToStop();
        } else /*if (this.calculator._reserver == null) {
                setActivity(Calculator.IDLE_STATE, "unreserve null");
            } else*/ {
            err("unreserve failed: " + this.calculator._reserver + " != " + this);
            notOwner("unreserve");
        }

        out("          ... unreserve done.");

        askToStop(false, "unreserve ...");
    }

    private void force_unreserve() throws Exception {
        out("force_unreserve ...");

        log("force_unreserve: SYNC !");

        if (this.calculator._reserver == this) {
            this.calculator._reserver = null;
            this.calculator._secretCode = "";
            this.calculator.log("force unreserved");
            //_writer.println(Calculator.RET_YES);
            //_writer.println(Calculator.END_OF_REQ);

            //_writer.flush();
            //_reader.reset();
            setActivity(Calculator.IDLE_STATE, "force_unreserve");
        } /*else if (this.calculator._reserver == null) {
                setActivity(Calculator.IDLE_STATE, "force_unreserve");
            }*/ else {
            err("force unreserve failed: " + this.calculator._reserver);
            //notOwner();
        }
        //askToStop();      

        out("               ... force_unreserve done.");

        askToStop(false, "force_unreserve ...");
    }
}
