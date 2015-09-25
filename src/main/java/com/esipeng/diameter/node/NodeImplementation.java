package com.esipeng.diameter.node;

import org.slf4j.Logger;

import com.esipeng.diameter.InvalidAVPLengthException;
import com.esipeng.diameter.Message;

abstract class NodeImplementation
{
  private Node node;
  protected NodeSettings settings;
  protected Logger logger;
  
  NodeImplementation(Node paramNode, NodeSettings paramNodeSettings, Logger paramLogger)
  {
    this.node = paramNode;
    this.settings = paramNodeSettings;
    this.logger = paramLogger; 
  }
  
  abstract void openIO() throws java.io.IOException;
  
  abstract void start();
  
  abstract void wakeup();
  
  abstract void initiateStop(long paramLong);
  abstract void join();
  abstract void closeIO();
  abstract boolean initiateConnection(Connection paramConnection, Peer paramPeer);
//abstract boolean initiateConnection(Connection paramConnection, Peer paramPeer,String realAddress);
  abstract void close(Connection paramConnection, boolean paramBoolean);
  abstract Connection newConnection(long paramLong1, long paramLong2);
  boolean anyOpenConnections() { return this.node.anyOpenConnections(this); }
  
  void registerInboundConnection(Connection paramConnection) {
    this.node.registerInboundConnection(paramConnection);
  }
  
  void unregisterConnection(Connection paramConnection) { this.node.unregisterConnection(paramConnection); }
  
  long calcNextTimeout() {
    return this.node.calcNextTimeout(this);
  }
  
  void closeConnection(Connection paramConnection) { this.node.closeConnection(paramConnection); }
  
  void closeConnection(Connection paramConnection, boolean paramBoolean) {
    this.node.closeConnection(paramConnection, paramBoolean);
  }
  
  boolean handleMessage(Message paramMessage, Connection paramConnection) throws InvalidAVPLengthException { return this.node.handleMessage(paramMessage, paramConnection); }
  
  void runTimers() {
    this.node.runTimers(this);
  }
  
  void logRawDecodedPacket(byte[] paramArrayOfByte, int paramInt1, int paramInt2) { this.node.logRawDecodedPacket(paramArrayOfByte, paramInt1, paramInt2); }
  
  void logGarbagePacket(Connection paramConnection, byte[] paramArrayOfByte, int paramInt1, int paramInt2) {
    this.node.logGarbagePacket(paramConnection, paramArrayOfByte, paramInt1, paramInt2);
  }
  
  Object getLockObject() { return this.node.getLockObject(); }
  
  void initiateCER(Connection paramConnection) throws InvalidAVPLengthException {
    this.node.initiateCER(paramConnection);
  }
}
