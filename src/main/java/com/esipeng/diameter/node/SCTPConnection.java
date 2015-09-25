package com.esipeng.diameter.node;


import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;

import com.sun.nio.sctp.SctpChannel;

class SCTPConnection extends Connection
{
  MySCTPNode node_impl;
  SctpChannel channel;
  ConnectionBuffers connection_buffers;
  
  public SCTPConnection(MySCTPNode paramTCPNode, long paramLong1, long paramLong2)
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
  
  InetAddress toInetAddress()
  {
			Iterator<SocketAddress> it;
			try {
				it = this.channel.getRemoteAddresses().iterator();
				InetSocketAddress addr = (InetSocketAddress)it.next();
				return addr.getAddress();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return null;
  }
  
  void sendMessage(byte[] paramArrayOfByte) {
    this.node_impl.sendMessage(this, paramArrayOfByte);
  }
  
  Object getRelevantNodeAuthInfo() {
    return this.channel;
  }
  
  java.util.Collection<InetAddress> getLocalAddresses() {
    ArrayList localArrayList = new ArrayList();
    try {

				Iterator<SocketAddress> it = this.channel.getAllLocalAddresses().iterator();
				while(it.hasNext()){
					SocketAddress sa = it.next();
					InetSocketAddress isa = (InetSocketAddress)sa;
					localArrayList.add(isa.getAddress());
				}
				 return localArrayList;
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}	
			return null;
   
  }
  
  Peer toPeer() {
	//Iterator<SocketAddress>  it = this.channel.getRemoteAddresses();
	//InetAddress addr = (InetAddress)it.next();
    return new Peer(toInetAddress(),3872);
  }
}

