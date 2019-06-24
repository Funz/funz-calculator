package org.funz.calculator;

import org.funz.*;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import java.util.Map;
import static org.funz.Protocol.END_OF_REQ;
import static org.funz.Protocol.RET_HEARTBEAT;
import static org.funz.Protocol.RET_INFO;
import static org.funz.Protocol.RET_YES;
import org.funz.util.ASCII;
import org.funz.util.Disk;
import org.funz.util.TimePeriod;

/**
 * Marshals the network requests from GUI to Calculator.
 */
public class OldClient implements Protocol {

    public static class CalculatorInfo {

        public CodeInfo codes[];
        public String comment;
        public TimePeriod periods[];
        public PluginInfo plugins[];
        public String spool;
        public String userName;
    }

    public static class CodeInfo {

        public String command;
        public String name;
        public String pluginFileName;
    }

    public static interface DataListener {

        public void informationLineArrived(String str);
    }

    public static class PluginInfo {

        public String className;
        public String name;
        public String type;
    }
    private DataInputStream _dis;
    private DataOutputStream _dos;
    private String _host;
    private byte _key[];
    protected DataListener _listener;
    private int _port;
    protected BufferedReader _reader;
    protected String _reason, _error, _secretCode = "";
    private volatile boolean _reserved = false;
    private boolean _isSecure = false;
    protected ArrayList _response = new ArrayList(1);
    protected Socket _socket;
    protected PrintWriter _writer;

    public void log(String s) {
        //System.err.println(s);
    }

    public OldClient(String host, int port) throws Exception {
        _host = host;
        _port = port;
        createSocket();
    }

    public synchronized boolean archiveResults() throws IOException {
        log(">" + METHOD_ARCH_RES);
        _writer.println(METHOD_ARCH_RES);
        _writer.println(END_OF_REQ);
        _writer.flush();
        return readResponse();
    }

    private class socketBuilder extends Thread {

        @Override
        public void run() {
            _reserved = false;
            //_log.log("    _reserved = false");
            try {
                if (_socket != null) {
                    if (!_socket.isClosed() && !_socket.isInputShutdown()) {
                        _socket.shutdownInput();
                    }
                    if (!_socket.isClosed() && !_socket.isOutputShutdown()) {
                        _socket.shutdownOutput();
                    }
                    if (!_socket.isClosed()) {
                        _socket.close();
                    }
                }
                _socket = new Socket(_host, _port);
                _socket.setTcpNoDelay(true);
                _socket.setTrafficClass(0x04);
                // will slow connection if used as is :
                //_socket.setReceiveBufferSize(SOCKET_BUFFER_SIZE);
                //_socket.setSendBufferSize(SOCKET_BUFFER_SIZE);

                if (_reader != null) {
                    try {
                        _reader.close();
                    } catch (Exception e) {
                    }
                }
                _reader = new BufferedReader(new InputStreamReader(_socket.getInputStream(), ASCII.CHARSET));

                if (_writer != null) {
                    try {
                        _writer.close();
                    } catch (Exception e) {
                    }
                }
                _writer = new PrintWriter(_socket.getOutputStream(), true);

                if (_dos != null) {
                    try {
                        _dos.close();
                    } catch (Exception e) {
                    }
                }
                _dos = new DataOutputStream(_socket.getOutputStream());

                if (_dis != null) {
                    try {
                        _dis.close();
                    } catch (Exception e) {
                    }
                }
                _dis = new DataInputStream(_socket.getInputStream());

            } catch (Exception e) {
                disconnect();
            }
        }
    }

    protected void createSocket() throws Exception {
        log(" >> creating socket...");
        new socketBuilder().start();
        //createSocket();
        int tries = 10;
        while (tries > 0) {
            tries--;
            try {
                Thread.sleep(100);
            } catch (InterruptedException ie) {
            }
            if (_socket != null) {
                log(" >> socket created.");
                return;
            }
        }
        log("    socket not created !");
        throw new Exception("Socket creation failed!");
    }

