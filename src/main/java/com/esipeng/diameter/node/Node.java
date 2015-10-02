package com.esipeng.diameter.node;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.esipeng.diameter.AVP;
import com.esipeng.diameter.AVP_Address;
import com.esipeng.diameter.AVP_Grouped;
import com.esipeng.diameter.AVP_UTF8String;
import com.esipeng.diameter.AVP_Unsigned32;
import com.esipeng.diameter.InvalidAVPLengthException;
import com.esipeng.diameter.Message;
import com.esipeng.diameter.MessageHeader;
import com.esipeng.diameter.Utils;

import static com.esipeng.diameter.ProtocolConstants.*;

public class Node {
    private MessageDispatcher message_dispatcher;
    private ConnectionListener connection_listener;
    private NodeSettings settings;
    private NodeValidator node_validator;
    private NodeState node_state;
    private Thread reconnect_thread;
    private boolean please_stop;
    private long shutdown_deadline;
    private Map<ConnectionKey, Connection> map_key_conn;
    private Set<Peer> persistent_peers;
    private Logger logger;
    private Object obj_conn_wait;
    private NodeImplementation tcp_node;
    private NodeImplementation sctp_node;

    public Node(MessageDispatcher dispatcher, ConnectionListener listner, NodeSettings settings) {
        this(dispatcher, listner, settings, null);
    }

    public Node(MessageDispatcher dispatcher, ConnectionListener listner, NodeSettings settings, NodeValidator validator) {
        this.message_dispatcher = (dispatcher == null ? new DefaultMessageDispatcher() : dispatcher);
        this.connection_listener = (listner == null ? new DefaultConnectionListener() : listner);
        this.settings = settings;
        this.node_validator = (validator == null ? new DefaultNodeValidator() : validator);
        this.node_state = new NodeState();
        this.logger = LoggerFactory.getLogger("com.esipeng.diameter.node");
        this.obj_conn_wait = new Object();
        this.tcp_node = null;
        this.sctp_node = null;
    }


    public void start()
            throws IOException, UnsupportedTransportProtocolException {
        if ((this.tcp_node != null) || (this.sctp_node != null))
            throw new IOException("Diameter stack is already running");
        this.logger.debug("Starting Diameter node");
        this.please_stop = false;
        prepare();
        if (this.tcp_node != null)
            this.tcp_node.start();
        if (this.sctp_node != null)
            this.sctp_node.start();
        this.reconnect_thread = new ReconnectThread();
        this.reconnect_thread.setDaemon(true);
        this.reconnect_thread.start();
        this.logger.debug("Diameter node started");
    }


    public void stop() {
        stop(0L);
    }


    public void stop(long expire) {
        this.logger.debug("Stopping Diameter node");
        this.shutdown_deadline = (System.currentTimeMillis() + expire);
        if (this.tcp_node != null)
            this.tcp_node.initiateStop(this.shutdown_deadline);
        if (this.sctp_node != null)
            this.sctp_node.initiateStop(this.shutdown_deadline);
        if (this.map_key_conn == null) {
            this.logger.debug("Cannot stop node: It appears to not be running. (This is the fault of the caller)");
            return;
        }

        Iterator<Entry<ConnectionKey, Connection>> itConnectionKey;
        Entry<ConnectionKey, Connection> connectionKeyEntry;
        Connection connection;
        synchronized (this.map_key_conn) {
            this.please_stop = true;

            itConnectionKey = this.map_key_conn.entrySet().iterator();
            while (itConnectionKey.hasNext()) {
                connectionKeyEntry = itConnectionKey.next();
                connection = connectionKeyEntry.getValue();
                switch (connection.state) {
                    case connecting:
                    case connected_in:
                    case connected_out:
                        this.logger.debug("Closing connection to " + connection.host_id + " because we are shutting down");
                        itConnectionKey.remove();
                        connection.node_impl.closeConnection(connection);
                        break;
                    case tls:
                        break;
                    case ready:
                        initiateConnectionClose(connection, 0);
                        break;
                    case closing:

                }

            }
        }

        if (this.tcp_node != null)
            this.tcp_node.wakeup();
        if (this.sctp_node != null)
            this.sctp_node.wakeup();
        synchronized (this.map_key_conn) {
            this.map_key_conn.notify();
        }
        try {
            if (this.tcp_node != null)
                this.tcp_node.join();
            if (this.sctp_node != null)
                this.sctp_node.join();
            this.reconnect_thread.join();
        } catch (InterruptedException aaa) {
        }
        this.reconnect_thread = null;


        synchronized (this.map_key_conn) {
            for (itConnectionKey = this.map_key_conn.entrySet().iterator(); itConnectionKey.hasNext(); ) {
                connectionKeyEntry = itConnectionKey.next();
                connection = connectionKeyEntry.getValue();
                closeConnection(connection);
            }
        }

        synchronized (this.obj_conn_wait) {
            this.obj_conn_wait.notifyAll();
        }
        this.map_key_conn = null;
        this.persistent_peers = null;
        if (this.tcp_node != null) {
            this.tcp_node.closeIO();
            this.tcp_node = null;
        }
        if (this.sctp_node != null) {
            this.sctp_node.closeIO();
            this.sctp_node = null;
        }
        this.logger.debug("Diameter node stopped");
    }

    private boolean anyReadyConnection() {
        if (this.map_key_conn == null)
            return false;
        synchronized (this.map_key_conn) {
            for (Entry<ConnectionKey, Connection> entry : this.map_key_conn.entrySet()) {
                Connection connection = entry.getValue();
                if (connection.state == Connection.State.ready)
                    return true;
            }
        }
        return false;
    }


    public boolean waitForConnection()
            throws InterruptedException {
        synchronized (this.obj_conn_wait) {
            while (!anyReadyConnection()) {
                this.obj_conn_wait.wait();
            }
            return anyReadyConnection();
        }
    }


