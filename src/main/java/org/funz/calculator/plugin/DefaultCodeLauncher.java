package org.funz.calculator.plugin;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import org.funz.Constants;
import org.funz.util.ParserUtils;
import static org.funz.util.ParserUtils.getASCIIFileLines;

/**
 * Offers classical code launching as system command (using bash or cmd.exe).
 * Includes standard and error streams redirection to out.txt and err.txt files.
 */
public class DefaultCodeLauncher extends CodeLauncher {

    public DefaultCalculatorPlugin _plugin;
    protected String _command, _reason = "no reason";
    public File _dir;
    public List _files;
    protected int _retCode = 0;
    boolean debug = new File(Constants.APP_INSTALL_DIR.getPath() + File.separator + "debug").exists();
    public OutputReader _progressSender;
    Thread shutdown;
    CalculatorTunnel tunnel;

    public DefaultCodeLauncher(DefaultCalculatorPlugin plugin) {
        _plugin = plugin;
        try{
            tunnel = new FTPCalculatorTunnel(_plugin._secured);
        }catch(Error e){
            System.err.println("Could not instanciate FTP Calculator Tunnel");
        }
        _plugin.setDataChannel(new CalculatorTunnel.DataChannelTunnel(_plugin.getDataChannel(), tunnel));
        shutdown = new Thread(new Runnable() {
            public void run() {
                stopRunning();
                if (tunnel!=null) tunnel.stop();
            }
        });
        Runtime.getRuntime().addShutdownHook(shutdown);
    }

    /**
     * Start the execution of the code. By default, uses directory size as a
     * progress information.
     *
     * @return int status of execution
     */
    protected int execute() throws Exception {
        if (_progressSender == null) {
            _progressSender = new DataVolumeReader(this);
        }
        _progressSender.start();
        tunnel.start();
        return runCommand();
    }

    /**
     * to overload the progress information (implements OutputReader)
     */
    public void setOutputReader(OutputReader outreader) {
        _progressSender = outreader;
    }

    /**
     * by default, cat the first file name with the command string
     */
    public String buildCommand() {
        StringBuilder sb = new StringBuilder();
        sb.append(_command);
        sb.append(" ");
        sb.append(((File) (_files.get(0))).getName());
        return sb.toString();
    }
    org.funz.util.Process _process;
    String outName = "out.txt", errName = "err.txt", logName = "log.txt";

