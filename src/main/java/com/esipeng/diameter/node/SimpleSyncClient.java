package com.esipeng.diameter.node;

import java.io.IOException;
import java.io.PrintStream;

import com.esipeng.diameter.Message;


public class SimpleSyncClient
        extends NodeManager {
    private Peer[] peers;

    public SimpleSyncClient(NodeSettings settings, Peer[] peers) {
        super(settings);
        this.peers = peers;
    }


    public void start()
            throws IOException, UnsupportedTransportProtocolException {
        super.start();
        for (Peer localPeer : this.peers) {
            node().initiateConnection(localPeer, true);
        }
    }


    protected void handleAnswer(Message message, ConnectionKey connectionKey, Object stateObj) {
        SyncCall localSyncCall = (SyncCall) stateObj;
        synchronized (localSyncCall) {
            localSyncCall.answer = message;
            localSyncCall.answer_ready = true;
            localSyncCall.notify();
        }
    }


    public Message sendRequest(Message message) {
        return sendRequest(message, -1L);
    }


    public Message sendRequest(Message message, long timeout) {
        SyncCall syncCall = new SyncCall();
        syncCall.answer_ready = false;
        syncCall.answer = null;

        long l1 = System.currentTimeMillis() + timeout;
        try {
            sendRequest(message, this.peers, syncCall, timeout);

            synchronized (syncCall) {
                if (timeout >= 0L) {
                    long l2 = System.currentTimeMillis();
                    long l3 = l1 - l2;
                    if (l3 > 0L)
                        while ((System.currentTimeMillis() < l1) && (!syncCall.answer_ready))
                            syncCall.wait(l3);
                } else {
                    while (!syncCall.answer_ready)
                        syncCall.wait();
                }
            }
        } catch (NotRoutableException localNotRoutableException) {
            System.out.println("SimpleSyncClient.sendRequest(): not routable");
        } catch (InterruptedException localInterruptedException) {
        } catch (NotARequestException localNotARequestException) {
        }


        return syncCall.answer;
    }

    private static class SyncCall {
        boolean answer_ready;
        Message answer;
    }
}

