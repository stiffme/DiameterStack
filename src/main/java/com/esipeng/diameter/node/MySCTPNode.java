package com.esipeng.diameter.node;

import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;

import org.slf4j.Logger;

import com.esipeng.diameter.InvalidAVPLengthException;
import com.sun.nio.sctp.MessageInfo;
import com.sun.nio.sctp.SctpChannel;
import com.sun.nio.sctp.SctpServerChannel;
import com.sun.nio.sctp.SctpStandardSocketOptions;

class MySCTPNode extends NodeImplementation {
    private Thread node_thread;
    private Selector selector;
    private SctpChannel serverChannel;
    private boolean please_stop;
    private long shutdown_deadline;
    private MessageInfo messageInfo;

    public MySCTPNode(Node node, NodeSettings nodeSetting, Logger logger) {

        super(node, nodeSetting, logger);
        messageInfo = MessageInfo.createOutgoing(null, 0);
    }

    void openIO() throws java.io.IOException {
        this.selector = Selector.open();
        if (this.settings.port() != 0) {
            this.serverChannel = SctpChannel.open();


            serverChannel.bind(new java.net.InetSocketAddress(this.settings.port()));
        }
    }

    void start() {
        this.logger.debug("Starting SCTP node");
        this.please_stop = false;
        this.node_thread = new SelectThread();
        this.node_thread.setDaemon(true);
        this.node_thread.start();
        this.logger.debug("Started SCTP node");
    }

    void wakeup() {
        this.logger.debug("Waking up selector thread");
        this.selector.wakeup();
    }

    void initiateStop(long expire) {
        this.logger.debug("Initiating stop of SCTP node");
        this.please_stop = true;
        this.shutdown_deadline = expire;
        this.logger.debug("Initiated stop of SCTP node");
    }

    void join() {
        this.logger.debug("Joining selector thread");
        try {
            this.node_thread.join();
        } catch (InterruptedException localInterruptedException) {
        }
        this.node_thread = null;
        this.logger.debug("Selector thread joined");
    }

    void closeIO() {
        this.logger.debug("Closing server channel, etc.");
        if (this.serverChannel != null) {
            try {
                this.serverChannel.close();
            } catch (java.io.IOException localIOException1) {
            }
        }
        this.serverChannel = null;
        try {
            this.selector.close();
        } catch (java.io.IOException localIOException2) {
        }
        this.selector = null;
        this.logger.debug("Closed selector, etc.");
    }