    public synchronized void disconnect() {
        log(">disconnect");
        try {
            if (_reserved) {
                unreserve();
            }
        } catch (Exception ex) {
            ex.printStackTrace(System.err);
        }
        if (isConnected()) {
            force_disconnect();
        }
        _reserved = false;
        log(">disconnect DONE");
    }

    public void force_disconnect() {
        log(">force_disconnect");
        try {
            if (_socket != null) {
                if (!_socket.isClosed() && !_socket.isInputShutdown()) {
                    log(" >> socket.shutdownInput");
                    _socket.shutdownInput();
                }
                if (!_socket.isClosed() && !_socket.isOutputShutdown()) {
                    log(" >> socket.shutdownOutput");
                    _socket.shutdownOutput();
                }
                if (!_socket.isClosed()) {
                    log(" >> socket.close");
                    _socket.close();
                }
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
        try {
            if (_reader != null) {
                log(" >> reader.close");
                _reader.close();
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
        try {
            if (_writer != null) {
                log(" >> writer.close");
                _writer.close();
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
        try {
            if (_dis != null) {
                log(" >> dis.close");
                _dis.close();
            }
        } catch (IOException ex) {
            ex.printStackTrace(System.err);
        }
        try {
            if (_dos != null) {
                log(" >> dos.close");
                _dos.close();
            }
        } catch (IOException ex) {
            ex.printStackTrace(System.err);
        }
    }
    public volatile boolean executing = false;

    public synchronized boolean execute(String codeName, DataListener listener) throws Exception {
        log(">" + METHOD_EXECUTE + " " + codeName);
        if (executing) {
            throw new IllegalArgumentException("Already executing !");
        }
        executing = true;
        _writer.println(METHOD_EXECUTE);
        _writer.println(codeName);
        _writer.println(END_OF_REQ);
        _writer.flush();
        _listener = listener;
        if (readResponse()) {
            boolean ret = readResponse();
            _listener = null;
            executing = false;
            log("..." + METHOD_EXECUTE + " " + codeName + " readResponse:" + ret);
            return ret;
        }
        _listener = null;
        executing = false;
        log("..." + METHOD_EXECUTE + " " + codeName + " FALSE (no readResponse)");
        return false;
    }

    public String getHost() {
        return _host;
    }

    public int getPort() {
        return _port;
    }

    public synchronized boolean getInfo(CalculatorInfo ci) throws Exception {
        log(">" + METHOD_GET_INFO);
        _writer.println(METHOD_GET_INFO);
        _writer.println(END_OF_REQ);
        _writer.flush();
        if (!readResponse()) {
            return false;
        }

        ci.userName = _reader.readLine();
        ci.spool = _reader.readLine();
        ci.comment = _reader.readLine();

        log(" >> " + ci.userName + " " + ci.spool + " " + ci.comment);
        //System.err.println("getInfo:"+ci.userName+" "+ci.spool+ " "+ci.comment);

        int n = Integer.parseInt(_reader.readLine());
        ci.codes = new CodeInfo[n];
        for (int i = 0; i < n; i++) {
            CodeInfo c = new CodeInfo();
            ci.codes[i] = c;
            c.name = _reader.readLine();
            c.pluginFileName = _reader.readLine();
            c.command = _reader.readLine();
            //System.err.println("getInfo: Code: "+c.name+" "+c.command);
            log(" >> Code: " + c.name + " " + c.command);
        }

        n = Integer.parseInt(_reader.readLine());
        //System.err.println("getInfo: "+n+" plugins");
        ci.plugins = new PluginInfo[n];
        for (int i = 0; i < n; i++) {
            PluginInfo p = new PluginInfo();
            ci.plugins[i] = p;
            p.name = _reader.readLine();
            //System.err.println("getInfo: Plugin "+i+"/"+n+" "+p.name);
            //p.type = _reader.readLine();
            //System.err.println("getInfo: Plugin: "+p.name+" "+p.type);
            // p.className = _reader.readLine();
            // System.err.println("getInfo: Plugin: "+p.name+" "+p.type+" "+p.className);
            log(" >> Plugin: " + p.name + " " + p.type + " " + p.className);
        }

        n = Integer.parseInt(_reader.readLine());
        ci.periods = new TimePeriod[n];
        for (int i = 0; i < n; i++) {
            String t1 = _reader.readLine();
            String t2 = _reader.readLine();
            TimePeriod p = new TimePeriod(t1, t2);
            ci.periods[i] = p;
            log(" >> Period: " + p);
        }
        return true;
    }

    public String getReason() {
        return _reason;
    }

    public boolean isConnected() {
        return _socket != null && _socket.isConnected();
    }

    public boolean isReserved() {
        return _reserved;
    }

    public boolean killRunningCode(String secretCode) throws Exception {
        log(">" + METHOD_KILL + " (" + secretCode + ")");
        if (!executing) {
            return false;
        }
        _writer.println(METHOD_KILL);
        _writer.println(secretCode);
        _writer.println(END_OF_REQ);
        _writer.flush();
        boolean res = readResponse();
        _reason = "killed by user";
        log(">" + METHOD_KILL + " " + (res ? "DONE" : "FAILED"));
        return res;
    }

    public synchronized boolean newCase(Map vars) throws Exception {
        log(">" + METHOD_NEW_CASE + " " + vars);
        _writer.println(METHOD_NEW_CASE);
        _writer.println(END_OF_REQ);
        _writer.flush();
        if (vars == null) {
            vars = new HashMap();
        }

        vars.put("USERNAME", System.getProperty("user.name"));

        _writer.println(vars.size());
        for (Iterator it = vars.keySet().iterator(); it.hasNext();) {
            String key = (String) it.next();
            _writer.println(key);
            //PATCH pour n'envoyer que la 1ere ligne de la valeur de la variable. sinon, la seconde ligne est interprete comme une commande par le calculator...
            String firstlinevalue = (String) vars.get(key);
            int returnCharIndex = firstlinevalue.indexOf("\n");
            if (returnCharIndex > -1) {
                firstlinevalue = firstlinevalue.substring(0, returnCharIndex) + "...";
            }
            _writer.println(firstlinevalue);
        }

        _writer.flush();
        return readResponse();
    }

    public synchronized boolean putFile(File file, File root) throws Exception {
        if (!file.exists()) {
            throw new IOException("File " + file + " does not exists, so cannot putFile");
        }

        log(">" + METHOD_PUT_FILE + " " + root + " / " + file);
        if (file == null || !file.exists()) {
            log(">" + METHOD_PUT_FILE + " ABORTED:  !file.exists()");
            return false;
        }
        if (_dos == null) {
            log(">" + METHOD_PUT_FILE + " ABORTED:  _dos == null");
            return false;
        }

        _writer.println(METHOD_PUT_FILE);
        String relpath = file.getAbsolutePath().replace(root.getAbsolutePath(), "").replace(File.separatorChar, '/');
        _writer.println(relpath);
        _writer.println(file.length());
        _writer.println(END_OF_REQ);
        _writer.flush();
        if (!readResponse()) {
            log(">" + METHOD_PUT_FILE + " ABORTED: !readResponse()");
            return false;
        }
        if (!_isSecure) {
            Disk.serializeFile(_dos, file, file.length(), null);
        } else {
            Disk.serializeEncryptedFile(_dos, file, file.length(), _key, null);
        }

        boolean res = readResponse();
        log(">" + METHOD_PUT_FILE + " " + (res ? "DONE" : "FAILED"));
        return res;
    }

    protected /*synchronized*/ boolean readResponse() throws IOException {
        log(">readResponse");
        try {
            if (_response != null) {
                _response.clear();
                _response.ensureCapacity(1);
            }

            String line;
            int counter = 0;
            while ((line = _reader.readLine()) != null) {
                log(" >> " + line);
                if (line.equals(END_OF_REQ)) {
                    log("  >>> RES");
                    break;
                }

                if (counter == 0 && line.equals(RET_HEARTBEAT)) {
                    log("  >>> HEARTBEAT");
                    continue;
                }
                if (counter == 0 && line.equals(RET_INFO)) {
                    log("  >>> INFO: ");
                    if (_listener != null) {
                        String info = _reader.readLine();
                        _listener.informationLineArrived(info);
                        log(info);
                    }
                    continue;
                }
                _response.add(line);
                counter++;
            }
            log(" >> NULL");

            if (line == null) {
                _reason = "no stream";
                log("  >>> NULL");
                throw new IOException("no stream");
            }

            if (!_socket.isConnected()) {
                log("  >>> BREAK");
                _reason = "connection lost";
                throw new IOException("connection lost");
            }

            if (_response == null || _response.size() == 0) {
                return false;
            } else {
                _error = (String) _response.get(0);
                if (!_error.equals(RET_YES)) {
                    _reason = (String) _response.get(1);
                    log("  >>> ERROR ret=" + _error + " reason=" + _reason);
                    return false;
                }
                return true;
            }
        } catch (IOException e) {
            log(" >> IOException: " + e.getLocalizedMessage());
            return false;
        }
    }

    public void reconnect() throws Exception {
        log(">reconnect");
        createSocket();
    }

    public synchronized boolean reserve(Project prj, StringBuffer ip, StringBuffer secretCode) throws Exception {
        log(">" + METHOD_RESERVE + " " + prj.getName() + " ip=" + ip + " (" + secretCode + ")");
        _writer.println(METHOD_RESERVE);
        _writer.println(END_OF_REQ);
        _writer.flush();

        if (!readResponse()) {
            log(" >> reserve !readResponse 1");
            return false;
        }

        _writer.println(prj.getCode());
        _writer.println(prj.getTaggedValues().size());
        for (String k : prj.getTaggedValues().keySet()) {
            _writer.println(k);
            _writer.println(prj.getTaggedValues().get(k));
        }
        _writer.flush();

        if (!readResponse()) {
            log(" >> reserve !readResponse 2");
            return false;
        }
        _secretCode = (String) _response.get(1);
        secretCode.append(_secretCode);
        ip.append((String) _response.get(2));
        String secure = (String) _response.get(3);
        _isSecure = secure.equals("Y");
        if (_isSecure) {
            try {
                MessageDigest md = MessageDigest.getInstance("MD5");
                md.update(PRIVATE_KEY.getBytes());
                md.update(_secretCode.getBytes());
                _key = md.digest();
            } catch (Exception e) {
                _key = new byte[]{0};
            }
        }

        _reserved = true;
        log(" _reserved = true");
        //to test auto unreserve on server-side : Thread.sleep(NetworkClient.RESERVE_TIMEOUT*2);
        return true;
    }

    public synchronized boolean transferArchive(File path, Disk.ProgressObserver observer) throws IOException {
        log(">" + METHOD_GET_ARCH + " " + path);
        _writer.println(METHOD_GET_ARCH);
        _writer.println(END_OF_REQ);
        _writer.flush();

        if (readResponse()) {
            try {
                long size = Long.parseLong(_reader.readLine());
                _writer.println(RET_SYNC);
                _writer.flush();
                File archive = new File(path.getPath() + File.separator + ARCHIVE_FILE);
                if (!_isSecure) {
                    //toClose.add(Disk.deserializeFile(_dis, archive.getPath(), size, observer));
                    Disk.deserializeFile(_dis, archive.getPath(), size, observer);
                } else {
                    //toClose.add(Disk.deserializeEncryptedFile(_dis, archive.getPath(), size, _key, observer));
                    Disk.deserializeEncryptedFile(_dis, archive.getPath(), size, _key, observer);
                }
            } catch (Exception e) {
                _reason = e.toString();
                return false;
            }
            return true;
        }
        return false;
    }

    public synchronized boolean unreserve() throws Exception {
        log(">" + METHOD_UNRESERVE);
        _writer.println(METHOD_UNRESERVE);
        _writer.println(END_OF_REQ);
        _writer.flush();
        _reserved = !readResponse();
        log(" >> reserved=" + _reserved);
        return !_reserved;
    }
}
