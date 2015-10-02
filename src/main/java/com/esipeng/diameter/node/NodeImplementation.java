package com.esipeng.diameter.node;

import org.slf4j.Logger;

import com.esipeng.diameter.InvalidAVPLengthException;
import com.esipeng.diameter.Message;

abstract class NodeImplementation {
    protected NodeSettings settings;
    protected Logger logger;
    private Node node;

    NodeImplementation(Node node, NodeSettings settings, Logger logger) {
        this.node = node;
        this.settings = settings;
        this.logger = logger;
    }

    abstract void openIO() throws java.io.IOException;

    abstract void start();

    abstract void wakeup();

    abstract void initiateStop(long expire);

    abstract void join();

    abstract void closeIO();

    abstract boolean initiateConnection(Connection connection, Peer peer);

    //abstract boolean initiateConnection(Connection connection, Peer paramPeer,String realAddress);
    abstract void close(Connection connection, boolean quick);

    abstract Connection newConnection(long watchDog, long idle);

    boolean anyOpenConnections() {
        return this.node.anyOpenConnections(this);
    }

    void registerInboundConnection(Connection connection) {
        this.node.registerInboundConnection(connection);
    }

    void unregisterConnection(Connection connection) {
        this.node.unregisterConnection(connection);
    }

    long calcNextTimeout() {
        return this.node.calcNextTimeout(this);
    }

    void closeConnection(Connection connection) {
        this.node.closeConnection(connection);
    }

    void closeConnection(Connection connection, boolean quick) {
        this.node.closeConnection(connection, quick);
    }

    boolean handleMessage(Message message, Connection connection) throws InvalidAVPLengthException {
        return this.node.handleMessage(message, connection);
    }

    void runTimers() {
        this.node.runTimers(this);
    }

    void logRawDecodedPacket(byte[] bytes, int start, int len) {
        this.node.logRawDecodedPacket(bytes, start, len);
    }

    void logGarbagePacket(Connection connection, byte[] bytes, int start, int len) {
        this.node.logGarbagePacket(connection, bytes, start, len);
    }

    Object getLockObject() {
        return this.node.getLockObject();
    }

    void initiateCER(Connection connection) throws InvalidAVPLengthException {
        this.node.initiateCER(connection);
    }
}