    public boolean waitForConnection(long expire)
            throws InterruptedException {
        long l1 = System.currentTimeMillis() + expire;
        synchronized (this.obj_conn_wait) {
            long l2 = System.currentTimeMillis();
            while ((!anyReadyConnection()) && (l2 < l1)) {
                long l3 = l1 - l2;
                this.obj_conn_wait.wait(l3);
                l2 = System.currentTimeMillis();
            }
            return anyReadyConnection();
        }
    }


    public void waitForConnectionTimeout(long expire)
            throws InterruptedException, ConnectionTimeoutException {
        waitForConnection(expire);
        if (!anyReadyConnection()) {
            throw new ConnectionTimeoutException("No connection was established within timeout (" + expire + " milliseconds)");
        }
    }


    public ConnectionKey findConnection(Peer peer) {
        this.logger.debug("Finding '" + peer.host() + "'");
        if (this.map_key_conn == null) {
            this.logger.debug(peer.host() + " NOT found (node is not ready)");
            return null;
        }
        synchronized (this.map_key_conn) {
            for (Entry<ConnectionKey, Connection> entry : this.map_key_conn.entrySet()) {
                Connection connection = entry.getValue();

                if (connection.state == Connection.State.ready) {
                    if ((connection.peer != null) && (connection.peer.equals(peer))) {
                        return connection.key;
                    }
                }
            }
            this.logger.debug(peer.host() + " NOT found");
            return null;
        }
    }


    public boolean isConnectionKeyValid(ConnectionKey connectionKey) {
        if (this.map_key_conn == null)
            return false;
        synchronized (this.map_key_conn) {
            return this.map_key_conn.get(connectionKey) != null;
        }
    }


    public Peer connectionKey2Peer(ConnectionKey connectionKey) {
        if (this.map_key_conn == null)
            return null;
        synchronized (this.map_key_conn) {
            Connection connection = this.map_key_conn.get(connectionKey);
            if (connection != null) {
                return connection.peer;
            }
            return null;
        }
    }


    public InetAddress connectionKey2InetAddress(ConnectionKey connectionKey) {
        if (this.map_key_conn == null)
            return null;
        synchronized (this.map_key_conn) {
            Connection connection = this.map_key_conn.get(connectionKey);
            if (connection != null) {
                return connection.toInetAddress();
            }
            return null;
        }
    }

    public int nextHopByHopIdentifier(ConnectionKey connectionKey)
            throws StaleConnectionException {
        if (this.map_key_conn == null)
            throw new StaleConnectionException();
        synchronized (this.map_key_conn) {
            Connection connection = this.map_key_conn.get(connectionKey);
            if (connection == null)
                throw new StaleConnectionException();
            return connection.nextHopByHopIdentifier();
        }
    }


    public void sendMessage(Message message, ConnectionKey connectionKey)
            throws StaleConnectionException {
        if (this.map_key_conn == null)
            throw new StaleConnectionException();
        synchronized (this.map_key_conn) {
            Connection connection = this.map_key_conn.get(connectionKey);
            if (connection == null)
                throw new StaleConnectionException();
            if (connection.state != Connection.State.ready)
                throw new StaleConnectionException();
            sendMessage(message, connection);
        }
    }

    private void sendMessage(Message message, Connection connection) {
        this.logger.debug("command=" + message.hdr.command_code + ", to=" + (connection.peer != null ? connection.peer.toString() : connection.host_id));
        byte[] arrayOfByte = message.encode();

        if (this.logger.isTraceEnabled()) {
            hexDump("Raw packet encoded", arrayOfByte, 0, arrayOfByte.length);
        }
        connection.sendMessage(arrayOfByte);
    }


    public void initiateConnection(Peer peer, boolean persist) {
        if (persist) {
            synchronized (this.persistent_peers) {
                this.persistent_peers.add(new Peer(peer));
            }
        }
        synchronized (this.map_key_conn) {
            for (Iterator<Entry<ConnectionKey, Connection>> itEntry = this.map_key_conn.entrySet().iterator(); itEntry.hasNext(); ) {
                Entry<ConnectionKey, Connection> entry = itEntry.next();
                Connection connection = entry.getValue();
                if ((connection.peer != null) && (connection.peer.equals(peer))) {
                    return;
                }
            }

            Connection connection;
            this.logger.debug("Initiating connection to '" + peer.host() + "' port " + peer.port());
            NodeImplementation nodeImplement = null;
            switch (peer.transportProtocol()) {
                case tcp:
                    this.logger.debug("Using tcp_node");
                    nodeImplement = this.tcp_node;
                    break;
                case sctp:
                    this.logger.debug("Using sctp_node");
                    nodeImplement = this.sctp_node;
            }

            if (nodeImplement != null) {
                connection = nodeImplement.newConnection(this.settings.watchdogInterval(), this.settings.idleTimeout());
                connection.host_id = peer.host();
                connection.peer = peer;
                if (nodeImplement.initiateConnection(connection, peer)) {
                    this.map_key_conn.put(connection.key, connection);
                    this.logger.debug("Initiated connection to [" + peer.toString() + "]");
                }
            } else {
                this.logger.debug("Transport connection to '" + peer.host() + "' cannot be established because the transport protocol (" + peer.transportProtocol() + ") is not supported");
            }
        }
    }

    private class ReconnectThread
            extends Thread {
        public ReconnectThread() {
            super();
        }

        public void run() {
            for (; ; ) {
                synchronized (Node.this.map_key_conn) {
                    if (Node.this.please_stop) return;
                    try {
                        Node.this.map_key_conn.wait(30000L);
                    } catch (InterruptedException exception) {
                    }
                    if (Node.this.please_stop) return;
                }
                synchronized (Node.this.persistent_peers) {
                    for (Peer peer : Node.this.persistent_peers)
                        Node.this.initiateConnection(peer, false);
                }
            }
        }
    }

    private static Boolean getUseOption(Boolean defaultValue, String name, Boolean defaultOne) {
        if (defaultValue != null)
            return defaultValue;
        String str = System.getProperty(name);
        if ((str != null) && (str.equals("true")))
            return Boolean.valueOf(true);
        if ((str != null) && (str.equals("false")))
            return Boolean.valueOf(false);
        if ((str != null) && (str.equals("maybe")))
            return null;
        return defaultOne;
    }

