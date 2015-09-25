package com.esipeng.diameter.node;

import java.net.Socket;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;

class TCPConnection extends Connection
{
  TCPNode node_impl;
  SocketChannel channel;
  ConnectionBuffers connection_buffers;
  
  public TCPConnection(TCPNode paramTCPNode, long paramLong1, long paramLong2)
  {
    super(paramTCPNode, paramLong1, paramLong2);
    this.node_impl = paramTCPNode;
    this.connection_buffers = new NormalConnectionBuffers();
  }
  
  void makeSpaceInNetInBuffer() {
    this.connection_buffers.makeSpaceInNetInBuffer();
  }
  
  void makeSpaceInAppOutBuffer(int paramInt) { this.connection_buffers.makeSpaceInAppOutBuffer(paramInt); }
  
  void consumeAppInBuffer(int paramInt) {
    this.connection_buffers.consumeAppInBuffer(paramInt);
  }
  
  void consumeNetOutBuffer(int paramInt) { this.connection_buffers.consumeNetOutBuffer(paramInt); }
  
  boolean hasNetOutput() {
    return this.connection_buffers.netOutBuffer().position() != 0;
  }
  
  void processNetInBuffer() {
    this.connection_buffers.processNetInBuffer();
  }
  
  void processAppOutBuffer() { this.connection_buffers.processAppOutBuffer(); }
  
  java.net.InetAddress toInetAddress()
  {
    return ((java.net.InetSocketAddress)this.channel.socket().getRemoteSocketAddress()).getAddress();
  }
  
  void sendMessage(byte[] paramArrayOfByte) {
    this.node_impl.sendMessage(this, paramArrayOfByte);
  }
  
  Object getRelevantNodeAuthInfo() {
    return this.channel;
  }
  
  java.util.Collection<java.net.InetAddress> getLocalAddresses() {
    ArrayList localArrayList = new ArrayList();
    localArrayList.add(this.channel.socket().getLocalAddress());
    return localArrayList;
  }
  
  Peer toPeer() {
    return new Peer(toInetAddress(), this.channel.socket().getPort());
  }
}
