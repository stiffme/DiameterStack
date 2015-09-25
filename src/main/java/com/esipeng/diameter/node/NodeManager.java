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




























public class NodeManager
  implements MessageDispatcher, ConnectionListener
{
  private Node node;
  private NodeSettings settings;
  private Map<ConnectionKey, Map<Integer, RequestData>> req_map;
  private Logger logger;
  private boolean stop_timeout_thread;
  private TimeoutThread timeout_thread;
  private boolean timeout_thread_actively_waiting;
  
  private class RequestData
  {
    public Object state;
    public long timeout_time;
    
    RequestData(Object paramObject, long paramLong)
    {
      this.state = paramObject;
      this.timeout_time = paramLong;
    }
  }
  











  public NodeManager(NodeSettings paramNodeSettings)
  {
    this(paramNodeSettings, null);
  }
  




  public NodeManager(NodeSettings paramNodeSettings, NodeValidator paramNodeValidator)
  {
    this.node = new Node(this, this, paramNodeSettings, paramNodeValidator);
    this.settings = paramNodeSettings;

    this.req_map = new HashMap<ConnectionKey, Map<Integer,RequestData>>();
//logger.info( "Hash Map is newed " + this.req_map.toString(), new Throwable());
    this.logger = LoggerFactory.getLogger("com.esipeng.diameter.node");
  }
  



  public void start()
    throws IOException, UnsupportedTransportProtocolException
  {
    this.node.start();
    this.stop_timeout_thread = false;
    this.timeout_thread_actively_waiting = false;
    this.timeout_thread = new TimeoutThread();
    this.timeout_thread.setDaemon(true);
    this.timeout_thread.start();
  }
  


  public void stop()
  {
    stop(0L);
  }
  




  public void stop(long paramLong)
  {
    this.node.stop(paramLong);
    this.stop_timeout_thread = true;
    synchronized (this.req_map) {
      for (Entry<ConnectionKey, Map<Integer, RequestData>> localEntry1 : this.req_map.entrySet()) {
        ConnectionKey localConnectionKey = (ConnectionKey)localEntry1.getKey();
				  
        for (Entry<Integer, RequestData> localEntry2 : localEntry1.getValue().entrySet())
          handleAnswer(null, localConnectionKey, ((RequestData)localEntry2.getValue()).state);
      }
      //ConnectionKey localConnectionKey;
      this.req_map.notify();
    }
    try {
      this.timeout_thread.join();
    } catch (InterruptedException localInterruptedException) {}
    this.timeout_thread = null;
    //logger.info( "Hash Map is newed", new Throwable());
    this.req_map = new HashMap<ConnectionKey, Map<Integer,RequestData>>();
  }
  




  public boolean waitForConnection()
    throws InterruptedException
  {
    return this.node.waitForConnection();
  }
  




  public boolean waitForConnection(long paramLong)
    throws InterruptedException
  {
    return this.node.waitForConnection(paramLong);
  }
  







  public void waitForConnectionTimeout(long paramLong)
    throws InterruptedException, ConnectionTimeoutException
  {
    this.node.waitForConnectionTimeout(paramLong);
  }
  

  public Node node() { return this.node; }
  
  public NodeSettings settings() { return this.settings; }
  
























  protected void handleRequest(Message paramMessage, ConnectionKey paramConnectionKey, Peer paramPeer)
  {
    Message localMessage = new Message();
    this.logger.debug( "Handling incoming request, command_code=" + paramMessage.hdr.command_code + ", peer=" + paramPeer.host() + ", end2end=" + paramMessage.hdr.end_to_end_identifier + ", hopbyhop=" + paramMessage.hdr.hop_by_hop_identifier);
    localMessage.prepareResponse(paramMessage);
    localMessage.hdr.setError(true);
    localMessage.add(new AVP_Unsigned32(268, 3002));
    this.node.addOurHostAndRealm(localMessage);
    Utils.copyProxyInfo(paramMessage, localMessage);
    Utils.setMandatory_RFC3588(localMessage);
    try {
      answer(localMessage, paramConnectionKey);
    }
    catch (NotAnAnswerException localNotAnAnswerException) {}
  }
  












  protected void handleAnswer(Message paramMessage, ConnectionKey paramConnectionKey, Object paramObject)
  {
    this.logger.debug( "Handling incoming answer, command_code=" + paramMessage.hdr.command_code + ", end2end=" + paramMessage.hdr.end_to_end_identifier + ", hopbyhop=" + paramMessage.hdr.hop_by_hop_identifier);
  }
  





  protected final void answer(Message paramMessage, ConnectionKey paramConnectionKey)
    throws NotAnAnswerException
  {
    if (paramMessage.hdr.isRequest())
      throw new NotAnAnswerException();
    try {
      this.node.sendMessage(paramMessage, paramConnectionKey);
    }
    catch (StaleConnectionException localStaleConnectionException) {}
  }
  








  protected final void forwardRequest(Message paramMessage, ConnectionKey paramConnectionKey, Object paramObject)
    throws StaleConnectionException, NotARequestException, NotProxiableException
  {
    forwardRequest(paramMessage, paramConnectionKey, paramObject, -1L);
  }
  











  protected final void forwardRequest(Message paramMessage, ConnectionKey paramConnectionKey, Object paramObject, long paramLong)
    throws StaleConnectionException, NotARequestException, NotProxiableException
  {
    if (!paramMessage.hdr.isProxiable())
      throw new NotProxiableException();
    int i = 0;
    String str = this.settings.hostId();
    for (AVP localAVP : paramMessage.subset(282)) {
      if (new AVP_UTF8String(localAVP).queryValue().equals(str)) {
        i = 1;
        break;
      }
    }
    if (i == 0)
    {
      paramMessage.add(new AVP_UTF8String(282, this.settings.hostId()));
    }
    
    sendRequest(paramMessage, paramConnectionKey, paramObject, paramLong);
  }
  








  protected final void forwardAnswer(Message paramMessage, ConnectionKey paramConnectionKey)
    throws StaleConnectionException, NotAnAnswerException, NotProxiableException
  {
    if (!paramMessage.hdr.isProxiable())
      throw new NotProxiableException();
    if (paramMessage.hdr.isRequest()) {
      throw new NotAnAnswerException();
    }
    paramMessage.add(new AVP_UTF8String(282, this.settings.hostId()));
    
    answer(paramMessage, paramConnectionKey);
  }
  







  public final void sendRequest(Message paramMessage, ConnectionKey paramConnectionKey, Object paramObject)
    throws StaleConnectionException, NotARequestException
  {
    sendRequest(paramMessage, paramConnectionKey, paramObject, -1L);
  }
  









  public final void sendRequest(Message paramMessage, ConnectionKey paramConnectionKey, Object paramObject, long paramLong)
    throws StaleConnectionException, NotARequestException
  {
    if (!paramMessage.hdr.isRequest())
      throw new NotARequestException();
    paramMessage.hdr.hop_by_hop_identifier = this.node.nextHopByHopIdentifier(paramConnectionKey);
    
    synchronized (this.req_map) {
      Map<Integer, RequestData> localMap = this.req_map.get(paramConnectionKey);
      if (localMap == null) throw new StaleConnectionException();
				if(paramLong > 0)
      	localMap.put(Integer.valueOf(paramMessage.hdr.hop_by_hop_identifier), new RequestData(paramObject, System.currentTimeMillis() + paramLong));
				else
					localMap.put(Integer.valueOf(paramMessage.hdr.hop_by_hop_identifier), new RequestData(paramObject, paramLong));
				logger.info( "Adding h2h" + paramMessage.hdr.hop_by_hop_identifier + " to " + localMap.toString() + "ConnectionKey is " + paramConnectionKey.toString());
      if ((paramLong >= 0L) && (!this.timeout_thread_actively_waiting))
        this.req_map.notify();
    }
    this.node.sendMessage(paramMessage, paramConnectionKey);
    this.logger.info( "Request sent, command_code=" + paramMessage.hdr.command_code + " hop_by_hop_identifier=" + paramMessage.hdr.hop_by_hop_identifier);
  }
  











  public final void sendRequest(Message paramMessage, Peer[] paramArrayOfPeer, Object paramObject)
    throws NotRoutableException, NotARequestException
  {
    sendRequest(paramMessage, paramArrayOfPeer, paramObject, -1L);
  }
  










  public final void sendRequest(Message paramMessage, Peer[] paramArrayOfPeer, Object paramObject, long paramLong)
    throws NotRoutableException, NotARequestException
  {
    this.logger.debug( "Sending request (command_code=" + paramMessage.hdr.command_code + ") to " + paramArrayOfPeer.length + " peers");
    paramMessage.hdr.end_to_end_identifier = this.node.nextEndToEndIdentifier();
    int i = 0;
    int j = 0;
    for (Peer localPeer1 : paramArrayOfPeer) {
      i = 1;
      this.logger.debug( "Considering sending request to " + localPeer1.host());
      ConnectionKey localConnectionKey = this.node.findConnection(localPeer1);
      if (localConnectionKey != null) {
        Peer localPeer2 = this.node.connectionKey2Peer(localConnectionKey);
        if (localPeer2 != null)
          if (!this.node.isAllowedApplication(paramMessage, localPeer2)) {
            this.logger.debug( "peer " + localPeer1.host() + " cannot handle request");
          }
          else {
            j = 1;
            try {
              sendRequest(paramMessage, localConnectionKey, paramObject, paramLong);
              return;
            }
            catch (StaleConnectionException localStaleConnectionException)
            {
              this.logger.debug( "Setting retransmit bit");
              paramMessage.hdr.setRetransmit(true);
            } } } }
    if (j != 0)
      throw new NotRoutableException("All capable peer connections went stale");
    if (i != 0) {
      throw new NotRoutableException("No capable peers");
    }
    throw new NotRoutableException();
  }
  






  public final boolean handle(Message paramMessage, ConnectionKey paramConnectionKey, Peer paramPeer)
  {
    if (paramMessage.hdr.isRequest()) {
      this.logger.debug( "Handling request");
      handleRequest(paramMessage, paramConnectionKey, paramPeer);
    } else {
      this.logger.debug( "Handling answer, hop_by_hop_identifier=" + paramMessage.hdr.hop_by_hop_identifier);
      
      Object localObject1 = null;
      int i = 0;
      synchronized (this.req_map) {
        Map<Integer, RequestData> localMap = this.req_map.get(paramConnectionKey);
        if (localMap != null) {
					RequestData rq = localMap.get(paramMessage.hdr.hop_by_hop_identifier);
					if(rq == null)	{
						logger.warn( "h2h " + paramMessage.hdr.hop_by_hop_identifier + " not found. LocalMap is " + localMap.toString() + "ConnectionKey is " + paramConnectionKey.toString());
						return false;
					}
          localObject1 = (localMap.get(Integer.valueOf(paramMessage.hdr.hop_by_hop_identifier))).state;
					
          localMap.remove(Integer.valueOf(paramMessage.hdr.hop_by_hop_identifier));
          i = 1;
        }
      }
      if (i != 0) {
        handleAnswer(paramMessage, paramConnectionKey, localObject1);
					logger.info( "h2h " + paramMessage.hdr.hop_by_hop_identifier + " answered");
      } else {
        this.logger.debug( "Answer did not match any outstanding request");
      }
    }
    return true;
  }
  




  public final void handle(ConnectionKey paramConnectionKey, Peer paramPeer, boolean paramBoolean)
  {
    synchronized (this.req_map) {
      if (paramBoolean)
      {
        this.req_map.put(paramConnectionKey, new HashMap<Integer, RequestData>());
				  //logger.info( "Saving connection key " + paramConnectionKey.toString(),new Throwable());
      }
      else {
        Map<Integer, RequestData> localMap = this.req_map.get(paramConnectionKey);
        if (localMap == null) { return;
        }
        this.req_map.remove(paramConnectionKey);
        //logger.info( "Removing connection key " + paramConnectionKey.toString(),new Throwable());
        for (Entry<Integer, RequestData> localEntry : localMap.entrySet()) {
          handleAnswer(null, paramConnectionKey, localEntry.getValue().state);
        }
      }
    }
  }
  


  private class TimeoutThread
    extends Thread
  {
    public TimeoutThread() { super(); }
    
    public void run() {
      while (!NodeManager.this.stop_timeout_thread) {
        synchronized (NodeManager.this.req_map) {
          int i = 0;
          long l = System.currentTimeMillis();
          for (Iterator<Entry<ConnectionKey, Map<Integer, RequestData>>> localIterator1 = NodeManager.this.req_map.entrySet().iterator(); localIterator1.hasNext();) {
					  Entry<ConnectionKey, Map<Integer, RequestData>> localEntry1 = localIterator1.next();
            ConnectionKey localConnectionKey = localEntry1.getKey();
            for (Entry<Integer, RequestData> localEntry2 : localEntry1.getValue().entrySet() ) {
              RequestData localRequestData = (RequestData)localEntry2.getValue();
              if (localRequestData.timeout_time >= 0L) i = 1;
              if ((localRequestData.timeout_time >= 0L) && (localRequestData.timeout_time <= l)) {
                localEntry1.getValue().remove(localEntry2.getKey());
                NodeManager.this.logger.info( "Timing out request");
                NodeManager.this.handleAnswer(null, localConnectionKey, localRequestData.state);
              }
            } }
          try { Entry localEntry1;
            ConnectionKey localConnectionKey;
            if (i != 0) {
              NodeManager.this.timeout_thread_actively_waiting = true;
              NodeManager.this.req_map.wait(1000L);
            } else {
              NodeManager.this.req_map.wait();
            }
            NodeManager.this.timeout_thread_actively_waiting = false;
          }
          catch (InterruptedException localInterruptedException) {}
        }
      }
    }
  }
}