    private NodeImplementation instantiateNodeImplementation(String clazzName) {
        Class selfClazz = getClass();
        ClassLoader loader = selfClazz.getClassLoader();
        if (loader == null)
            loader = ClassLoader.getSystemClassLoader();
        try {
            Class implement = loader.loadClass(clazzName);
            Constructor constructor;
            try {
                constructor = implement.getConstructor(new Class[]{getClass(), this.settings.getClass(), loader.loadClass("org.slf4j.Logger")});

            } catch (NoSuchMethodException localNoSuchMethodException) {
                this.logger.debug("Could not find constructor for {} {}", clazzName, localNoSuchMethodException);
                return null;
            } catch (NoClassDefFoundError localNoClassDefFoundError1) {
                this.logger.debug("Could not find constructor for {} {}", clazzName, localNoClassDefFoundError1);
                return null;
            } catch (UnsatisfiedLinkError localUnsatisfiedLinkError1) {
                this.logger.debug("Could not find constructor for {} {}", clazzName, localUnsatisfiedLinkError1);
                return null;
            }
            if (constructor == null) return null;
            try {
                return (NodeImplementation) constructor.newInstance(new Object[]{this, this.settings, this.logger});
            } catch (InstantiationException localInstantiationException) {
                return null;
            } catch (IllegalAccessException localIllegalAccessException) {
                return null;
            } catch (InvocationTargetException localInvocationTargetException) {
                return null;
            } catch (UnsatisfiedLinkError localUnsatisfiedLinkError2) {
                this.logger.debug("Could not construct a {} {}", clazzName, localUnsatisfiedLinkError2);
                return null;
            } catch (NoClassDefFoundError localNoClassDefFoundError2) {
                return null;
            }


            //return null;
        } catch (ClassNotFoundException localClassNotFoundException) {
            this.logger.debug("class {}  not found/loaded {}", clazzName, localClassNotFoundException);
        }

        return null;
    }


    private NodeImplementation loadTransportProtocol(Boolean defaultValue, String name, Boolean defaultValue2, String clazzName, String transportName)
            throws IOException, UnsupportedTransportProtocolException {
        Boolean userValueExist = getUseOption(defaultValue, name, defaultValue2);
        NodeImplementation implement = null;
        if ((userValueExist == null) || (userValueExist.booleanValue())) {
            implement = instantiateNodeImplementation(clazzName);
            if (implement != null) {
                implement.openIO();
            } else if (userValueExist != null)
                throw new UnsupportedTransportProtocolException(transportName + " support could not be loaded");
        }
        this.logger.debug(transportName + " support was " + (implement != null ? "loaded" : "not loaded"));
        return implement;
    }

    private void prepare() throws IOException, UnsupportedTransportProtocolException {
        //if(use_sctp == false)
        if (this.settings.useTCP())
            this.tcp_node = loadTransportProtocol(this.settings.useTCP(), "com.esipeng.diameter.node.use_tcp", true, "com.esipeng.diameter.node.TCPNode", "TCP");
        //else
        if (this.settings.useSCTP())
            this.sctp_node = loadTransportProtocol(this.settings.useSCTP(), "com.esipeng.diameter.node.use_sctp", true, "com.esipeng.diameter.node.MySCTPNode", "SCTP");

        if ((this.tcp_node == null) && (this.sctp_node == null)) {
            this.logger.warn("No transport protocol classes could be loaded. The stack is running but without have any connectivity");
        }
        this.map_key_conn = new HashMap();
        this.persistent_peers = new HashSet();
    }


    long calcNextTimeout(NodeImplementation implement) {
        long l1 = -1L;
        synchronized (this.map_key_conn) {
            for (Entry<ConnectionKey, Connection> entry : this.map_key_conn.entrySet()) {
                Connection connection = entry.getValue();
                if (connection.node_impl == implement) {
                    boolean bool = connection.state == Connection.State.ready;
                    long l2 = connection.timers.calcNextTimeout(bool);
                    if ((l1 == -1L) || (l2 < l1))
                        l1 = l2;
                }
            }
        }
        if ((this.please_stop) && (this.shutdown_deadline < l1))
            l1 = this.shutdown_deadline;
        return l1;
    }


    void runTimers(NodeImplementation implement) {
        synchronized (this.map_key_conn) {
            Iterator<Entry<ConnectionKey, Connection>> iterator = this.map_key_conn.entrySet().iterator();
            while (iterator.hasNext()) {

                Entry<ConnectionKey, Connection> entry = iterator.next();
                Connection connection = entry.getValue();
                if (connection.node_impl == implement) {
                    boolean bool = connection.state == Connection.State.ready;
                    switch (connection.timers.calcAction(bool)) {
                        case none:
                            break;
                        case disconnect_no_cer:
                            this.logger.warn("Disconnecting due to no CER/CEA");
                            iterator.remove();
                            closeConnection(connection);
                            break;
                        case disconnect_idle:
                            this.logger.warn("Disconnecting due to idle");

                            iterator.remove();
                            initiateConnectionClose(connection, 1);
                            break;
                        case disconnect_no_dw:
                            this.logger.warn("Disconnecting due to no DWA");
                            iterator.remove();
                            closeConnection(connection);
                            break;
                        case dwr:
                            sendDWR(connection);
                    }

                }
            }
        }
    }

    void logRawDecodedPacket(byte[] bytes, int start, int len) {
        hexDump("Raw packet decoded", bytes, start, len);
    }

    void logGarbagePacket(Connection connection, byte[] bytes, int start, int len) {
        hexDump("Garbage from " + connection.host_id, bytes, start, len);
    }

