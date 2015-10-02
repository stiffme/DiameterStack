package com.esipeng.diameter.node;

public abstract interface ConnectionListener {
    public abstract void handle(ConnectionKey key, Peer peer, boolean add);
}