    private class SelectThread
            extends Thread {
        public SelectThread() {
            super();
        }

        public void run() {
            try {
                run_();
                if (MySCTPNode.this.serverChannel != null)
                    MySCTPNode.this.serverChannel.close();
            } catch (java.io.IOException localIOException) {
            } catch (InvalidAVPLengthException e) {
                e.printStackTrace();
            }
        }

        private void run_() throws java.io.IOException, InvalidAVPLengthException {
            if (MySCTPNode.this.serverChannel != null) {
                MySCTPNode.this.serverChannel.configureBlocking(false);


                MySCTPNode.this.serverChannel.register(MySCTPNode.this.selector, 16);
            }
            for (; ; ) {
                if (MySCTPNode.this.please_stop) {
                    if (System.currentTimeMillis() >= MySCTPNode.this.shutdown_deadline)
                        break;
                    if (!MySCTPNode.this.anyOpenConnections())
                        break;
                }
                long l1 = MySCTPNode.this.calcNextTimeout();

                int i;
                if (l1 != -1L) {
                    long l2 = System.currentTimeMillis();
                    if (l1 > l2) {
                        i = MySCTPNode.this.selector.select(l1 - l2);
                    } else
                        i = MySCTPNode.this.selector.selectNow();
                } else {
                    i = MySCTPNode.this.selector.select();
                }


                Iterator<SelectionKey> selectionKeyIterator = MySCTPNode.this.selector.selectedKeys().iterator();

                while (selectionKeyIterator.hasNext()) {
                    SelectionKey localSelectionKey = selectionKeyIterator.next();
                    //Object socketChannel;
                    //Object connection;
                    if (localSelectionKey.isAcceptable()) {
                        MySCTPNode.this.logger.debug("Got an inbound connection (key is acceptable)");
                        SctpServerChannel sctpServerChannel = (SctpServerChannel) localSelectionKey.channel();
                        SctpChannel sctpChannel = ((SctpServerChannel) sctpServerChannel).accept();
                        Iterator<SocketAddress> it = sctpChannel.getRemoteAddresses().iterator();
                        SocketAddress sockAddr = it.next();

                        java.net.InetSocketAddress localInetSocketAddress = (java.net.InetSocketAddress) sockAddr;
                        MySCTPNode.this.logger.debug("Got an inbound connection from {}", localInetSocketAddress.toString());
                        if (!MySCTPNode.this.please_stop) {
                            SCTPConnection sctpConnection = new SCTPConnection(MySCTPNode.this, MySCTPNode.this.settings.watchdogInterval(), MySCTPNode.this.settings.idleTimeout());
                            sctpConnection.host_id = localInetSocketAddress.getAddress().getHostAddress();
                            sctpConnection.state = Connection.State.connected_in;
                            sctpConnection.channel = sctpChannel;
                            sctpChannel.configureBlocking(false);
                            sctpChannel.register(MySCTPNode.this.selector, 1, sctpConnection);

                            MySCTPNode.this.registerInboundConnection(sctpConnection);
                        } else {
                            sctpChannel.close();
                        }
                    } else if (localSelectionKey.isConnectable()) {
                        MySCTPNode.this.logger.debug("An outbound connection is ready (key is connectable)");
                        SctpChannel socketChannel = (SctpChannel) localSelectionKey.channel();
                        SCTPConnection connection = (SCTPConnection) localSelectionKey.attachment();
                        try {
                            if (socketChannel.finishConnect()) {
                                MySCTPNode.this.logger.debug("Connected!");
                                connection.state = Connection.State.connected_out;
                                socketChannel.register(MySCTPNode.this.selector, 1, connection);
                                MySCTPNode.this.initiateCER(connection);
                            }
                        } catch (java.io.IOException localIOException1) {
                            MySCTPNode.this.logger.warn("Connection to '{}' failed {}", connection.host_id, localIOException1);
                            try {
                                socketChannel.register(MySCTPNode.this.selector, 0);
                                socketChannel.close();
                            } catch (java.io.IOException localIOException2) {
                            }
                            MySCTPNode.this.unregisterConnection((Connection) connection);
                        }
                    } else if (localSelectionKey.isReadable()) {
                        MySCTPNode.this.logger.debug("Key is readable");

                        SctpChannel socketChannel = (SctpChannel) localSelectionKey.channel();
                        SCTPConnection connection = (SCTPConnection) localSelectionKey.attachment();
                        MySCTPNode.this.handleReadable(connection);
                        if ((connection.state != Connection.State.closed) && (connection.hasNetOutput())) {
                            (socketChannel).register(MySCTPNode.this.selector, 5, connection);
                        }
                    } else if (localSelectionKey.isWritable()) {
                        MySCTPNode.this.logger.debug("Key is writable");
                        SctpChannel socketChannel = (SctpChannel) localSelectionKey.channel();
                        SCTPConnection connection = (SCTPConnection) localSelectionKey.attachment();
                        synchronized (MySCTPNode.this.getLockObject()) {
                            MySCTPNode.this.handleWritable(connection);
                            if ((connection.state != Connection.State.closed) && (connection.hasNetOutput())) {
                                socketChannel.register(MySCTPNode.this.selector, 5, connection);
                            }
                        }
                    }

                    selectionKeyIterator.remove();
                }

                MySCTPNode.this.runTimers();
            }
        }
    }


