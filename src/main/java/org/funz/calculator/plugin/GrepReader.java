package org.funz.calculator.plugin;

import java.io.File;
import java.io.FileFilter;
import org.funz.util.ASCII;
import static org.funz.util.ParserUtils.getLastLineContaining;

/** Thread to follow progress by reading a matching line from a given file pattern (similar to grep usage)
 * @author richet
 */
public class GrepReader extends OutputReader {

    String _fileMatchPatern;
    String _linecontent;

    public GrepReader(DefaultCodeLauncher l, String fileMatchPatern, String linecontent) {
        super(l);
        _fileMatchPatern = fileMatchPatern;
        _linecontent = linecontent;
        _information = "-";
    }

    public void run() {
        if (_launcher._plugin.getDataChannel() == null) {
            return;
        }
        while (!_stopMe) {
            synchronized (this) {
                try {
                    wait(5000);
                } catch (Exception e) {
                }
            }

            File[] matchfiles = _launcher._dir.listFiles(new FileFilter() {

                public boolean accept(File pathname) {
                    return pathname.getName().indexOf(_fileMatchPatern) >= 0;
                }
            });

            if (matchfiles != null && matchfiles.length == 1) {
                _information = getLastLineContaining(matchfiles[0], _linecontent);
            }

            if (!_launcher._plugin.getDataChannel().sendInfomationLineToConsole(_information)) {
                break;
            }
        }
    }
}
