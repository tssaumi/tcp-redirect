package com.tssaumi.tcpredirect;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Flow:
 * <OL>
 *     <LI>Client side send request message: [CHANNEL ID](padding space)</LI>
 * </OL>
 */
@Slf4j
public class HandShakeThread extends Thread {
    protected Socket socket = null;
    protected final AtomicLong connCount = new AtomicLong(1);

    protected RedirectTarget target = null;

    public HandShakeThread(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        InputStream is = null;
        OutputStream os = null;
        Socket serverSocket = null;
        try {
            is = socket.getInputStream();
            os = socket.getOutputStream();

            // hand shaking
            handShake(socket, is, os);

            // start redirecting ...
            serverSocket = new Socket(target.host, target.port);
            log.info("Server connected: {}", serverSocket);
            long uid = connCount.getAndIncrement();
            TCPFwd tcpFwd = new TCPFwd(uid, socket, serverSocket, false, false);

        } catch(Exception e) {
            log.error("Fail hand shake with client! Drop socket: {}", socket, e);
            SocketKiller.killSocket(socket, is, os);
        }
    }

    protected RedirectTarget getTarget(String channelId) {
        return TCPRedirectServer.getTarget(channelId);
    }

    protected void handShake(Socket socket, InputStream is, OutputStream os) throws IOException {
        long startTime = System.nanoTime();

        socket.setSoTimeout(Constants.MAX_HAND_SHAKE_IDLE_MS);

        // wait for message, which is fixed 50 byte length;
        byte[] reqMsg = new byte[Constants.HAND_SHAKE_MSG_SIZE];
        int readSize = 0;
        while(readSize < Constants.HAND_SHAKE_MSG_SIZE) {
            long curTime = System.nanoTime();
            if((curTime - startTime) > Constants.MAX_HAND_SHAKE_IDLE_MS * 1_000_000L) {
                // timeout!
                log.error("Timeout during hand shaking. Elapsed Time({})", Util.elapsedTimeUs(startTime, curTime));
                throw new IOException("Timeout during hand shaking. Elapsed Time("+Util.elapsedTimeUs(startTime, curTime)+")");
            }
            int len = is.read(reqMsg, readSize, reqMsg.length - readSize);
            if(len < 0) {
                // end of stream detetced!
                throw new IOException("Read "+len+" from input stream during hand shaking.");
            } else {
                readSize += len;
            }
        }
        // check request message
        if(readSize != Constants.HAND_SHAKE_MSG_SIZE) {
            // invalid request message size
            throw new IOException("Invalid request message size: size="+readSize);
        }
        if(reqMsg[Constants.HAND_SHAKE_MSG_SIZE - 1] != Constants.HAND_SHAKE_MSG_DELIMITER) {
            // Invalid request message delimiter
            throw new IOException("Invalid request message. Delimiter: "+reqMsg[Constants.HAND_SHAKE_MSG_SIZE - 1]);
        }
        String reqMsgStr = new String(reqMsg, 0, Constants.HAND_SHAKE_MSG_SIZE - 1, StandardCharsets.UTF_8);
        String channelId = reqMsgStr.trim();
        log.info("Socket {}: Channel ID: {}", socket, channelId);

        this.target = getTarget(channelId);
        if(target == null) {
            // invalid channel ID
            throw new IOException("Invalid channel ID: "+channelId);
        }

        byte[] respBytes = new byte[Constants.HAND_SHAKE_MSG_SIZE];
        Arrays.fill(respBytes, (byte)0x20);
        byte[] addrBytes = ("" + target.host + ":" + target.port).getBytes(StandardCharsets.UTF_8);
        for(int i=0; i < addrBytes.length; i++) {
            respBytes[i] = addrBytes[i];
        }
        respBytes[respBytes.length - 1] = Constants.HAND_SHAKE_MSG_DELIMITER;
        os.write(respBytes, 0, Constants.HAND_SHAKE_MSG_SIZE);
        os.flush();

        socket.setSoTimeout(0);
    }

}
