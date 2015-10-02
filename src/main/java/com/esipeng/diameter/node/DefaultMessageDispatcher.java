package com.esipeng.diameter.node;

import com.esipeng.diameter.Message;

class DefaultMessageDispatcher implements MessageDispatcher {
    public boolean handle(Message message, ConnectionKey key, Peer peer) {
        return false;
    }
}

