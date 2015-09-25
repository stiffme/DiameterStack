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


public class Node
{
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
  
  public Node(MessageDispatcher paramMessageDispatcher, ConnectionListener paramConnectionListener, NodeSettings paramNodeSettings)
  {
    this(paramMessageDispatcher, paramConnectionListener, paramNodeSettings, null);
  }
  














  public Node(MessageDispatcher paramMessageDispatcher, ConnectionListener paramConnectionListener, NodeSettings paramNodeSettings, NodeValidator paramNodeValidator)
  {
    this.message_dispatcher = (paramMessageDispatcher == null ? new DefaultMessageDispatcher() : paramMessageDispatcher);
    this.connection_listener = (paramConnectionListener == null ? new DefaultConnectionListener() : paramConnectionListener);
    this.settings = paramNodeSettings;
    this.node_validator = (paramNodeValidator == null ? new DefaultNodeValidator() : paramNodeValidator);
    this.node_state = new NodeState();
    this.logger = LoggerFactory.getLogger("com.esipeng.diameter.node");
    this.obj_conn_wait = new Object();
    this.tcp_node = null;
    this.sctp_node = null;
  }
  





  public void start()
    throws IOException, UnsupportedTransportProtocolException
  {
    if ((this.tcp_node != null) || (this.sctp_node != null))
      throw new IOException("Diameter stack is already running");
    this.logger.debug( "Starting Diameter node");
    this.please_stop = false;
    prepare();
    if (this.tcp_node != null)
      this.tcp_node.start();
    if (this.sctp_node != null)
      this.sctp_node.start();
    this.reconnect_thread = new ReconnectThread();
    this.reconnect_thread.setDaemon(true);
    this.reconnect_thread.start();
    this.logger.debug( "Diameter node started");
  }
  



  public void stop()
  {
    stop(0L);
  }
  