    private void handleReadable(SCTPConnection paramSCTPConnection) throws InvalidAVPLengthException {
        this.logger.debug("handlereadable()...");
        paramSCTPConnection.makeSpaceInNetInBuffer();
        ConnectionBuffers localConnectionBuffers = paramSCTPConnection.connection_buffers;
        this.logger.debug("pre: conn.in_buffer.position=" + localConnectionBuffers.netInBuffer().position());
        int i;
        try {
            int j = 0;
            i = 0;
            //MessageInfo ms;
            do {
                MessageInfo ms = paramSCTPConnection.channel.receive(localConnectionBuffers.netInBuffer(), null, null);
                if (ms == null) {
                    this.logger.debug("null message info is returned");
                    break;
                }
                i = ms.bytes();
                this.logger.debug("readloop: connection_buffers.netInBuffer().position=" + localConnectionBuffers.netInBuffer().position());
                paramSCTPConnection.makeSpaceInNetInBuffer();
            } while (i > 0 && j++ < 3);

            //while (((i = paramSCTPConnection.channel.receive(localConnectionBuffers.netInBuffer(),null,null)) > 0) && (j++ < 3)) {
            //  this.logger.debug( "readloop: connection_buffers.netInBuffer().position=" + localConnectionBuffers.netInBuffer().position());
            //  paramSCTPConnection.makeSpaceInNetInBuffer();
            //}
        } catch (java.io.IOException localIOException) {
            this.logger.debug("got IOException", localIOException);
            closeConnection(paramSCTPConnection);
            return;
        }
        paramSCTPConnection.processNetInBuffer();
        processInBuffer(paramSCTPConnection);
        if ((i < 0) && (paramSCTPConnection.state != Connection.State.closed)) {
            this.logger.debug("count<0");
            closeConnection(paramSCTPConnection);
            return;
        }
    }

    private void processInBuffer(SCTPConnection paramSCTPConnection) throws InvalidAVPLengthException {
        java.nio.ByteBuffer localByteBuffer = paramSCTPConnection.connection_buffers.appInBuffer();
        this.logger.debug("pre: app_in_buffer.position=" + localByteBuffer.position());
        int i = localByteBuffer.position();
        byte[] arrayOfByte = new byte[i];
        localByteBuffer.position(0);
        localByteBuffer.get(arrayOfByte);
        localByteBuffer.position(i);
        int j = 0;

        while (j < arrayOfByte.length) {
            int k = arrayOfByte.length - j;
            if (k < 4) break;
            int m = com.esipeng.diameter.Message.decodeSize(arrayOfByte, j);
            if (k < m) break;
            com.esipeng.diameter.Message localMessage = new com.esipeng.diameter.Message();
            com.esipeng.diameter.Message.decode_status localdecode_status = localMessage.decode(arrayOfByte, j, m);

            switch (localdecode_status) {
                case decoded:
                    logRawDecodedPacket(arrayOfByte, j, m);
                    j += m;
                    boolean bool = handleMessage(localMessage, paramSCTPConnection);
                    if (!bool) {
                        this.logger.debug("handle error");
                        closeConnection(paramSCTPConnection);
                        return;
                    }


                    break;
                case not_enough:
                    break;
                case garbage:
                    logGarbagePacket(paramSCTPConnection, arrayOfByte, j, m);
                    closeConnection(paramSCTPConnection, true);
                    return;
            }
            if (localdecode_status == com.esipeng.diameter.Message.decode_status.not_enough) break;
        }
        paramSCTPConnection.consumeAppInBuffer(j);
    }

    private void handleWritable(Connection paramConnection) {
        SCTPConnection localSCTPConnection = (SCTPConnection) paramConnection;
        this.logger.debug("handleWritable():");
        java.nio.ByteBuffer localByteBuffer = localSCTPConnection.connection_buffers.netOutBuffer();


        localByteBuffer.flip();

        try {

            int i = localSCTPConnection.channel.send(localByteBuffer, messageInfo);
            if (i < 0) {
                closeConnection(localSCTPConnection);
                return;
            }

            localByteBuffer.compact();
            localSCTPConnection.processAppOutBuffer();
            if (!localSCTPConnection.hasNetOutput())
                localSCTPConnection.channel.register(this.selector, 1, localSCTPConnection);
        } catch (java.io.IOException localIOException) {
            closeConnection(localSCTPConnection);
            return;
        }
    }

