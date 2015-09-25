package com.esipeng.diameter.node;

public abstract interface ConnectionListener
{
  public abstract void handle(ConnectionKey paramConnectionKey, Peer paramPeer, boolean paramBoolean);
}

