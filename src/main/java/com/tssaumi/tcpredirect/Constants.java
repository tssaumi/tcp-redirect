package com.tssaumi.tcpredirect;

public class Constants {

    public static final String CLIENT_PROP_FILE = "client.properties";
    public static final String SERVER_PROP_FILE = "server.properties";

    /**
     * Max hand shake idle in ms (default 10s)
     */
    public static final int MAX_HAND_SHAKE_IDLE_MS = 10000;

    /**
     * Fixed-length hand shaking message, for both client and server side.
     */
    public static final int HAND_SHAKE_MSG_SIZE = 50;

    /**
     * Delimiter of hand shaking message. (0x03 STX)
     */
    public static final byte HAND_SHAKE_MSG_DELIMITER = 0x03;
}
