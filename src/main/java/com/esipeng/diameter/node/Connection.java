package com.esipeng.diameter.node;

import java.util.Random;

abstract class Connection {
    NodeImplementation node_impl;
    public Peer peer;
    public String host_id;
    public ConnectionTimers timers;
    public ConnectionKey key;
    private int hop_by_hop_identifier_seq;
    public State state;

    public static enum State {
        connecting,
        connected_in,
        connected_out,
        tls,
        ready,
        closing,
        closed;

        private State() {
        }
    }

    public Connection(NodeImplementation nodeImplement, long watchdogTimer, long idleTimer) {
        this.node_impl = nodeImplement;
        this.timers = new ConnectionTimers(watchdogTimer, idleTimer);
        this.key = new ConnectionKey();
        this.hop_by_hop_identifier_seq = new Random().nextInt();
        this.state = State.connected_in;
    }

    public synchronized int nextHopByHopIdentifier() {
        return this.hop_by_hop_identifier_seq++;
    }

    abstract java.net.InetAddress toInetAddress();

    abstract void sendMessage(byte[] paramArrayOfByte);

    abstract Object getRelevantNodeAuthInfo();

    abstract java.util.Collection<java.net.InetAddress> getLocalAddresses();

    abstract Peer toPeer();

    long watchdogInterval() {
        return this.timers.cfg_watchdog_timer;
    }
}
