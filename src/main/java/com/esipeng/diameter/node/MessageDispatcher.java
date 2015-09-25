package com.esipeng.diameter.node;

import com.esipeng.diameter.Message;

public abstract interface MessageDispatcher
{
  public abstract boolean handle(Message paramMessage, ConnectionKey paramConnectionKey, Peer paramPeer);
}