    void sendMessage(SCTPConnection paramSCTPConnection, byte[] paramArrayOfByte) {
        int i = !paramSCTPConnection.hasNetOutput() ? 1 : 0;
        paramSCTPConnection.makeSpaceInAppOutBuffer(paramArrayOfByte.length);

        paramSCTPConnection.connection_buffers.appOutBuffer().put(paramArrayOfByte);
        paramSCTPConnection.connection_buffers.processAppOutBuffer();


        if (i != 0)
            outputBecameAvailable(paramSCTPConnection);
    }

    private void outputBecameAvailable(Connection paramConnection) {
        SCTPConnection localSCTPConnection = (SCTPConnection) paramConnection;
        handleWritable(localSCTPConnection);
        if (localSCTPConnection.hasNetOutput()) {
            try {
                localSCTPConnection.channel.register(this.selector, 5, localSCTPConnection);
            } catch (java.nio.channels.ClosedChannelException localClosedChannelException) {
            }
        }
    }

    boolean initiateConnection(Connection connection, Peer peer) {
        SCTPConnection localSCTPConnection = (SCTPConnection) connection;
        try {
            SctpChannel localSocketChannel = SctpChannel.open();
            localSocketChannel.configureBlocking(false);
            NodeSettings.PortRange localPortRange = this.settings.TCPPortRange();
            if (localPortRange != null)
                bindChannelInRange(localSocketChannel, localPortRange.min, localPortRange.max);
            java.net.InetSocketAddress localInetSocketAddress;
            if (peer.getRealAddress() != null && peer.getRealAddress().length() > 0) {
                localInetSocketAddress = new java.net.InetSocketAddress(peer.getRealAddress(), peer.port());
            } else {
                localInetSocketAddress = new java.net.InetSocketAddress(peer.host(), peer.port());
            }
            //java.net.InetSocketAddress localInetSocketAddress = new java.net.InetSocketAddress(paramPeer.host(), paramPeer.port());
            try {
                this.logger.debug("Initiating SCTP connection to " + localInetSocketAddress.toString());
                if (localSocketChannel.connect(localInetSocketAddress)) {
                    this.logger.debug("Connected!");
                    localSCTPConnection.state = Connection.State.connected_out;
                    localSCTPConnection.channel = localSocketChannel;
                    this.selector.wakeup();
                    localSocketChannel.register(this.selector, 1, localSCTPConnection);
                    initiateCER(localSCTPConnection);
                    return true;
                }
            } catch (java.nio.channels.UnresolvedAddressException localUnresolvedAddressException) {
                localSocketChannel.close();
                return false;
            } catch (InvalidAVPLengthException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            localSCTPConnection.state = Connection.State.connecting;
            localSCTPConnection.channel = localSocketChannel;
            this.selector.wakeup();
            localSocketChannel.register(this.selector, 8, localSCTPConnection);
        } catch (java.io.IOException localIOException) {
            this.logger.warn("java.io.IOException caught while initiating connection to '" + peer.host() + "'.", localIOException);
        }
        return true;
    }

    void close(Connection connection, boolean quick) {
        SCTPConnection localSCTPConnection = (SCTPConnection) connection;
        try {
            localSCTPConnection.channel.register(this.selector, 0);
            if (quick) {

                localSCTPConnection.channel.setOption(SctpStandardSocketOptions.SO_LINGER, 0);
            }
            localSCTPConnection.channel.close();
        } catch (java.io.IOException localIOException) {
        }
    }

    Connection newConnection(long watchDog, long idle) {
        return new SCTPConnection(this, watchDog, idle);
    }

    private static int last_tried_port = 0;

    private void bindChannelInRange(SctpChannel paramSocketChannel, int paramInt1, int paramInt2) throws java.io.IOException {
        int i = paramInt2 - paramInt1 + 1;
        int j = 0;
        if (j < i) {
            last_tried_port += 1;
            if (last_tried_port < paramInt1) last_tried_port = paramInt1;
            if (last_tried_port > paramInt2) last_tried_port = paramInt1;
            try {
                paramSocketChannel.bind(new java.net.InetSocketAddress(last_tried_port));
            } catch (java.net.BindException localBindException) {
            }
            return;
        }
        throw new java.net.BindException("Could not bind socket in range " + paramInt1 + "-" + paramInt2);
    }
}
