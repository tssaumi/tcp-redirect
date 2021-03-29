package com.tssaumi.tcpredirect;

import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

@Slf4j
public class PortListener extends Thread {
    protected volatile boolean running = false;

    protected String bindAddr = null;
    protected int localPort = -1;
    protected ServerSocket ss = null;

    public PortListener(String threadName, String bindAddr, int localPort) {
        super(threadName);
        this.bindAddr = bindAddr;
        this.localPort = localPort;
    }

    public PortListener(String bindAddr, int localPort) {
        super("ServerPortListener");
        this.bindAddr = bindAddr;
        this.localPort = localPort;
    }

    protected void closeServerSocket() {
        if(ss != null) {
            SocketKiller.killServerSocket(ss);
            ss = null;
        }
    }

    protected void createServerSocket() throws Exception {
        closeServerSocket();
        if(bindAddr != null && bindAddr.length() > 0) {
            InetAddress[] addrs = InetAddress.getAllByName(bindAddr);
            ss = new ServerSocket(localPort, 3, addrs[0]);
        } else {
            ss = new ServerSocket(localPort);
        }
        log.info("Server socket ready.");
    }

    public void start() {
        running = true;
        while(running) {
            try {
                createServerSocket();
                while (running) {
                    try {
                        Socket socket = ss.accept();
                        handShake(socket);
                    } catch (Exception e) {
                        log.error("Fail to accept new socket!", e);
                        break;
                    }
                }
            } catch(Exception e) {
                log.error("Fail to create server socket!", e);
            } finally {
                try {
                    log.info("Sleep 3s before listen to target port again ...");
                    closeServerSocket();
                    sleep(3000);
                } catch(Exception e) {
                    log.error("Fail to sleep.", e);
                }
            }
        }
    }

    protected void handShake(Socket socket) {
        HandShakeThread hs = new HandShakeThread(socket);
        hs.start();
    }
}
