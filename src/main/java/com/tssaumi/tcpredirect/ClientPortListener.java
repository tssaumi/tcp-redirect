package com.tssaumi.tcpredirect;

import lombok.extern.slf4j.Slf4j;

import java.net.Socket;

@Slf4j
public class ClientPortListener extends PortListener {

    protected String channelId = null;
    public ClientPortListener(String channelId, String bindAddr, int localPort) {
        super("Ch:"+channelId, bindAddr, localPort);
        this.channelId = channelId;
    }

    protected void handShake(Socket socket) {
    }
}
