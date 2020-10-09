/*
 * Created on 25 juin 07 by richet
 */
package org.funz.calculator.network;

import org.funz.calculator.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;
import java.net.BindException;

import org.w3c.dom.Element;

public class Host {
    
    String address, ip, state = "available";
    private final Calculator calculator;
    public boolean connected = false;
    InetSocketAddress isa;
    DatagramPacket packet;
    int port;
    boolean reachable = false;
    DatagramSocket socket;
    
    public Host(Calculator calculator, Element e) {
        this.calculator = calculator;
        address = e.getAttribute(Calculator.ATTR_NAME);
        port = Integer.parseInt(e.getAttribute(Calculator.ATTR_PORT));
        buildSocket(address, port);
    }
    
    public Host(Calculator calculator, String address_port) {
        this.calculator = calculator;
        address = address_port.split(":")[0];
        port = Integer.parseInt(address_port.split(":")[1]);
        buildSocket(address, port);
    }
    
    private void buildSocket(final String _address, final int _port) {
        try {
            isa = new InetSocketAddress(InetAddress.getByName(_address), _port);
            if (socket!=null) socket.close();
            if (address.startsWith("239.")) {
                socket = new MulticastSocket() {
                    @Override
                    public String toString() {
                        return "Host socket: "+_address+":"+_port+" "+super.toString();
                    }
                };
                ((MulticastSocket) socket).setTimeToLive(16);
            } else {
                socket = new DatagramSocket() {
                    @Override
                    public String toString() {
                        return "Host socket: "+_address+":"+_port+" "+super.toString();
                    }
                };
            }
            reachable = true;
        } catch (UnknownHostException uhe) {
            calculator.err("host not found: " + _address);
        } catch (BindException ex) {
            calculator.err("Address already in use: " +_address+ ":" +_port +" or no more UDP ports available on system (check your limitations 'sysctl net.ipv4.ip_local_port_range')");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    volatile boolean rebuildPacket = true;

    @Override
    public void finalize() throws Throwable {
        if (socket!=null) 
            socket.close();
    
        super.finalize(); //To change body of generated methods, choose Tools | Templates.
    }
    
    synchronized void buildPacket() throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append(this.calculator._name);
        sb.append('\n');
        sb.append(this.calculator._port);
        sb.append('\n');
        sb.append(this.calculator._since);
        sb.append('\n');
        sb.append(System.getProperty("os.name"));
        sb.append(" ");
        sb.append(System.getProperty("os.version"));
        sb.append('\n');
        sb.append(this.calculator.getActivity());
        sb.append('\n');
        sb.append(calculator._codes.length);
        for (int i = 0; i < calculator._codes.length; i++) {
            sb.append('\n');
            sb.append(this.calculator._codes[i].name);
        }
        sb.append('\n');
        
        byte data[] = sb.toString().getBytes();
        packet = new DatagramPacket(data, data.length, isa);
        packet.setData(data);
    }
    
    public synchronized void ping() {
        try {
            if (reachable) {
                if (rebuildPacket) {
                    buildPacket();
                    rebuildPacket = false;
                }
                //System.err.println("[Send packet]@"+this.address+" : \n"+new String(packet.getData()));
                socket.send(packet);
            }
        } catch (Exception e) {
            //System.err.println("Cannot ping "+address+" : \n"+e);
        }
    }
    
    public void rebuildPacket() {
        rebuildPacket = true;
    }
}