    /**
     * Called by execute() when the code is launched by a script. The first file
     * of the project is used as the (only) argument of the script. To be
     * overloaded for special cases, for instance when the script asks for
     * several arguments, or some options.
     *
     * @return int return status of the process
     */
    protected int runCommand() throws Exception {
        File prebat = new File(_dir, "PRE.bat");
        if (prebat.isFile()) {
            try {
                Runtime.getRuntime().exec("cmd.exe " + prebat.getName());
            } catch (Exception e) {
                System.err.println("could not launch PRE.bat:\n" + e);
            }
        }

        File presh = new File(_dir, "PRE.sh");
        if (presh.isFile()) {
            try {
                Runtime.getRuntime().exec("/bin/sh " + presh.getName());
            } catch (Exception e) {
                System.err.println("could not launch PRE.sh:\n" + e);
            }
        }

        File prepy = new File(_dir, "PRE.py");
        if (prepy.isFile()) {
            if (ParserUtils.getASCIIFileContent(prepy).contains("print(")) {
                try {
                    Runtime.getRuntime().exec("python3 " + prepy.getName());
                } catch (Exception e) {
                    System.err.println("could not launch PRE.py:\n" + e);
                }
            } else if (ParserUtils.getASCIIFileContent(prepy).contains("print ")) {
                try {
                    Runtime.getRuntime().exec("python2 " + prepy.getName());
                } catch (Exception e) {
                    System.err.println("could not launch PRE.py:\n" + e);
                }
            } else {
                try {
                    Runtime.getRuntime().exec("python " + prepy.getName());
                } catch (Exception e) {
                    System.err.println("could not launch PRE.py:\n" + e);
                }
            }
        }

        //System.err.println("runCommand " + Arrays.asList(_dir.listFiles()));
        _process = new org.funz.util.Process(buildCommand(), _dir, _plugin.getVariables()) {
            @Override
            public void over(int i) {
                try {
                    executionOver(i);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        };
        int ret = _process.runCommand(new FileOutputStream(_dir + File.separator + outName), new FileOutputStream(_dir + File.separator + errName), new FileOutputStream(_dir + File.separator + logName));
        _reason = _process.getFailReason();

        if (ret == 0) {
            File postbat = new File(_dir, "POST.bat");
            if (postbat.isFile()) {
                try {
                    Runtime.getRuntime().exec("cmd.exe " + postbat.getName());
                } catch (Exception e) {
                    System.err.println("could not launch POST.bat:\n" + e);
                }
            }

            File postsh = new File(_dir, "POST.sh");
            if (postsh.isFile()) {
                try {
                    Runtime.getRuntime().exec("/bin/sh " + postsh.getName());
                } catch (Exception e) {
                    System.err.println("could not launch POST.sh:\n" + e);
                }
            }

            File postpy = new File(_dir, "POST.py");
            if (postpy.isFile()) {
                if (ParserUtils.getASCIIFileContent(postpy).contains("print(")) {
                try {
                    Runtime.getRuntime().exec("python3 " + postpy.getName());
                } catch (Exception e) {
                    System.err.println("could not launch POST.py:\n" + e);
                }
            } else if (ParserUtils.getASCIIFileContent(postpy).contains("print ")) {
                try {
                    Runtime.getRuntime().exec("python2 " + postpy.getName());
                } catch (Exception e) {
                    System.err.println("could not launch POST.py:\n" + e);
                }
            } else {
                try {
                    Runtime.getRuntime().exec("python " + postpy.getName());
                } catch (Exception e) {
                    System.err.println("could not launch POST.py:\n" + e);
                }
            }
            }
        }
        if (tunnel!=null) tunnel.stop();
        return ret;
    }

    /**
     * Called each time the execution is over.
     *
     * @param int exitCode return status.
     */
    public void executionOver(int exitCode) throws Exception {
        if (tunnel != null) {
            tunnel.stop();
        }
        if (_progressSender != null) {
            _progressSender.askToStop();
            _progressSender.join();
        }
        if (shutdown != null && !shutdown.isAlive()) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdown);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public boolean failed() {
        return _retCode != 0;
    }

    public File getDirectory() {
        return _dir;
    }

    public List getInputFiles() {
        return _files;
    }

    public String getReason() {
        return _reason;
    }

    /**
     * Thread's main loop.
     */
    public void run() {
        try {
            _retCode = execute();
        } catch (Exception e) {
            //e.printStackTrace();
            _reason = e.getMessage() == null ? e.toString() : e.getMessage();
            _retCode = -1;
        }
    }

    public void setExecutionParameters(String command, File dir, List files) {
        _command = command.replace("/",File.separator).replace("\\", File.separator);
        _dir = dir;
        _files = files;
        if (tunnel != null) {
            ((FTPCalculatorTunnel) tunnel).setParameters(_plugin.code, _plugin.user, _dir);
        }
    }

    /**
     * Method to cancel an execution, for instance when the user press the
     * "stop" button on the GUI. This extended version of the method tries to
     * stop the running calculation process using these steps:
     * <br/>1. try to kill with Process.destroy method, and closes the
     * out/err/in streams.
     * <br/>2. try to find a STOP.bat file to launch using cmd.exe
     * <br/>3. try to find a STOP.sh file to launch using cmd.exe
     * <br/>4. try to find a PID file, and call "taskkill /PID pid /T /F" (or
     * kill -2, -15, -9 in Unix) for each integer in this file.
     */
    public void stopRunning() {
        if (tunnel != null) {
            tunnel.stop();
            tunnel = null;
        }        
        if (_progressSender != null) {
            _progressSender.askToStop();
        }

        if (_process != null) {
            _process.stop();
        }

        File killbat = new File(_dir, "STOP.bat");
        if (killbat.isFile()) {
            try {
                Runtime.getRuntime().exec("cmd.exe " + killbat.getName());
            } catch (Exception e) {
                System.err.println("could not launch STOP.bat:\n" + e);
            }
            killbat.delete();
        }

        File killsh = new File(_dir, "STOP.sh");
        if (killsh.isFile()) {
            try {
                Runtime.getRuntime().exec("/bin/sh " + killsh.getName());
            } catch (Exception e) {
                System.err.println("could not launch STOP.sh:\n" + e);
            }
            killsh.delete();
        }

        File pid = new File(_dir, "PID");
        if (pid.isFile()) {
            String[] pids = getASCIIFileLines(pid);
            for (int i = 0; i < pids.length; i++) {
                System.err.println("Killing PID " + pids[i]);
                int p = 0;
                try {
                    p = Integer.parseInt(pids[i]);
                } catch (NumberFormatException nfe) {
                    continue;
                }
                if (System.getProperty("os.name").toLowerCase().indexOf("windows") > 0) {
                    exec("taskkill /PID " + p + " /T /F");
                } else {
                    exec("kill -2 " + p);
                    exec("kill -15 " + p);
                    exec("kill -9 " + p);
                }

            }
            pid.delete();
        }
        if (shutdown != null && !shutdown.isAlive()) {
            Runtime.getRuntime().removeShutdownHook(shutdown);
        }
    }

    private void exec(String cmd) {
        System.err.println("exec: " + cmd);
        try {
            Runtime.getRuntime().exec(cmd);
        } catch (Exception e) {
            System.err.println("could not exec: " + e);
        }
    }
}
