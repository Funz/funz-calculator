package org.funz.calculator.plugin;

import org.funz.util.Disk;

/** Thread to follow progress by returning current working dir size.
 * @author richet
 */
public class DataVolumeReader extends OutputReader {
    
    public DataVolumeReader(DefaultCodeLauncher l) {
        super(l);
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
                } catch (InterruptedException e) {
                }
            }
            try {
            _information = Disk.getHumanFileSizeString(Disk.getDirSize(_launcher._dir)) + " of data...";

            } catch (Exception e) {
                _information = "?";
            }

            if (!_launcher._plugin.getDataChannel().sendInfomationLineToConsole(_information)) {
                break;
            }
        }
    }
}
