package com.esipeng.diameter.node;


public class EmptyHostNameException
        extends Exception {
    public EmptyHostNameException() {
    }


    public EmptyHostNameException(String message) {
        super(message);
    }

    public EmptyHostNameException(Throwable throwable) {
        super(throwable);
    }
}