    void hexDump(String head, byte[] bytes, int start, int len) {
        if (!this.logger.isTraceEnabled()) {
            return;
        }
        if (len > 1024) len = 1024;
        StringBuffer buffer = new StringBuffer(head.length() + 1 + len * 3 + (len / 16 + 1) * 15);
        buffer.append(head + "\n");
        for (int i = 0; i < len; i += 16) {
            buffer.append(String.format("%04X ", new Object[]{Integer.valueOf(i)}));
            byte b;
            for (int j = i; j < i + 16; j++) {
                if (j % 4 == 0)
                    buffer.append(' ');
                if (j < len) {
                    b = bytes[(start + j)];
                    buffer.append(String.format("%02X", new Object[]{Byte.valueOf(b)}));
                } else {
                    buffer.append("  ");
                }
            }
            buffer.append("     ");
            for (int j = i; (j < i + 16) && (j < len); j++) {
                b = bytes[(start + j)];
                if ((b >= 32) && (b < Byte.MAX_VALUE)) {
                    buffer.append((char) b);
                } else
                    buffer.append('.');
            }
            buffer.append('\n');
        }
        if (len > 1024)
            buffer.append("...\n");
        this.logger.trace(buffer.toString());
    }


    void closeConnection(Connection connection) {
        closeConnection(connection, false);
    }

    void closeConnection(Connection connection, boolean force) {
        if (connection.state == Connection.State.closed) return;
        this.logger.debug("Closing connection to " + (connection.peer != null ? connection.peer.toString() : connection.host_id));
        synchronized (this.map_key_conn) {
            connection.node_impl.close(connection, force);
            this.map_key_conn.remove(connection.key);
            connection.state = Connection.State.closed;
        }
        this.connection_listener.handle(connection.key, connection.peer, false);
    }

    private void initiateConnectionClose(Connection connection, int cause) {
        if (connection.state != Connection.State.ready)
            return;
        connection.state = Connection.State.closing;
        sendDPR(connection, cause);
    }

    boolean handleMessage(Message message, Connection connection) throws InvalidAVPLengthException {
        if (this.logger.isDebugEnabled())
            this.logger.debug("command_code=" + message.hdr.command_code + " application_id=" + message.hdr.application_id + " connection_state=" + connection.state);
        connection.timers.markActivity();
        if (connection.state == Connection.State.connected_in) {
            if ((!message.hdr.isRequest()) || (message.hdr.command_code != DIAMETER_COMMAND_CAPABILITIES_EXCHANGE) || (message.hdr.application_id != 0)) {


                this.logger.warn("Got something that wasn't a CER");
                return false;
            }
            connection.timers.markRealActivity();
            return handleCER(message, connection);
        }
        if (connection.state == Connection.State.connected_out) {
            if ((message.hdr.isRequest()) || (message.hdr.command_code != 257) || (message.hdr.application_id != 0)) {


                this.logger.warn("Got something that wasn't a CEA");
                return false;
            }
            connection.timers.markRealActivity();
            return handleCEA(message, connection);
        }
        switch (message.hdr.command_code) {
            case DIAMETER_COMMAND_CAPABILITIES_EXCHANGE:
                this.logger.warn("Got CER from " + connection.host_id + " after initial capability-exchange");

                return false;
            case DIAMETER_COMMAND_DEVICE_WATCHDOG:
                if (message.hdr.isRequest()) {
                    return handleDWR(message, connection);
                }
                return handleDWA(message, connection);
            case DIAMETER_COMMAND_DISCONNECT_PEER:
                if (message.hdr.isRequest()) {
                    return handleDPR(message, connection);
                }
                return handleDPA(message, connection);
        }
        connection.timers.markRealActivity();
        if (message.hdr.isRequest()) {
            if (isLoopedMessage(message)) {
                rejectLoopedRequest(message, connection);
                return true;
            }
            if (!isAllowedApplication(message, connection.peer)) {
                rejectDisallowedRequest(message, connection);
                return true;
            }
        }

        if (!this.message_dispatcher.handle(message, connection.key, connection.peer)) {
            if (message.hdr.isRequest()) {
                return handleUnknownRequest(message, connection);
            }
            return true;
        }
        return true;
    }


    private boolean isLoopedMessage(Message message) {
        for (AVP routeRecords : message.subset(DI_ROUTE_RECORD)) {
            AVP_UTF8String record = new AVP_UTF8String(routeRecords);
            if (record.queryValue().equals(this.settings.hostId()))
                return true;
        }
        return false;
    }

    private void rejectLoopedRequest(Message message, Connection connection) {
        this.logger.warn("Rejecting looped request from " + connection.peer.host() + " (command=" + message.hdr.command_code + ").");
        rejectRequest(message, connection, DIAMETER_RESULT_LOOP_DETECTED);
    }

    private static class AVP_VendorSpecificApplicationId
            extends AVP_Grouped {
        public AVP_VendorSpecificApplicationId(AVP avp) throws InvalidAVPLengthException, InvalidAVPValueException {
            super(avp);
            AVP[] children = queryAVPs();
            if (children.length < 2)
                throw new InvalidAVPValueException(avp);
            int i = 0;
            int j = 0;
            for (AVP child : children) {
                if (child.code == DI_VENDOR_ID) {
                    i = 1;
                } else if (child.code == DI_AUTH_APPLICATION_ID) {
                    j = 1;
                } else if (child.code == DI_ACCT_APPLICATION_ID) {
                    j = 1;
                }
            }
            if ((i == 0) || (j == 0))
                throw new InvalidAVPValueException(avp);
        }

        public AVP_VendorSpecificApplicationId(int vendor, int authId, int acctId) throws InvalidAVPLengthException {
            super(DI_VENDOR_SPECIFIC_APPLICATION_ID, new AVP[0]);
            AVP_Unsigned32 avp;
            if (authId != 0) {
                avp = new AVP_Unsigned32(DI_AUTH_APPLICATION_ID, authId);
            } else
                avp = new AVP_Unsigned32(DI_ACCT_APPLICATION_ID, acctId);
            setAVPs(new AVP[]{new AVP_Unsigned32(DI_VENDOR_ID, vendor), avp});
            //super(260,avps);
        }

        public int vendorId()
                throws InvalidAVPLengthException, InvalidAVPValueException {
            for (AVP avp : queryAVPs()) {
                if (avp.code == DI_VENDOR_ID)
                    return new AVP_Unsigned32(avp).queryValue();
            }
            throw new InvalidAVPValueException(this);
        }

        public Integer authAppId() throws InvalidAVPLengthException {
            for (AVP avp : queryAVPs()) {
                if (avp.code == DI_AUTH_APPLICATION_ID)
                    return Integer.valueOf(new AVP_Unsigned32(avp).queryValue());
            }
            return null;
        }

        public Integer acctAppId() throws InvalidAVPLengthException {
            for (AVP avp : queryAVPs()) {
                if (avp.code == DI_ACCT_APPLICATION_ID)
                    return Integer.valueOf(new AVP_Unsigned32(avp).queryValue());
            }
            return null;
        }
    }


