package com.esipeng.diameter.node;


import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;

import com.sun.nio.sctp.SctpChannel;

class SCTPConnection extends Connection {
    MySCTPNode node_impl;
    SctpChannel channel;
    ConnectionBuffers connection_buffers;

    public SCTPConnection(MySCTPNode sctpNode, long watchDog, long idle) {
        super(sctpNode, watchDog, idle);
        this.node_impl = sctpNode;
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

    InetAddress toInetAddress() {
        Iterator<SocketAddress> it;
        try {
            it = this.channel.getRemoteAddresses().iterator();
            InetSocketAddress addr = (InetSocketAddress) it.next();
            return addr.getAddress();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    void sendMessage(byte[] bytes) {
        this.node_impl.sendMessage(this, bytes);
    }

    Object getRelevantNodeAuthInfo() {
        return this.channel;
    }

    java.util.Collection<InetAddress> getLocalAddresses() {
        ArrayList addressList = new ArrayList();
        try {

            Iterator<SocketAddress> it = this.channel.getAllLocalAddresses().iterator();
            while (it.hasNext()) {
                SocketAddress sa = it.next();
                InetSocketAddress isa = (InetSocketAddress) sa;
                addressList.add(isa.getAddress());
            }
            return addressList;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;

    }

    Peer toPeer() {
        //Iterator<SocketAddress>  it = this.channel.getRemoteAddresses();
        //InetAddress addr = (InetAddress)it.next();
        return new Peer(toInetAddress(), 3872);
    }
}

