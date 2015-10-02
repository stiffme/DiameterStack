package com.esipeng.diameter.node;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.esipeng.diameter.AVP;
import com.esipeng.diameter.AVP_UTF8String;
import com.esipeng.diameter.AVP_Unsigned32;
import com.esipeng.diameter.Message;
import com.esipeng.diameter.MessageHeader;
import com.esipeng.diameter.Utils;

import static com.esipeng.diameter.ProtocolConstants.*;

public class NodeManager
        implements MessageDispatcher, ConnectionListener {
    private Node node;
    private NodeSettings settings;
    private Map<ConnectionKey, Map<Integer, RequestData>> req_map;
    private Logger logger;
    private boolean stop_timeout_thread;
    private TimeoutThread timeout_thread;
    private boolean timeout_thread_actively_waiting;

    private class RequestData {
        public Object state;
        public long timeout_time;

        RequestData(Object stateObj, long timeout) {
            this.state = stateObj;
            this.timeout_time = timeout;
        }
    }


    public NodeManager(NodeSettings settings) {
        this(settings, null);
    }


    public NodeManager(NodeSettings settings, NodeValidator validator) {
        this.node = new Node(this, this, settings, validator);
        this.settings = settings;

        this.req_map = new HashMap<ConnectionKey, Map<Integer, RequestData>>();
        this.logger = LoggerFactory.getLogger("com.esipeng.diameter.node");
    }


    public void start()
            throws IOException, UnsupportedTransportProtocolException {
        this.node.start();
        this.stop_timeout_thread = false;
        this.timeout_thread_actively_waiting = false;
        this.timeout_thread = new TimeoutThread();
        this.timeout_thread.setDaemon(true);
        this.timeout_thread.start();
    }


    public void stop() {
        stop(0L);
    }


    public void stop(long timeout) {
        this.node.stop(timeout);
        this.stop_timeout_thread = true;
        synchronized (this.req_map) {
            for (Entry<ConnectionKey, Map<Integer, RequestData>> entry : this.req_map.entrySet()) {
                ConnectionKey connectionkey = entry.getKey();
                for (Entry<Integer, RequestData> entryRequest : entry.getValue().entrySet())
                    handleAnswer(null, connectionkey, entryRequest.getValue().state);
            }
            this.req_map.notify();
        }
        try {
            this.timeout_thread.join();
        } catch (InterruptedException localInterruptedException) {
        }
        this.timeout_thread = null;
        this.req_map = new HashMap<ConnectionKey, Map<Integer, RequestData>>();
    }


    public boolean waitForConnection()
            throws InterruptedException {
        return this.node.waitForConnection();
    }


    public boolean waitForConnection(long timeout)
            throws InterruptedException {
        return this.node.waitForConnection(timeout);
    }


    public void waitForConnectionTimeout(long timeout)
            throws InterruptedException, ConnectionTimeoutException {
        this.node.waitForConnectionTimeout(timeout);
    }


    public Node node() {
        return this.node;
    }

    public NodeSettings settings() {
        return this.settings;
    }


    protected void handleRequest(Message message, ConnectionKey connectionKey, Peer peer) {
        Message answer = new Message();
        this.logger.debug("Handling incoming request, command_code=" + message.hdr.command_code + ", peer=" + peer.host() + ", end2end=" + message.hdr.end_to_end_identifier + ", hopbyhop=" + message.hdr.hop_by_hop_identifier);
        answer.prepareResponse(message);
        answer.hdr.setError(true);
        answer.add(new AVP_Unsigned32(DI_RESULT_CODE, DIAMETER_RESULT_UNABLE_TO_DELIVER));
        this.node.addOurHostAndRealm(answer);
        Utils.copyProxyInfo(message, answer);
        Utils.setMandatory_RFC3588(answer);
        try {
            answer(answer, connectionKey);
        } catch (NotAnAnswerException localNotAnAnswerException) {
        }
    }


    protected void handleAnswer(Message message, ConnectionKey connectionKey, Object stateObj) {
        this.logger.debug("Handling incoming answer, command_code=" + message.hdr.command_code + ", end2end=" + message.hdr.end_to_end_identifier + ", hopbyhop=" + message.hdr.hop_by_hop_identifier);
    }


    protected final void answer(Message message, ConnectionKey connectionKey)
            throws NotAnAnswerException {
        if (message.hdr.isRequest())
            throw new NotAnAnswerException();
        try {
            this.node.sendMessage(message, connectionKey);
        } catch (StaleConnectionException localStaleConnectionException) {
        }
    }


    protected final void forwardRequest(Message message, ConnectionKey connectionKey, Object stateObj)
            throws StaleConnectionException, NotARequestException, NotProxiableException {
        forwardRequest(message, connectionKey, stateObj, -1L);
    }


    protected final void forwardRequest(Message message, ConnectionKey connectionKey, Object paramObject, long timeout)
            throws StaleConnectionException, NotARequestException, NotProxiableException {
        if (!message.hdr.isProxiable())
            throw new NotProxiableException();
        int i = 0;
        String str = this.settings.hostId();
        for (AVP routeRecord : message.subset(DI_ROUTE_RECORD)) {
            if (new AVP_UTF8String(routeRecord).queryValue().equals(str)) {
                i = 1;
                break;
            }
        }
        if (i == 0) {
            message.add(new AVP_UTF8String(DI_ROUTE_RECORD, this.settings.hostId()));
        }

        sendRequest(message, connectionKey, paramObject, timeout);
    }


    protected final void forwardAnswer(Message message, ConnectionKey connectionKey)
            throws StaleConnectionException, NotAnAnswerException, NotProxiableException {
        if (!message.hdr.isProxiable())
            throw new NotProxiableException();
        if (message.hdr.isRequest()) {
            throw new NotAnAnswerException();
        }
        message.add(new AVP_UTF8String(DI_ROUTE_RECORD, this.settings.hostId()));

        answer(message, connectionKey);
    }


    public final void sendRequest(Message message, ConnectionKey connectionKey, Object stateObj)
            throws StaleConnectionException, NotARequestException {
        sendRequest(message, connectionKey, stateObj, -1L);
    }


    public final void sendRequest(Message message, ConnectionKey connectionKey, Object paramObject, long timeout)
            throws StaleConnectionException, NotARequestException {
        if (!message.hdr.isRequest())
            throw new NotARequestException();
        message.hdr.hop_by_hop_identifier = this.node.nextHopByHopIdentifier(connectionKey);

        synchronized (this.req_map) {
            Map<Integer, RequestData> requestMap = this.req_map.get(connectionKey);
            if (requestMap == null) throw new StaleConnectionException();
            if (timeout > 0)
                requestMap.put(Integer.valueOf(message.hdr.hop_by_hop_identifier), new RequestData(paramObject, System.currentTimeMillis() + timeout));
            else
                requestMap.put(Integer.valueOf(message.hdr.hop_by_hop_identifier), new RequestData(paramObject, timeout));
            logger.info("Adding h2h" + message.hdr.hop_by_hop_identifier + " to " + requestMap.toString() + "ConnectionKey is " + connectionKey.toString());
            if ((timeout >= 0L) && (!this.timeout_thread_actively_waiting))
                this.req_map.notify();
        }
        this.node.sendMessage(message, connectionKey);
        this.logger.info("Request sent, command_code=" + message.hdr.command_code + " hop_by_hop_identifier=" + message.hdr.hop_by_hop_identifier);
    }


    public final void sendRequest(Message message, Peer[] peers, Object stateObj)
            throws NotRoutableException, NotARequestException {
        sendRequest(message, peers, stateObj, -1L);
    }


    public final void sendRequest(Message message, Peer[] peers, Object stateObj, long timeout)
            throws NotRoutableException, NotARequestException {
        this.logger.debug("Sending request (command_code=" + message.hdr.command_code + ") to " + peers.length + " peers");
        message.hdr.end_to_end_identifier = this.node.nextEndToEndIdentifier();
        boolean hasPeers = false;
        boolean hasCapablePeer = false;
        for (Peer peer : peers) {
            hasPeers = true;
            this.logger.debug("Considering sending request to " + peer.host());
            ConnectionKey connectionKey = this.node.findConnection(peer);
            if (connectionKey != null) {
                Peer peer2 = this.node.connectionKey2Peer(connectionKey);
                if (peer2 != null)
                    if (!this.node.isAllowedApplication(message, peer2)) {
                        this.logger.debug("peer " + peer.host() + " cannot handle request");
                    } else {
                        hasCapablePeer = true;
                        try {
                            sendRequest(message, connectionKey, stateObj, timeout);
                            return;
                        } catch (StaleConnectionException localStaleConnectionException) {
                            this.logger.debug("Setting retransmit bit");
                            message.hdr.setRetransmit(true);
                        }
                    }
            }
        }
        if (hasCapablePeer != false)
            throw new NotRoutableException("All capable peer connections went stale");
        if (hasPeers != false) {
            throw new NotRoutableException("No capable peers");
        }
        throw new NotRoutableException();
    }


    public final boolean handle(Message message, ConnectionKey key, Peer peer) {
        if (message.hdr.isRequest()) {
            this.logger.debug("Handling request");
            handleRequest(message, key, peer);
        } else {
            this.logger.debug("Handling answer, hop_by_hop_identifier=" + message.hdr.hop_by_hop_identifier);

            Object stateObj = null;
            boolean isAnswered = false;
            synchronized (this.req_map) {
                Map<Integer, RequestData> requestsMap = this.req_map.get(key);
                if (requestsMap != null) {
                    RequestData rq = requestsMap.get(message.hdr.hop_by_hop_identifier);
                    if (rq == null) {
                        logger.warn("h2h " + message.hdr.hop_by_hop_identifier + " not found. LocalMap is " + requestsMap.toString() + "ConnectionKey is " + key.toString());
                        return false;
                    }
                    stateObj = (requestsMap.get(Integer.valueOf(message.hdr.hop_by_hop_identifier))).state;

                    requestsMap.remove(Integer.valueOf(message.hdr.hop_by_hop_identifier));
                    isAnswered = true;
                }
            }
            if (isAnswered != false) {
                handleAnswer(message, key, stateObj);
                logger.info("h2h " + message.hdr.hop_by_hop_identifier + " answered");
            } else {
                this.logger.debug("Answer did not match any outstanding request");
            }
        }
        return true;
    }


    public final void handle(ConnectionKey key, Peer peer, boolean add) {
        synchronized (this.req_map) {
            if (add) {
                this.req_map.put(key, new HashMap<Integer, RequestData>());
                //logger.info( "Saving connection key " + paramConnectionKey.toString(),new Throwable());
            } else {
                Map<Integer, RequestData> requestsMap = this.req_map.get(key);
                if (requestsMap == null) {
                    return;
                }
                this.req_map.remove(key);
                //logger.info( "Removing connection key " + paramConnectionKey.toString(),new Throwable());
                for (Entry<Integer, RequestData> localEntry : requestsMap.entrySet()) {
                    handleAnswer(null, key, localEntry.getValue().state);
                }
            }
        }
    }


    private class TimeoutThread
            extends Thread {
        public TimeoutThread() {
            super();
        }

        public void run() {
            while (!NodeManager.this.stop_timeout_thread) {
                synchronized (NodeManager.this.req_map) {
                    boolean timeoutKey = false;
                    long currentTime = System.currentTimeMillis();
                    for (Iterator<Entry<ConnectionKey, Map<Integer, RequestData>>> itConnectionKey = NodeManager.this.req_map.entrySet().iterator(); itConnectionKey.hasNext(); ) {
                        Entry<ConnectionKey, Map<Integer, RequestData>> connectionKeyEntry = itConnectionKey.next();
                        ConnectionKey connectionKey = connectionKeyEntry.getKey();
                        for (Entry<Integer, RequestData> requestDataMap : connectionKeyEntry.getValue().entrySet()) {
                            RequestData requestData =  requestDataMap.getValue();
                            if (requestData.timeout_time >= 0L)
                                timeoutKey = true;
                            if ((requestData.timeout_time >= 0L) && (requestData.timeout_time <= currentTime)) {
                                connectionKeyEntry.getValue().remove(requestDataMap.getKey());
                                NodeManager.this.logger.info("Timing out request");
                                NodeManager.this.handleAnswer(null, connectionKey, requestData.state);
                            }
                        }
                    }
                    try {
                        if (timeoutKey != false) {
                            NodeManager.this.timeout_thread_actively_waiting = true;
                            NodeManager.this.req_map.wait(1000L);
                        } else {
                            NodeManager.this.req_map.wait();
                        }
                        NodeManager.this.timeout_thread_actively_waiting = false;
                    } catch (InterruptedException localInterruptedException) {}
                }
            }
        }
    }
}