    public boolean isAllowedApplication(Message message, Peer peer) {
        try {
            AVP authAvp = message.find(DI_AUTH_APPLICATION_ID);
            int i;
            if (authAvp != null) {
                i = new AVP_Unsigned32(authAvp).queryValue();
                if (this.logger.isDebugEnabled())
                    this.logger.debug("auth-application-id=" + i);
                return peer.capabilities.isAllowedAuthApp(i);
            }
            authAvp = message.find(DI_ACCT_APPLICATION_ID);
            if (authAvp != null) {
                i = new AVP_Unsigned32(authAvp).queryValue();
                if (this.logger.isDebugEnabled())
                    this.logger.debug("acct-application-id=" + i);
                return peer.capabilities.isAllowedAcctApp(i);
            }
            authAvp = message.find(DI_VENDOR_SPECIFIC_APPLICATION_ID);
            if (authAvp != null) {
                AVP_VendorSpecificApplicationId vendorSpecificId = new AVP_VendorSpecificApplicationId(authAvp);
                int j = vendorSpecificId.vendorId();
                if (this.logger.isDebugEnabled()) {
                    if (vendorSpecificId.authAppId() != null)
                        this.logger.debug("vendor-id=" + j + ", auth_app=" + vendorSpecificId.authAppId());
                    if (vendorSpecificId.acctAppId() != null)
                        this.logger.debug("vendor-id=" + j + ", acct_app=" + vendorSpecificId.acctAppId());
                }
                if (vendorSpecificId.authAppId() != null)
                    return peer.capabilities.isAllowedAuthApp(j, vendorSpecificId.authAppId().intValue());
                if (vendorSpecificId.acctAppId() != null)
                    return peer.capabilities.isAllowedAcctApp(j, vendorSpecificId.acctAppId().intValue());
                return false;
            }
            this.logger.warn("No auth-app-id, acct-app-id nor vendor-app in packet");
        } catch (InvalidAVPLengthException localInvalidAVPLengthException) {
            this.logger.debug("Encountered invalid AVP length while looking at application-id", localInvalidAVPLengthException);
        } catch (InvalidAVPValueException localInvalidAVPValueException) {
            this.logger.debug("Encountered invalid AVP value while looking at application-id", localInvalidAVPValueException);
        }
        return false;
    }

    private void rejectDisallowedRequest(Message message, Connection connection) {
        this.logger.warn("Rejecting request  from " + connection.peer.host() + " (command=" + message.hdr.command_code + ") because it is not allowed.");
        rejectRequest(message, connection, 3007);
    }

    private void rejectRequest(Message message, Connection connection, int cause) {
        Message answer = new Message();
        answer.prepareResponse(message);
        if ((cause >= 3000) && (cause <= 3999))
            answer.hdr.setError(true);
        answer.add(new AVP_Unsigned32(DI_RESULT_CODE, cause));
        addOurHostAndRealm(answer);
        Utils.copyProxyInfo(message, answer);
        Utils.setMandatory_RFC3588(answer);
        sendMessage(answer, connection);
    }


    public void addOurHostAndRealm(Message paramMessage) {
        paramMessage.add(new AVP_UTF8String(DI_ORIGIN_HOST, this.settings.hostId()));
        paramMessage.add(new AVP_UTF8String(DI_ORIGIN_REALM, this.settings.realm()));
    }


    public int nextEndToEndIdentifier() {
        return this.node_state.nextEndToEndIdentifier();
    }


    public String makeNewSessionId() {
        return makeNewSessionId(null);
    }


    public String makeNewSessionId(String postfix) {
        String str = this.settings.hostId() + ";" + this.node_state.nextSessionId_second_part();
        if (postfix == null) {
            return str;
        }
        return str + ";" + postfix;
    }


    public int stateId() {
        return this.node_state.stateId();
    }

    private boolean doElection(String hostId) {
        int i = this.settings.hostId().compareTo(hostId);
        if (i == 0) {
            this.logger.warn("Got CER with host-id=" + hostId + ". Suspecting this is a connection from ourselves.");

            return false;
        }
        int j = i > 0 ? 1 : 0;
        synchronized (this.map_key_conn) {
            for (Entry<ConnectionKey, Connection> entry : this.map_key_conn.entrySet()) {
                Connection localConnection = entry.getValue();
                if ((localConnection.host_id != null) && (localConnection.host_id.equals(hostId)) && (localConnection.state == Connection.State.ready)) {

                    this.logger.debug("New connection to a peer we already have a connection to (" + hostId + ")");
                    if (j != 0) {
                        closeConnection(localConnection);
                        return true;
                    }
                    return false;
                }
            }
        }
        return true;
    }

