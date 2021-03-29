package com.tssaumi.tcpredirect;

public class RedirectTarget {
    public String channel = null;	// must be global unique
    public String host = null;
    public int port = -1;

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Target[channel=");
        sb.append(channel);
        sb.append(" ==> ");
        sb.append(host);
        sb.append(":");
        sb.append(port);
        sb.append("]");
        return sb.toString();
    }
}
