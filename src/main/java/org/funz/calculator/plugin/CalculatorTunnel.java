package org.funz.calculator.plugin;

import java.io.File;

/**
 *
 * @author richet
 */
public interface CalculatorTunnel {

    public void start();

    public void stop();

    public String addTunnelInformation(String data);

    public static class DataChannelTunnel implements DataChannel {

        DataChannel source;
        CalculatorTunnel tunnel;

        DataChannelTunnel(DataChannel source, CalculatorTunnel tunnel) {
            this.source = source;
            this.tunnel = tunnel;
        }

        public boolean sendInfomationLineToConsole(String string) {
            if (source == null) {
                return false;
            }
            if (tunnel == null) {
                return false;
            }
            return source.sendInfomationLineToConsole(tunnel.addTunnelInformation(string));
        }
    }
}