    private boolean handleCER(Message message, Connection connection) throws InvalidAVPLengthException {
        this.logger.debug("CER received from " + connection.host_id);


        AVP originHost = message.find(DI_ORIGIN_HOST);
        if (originHost == null) {
            this.logger.debug("CER from " + connection.host_id + " is missing the Origin-Host_id AVP. Rejecting.");
            Message rejectAnswer = new Message();
            rejectAnswer.prepareResponse(message);
            rejectAnswer.add(new AVP_Unsigned32(DI_RESULT_CODE, DIAMETER_RESULT_MISSING_AVP));
            addOurHostAndRealm(rejectAnswer);
            rejectAnswer.add(new AVP_FailedAVP(new AVP_UTF8String(DI_ORIGIN_HOST, "")));
            Utils.setMandatory_RFC3588(rejectAnswer);
            sendMessage(rejectAnswer, connection);
            return false;
        }
        String str = new AVP_UTF8String(originHost).queryValue();
        this.logger.debug("Peer's origin-host-id is " + str);

        NodeValidator.AuthenticationResult authResult = this.node_validator.authenticateNode(str, connection.getRelevantNodeAuthInfo());
        Message answer;
        if ((authResult == null) || (!authResult.known)) {
            this.logger.debug("We do not know " + connection.host_id + " Rejecting.");
            answer = new Message();
            answer.prepareResponse(message);
            if ((authResult != null) && ((authResult).result_code != null)) {
                answer.add(new AVP_Unsigned32(DI_RESULT_CODE, authResult.result_code.intValue()));
            } else
                answer.add(new AVP_Unsigned32(DI_RESULT_CODE, DIAMETER_RESULT_UNKNOWN_PEER));
            addOurHostAndRealm(answer);
            if ((authResult != null) && (authResult.error_message != null))
                answer.add(new AVP_UTF8String(DI_ERROR_MESSAGE, authResult.error_message));
            Utils.setMandatory_RFC3588(answer);
            sendMessage(answer, connection);
            return false;
        }


        if (!doElection(str)) {
            this.logger.debug("CER from " + connection.host_id + " lost the election. Rejecting.");
            answer = new Message();
            answer.prepareResponse(message);
            answer.add(new AVP_Unsigned32(DI_RESULT_CODE, DIAMETER_RESULT_ELECTION_LOST));
            addOurHostAndRealm(answer);
            Utils.setMandatory_RFC3588(answer);
            sendMessage(answer, connection);
            return false;
        }


        connection.peer = connection.toPeer();
        connection.peer.host(str);
        connection.host_id = str;

        if (handleCEx(message, connection)) {
            Message answerMsg = new Message();
            answerMsg.prepareResponse(message);

            answerMsg.add(new AVP_Unsigned32(DI_RESULT_CODE, DIAMETER_RESULT_SUCCESS));
            addCEStuff(answerMsg, connection.peer.capabilities, connection);

            this.logger.debug("Connection to " + connection.peer.toString() + " is now ready");
            Utils.setMandatory_RFC3588(answerMsg);
            sendMessage(answerMsg, connection);
            connection.state = Connection.State.ready;
            this.connection_listener.handle(connection.key, connection.peer, true);
            synchronized (this.obj_conn_wait) {
                this.obj_conn_wait.notifyAll();
            }
            return true;
        }
        return false;
    }

    private boolean handleCEA(Message message, Connection connection) {
        this.logger.debug("CEA received from " + connection.host_id);
        AVP resultAvp = message.find(DI_RESULT_CODE);
        if (resultAvp == null) {
            this.logger.warn("CEA from " + connection.host_id + " did not contain a Result-Code AVP (violation of RFC3588 section 5.3.2 page 61). Dropping connection");
            return false;
        }
        int i;
        try {
            i = new AVP_Unsigned32(resultAvp).queryValue();
        } catch (InvalidAVPLengthException localInvalidAVPLengthException) {
            this.logger.debug("CEA from " + connection.host_id + " contained an ill-formed Result-Code. Dropping connection");
            return false;
        }
        if (i != DIAMETER_RESULT_SUCCESS) {
            this.logger.debug("CEA from " + connection.host_id + " was rejected with Result-Code " + i + ". Dropping connection");
            return false;
        }
        resultAvp = message.find(DI_ORIGIN_HOST);
        if (resultAvp == null) {
            this.logger.warn("Peer did not include origin-host-id in CEA (violation of RFC3588 section 5.3.2 page 61). Dropping connection");
            return false;
        }
        String str = new AVP_UTF8String(resultAvp).queryValue();
        this.logger.debug("Node:Peer's origin-host-id is '" + str + "'. Expected: '" + connection.host_id + "'");

        connection.peer = connection.toPeer();
        connection.peer.host(str);
        connection.host_id = str;
        boolean success = handleCEx(message, connection);
        if (success) {
            connection.state = Connection.State.ready;
            this.logger.debug("Connection to " + connection.peer.toString() + " is now ready");
            this.connection_listener.handle(connection.key, connection.peer, true);
            synchronized (this.obj_conn_wait) {
                this.obj_conn_wait.notifyAll();
            }
            return true;
        }
        return false;
    }

