/*
 * Created on 13 févr. 2006 by richet
 */
package org.funz.codes;

import java.io.File;
import java.util.Properties;
import org.funz.calculator.plugin.CodeLauncher;
import org.funz.calculator.plugin.DataChannel;
import org.funz.calculator.plugin.DefaultCalculatorPlugin;
import org.funz.calculator.plugin.DefaultCodeLauncher;
import org.funz.calculator.plugin.OutputReader;
import org.funz.util.ASCII;
import static org.funz.util.ParserUtils.countLines;
import static org.funz.util.ParserUtils.getASCIIFileLines;

public class CatnCount_CPlugin extends DefaultCalculatorPlugin {

    public class CatnCountLauncher extends DefaultCodeLauncher {

        CatnCountLauncher(CatnCount_CPlugin plugin) {
            super(plugin);
        }

        public int execute() throws Exception {
            _progressSender = new CatReader(this);
            _progressSender.start();

            File f = (File) _files.get(0);
            _dir = getDirectory();

            String outName = "out.txt";
            while (f.getName().equals(outName)) {
                outName = "_" + outName;
            } //$NON-NLS-1$
            File out = new File(_dir, "out.txt");

            System.out.println("> Input file: " + f.getAbsolutePath());
            System.out.println("> Output file: " + out.getAbsolutePath());

            String[] lines = getASCIIFileLines(f);
            StringBuffer catlines = new StringBuffer();
            //System.out.println("/ "+lines.length);
            for (int i = 0; i < lines.length; i++) {
                if (_progressSender._stopMe) {
                    return -1;
                }
                //System.out.println("* "+i);
                catlines.append("> " + lines[i] + "\n");
                ASCII.saveFile(out, catlines.toString());
                //System.out.println(">/ "+ASCII.countLines(out, "", true));
                Thread.sleep(10);
            }

            return 0;
        }

        public class CatReader extends OutputReader {

            public CatReader(DefaultCodeLauncher l) {
               super(l);
                _information = "?";
            }

            public void run() {
                if (getDataChannel() == null) {
                    return;
                }
                while (!_stopMe) {
                    synchronized (this) {
                        try {
                            wait(1000);
                        } catch (Exception e) {
                        }
                    }

                    File out = new File(_dir, "out.txt");
                    if (out.exists()) {
                        _information = "" + countLines(out, "", true);
                    } else {
                        _information = "0";
                    }

                    System.out.println("> Information sent : " + _information);

                    if (!getDataChannel().sendInfomationLineToConsole(_information)) {
                        break;
                    }
                }
            }
        }
        //File _dir;
    }

    public CatnCount_CPlugin() {
    }

    public CodeLauncher createCodeLauncher(Properties variables, DataChannel channel) {
        return new CatnCountLauncher(this);
    }
}