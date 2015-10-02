package com.esipeng.diameter.node;

import java.net.Socket;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;

class TCPConnection extends Connection {
    TCPNode node_impl;
    SocketChannel channel;
    ConnectionBuffers connection_buffers;

    public TCPConnection(TCPNode tcpNode, long watchDog, long idle) {
        super(tcpNode, watchDog, idle);
        this.node_impl = tcpNode;
        this.connection_buffers = new NormalConnectionBuffers();
    }

    void makeSpaceInNetInBuffer() {
        this.connection_buffers.makeSpaceInNetInBuffer();
    }

    void makeSpaceInAppOutBuffer(int len) {
        this.connection_buffers.makeSpaceInAppOutBuffer(len);
    }

    void consumeAppInBuffer(int len) {
        this.connection_buffers.consumeAppInBuffer(len);
    }

    void consumeNetOutBuffer(int len) {
        this.connection_buffers.consumeNetOutBuffer(len);
    }

    boolean hasNetOutput() {
        return this.connection_buffers.netOutBuffer().position() != 0;
    }

    void processNetInBuffer() {
        this.connection_buffers.processNetInBuffer();
    }

    void processAppOutBuffer() {
        this.connection_buffers.processAppOutBuffer();
    }

    java.net.InetAddress toInetAddress() {
        return ((java.net.InetSocketAddress) this.channel.socket().getRemoteSocketAddress()).getAddress();
    }

    void sendMessage(byte[] bytes) {
        this.node_impl.sendMessage(this, bytes);
    }

    Object getRelevantNodeAuthInfo() {
        return this.channel;
    }

    java.util.Collection<java.net.InetAddress> getLocalAddresses() {
        ArrayList addressList = new ArrayList();
        addressList.add(this.channel.socket().getLocalAddress());
        return addressList;
    }

    Peer toPeer() {
        return new Peer(toInetAddress(), this.channel.socket().getPort());
    }
}