    private boolean handleCEx(Message message, Connection connection) {
        this.logger.debug("Processing CER/CEA");
        try {
            Capability cap = new Capability();
            for (Iterator<AVP> supportedVendorId = message.subset(DI_SUPPORTED_VENDOR_ID).iterator(); supportedVendorId.hasNext(); ) {
                AVP supportedVendorAvp = supportedVendorId.next();
                int supportedVendor = new AVP_Unsigned32(supportedVendorAvp).queryValue();
                this.logger.debug("peer supports vendor " + supportedVendor);
                cap.addSupportedVendor(supportedVendor);
            }

            for (Iterator<AVP> itAuth = message.subset(DI_AUTH_APPLICATION_ID).iterator(); itAuth.hasNext(); ) {
                AVP authAvp = itAuth.next();
                int authId = new AVP_Unsigned32(authAvp).queryValue();
                this.logger.debug("peer supports auth-app " + authId);
                if (authId != 0)
                    cap.addAuthApp(authId);
            }

            for (Iterator<AVP> itAcct = message.subset(DI_ACCT_APPLICATION_ID).iterator(); itAcct.hasNext(); ) {
                AVP acctAvp = itAcct.next();
                int acctId = new AVP_Unsigned32(acctAvp).queryValue();
                this.logger.debug("peer supports acct-app " + acctId);
                if (acctId != 0)
                    cap.addAcctApp(acctId);
            }
            for (Iterator<AVP> itVendorSpecific = message.subset(DI_VENDOR_SPECIFIC_APPLICATION_ID).iterator(); itVendorSpecific.hasNext(); ) {
                AVP vendorSpecific = itVendorSpecific.next();
                AVP_VendorSpecificApplicationId typedVendorSpecific = new AVP_VendorSpecificApplicationId(vendorSpecific);
                int vendorId = typedVendorSpecific.vendorId();
                if (typedVendorSpecific.authAppId() != null)
                    cap.addVendorAuthApp(vendorId, typedVendorSpecific.authAppId().intValue());
                if (typedVendorSpecific.acctAppId() != null)
                    cap.addVendorAcctApp(vendorId, typedVendorSpecific.acctAppId().intValue());
            }

            Capability commonCap = this.node_validator.authorizeNode(connection.host_id, this.settings, cap);
            if (this.logger.isDebugEnabled()) {
                String logMsg = "";
                for (Iterator<Integer> itSupportedVendor = commonCap.supported_vendor.iterator(); itSupportedVendor.hasNext(); ) {
                    Integer supportedVendor = itSupportedVendor.next();
                    logMsg = logMsg + "  supported_vendor " + supportedVendor + "\n";
                }

                for (Iterator<Integer> itAuthApp = commonCap.auth_app.iterator(); itAuthApp.hasNext(); ) {
                    Integer authApp = itAuthApp.next();
                    logMsg = logMsg + "  auth_app " + authApp + "\n";
                }

                for (Iterator<Integer> itAcctApp = commonCap.acct_app.iterator(); itAcctApp.hasNext(); ) {
                    Integer acctApp = itAcctApp.next();
                    logMsg = logMsg + "  acct_app " + acctApp + "\n";
                }
                for (Iterator<Capability.VendorApplication> itAuthVendor = commonCap.auth_vendor.iterator(); itAuthVendor.hasNext(); ) {
                    Capability.VendorApplication authVendor = itAuthVendor.next();
                    logMsg = logMsg + "  vendor_auth_app: vendor " + authVendor.vendor_id + ", application " + authVendor.application_id + "\n";
                }

                for (Iterator<Capability.VendorApplication> itAcctVendor = commonCap.acct_vendor.iterator(); itAcctVendor.hasNext(); ) {
                    Capability.VendorApplication acctVendor = itAcctVendor.next();
                    logMsg = logMsg + "  vendor_acct_app: vendor " + acctVendor.vendor_id + ", application " + acctVendor.application_id + "\n";
                }
                this.logger.debug("Resulting capabilities:\n" + logMsg);
            }
            if (commonCap.isEmpty()) {
                this.logger.warn("No application in common with " + connection.host_id);
                if (message.hdr.isRequest()) {
                    Message answer = new Message();
                    answer.prepareResponse(message);
                    answer.add(new AVP_Unsigned32(DI_RESULT_CODE, DIAMETER_RESULT_NO_COMMON_APPLICATION));
                    addOurHostAndRealm(answer);
                    Utils.setMandatory_RFC3588(answer);
                    sendMessage(answer, connection);
                }
                return false;
            }

            connection.peer.capabilities = commonCap;
        } catch (InvalidAVPLengthException localInvalidAVPLengthException) {
            this.logger.warn("Invalid AVP in CER/CEA", localInvalidAVPLengthException);
            if (message.hdr.isRequest()) {
                Message rejectAnswer = new Message();
                rejectAnswer.prepareResponse(message);
                rejectAnswer.add(new AVP_Unsigned32(DI_RESULT_CODE, DIAMETER_RESULT_INVALID_AVP_LENGTH));
                addOurHostAndRealm(rejectAnswer);
                rejectAnswer.add(new AVP_FailedAVP(localInvalidAVPLengthException.avp));
                Utils.setMandatory_RFC3588(rejectAnswer);
                sendMessage(rejectAnswer, connection);
            }
            return false;
        } catch (InvalidAVPValueException localInvalidAVPValueException) {
            Message rejectAnswer;
            this.logger.warn("Invalid AVP in CER/CEA", localInvalidAVPValueException);
            if (message.hdr.isRequest()) {
                rejectAnswer = new Message();
                rejectAnswer.prepareResponse(message);
                rejectAnswer.add(new AVP_Unsigned32(DI_RESULT_CODE, DIAMETER_RESULT_INVALID_AVP_VALUE));
                addOurHostAndRealm(rejectAnswer);
                rejectAnswer.add(new AVP_FailedAVP(localInvalidAVPValueException.avp));
                Utils.setMandatory_RFC3588(rejectAnswer);
                sendMessage(rejectAnswer, connection);
            }
            return false;
        }
        return true;
    }


    void initiateCER(Connection paramConnection) throws InvalidAVPLengthException {
        sendCER(paramConnection);
    }

    private void sendCER(Connection connection) throws InvalidAVPLengthException {
        this.logger.debug("Sending CER to " + connection.host_id);
        Message message = new Message();
        message.hdr.setRequest(true);
        message.hdr.command_code = 257;
        message.hdr.application_id = 0;
        message.hdr.hop_by_hop_identifier = connection.nextHopByHopIdentifier();
        message.hdr.end_to_end_identifier = this.node_state.nextEndToEndIdentifier();
        addCEStuff(message, this.settings.capabilities(), connection);
        Utils.setMandatory_RFC3588(message);

        sendMessage(message, connection);
    }