  public void stop(long paramLong)
  {
    this.logger.debug( "Stopping Diameter node");
    this.shutdown_deadline = (System.currentTimeMillis() + paramLong);
    if (this.tcp_node != null)
      this.tcp_node.initiateStop(this.shutdown_deadline);
    if (this.sctp_node != null)
      this.sctp_node.initiateStop(this.shutdown_deadline);
    if (this.map_key_conn == null) {
      this.logger.debug( "Cannot stop node: It appears to not be running. (This is the fault of the caller)"); return; }
    Iterator localIterator;
    Entry localEntry;
    Connection localConnection; synchronized (this.map_key_conn) {
      this.please_stop = true;
      
      localIterator = this.map_key_conn.entrySet().iterator();
      while (localIterator.hasNext())
      {

        localEntry = (Entry)localIterator.next();
        localConnection = (Connection)localEntry.getValue();
        switch (localConnection.state) {
        case connecting: 
        case connected_in: 
        case connected_out: 
          this.logger.debug( "Closing connection to " + localConnection.host_id + " because we are shutting down");
          localIterator.remove();
          localConnection.node_impl.closeConnection(localConnection);
          break;
        case tls: 
          break;
        case ready: 
          initiateConnectionClose(localConnection, 0);
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
    } catch (InterruptedException aaa) {}
    this.reconnect_thread = null;
    

    synchronized (this.map_key_conn) {
      for (localIterator = this.map_key_conn.entrySet().iterator(); localIterator.hasNext();) { localEntry = (Entry)localIterator.next();
        localConnection = (Connection)localEntry.getValue();
        closeConnection(localConnection);
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
    this.logger.debug( "Diameter node stopped");
  }
  
  private boolean anyReadyConnection() {
    if (this.map_key_conn == null)
      return false;
    synchronized (this.map_key_conn) {
      for (Entry localEntry : this.map_key_conn.entrySet()) {
        Connection localConnection = (Connection)localEntry.getValue();
        if (localConnection.state == Connection.State.ready)
          return true;
      }
    }
    return false;
  }
  



  public boolean waitForConnection()
    throws InterruptedException
  {
    synchronized (this.obj_conn_wait) {
      while (!anyReadyConnection()) {
        this.obj_conn_wait.wait();
      }
      return anyReadyConnection();
    }
  }
  



  public boolean waitForConnection(long paramLong)
    throws InterruptedException
  {
    long l1 = System.currentTimeMillis() + paramLong;
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
  







  public void waitForConnectionTimeout(long paramLong)
    throws InterruptedException, ConnectionTimeoutException
  {
    waitForConnection(paramLong);
    if (!anyReadyConnection()) {
      throw new ConnectionTimeoutException("No connection was established within timeout (" + paramLong + " milliseconds)");
    }
  }
  




  public ConnectionKey findConnection(Peer paramPeer)
  {
    this.logger.debug( "Finding '" + paramPeer.host() + "'");
    if (this.map_key_conn == null) {
      this.logger.debug( paramPeer.host() + " NOT found (node is not ready)");
      return null;
    }
    synchronized (this.map_key_conn)
    {
      for (Entry localEntry : this.map_key_conn.entrySet()) {
        Connection localConnection = (Connection)localEntry.getValue();
        
        if (localConnection.state == Connection.State.ready)
        {
          if ((localConnection.peer != null) && (localConnection.peer.equals(paramPeer)))
          {

            return localConnection.key; }
        }
      }
      this.logger.debug( paramPeer.host() + " NOT found");
      return null;
    }
  }
  





  public boolean isConnectionKeyValid(ConnectionKey paramConnectionKey)
  {
    if (this.map_key_conn == null)
      return false;
    synchronized (this.map_key_conn) {
      return this.map_key_conn.get(paramConnectionKey) != null;
    }
  }
  

  public Peer connectionKey2Peer(ConnectionKey paramConnectionKey)
  {
    if (this.map_key_conn == null)
      return null;
    synchronized (this.map_key_conn) {
      Connection localConnection = (Connection)this.map_key_conn.get(paramConnectionKey);
      if (localConnection != null) {
        return localConnection.peer;
      }
      return null;
    }
  }
  





  public InetAddress connectionKey2InetAddress(ConnectionKey paramConnectionKey)
  {
    if (this.map_key_conn == null)
      return null;
    synchronized (this.map_key_conn) {
      Connection localConnection = (Connection)this.map_key_conn.get(paramConnectionKey);
      if (localConnection != null) {
        return localConnection.toInetAddress();
      }
      return null;
    }
  }
  
  public int nextHopByHopIdentifier(ConnectionKey paramConnectionKey)
    throws StaleConnectionException
  {
    if (this.map_key_conn == null)
      throw new StaleConnectionException();
    synchronized (this.map_key_conn) {
      Connection localConnection = (Connection)this.map_key_conn.get(paramConnectionKey);
      if (localConnection == null)
        throw new StaleConnectionException();
      return localConnection.nextHopByHopIdentifier();
    }
  }
  



  public void sendMessage(Message paramMessage, ConnectionKey paramConnectionKey)
    throws StaleConnectionException
  {
    if (this.map_key_conn == null)
      throw new StaleConnectionException();
    synchronized (this.map_key_conn) {
      Connection localConnection = (Connection)this.map_key_conn.get(paramConnectionKey);
      if (localConnection == null)
        throw new StaleConnectionException();
      if (localConnection.state != Connection.State.ready)
        throw new StaleConnectionException();
      sendMessage(paramMessage, localConnection);
    }
  }
  
  private void sendMessage(Message paramMessage, Connection paramConnection) { this.logger.debug( "command=" + paramMessage.hdr.command_code + ", to=" + (paramConnection.peer != null ? paramConnection.peer.toString() : paramConnection.host_id));
    byte[] arrayOfByte = paramMessage.encode();
    
    if (this.logger.isTraceEnabled()) {
      hexDump("Raw packet encoded", arrayOfByte, 0, arrayOfByte.length);
    }
    paramConnection.sendMessage(arrayOfByte);
  }
  

















  public void initiateConnection(Peer paramPeer, boolean paramBoolean)
  {
    if (paramBoolean) {
      synchronized (this.persistent_peers) {
        this.persistent_peers.add(new Peer(paramPeer));
      }
    }
    synchronized (this.map_key_conn) {
      for (Iterator<Entry<ConnectionKey, Connection>> localObject2 = this.map_key_conn.entrySet().iterator(); localObject2.hasNext();) {
	               Entry<ConnectionKey, Connection> localObject3 = localObject2.next();
        Connection localConnection = localObject3.getValue();
        if ((localConnection.peer != null) && (localConnection.peer.equals(paramPeer))) {
          return;
        }
      }
      Connection localObject3;
      this.logger.debug( "Initiating connection to '" + paramPeer.host() + "' port " + paramPeer.port());
      NodeImplementation localObject2 = null;
      switch (paramPeer.transportProtocol()) {
      case tcp: 
					this.logger.debug( "Using tcp_node");
        localObject2 = this.tcp_node;
        break;
      case sctp: 
					this.logger.debug( "Using sctp_node");
        localObject2 = this.sctp_node;
      }
      
      if (localObject2 != null) {
        localObject3 = localObject2.newConnection(this.settings.watchdogInterval(), this.settings.idleTimeout());
        localObject3.host_id = paramPeer.host();
        localObject3.peer = paramPeer;
        if (localObject2.initiateConnection(localObject3, paramPeer)) {
          this.map_key_conn.put(localObject3.key, localObject3);
          this.logger.debug( "Initiated connection to [" + paramPeer.toString() + "]");
        }
      } else {
        this.logger.debug( "Transport connection to '" + paramPeer.host() + "' cannot be established because the transport protocol (" + paramPeer.transportProtocol() + ") is not supported");
      }
    }
  }
  
  private class ReconnectThread
    extends Thread {
    public ReconnectThread() { super(); }
    
    public void run() {
      for (;;) {
        synchronized (Node.this.map_key_conn) {
          if (Node.this.please_stop) return;
          try {
            Node.this.map_key_conn.wait(30000L);
          } catch (InterruptedException localInterruptedException) {}
          if (Node.this.please_stop) return;
        }
        synchronized (Node.this.persistent_peers) {
          for (Peer localPeer : Node.this.persistent_peers)
            Node.this.initiateConnection(localPeer, false);
        }
      }
    }
  }
  
  private static Boolean getUseOption(Boolean paramBoolean1, String paramString, Boolean paramBoolean2) {
    if (paramBoolean1 != null)
      return paramBoolean1;
    String str = System.getProperty(paramString);
    if ((str != null) && (str.equals("true")))
      return Boolean.valueOf(true);
    if ((str != null) && (str.equals("false")))
      return Boolean.valueOf(false);
    if ((str != null) && (str.equals("maybe")))
      return null;
    return paramBoolean2;
  }
  
  private NodeImplementation instantiateNodeImplementation(String paramString)
  {
    Class localClass1 = getClass();
    ClassLoader localClassLoader = localClass1.getClassLoader();
    if (localClassLoader == null)
      localClassLoader = ClassLoader.getSystemClassLoader();
    try {
      Class localClass2 = localClassLoader.loadClass(paramString);
      Constructor localConstructor;
      try {
        localConstructor = localClass2.getConstructor(new Class[] { getClass(), this.settings.getClass(), localClassLoader.loadClass("org.slf4j.Logger") });

      }
      catch (NoSuchMethodException localNoSuchMethodException)
      {
        this.logger.debug("Could not find constructor for {} {}" , paramString, localNoSuchMethodException);
        return null;
      }
      catch (NoClassDefFoundError localNoClassDefFoundError1) {
        this.logger.debug("Could not find constructor for {} {}" , paramString, localNoClassDefFoundError1);       
        return null;
      } catch (UnsatisfiedLinkError localUnsatisfiedLinkError1) {
        this.logger.debug( "Could not find constructor for {} {}" , paramString, localUnsatisfiedLinkError1);
        return null;
      }
      if (localConstructor == null) return null;
      try {
        return (NodeImplementation)localConstructor.newInstance(new Object[] { this, this.settings, this.logger });
      }
      catch (InstantiationException localInstantiationException) {
        return null;
      } catch (IllegalAccessException localIllegalAccessException) {
        return null;
      } catch (InvocationTargetException localInvocationTargetException) {
        return null;
      } catch (UnsatisfiedLinkError localUnsatisfiedLinkError2) {
        this.logger.debug("Could not construct a {} {}" , paramString, localUnsatisfiedLinkError2);
        return null;
      }
      catch (NoClassDefFoundError localNoClassDefFoundError2) {
        return null;
      }
      

      //return null;
    }
    catch (ClassNotFoundException localClassNotFoundException)
    {
      this.logger.debug( "class {}  not found/loaded {}" , paramString , localClassNotFoundException);
    }

	return null;
  }
  



  private NodeImplementation loadTransportProtocol(Boolean paramBoolean1, String paramString1, Boolean paramBoolean2, String paramString2, String paramString3)
    throws IOException, UnsupportedTransportProtocolException
  {
    Boolean localBoolean = getUseOption(paramBoolean1, paramString1, paramBoolean2);
    NodeImplementation localNodeImplementation = null;
    if ((localBoolean == null) || (localBoolean.booleanValue())) {
      localNodeImplementation = instantiateNodeImplementation(paramString2);
      if (localNodeImplementation != null) {
        localNodeImplementation.openIO();
      } else if (localBoolean != null)
        throw new UnsupportedTransportProtocolException(paramString3 + " support could not be loaded");
    }
    this.logger.debug( paramString3 + " support was " + (localNodeImplementation != null ? "loaded" : "not loaded"));
    return localNodeImplementation;
  }
  
  private void prepare() throws IOException, UnsupportedTransportProtocolException { 
				//if(use_sctp == false)
				if(this.settings.useTCP())
					this.tcp_node = loadTransportProtocol(this.settings.useTCP(), "com.esipeng.diameter.node.use_tcp", true, "com.esipeng.diameter.node.TCPNode", "TCP");
    //else 
				if(this.settings.useSCTP())
    		this.sctp_node = loadTransportProtocol(this.settings.useSCTP(), "com.esipeng.diameter.node.use_sctp", true, "com.esipeng.diameter.node.MySCTPNode", "SCTP");
    
    if ((this.tcp_node == null) && (this.sctp_node == null)) {
      this.logger.warn( "No transport protocol classes could be loaded. The stack is running but without have any connectivity");
    }
    this.map_key_conn = new HashMap();
    this.persistent_peers = new HashSet();
  }
  



  long calcNextTimeout(NodeImplementation paramNodeImplementation)
  {
    long l1 = -1L;
    synchronized (this.map_key_conn) {
      for (Entry localEntry : this.map_key_conn.entrySet()) {
        Connection localConnection = (Connection)localEntry.getValue();
        if (localConnection.node_impl == paramNodeImplementation) {
          boolean bool = localConnection.state == Connection.State.ready;
          long l2 = localConnection.timers.calcNextTimeout(bool);
          if ((l1 == -1L) || (l2 < l1))
            l1 = l2;
        }
      } }
    if ((this.please_stop) && (this.shutdown_deadline < l1))
      l1 = this.shutdown_deadline;
    return l1;
  }
  


  void runTimers(NodeImplementation paramNodeImplementation)
  {
    synchronized (this.map_key_conn) {
      Iterator localIterator = this.map_key_conn.entrySet().iterator();
      while (localIterator.hasNext())
      {

        Entry localEntry = (Entry)localIterator.next();
        Connection localConnection = (Connection)localEntry.getValue();
        if (localConnection.node_impl == paramNodeImplementation) {
          boolean bool = localConnection.state == Connection.State.ready;
          switch (localConnection.timers.calcAction(bool)) {
          case none: 
            break;
          case disconnect_no_cer: 
            this.logger.warn( "Disconnecting due to no CER/CEA");
            localIterator.remove();
            closeConnection(localConnection);
            break;
          case disconnect_idle: 
            this.logger.warn( "Disconnecting due to idle");
            
            localIterator.remove();
            initiateConnectionClose(localConnection, 1);
            break;
          case disconnect_no_dw: 
            this.logger.warn( "Disconnecting due to no DWA");
            localIterator.remove();
            closeConnection(localConnection);
            break;
          case dwr: 
            sendDWR(localConnection);
          }
          
        }
      }
    }
  }
  
  void logRawDecodedPacket(byte[] paramArrayOfByte, int paramInt1, int paramInt2)
  {
    hexDump("Raw packet decoded", paramArrayOfByte, paramInt1, paramInt2);
  }
  
  void logGarbagePacket(Connection paramConnection, byte[] paramArrayOfByte, int paramInt1, int paramInt2) {
    hexDump("Garbage from " + paramConnection.host_id, paramArrayOfByte, paramInt1, paramInt2);
  }
  
  void hexDump(String paramString, byte[] paramArrayOfByte, int paramInt1, int paramInt2) {
    if (!this.logger.isTraceEnabled()) {
      return;
    }
    if (paramInt2 > 1024) paramInt2 = 1024;
    StringBuffer localStringBuffer = new StringBuffer(paramString.length() + 1 + paramInt2 * 3 + (paramInt2 / 16 + 1) * 15);
    localStringBuffer.append(paramString + "\n");
    for (int i = 0; i < paramInt2; i += 16) {
      localStringBuffer.append(String.format("%04X ", new Object[] { Integer.valueOf(i) }));
      byte b; for (int j = i; j < i + 16; j++) {
        if (j % 4 == 0)
          localStringBuffer.append(' ');
        if (j < paramInt2) {
          b = paramArrayOfByte[(paramInt1 + j)];
          localStringBuffer.append(String.format("%02X", new Object[] { Byte.valueOf(b) }));
        } else {
          localStringBuffer.append("  ");
        } }
      localStringBuffer.append("     ");
      for (int j = i; (j < i + 16) && (j < paramInt2); j++) {
        b = paramArrayOfByte[(paramInt1 + j)];
        if ((b >= 32) && (b < Byte.MAX_VALUE)) {
          localStringBuffer.append((char)b);
        } else
          localStringBuffer.append('.');
      }
      localStringBuffer.append('\n');
    }
    if (paramInt2 > 1024)
      localStringBuffer.append("...\n");
    this.logger.trace(localStringBuffer.toString());
  }
  


  void closeConnection(Connection paramConnection) { closeConnection(paramConnection, false); }
  
  void closeConnection(Connection paramConnection, boolean paramBoolean) {
    if (paramConnection.state == Connection.State.closed) return;
    this.logger.debug( "Closing connection to " + (paramConnection.peer != null ? paramConnection.peer.toString() : paramConnection.host_id));
    synchronized (this.map_key_conn) {
      paramConnection.node_impl.close(paramConnection, paramBoolean);
      this.map_key_conn.remove(paramConnection.key);
      paramConnection.state = Connection.State.closed;
    }
    this.connection_listener.handle(paramConnection.key, paramConnection.peer, false);
  }
  
  private void initiateConnectionClose(Connection paramConnection, int paramInt)
  {
    if (paramConnection.state != Connection.State.ready)
      return;
    paramConnection.state = Connection.State.closing;
    sendDPR(paramConnection, paramInt);
  }
  
  boolean handleMessage(Message paramMessage, Connection paramConnection) throws InvalidAVPLengthException {
    if (this.logger.isDebugEnabled())
      this.logger.debug( "command_code=" + paramMessage.hdr.command_code + " application_id=" + paramMessage.hdr.application_id + " connection_state=" + paramConnection.state);
    paramConnection.timers.markActivity();
    if (paramConnection.state == Connection.State.connected_in)
    {
      if ((!paramMessage.hdr.isRequest()) || (paramMessage.hdr.command_code != 257) || (paramMessage.hdr.application_id != 0))
      {


        this.logger.warn( "Got something that wasn't a CER");
        return false;
      }
      paramConnection.timers.markRealActivity();
      return handleCER(paramMessage, paramConnection); }
    if (paramConnection.state == Connection.State.connected_out)
    {
      if ((paramMessage.hdr.isRequest()) || (paramMessage.hdr.command_code != 257) || (paramMessage.hdr.application_id != 0))
      {


        this.logger.warn( "Got something that wasn't a CEA");
        return false;
      }
      paramConnection.timers.markRealActivity();
      return handleCEA(paramMessage, paramConnection);
    }
    switch (paramMessage.hdr.command_code) {
    case 257: 
      this.logger.warn( "Got CER from " + paramConnection.host_id + " after initial capability-exchange");
      
      return false;
    case 280: 
      if (paramMessage.hdr.isRequest()) {
        return handleDWR(paramMessage, paramConnection);
      }
      return handleDWA(paramMessage, paramConnection);
    case 282: 
      if (paramMessage.hdr.isRequest()) {
        return handleDPR(paramMessage, paramConnection);
      }
      return handleDPA(paramMessage, paramConnection);
    }
    paramConnection.timers.markRealActivity();
    if (paramMessage.hdr.isRequest()) {
      if (isLoopedMessage(paramMessage)) {
        rejectLoopedRequest(paramMessage, paramConnection);
        return true;
      }
      if (!isAllowedApplication(paramMessage, paramConnection.peer)) {
        rejectDisallowedRequest(paramMessage, paramConnection);
        return true;
      }
    }
    
    if (!this.message_dispatcher.handle(paramMessage, paramConnection.key, paramConnection.peer)) {
      if (paramMessage.hdr.isRequest()) {
        return handleUnknownRequest(paramMessage, paramConnection);
      }
      return true;
    }
    return true;
  }
  


  private boolean isLoopedMessage(Message paramMessage)
  {
    for (AVP localAVP : paramMessage.subset(282)) {
      AVP_UTF8String localAVP_UTF8String = new AVP_UTF8String(localAVP);
      if (localAVP_UTF8String.queryValue().equals(this.settings.hostId()))
        return true;
    }
    return false;
  }
  
  private void rejectLoopedRequest(Message paramMessage, Connection paramConnection) { this.logger.warn( "Rejecting looped request from " + paramConnection.peer.host() + " (command=" + paramMessage.hdr.command_code + ").");
    rejectRequest(paramMessage, paramConnection, 3005);
  }
  
  private static class AVP_VendorSpecificApplicationId
    extends AVP_Grouped
  {
    public AVP_VendorSpecificApplicationId(AVP paramAVP) throws InvalidAVPLengthException, InvalidAVPValueException
    {
      super(paramAVP);
      AVP[] arrayOfAVP1 = queryAVPs();
      if (arrayOfAVP1.length < 2)
        throw new InvalidAVPValueException(paramAVP);
      int i = 0;
      int j = 0;
      for (AVP localAVP : arrayOfAVP1) {
        if (localAVP.code == 266) {
          i = 1;
        } else if (localAVP.code == 258) {
          j = 1;
        } else if (localAVP.code == 259) {
          j = 1;
        }
      }
      if ((i == 0) || (j == 0))
        throw new InvalidAVPValueException(paramAVP);
    }
    
    public AVP_VendorSpecificApplicationId(int paramInt1, int paramInt2, int paramInt3) throws InvalidAVPLengthException { 
				super(260,new AVP[0]);
      AVP_Unsigned32 localAVP_Unsigned32;
      if (paramInt2 != 0) {
        localAVP_Unsigned32 = new AVP_Unsigned32(258, paramInt2);
      } else
        localAVP_Unsigned32 = new AVP_Unsigned32(259, paramInt3);
      setAVPs( new AVP[] { new AVP_Unsigned32(266, paramInt1), localAVP_Unsigned32 });
				//super(260,avps);
    }
    
    public int vendorId()
      throws InvalidAVPLengthException, InvalidAVPValueException
    {
      for (AVP localAVP : queryAVPs()) {
        if (localAVP.code == 266)
          return new AVP_Unsigned32(localAVP).queryValue();
      }
      throw new InvalidAVPValueException(this);
    }
    
    public Integer authAppId() throws InvalidAVPLengthException { for (AVP localAVP : queryAVPs()) {
        if (localAVP.code == 258)
          return Integer.valueOf(new AVP_Unsigned32(localAVP).queryValue());
      }
      return null;
    }
    
    public Integer acctAppId() throws InvalidAVPLengthException { for (AVP localAVP : queryAVPs()) {
        if (localAVP.code == 259)
          return Integer.valueOf(new AVP_Unsigned32(localAVP).queryValue());
      }
      return null;
    }
  }
  








  public boolean isAllowedApplication(Message paramMessage, Peer paramPeer)
  {
    try
    {
      AVP localAVP = paramMessage.find(258);
      int i; if (localAVP != null) {
        i = new AVP_Unsigned32(localAVP).queryValue();
        if (this.logger.isDebugEnabled())
          this.logger.debug( "auth-application-id=" + i);
        return paramPeer.capabilities.isAllowedAuthApp(i);
      }
      localAVP = paramMessage.find(259);
      if (localAVP != null) {
        i = new AVP_Unsigned32(localAVP).queryValue();
        if (this.logger.isDebugEnabled())
          this.logger.debug( "acct-application-id=" + i);
        return paramPeer.capabilities.isAllowedAcctApp(i);
      }
      localAVP = paramMessage.find(260);
      if (localAVP != null) {
        AVP_VendorSpecificApplicationId localAVP_VendorSpecificApplicationId = new AVP_VendorSpecificApplicationId(localAVP);
        int j = localAVP_VendorSpecificApplicationId.vendorId();
        if (this.logger.isDebugEnabled()) {
          if (localAVP_VendorSpecificApplicationId.authAppId() != null)
            this.logger.debug( "vendor-id=" + j + ", auth_app=" + localAVP_VendorSpecificApplicationId.authAppId());
          if (localAVP_VendorSpecificApplicationId.acctAppId() != null)
            this.logger.debug( "vendor-id=" + j + ", acct_app=" + localAVP_VendorSpecificApplicationId.acctAppId());
        }
        if (localAVP_VendorSpecificApplicationId.authAppId() != null)
          return paramPeer.capabilities.isAllowedAuthApp(j, localAVP_VendorSpecificApplicationId.authAppId().intValue());
        if (localAVP_VendorSpecificApplicationId.acctAppId() != null)
          return paramPeer.capabilities.isAllowedAcctApp(j, localAVP_VendorSpecificApplicationId.acctAppId().intValue());
        return false;
      }
      this.logger.warn( "No auth-app-id, acct-app-id nor vendor-app in packet");
    } catch (InvalidAVPLengthException localInvalidAVPLengthException) {
      this.logger.debug( "Encountered invalid AVP length while looking at application-id", localInvalidAVPLengthException);
    } catch (InvalidAVPValueException localInvalidAVPValueException) {
      this.logger.debug( "Encountered invalid AVP value while looking at application-id", localInvalidAVPValueException);
    }
    return false;
  }
  
  private void rejectDisallowedRequest(Message paramMessage, Connection paramConnection) { this.logger.warn( "Rejecting request  from " + paramConnection.peer.host() + " (command=" + paramMessage.hdr.command_code + ") because it is not allowed.");
    rejectRequest(paramMessage, paramConnection, 3007);
  }
  
  private void rejectRequest(Message paramMessage, Connection paramConnection, int paramInt) {
    Message localMessage = new Message();
    localMessage.prepareResponse(paramMessage);
    if ((paramInt >= 3000) && (paramInt <= 3999))
      localMessage.hdr.setError(true);
    localMessage.add(new AVP_Unsigned32(268, paramInt));
    addOurHostAndRealm(localMessage);
    Utils.copyProxyInfo(paramMessage, localMessage);
    Utils.setMandatory_RFC3588(localMessage);
    sendMessage(localMessage, paramConnection);
  }
  





  public void addOurHostAndRealm(Message paramMessage)
  {
    paramMessage.add(new AVP_UTF8String(264, this.settings.hostId()));
    paramMessage.add(new AVP_UTF8String(296, this.settings.realm()));
  }
  



  public int nextEndToEndIdentifier()
  {
    return this.node_state.nextEndToEndIdentifier();
  }
  




  public String makeNewSessionId()
  {
    return makeNewSessionId(null);
  }
  








  public String makeNewSessionId(String paramString)
  {
    String str = this.settings.hostId() + ";" + this.node_state.nextSessionId_second_part();
    if (paramString == null) {
      return str;
    }
    return str + ";" + paramString;
  }
  



  public int stateId()
  {
    return this.node_state.stateId();
  }
  
  private boolean doElection(String paramString)
  {
    int i = this.settings.hostId().compareTo(paramString);
    if (i == 0) {
      this.logger.warn( "Got CER with host-id=" + paramString + ". Suspecting this is a connection from ourselves.");
      
      return false;
    }
    int j = i > 0 ? 1 : 0;
    synchronized (this.map_key_conn) {
      for (Entry localEntry : this.map_key_conn.entrySet()) {
        Connection localConnection = (Connection)localEntry.getValue();
        if ((localConnection.host_id != null) && (localConnection.host_id.equals(paramString)) && (localConnection.state == Connection.State.ready))
        {

          this.logger.debug( "New connection to a peer we already have a connection to (" + paramString + ")");
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
  
  private boolean handleCER(Message paramMessage, Connection paramConnection) throws InvalidAVPLengthException {
    this.logger.debug( "CER received from " + paramConnection.host_id);
    


    Object localObject1 = paramMessage.find(264);
    if (localObject1 == null)
    {
      this.logger.debug( "CER from " + paramConnection.host_id + " is missing the Origin-Host_id AVP. Rejecting.");
      Message localObject2 = new Message();
      ((Message)localObject2).prepareResponse(paramMessage);
      ((Message)localObject2).add(new AVP_Unsigned32(268, 5005));
      addOurHostAndRealm((Message)localObject2);
      ((Message)localObject2).add(new AVP_FailedAVP(new AVP_UTF8String(264, "")));
      Utils.setMandatory_RFC3588((Message)localObject2);
      sendMessage((Message)localObject2, paramConnection);
      return false;
    }
    String str = new AVP_UTF8String((AVP)localObject1).queryValue();
    this.logger.debug( "Peer's origin-host-id is " + str);
    



    Object localObject2 = this.node_validator.authenticateNode(str, paramConnection.getRelevantNodeAuthInfo());
    Message localMessage; if ((localObject2 == null) || (!((NodeValidator.AuthenticationResult)localObject2).known)) {
      this.logger.debug( "We do not know " + paramConnection.host_id + " Rejecting.");
      localMessage = new Message();
      localMessage.prepareResponse(paramMessage);
      if ((localObject2 != null) && (((NodeValidator.AuthenticationResult)localObject2).result_code != null)) {
        localMessage.add(new AVP_Unsigned32(268, ((NodeValidator.AuthenticationResult)localObject2).result_code.intValue()));
      } else
        localMessage.add(new AVP_Unsigned32(268, 3010));
      addOurHostAndRealm(localMessage);
      if ((localObject2 != null) && (((NodeValidator.AuthenticationResult)localObject2).error_message != null))
        localMessage.add(new AVP_UTF8String(281, ((NodeValidator.AuthenticationResult)localObject2).error_message));
      Utils.setMandatory_RFC3588(localMessage);
      sendMessage(localMessage, paramConnection);
      return false;
    }
    

    if (!doElection(str)) {
      this.logger.debug( "CER from " + paramConnection.host_id + " lost the election. Rejecting.");
      localMessage = new Message();
      localMessage.prepareResponse(paramMessage);
      localMessage.add(new AVP_Unsigned32(268, 4003));
      addOurHostAndRealm(localMessage);
      Utils.setMandatory_RFC3588(localMessage);
      sendMessage(localMessage, paramConnection);
      return false;
    }
    

    paramConnection.peer = paramConnection.toPeer();
    paramConnection.peer.host(str);
    paramConnection.host_id = str;
    
    if (handleCEx(paramMessage, paramConnection))
    {
      localObject1 = new Message();
      ((Message)localObject1).prepareResponse(paramMessage);
      
      ((Message)localObject1).add(new AVP_Unsigned32(268, 2001));
      addCEStuff((Message)localObject1, paramConnection.peer.capabilities, paramConnection);
      
      this.logger.debug( "Connection to " + paramConnection.peer.toString() + " is now ready");
      Utils.setMandatory_RFC3588((Message)localObject1);
      sendMessage((Message)localObject1, paramConnection);
      paramConnection.state = Connection.State.ready;
      this.connection_listener.handle(paramConnection.key, paramConnection.peer, true);
      synchronized (this.obj_conn_wait) {
        this.obj_conn_wait.notifyAll();
      }
      return true;
    }
    return false;
  }
  
  private boolean handleCEA(Message paramMessage, Connection paramConnection) { this.logger.debug( "CEA received from " + paramConnection.host_id);
    AVP localAVP = paramMessage.find(268);
    if (localAVP == null) {
      this.logger.warn( "CEA from " + paramConnection.host_id + " did not contain a Result-Code AVP (violation of RFC3588 section 5.3.2 page 61). Dropping connection");
      return false;
    }
    int i;
    try {
      i = new AVP_Unsigned32(localAVP).queryValue();
    } catch (InvalidAVPLengthException localInvalidAVPLengthException) {
      this.logger.debug( "CEA from " + paramConnection.host_id + " contained an ill-formed Result-Code. Dropping connection");
      return false;
    }
    if (i != 2001) {
      this.logger.debug( "CEA from " + paramConnection.host_id + " was rejected with Result-Code " + i + ". Dropping connection");
      return false;
    }
    localAVP = paramMessage.find(264);
    if (localAVP == null) {
      this.logger.warn( "Peer did not include origin-host-id in CEA (violation of RFC3588 section 5.3.2 page 61). Dropping connection");
      return false;
    }
    String str = new AVP_UTF8String(localAVP).queryValue();
    this.logger.debug( "Node:Peer's origin-host-id is '" + str + "'. Expected: '" + paramConnection.host_id + "'");
    
    paramConnection.peer = paramConnection.toPeer();
    paramConnection.peer.host(str);
    paramConnection.host_id = str;
    boolean bool = handleCEx(paramMessage, paramConnection);
    if (bool) {
      paramConnection.state = Connection.State.ready;
      this.logger.debug( "Connection to " + paramConnection.peer.toString() + " is now ready");
      this.connection_listener.handle(paramConnection.key, paramConnection.peer, true);
      synchronized (this.obj_conn_wait) {
        this.obj_conn_wait.notifyAll();
      }
      return true;
    }
    return false;
  }
  
  private boolean handleCEx(Message paramMessage, Connection paramConnection) {
    this.logger.debug( "Processing CER/CEA");
    try
    {
      Capability localCapability = new Capability();
      for (Iterator<AVP> localObject1 = paramMessage.subset(265).iterator(); ((Iterator)localObject1).hasNext();) {AVP localObject2 = (AVP)((Iterator)localObject1).next();
        int i = new AVP_Unsigned32((AVP)localObject2).queryValue();
        this.logger.debug( "peer supports vendor " + i);
        localCapability.addSupportedVendor(i); }
      Object localObject2;
      int i; for (Iterator<AVP>localObject1 = paramMessage.subset(258).iterator(); ((Iterator)localObject1).hasNext();) { localObject2 = (AVP)((Iterator)localObject1).next();
        i = new AVP_Unsigned32((AVP)localObject2).queryValue();
        this.logger.debug( "peer supports auth-app " + i);
        if (i != 0)
          localCapability.addAuthApp(i);
      }
      for (Iterator<AVP>localObject1 = paramMessage.subset(259).iterator(); ((Iterator)localObject1).hasNext();) { localObject2 = (AVP)((Iterator)localObject1).next();
        i = new AVP_Unsigned32((AVP)localObject2).queryValue();
        this.logger.debug( "peer supports acct-app " + i);
        if (i != 0)
          localCapability.addAcctApp(i);
      }
      for (Iterator<AVP>localObject1 = paramMessage.subset(260).iterator(); ((Iterator)localObject1).hasNext();) { localObject2 = (AVP)((Iterator)localObject1).next();
        AVP localObject3 = new AVP_VendorSpecificApplicationId((AVP)localObject2);
        int j = ((AVP_VendorSpecificApplicationId)localObject3).vendorId();
        if (((AVP_VendorSpecificApplicationId)localObject3).authAppId() != null)
          localCapability.addVendorAuthApp(j, ((AVP_VendorSpecificApplicationId)localObject3).authAppId().intValue());
        if (((AVP_VendorSpecificApplicationId)localObject3).acctAppId() != null)
          localCapability.addVendorAcctApp(j, ((AVP_VendorSpecificApplicationId)localObject3).acctAppId().intValue());
      }
      Object localObject3;
      Capability localObject1 = this.node_validator.authorizeNode(paramConnection.host_id, this.settings, localCapability);
      if (this.logger.isDebugEnabled()) {
        localObject2 = "";
        for (localObject3 = ((Capability)localObject1).supported_vendor.iterator(); ((Iterator)localObject3).hasNext();) {Integer localObject4 = (Integer)((Iterator)localObject3).next();
          localObject2 = (String)localObject2 + "  supported_vendor " + localObject4 + "\n"; }
        Object localObject4; for (localObject3 = ((Capability)localObject1).auth_app.iterator(); ((Iterator)localObject3).hasNext();) { localObject4 = (Integer)((Iterator)localObject3).next();
          localObject2 = (String)localObject2 + "  auth_app " + localObject4 + "\n"; }
        for (localObject3 = ((Capability)localObject1).acct_app.iterator(); ((Iterator)localObject3).hasNext();) { localObject4 = (Integer)((Iterator)localObject3).next();
          localObject2 = (String)localObject2 + "  acct_app " + localObject4 + "\n"; }
        for (localObject3 = ((Capability)localObject1).auth_vendor.iterator(); ((Iterator)localObject3).hasNext();) { localObject4 = (Capability.VendorApplication)((Iterator)localObject3).next();
          localObject2 = (String)localObject2 + "  vendor_auth_app: vendor " + ((Capability.VendorApplication)localObject4).vendor_id + ", application " + ((Capability.VendorApplication)localObject4).application_id + "\n"; }
        for (localObject3 = ((Capability)localObject1).acct_vendor.iterator(); ((Iterator)localObject3).hasNext();) { localObject4 = (Capability.VendorApplication)((Iterator)localObject3).next();
          localObject2 = (String)localObject2 + "  vendor_acct_app: vendor " + ((Capability.VendorApplication)localObject4).vendor_id + ", application " + ((Capability.VendorApplication)localObject4).application_id + "\n"; }
        this.logger.debug( "Resulting capabilities:\n" + (String)localObject2);
      }
      if (((Capability)localObject1).isEmpty()) {
        this.logger.warn( "No application in common with " + paramConnection.host_id);
        if (paramMessage.hdr.isRequest()) {
          localObject2 = new Message();
          ((Message)localObject2).prepareResponse(paramMessage);
          ((Message)localObject2).add(new AVP_Unsigned32(268, 5010));
          addOurHostAndRealm((Message)localObject2);
          Utils.setMandatory_RFC3588((Message)localObject2);
          sendMessage((Message)localObject2, paramConnection);
        }
        return false;
      }
      
      paramConnection.peer.capabilities = ((Capability)localObject1);
    } catch (InvalidAVPLengthException localInvalidAVPLengthException) {
      this.logger.warn( "Invalid AVP in CER/CEA", localInvalidAVPLengthException);
      if (paramMessage.hdr.isRequest()) {
        Message localObject1 = new Message();
        ((Message)localObject1).prepareResponse(paramMessage);
        ((Message)localObject1).add(new AVP_Unsigned32(268, 5014));
        addOurHostAndRealm((Message)localObject1);
        ((Message)localObject1).add(new AVP_FailedAVP(localInvalidAVPLengthException.avp));
        Utils.setMandatory_RFC3588((Message)localObject1);
        sendMessage((Message)localObject1, paramConnection);
      }
      return false;
    } catch (InvalidAVPValueException localInvalidAVPValueException) { Object localObject1;
      this.logger.warn( "Invalid AVP in CER/CEA", localInvalidAVPValueException);
      if (paramMessage.hdr.isRequest()) {
        localObject1 = new Message();
        ((Message)localObject1).prepareResponse(paramMessage);
        ((Message)localObject1).add(new AVP_Unsigned32(268, 5004));
        addOurHostAndRealm((Message)localObject1);
        ((Message)localObject1).add(new AVP_FailedAVP(localInvalidAVPValueException.avp));
        Utils.setMandatory_RFC3588((Message)localObject1);
        sendMessage((Message)localObject1, paramConnection);
      }
      return false;
    }
    return true;
  }
  

  void initiateCER(Connection paramConnection) throws InvalidAVPLengthException { sendCER(paramConnection); }
  
  private void sendCER(Connection paramConnection) throws InvalidAVPLengthException {
    this.logger.debug( "Sending CER to " + paramConnection.host_id);
    Message localMessage = new Message();
    localMessage.hdr.setRequest(true);
    localMessage.hdr.command_code = 257;
    localMessage.hdr.application_id = 0;
    localMessage.hdr.hop_by_hop_identifier = paramConnection.nextHopByHopIdentifier();
    localMessage.hdr.end_to_end_identifier = this.node_state.nextEndToEndIdentifier();
    addCEStuff(localMessage, this.settings.capabilities(), paramConnection);
    Utils.setMandatory_RFC3588(localMessage);
    
    sendMessage(localMessage, paramConnection);
  }
  
  private void addCEStuff(Message paramMessage, Capability paramCapability, Connection paramConnection) throws InvalidAVPLengthException {
    addOurHostAndRealm(paramMessage);
    
    Collection localCollection = paramConnection.getLocalAddresses();
    for (Iterator localIterator = localCollection.iterator(); localIterator.hasNext();) {Object localObject = (InetAddress)localIterator.next();
      paramMessage.add(new AVP_Address(257, (InetAddress)localObject)); }
    Object localObject;
    paramMessage.add(new AVP_Unsigned32(266, this.settings.vendorId()));
    
    paramMessage.add(new AVP_UTF8String(269, this.settings.productName()));
    
    paramMessage.add(new AVP_Unsigned32(278, this.node_state.stateId()));
    

    for (Iterator localIterator = paramCapability.supported_vendor.iterator(); localIterator.hasNext();) { localObject = (Integer)localIterator.next();
      paramMessage.add(new AVP_Unsigned32(265, ((Integer)localObject).intValue()));
    }
    
    for (Iterator localIterator = paramCapability.auth_app.iterator(); localIterator.hasNext();) { localObject = (Integer)localIterator.next();
      paramMessage.add(new AVP_Unsigned32(258, ((Integer)localObject).intValue()));
    }
    


    for (Iterator localIterator = paramCapability.acct_app.iterator(); localIterator.hasNext();) { localObject = (Integer)localIterator.next();
      paramMessage.add(new AVP_Unsigned32(259, ((Integer)localObject).intValue()));
    }
    
    for (Iterator localIterator = paramCapability.auth_vendor.iterator(); localIterator.hasNext();) { localObject = (Capability.VendorApplication)localIterator.next();
      paramMessage.add(new AVP_VendorSpecificApplicationId(((Capability.VendorApplication)localObject).vendor_id, ((Capability.VendorApplication)localObject).application_id, 0));
    }
    for (Iterator localIterator = paramCapability.acct_vendor.iterator(); localIterator.hasNext();) { localObject = (Capability.VendorApplication)localIterator.next();
      paramMessage.add(new AVP_VendorSpecificApplicationId(((Capability.VendorApplication)localObject).vendor_id, 0, ((Capability.VendorApplication)localObject).application_id));
    }
    
    if (this.settings.firmwareRevision() != 0)
      paramMessage.add(new AVP_Unsigned32(267, this.settings.firmwareRevision()));
  }
  
  private boolean handleDWR(Message paramMessage, Connection paramConnection) {
    this.logger.debug( "DWR received from " + paramConnection.host_id);
    paramConnection.timers.markDWR();
    Message localMessage = new Message();
    localMessage.prepareResponse(paramMessage);
    localMessage.add(new AVP_Unsigned32(268, 2001));
    addOurHostAndRealm(localMessage);
    localMessage.add(new AVP_Unsigned32(278, this.node_state.stateId()));
    Utils.setMandatory_RFC3588(localMessage);
    
    sendMessage(localMessage, paramConnection);
    return true;
  }
  
  private boolean handleDWA(Message paramMessage, Connection paramConnection) { this.logger.debug( "DWA received from " + paramConnection.host_id);
    paramConnection.timers.markDWA();
    return true;
  }
  
  private boolean handleDPR(Message paramMessage, Connection paramConnection) { this.logger.debug( "DPR received from " + paramConnection.host_id);
    Message localMessage = new Message();
    localMessage.prepareResponse(paramMessage);
    localMessage.add(new AVP_Unsigned32(268, 2001));
    addOurHostAndRealm(localMessage);
    Utils.setMandatory_RFC3588(localMessage);
    
    sendMessage(localMessage, paramConnection);
    return false;
  }
  
  private boolean handleDPA(Message paramMessage, Connection paramConnection) { if (paramConnection.state == Connection.State.closing) {
      this.logger.debug( "Got a DPA from " + paramConnection.host_id);
    } else
      this.logger.warn( "Got a DPA. This is not expected (state=" + paramConnection.state + ")");
    return false;
  }
  
  private boolean handleUnknownRequest(Message paramMessage, Connection paramConnection) { this.logger.debug( "Unknown request received from " + paramConnection.host_id);
    rejectRequest(paramMessage, paramConnection, 3002);
    return true;
  }
  
  private void sendDWR(Connection paramConnection) {
    this.logger.debug( "Sending DWR to " + paramConnection.host_id);
    Message localMessage = new Message();
    localMessage.hdr.setRequest(true);
    localMessage.hdr.command_code = 280;
    localMessage.hdr.application_id = 0;
    localMessage.hdr.hop_by_hop_identifier = paramConnection.nextHopByHopIdentifier();
    localMessage.hdr.end_to_end_identifier = this.node_state.nextEndToEndIdentifier();
    addOurHostAndRealm(localMessage);
    localMessage.add(new AVP_Unsigned32(278, this.node_state.stateId()));
    Utils.setMandatory_RFC3588(localMessage);
    
    sendMessage(localMessage, paramConnection);
    
    paramConnection.timers.markDWR_out();
  }
  
  private void sendDPR(Connection paramConnection, int paramInt) {
    this.logger.debug( "Sending DPR to " + paramConnection.host_id);
    Message localMessage = new Message();
    localMessage.hdr.setRequest(true);
    localMessage.hdr.command_code = 282;
    localMessage.hdr.application_id = 0;
    localMessage.hdr.hop_by_hop_identifier = paramConnection.nextHopByHopIdentifier();
    localMessage.hdr.end_to_end_identifier = this.node_state.nextEndToEndIdentifier();
    addOurHostAndRealm(localMessage);
    localMessage.add(new AVP_Unsigned32(273, paramInt));
    Utils.setMandatory_RFC3588(localMessage);
    
    sendMessage(localMessage, paramConnection);
  }
  
  boolean anyOpenConnections(NodeImplementation paramNodeImplementation) {
    synchronized (this.map_key_conn) {
      for (Entry localEntry : this.map_key_conn.entrySet()) {
        Connection localConnection = (Connection)localEntry.getValue();
        if (localConnection.node_impl == paramNodeImplementation)
          return true;
      }
    }
    return false;
  }
  
  void registerInboundConnection(Connection paramConnection) { synchronized (this.map_key_conn) {
      this.map_key_conn.put(paramConnection.key, paramConnection);
    }
  }
  
  void unregisterConnection(Connection paramConnection) { synchronized (this.map_key_conn) {
      this.map_key_conn.remove(paramConnection.key);
    }
  }
  
  Object getLockObject() { return this.map_key_conn; }
}
