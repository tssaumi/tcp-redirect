package com.tssaumi.tcpredirect;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@Slf4j
public class ClientHandShakeThread extends HandShakeThread {

    public ClientHandShakeThread(Socket socket, RedirectTarget target) {
        super(socket);
        this.target = target;
    }

    protected void handShake(Socket socket, InputStream is, OutputStream os) throws IOException {
        long startTime = System.nanoTime();

        socket.setSoTimeout(Constants.MAX_HAND_SHAKE_IDLE_MS);

        // send request message
        byte[] reqMsg = new byte[Constants.HAND_SHAKE_MSG_SIZE];
        Arrays.fill(reqMsg, (byte)0x20);
        byte[] channelBytes = target.channel.getBytes(StandardCharsets.UTF_8);
        for(int i=0; i < channelBytes.length; i++) {
            reqMsg[i] = channelBytes[i];
        }
        reqMsg[reqMsg.length - 1] = Constants.HAND_SHAKE_MSG_DELIMITER;

        os.write(reqMsg);
        os.flush();

        // wait for message, which is fixed 50 byte length;
        byte[] respMsg = new byte[Constants.HAND_SHAKE_MSG_SIZE];
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
            throw new IOException("Invalid response message size: size="+readSize);
        }
        if(respMsg[Constants.HAND_SHAKE_MSG_SIZE - 1] != Constants.HAND_SHAKE_MSG_DELIMITER) {
            // Invalid request message delimiter
            throw new IOException("Invalid response message. Delimiter: "+respMsg[Constants.HAND_SHAKE_MSG_SIZE - 1]);
        }
        log.info("Socket {}: Hand shaking OK!", socket);

        socket.setSoTimeout(0);
    }
}