    private void addCEStuff(Message message, Capability capability, Connection connection) throws InvalidAVPLengthException {
        addOurHostAndRealm(message);

        Collection<InetAddress> localAddresses = connection.getLocalAddresses();
        for (Iterator<InetAddress> itAddress = localAddresses.iterator(); itAddress.hasNext(); ) {
            InetAddress localAddress = itAddress.next();
            message.add(new AVP_Address(DI_HOST_IP_ADDRESS, localAddress));
        }

        message.add(new AVP_Unsigned32(DI_VENDOR_ID, this.settings.vendorId()));

        message.add(new AVP_UTF8String(DI_PRODUCT_NAME, this.settings.productName()));

        message.add(new AVP_Unsigned32(DI_ORIGIN_STATE_ID, this.node_state.stateId()));


        for (Iterator<Integer> itSupportedVendor = capability.supported_vendor.iterator(); itSupportedVendor.hasNext(); ) {
            Integer vendorId = itSupportedVendor.next();
            message.add(new AVP_Unsigned32(DI_SUPPORTED_VENDOR_ID, vendorId.intValue()));
        }

        for (Iterator<Integer> itAuthApp = capability.auth_app.iterator(); itAuthApp.hasNext(); ) {
            Integer authApp = itAuthApp.next();
            message.add(new AVP_Unsigned32(DI_AUTH_APPLICATION_ID, authApp.intValue()));
        }


        for (Iterator<Integer> itAcctApp = capability.acct_app.iterator(); itAcctApp.hasNext(); ) {
            Integer acctApp = itAcctApp.next();
            message.add(new AVP_Unsigned32(DI_ACCT_APPLICATION_ID, acctApp.intValue()));
        }

        for (Iterator<Capability.VendorApplication> itAuthVendor = capability.auth_vendor.iterator(); itAuthVendor.hasNext(); ) {
            Capability.VendorApplication authVendor = itAuthVendor.next();
            message.add(new AVP_VendorSpecificApplicationId(authVendor.vendor_id, authVendor.application_id, 0));
        }
        for (Iterator<Capability.VendorApplication> itAcctVendor = capability.acct_vendor.iterator(); itAcctVendor.hasNext(); ) {
            Capability.VendorApplication acctVendor = itAcctVendor.next();
            message.add(new AVP_VendorSpecificApplicationId(acctVendor.vendor_id, 0, acctVendor.application_id));
        }

        if (this.settings.firmwareRevision() != 0)
            message.add(new AVP_Unsigned32(DI_FIRMWARE_REVISION, this.settings.firmwareRevision()));
    }

    private boolean handleDWR(Message message, Connection connection) {
        this.logger.debug("DWR received from " + connection.host_id);
        connection.timers.markDWR();
        Message answer = new Message();
        answer.prepareResponse(message);
        answer.add(new AVP_Unsigned32(DI_RESULT_CODE, DIAMETER_RESULT_SUCCESS));
        addOurHostAndRealm(answer);
        answer.add(new AVP_Unsigned32(DI_ORIGIN_STATE_ID, this.node_state.stateId()));
        Utils.setMandatory_RFC3588(answer);

        sendMessage(answer, connection);
        return true;
    }

    private boolean handleDWA(Message message, Connection connection) {
        this.logger.debug("DWA received from " + connection.host_id);
        connection.timers.markDWA();
        return true;
    }

    private boolean handleDPR(Message message, Connection connection) {
        this.logger.debug("DPR received from " + connection.host_id);
        Message answer = new Message();
        answer.prepareResponse(message);
        answer.add(new AVP_Unsigned32(DI_RESULT_CODE, DIAMETER_RESULT_SUCCESS));
        addOurHostAndRealm(answer);
        Utils.setMandatory_RFC3588(answer);

        sendMessage(answer, connection);
        return false;
    }

    private boolean handleDPA(Message message, Connection connection) {
        if (connection.state == Connection.State.closing) {
            this.logger.debug("Got a DPA from " + connection.host_id);
        } else
            this.logger.warn("Got a DPA. This is not expected (state=" + connection.state + ")");
        return false;
    }

    private boolean handleUnknownRequest(Message message, Connection connection) {
        this.logger.debug("Unknown request received from " + connection.host_id);
        rejectRequest(message, connection, DIAMETER_RESULT_UNABLE_TO_DELIVER);
        return true;
    }

    private void sendDWR(Connection connection) {
        this.logger.debug("Sending DWR to " + connection.host_id);
        Message answer = new Message();
        answer.hdr.setRequest(true);
        answer.hdr.command_code = DIAMETER_COMMAND_DEVICE_WATCHDOG;
        answer.hdr.application_id = 0;
        answer.hdr.hop_by_hop_identifier = connection.nextHopByHopIdentifier();
        answer.hdr.end_to_end_identifier = this.node_state.nextEndToEndIdentifier();
        addOurHostAndRealm(answer);
        answer.add(new AVP_Unsigned32(DI_ORIGIN_STATE_ID, this.node_state.stateId()));
        Utils.setMandatory_RFC3588(answer);

        sendMessage(answer, connection);

        connection.timers.markDWR_out();
    }

    private void sendDPR(Connection connection, int cause) {
        this.logger.debug("Sending DPR to " + connection.host_id);
        Message answer = new Message();
        answer.hdr.setRequest(true);
        answer.hdr.command_code = DIAMETER_COMMAND_DISCONNECT_PEER;
        answer.hdr.application_id = 0;
        answer.hdr.hop_by_hop_identifier = connection.nextHopByHopIdentifier();
        answer.hdr.end_to_end_identifier = this.node_state.nextEndToEndIdentifier();
        addOurHostAndRealm(answer);
        answer.add(new AVP_Unsigned32(DI_DISCONNECT_CAUSE, cause));
        Utils.setMandatory_RFC3588(answer);

        sendMessage(answer, connection);
    }

    boolean anyOpenConnections(NodeImplementation paramNodeImplementation) {
        synchronized (this.map_key_conn) {
            for (Entry<ConnectionKey, Connection> entry : this.map_key_conn.entrySet()) {
                Connection connection = entry.getValue();
                if (connection.node_impl == paramNodeImplementation)
                    return true;
            }
        }
        return false;
    }

    void registerInboundConnection(Connection connection) {
        synchronized (this.map_key_conn) {
            this.map_key_conn.put(connection.key, connection);
        }
    }

    void unregisterConnection(Connection connection) {
        synchronized (this.map_key_conn) {
            this.map_key_conn.remove(connection.key);
        }
    }

    Object getLockObject() {
        return this.map_key_conn;
    }
}
