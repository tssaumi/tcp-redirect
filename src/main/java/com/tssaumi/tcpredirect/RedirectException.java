package com.tssaumi.tcpredirect;

public class RedirectException extends Exception {

    public RedirectException(String msg, Throwable cause) {
        super(msg, cause);
    }

    public RedirectException(String msg) {
        super(msg);
    }

}
