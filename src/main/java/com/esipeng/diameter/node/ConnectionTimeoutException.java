package com.esipeng.diameter.node;

import java.util.concurrent.TimeoutException;

public class ConnectionTimeoutException
        extends TimeoutException {
    public ConnectionTimeoutException(String msg) {
        super(msg);
    }
}

