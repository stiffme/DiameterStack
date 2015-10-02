package com.esipeng.diameter.node;


public class NotRoutableException
        extends Exception {
    public NotRoutableException() {
    }


    public NotRoutableException(String message) {
        super(message);
    }

    public NotRoutableException(Throwable message) {
        super(message);
    }
}

