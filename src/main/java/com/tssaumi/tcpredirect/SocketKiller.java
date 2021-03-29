package com.tssaumi.tcpredirect;

import java.io.*;
import java.net.*;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SocketKiller extends Thread {
	private ServerSocket serverSocket = null;
    private Socket socket = null;
    private InputStream is = null;
    private OutputStream os = null;

    /**
     * Start a new thread to close server socket.
     */
    public static void killServerSocket(ServerSocket serverSocket) {
        SocketKiller killer = new SocketKiller(serverSocket, null, null, null);
        killer.start();
    }
    
    /**
     * Start a new thread to close socket and its corresponding in/out stream.
     */
    public static void killSocket(Socket socket, InputStream is, OutputStream os) {
        SocketKiller killer = new SocketKiller(null, socket, is, os);
        killer.start();
    }
    
    private SocketKiller(ServerSocket serverSocket, Socket socket, InputStream is, OutputStream os) {
    	this.serverSocket = serverSocket;
        this.socket = socket;
        this.is = is;
        this.os = os;
    }
    
    public void run() {
        try {
            if(is != null) {
                is.close();
            }
        } catch(Exception e) {
            log.error("Fail to close input stream! socket={}", socket, e);
        }
        try {
            if(os != null) {
                os.close();
            }
        } catch(Exception e) {
            log.error("Fail to close output stream! socket={}", socket, e);
        }
        try {
            if(socket != null) {
                socket.close();
            }
        } catch(Exception e) {
            log.error("Fail to close socket! socket={}", socket, e);
        }
        try {
            if(serverSocket != null) {
                serverSocket.close();
                log.info("Server socket closed.");
            }
        } catch(Exception e) {
            log.error("Fail to close server socket! serverSocket={}", serverSocket, e);
        }
    }
}