package com.esipeng.diameter.session;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.esipeng.diameter.Message;
import com.esipeng.diameter.node.Peer;

public class SessionManager extends com.esipeng.diameter.node.NodeManager
{
  private java.util.Map<String, SessionAndTimeout> map_session;
  private Peer[] peers;
  private Thread timer_thread;
  private long earliest_timeout;
  private boolean stop;
  Logger logger;
  
  private static class SessionAndTimeout
  {
    public Session session;
    public long timeout;
    public boolean deleted;
    
    public SessionAndTimeout(Session paramSession)
    {
      this.session = paramSession;
      this.timeout = paramSession.calcNextTimeout();
      this.deleted = false;
    }
  }
  

  private static class RequestState
  {
    public int command_code;
    
    public Object state;
    
    public Session session;
  }
  

  public SessionManager(com.esipeng.diameter.node.NodeSettings paramNodeSettings, Peer[] paramArrayOfPeer)
    throws com.esipeng.diameter.node.InvalidSettingException
  {
    super(paramNodeSettings);
    if (paramNodeSettings.port() == 0)
      throw new com.esipeng.diameter.node.InvalidSettingException("If you have sessions then you must allow inbound connections");
    this.map_session = new java.util.HashMap();
    this.peers = paramArrayOfPeer;
    this.earliest_timeout = Long.MAX_VALUE;
    this.stop = false;
    this.logger = Logger.getLogger("com.esipeng.diameter.session");
  }
  


  public void start()
    throws java.io.IOException, com.esipeng.diameter.node.UnsupportedTransportProtocolException
  {
    this.logger.log(Level.FINE, "Starting session manager");
    super.start();
    this.timer_thread = new TimerThread();
    this.timer_thread.setDaemon(true);
    this.timer_thread.start();
    for (Peer localPeer : this.peers) {
      super.node().initiateConnection(localPeer, true);
    }
  }
  



  public void stop(long paramLong)
  {
    this.logger.log(Level.FINE, "Stopping session manager");
    super.stop(paramLong);
    synchronized (this.map_session) {
      this.stop = true;
      this.map_session.notify();
    }
    try {
      this.timer_thread.join();
    } catch (InterruptedException localInterruptedException) {}
    this.logger.log(Level.FINE, "Session manager stopped");
  }
  




  protected void handleRequest(Message paramMessage, com.esipeng.diameter.node.ConnectionKey paramConnectionKey, Peer paramPeer)
  {
    this.logger.log(Level.FINE, "Handling request, command_code=" + paramMessage.hdr.command_code);
    
    Message localMessage = new Message();
    localMessage.prepareResponse(paramMessage);
    
    String str = extractSessionId(paramMessage);
    if (str == null) {
      this.logger.log(Level.FINE, "Cannot handle request - no Session-Id AVP in request");
      localMessage.add(new com.esipeng.diameter.AVP_Unsigned32(268, 5005));
      node().addOurHostAndRealm(localMessage);
      localMessage.add(new com.esipeng.diameter.AVP_Grouped(279, new com.esipeng.diameter.AVP[] { new com.esipeng.diameter.AVP_UTF8String(263, "") }));
      com.esipeng.diameter.Utils.copyProxyInfo(paramMessage, localMessage);
      com.esipeng.diameter.Utils.setMandatory_RFC3588(localMessage);
      try {
        answer(localMessage, paramConnectionKey);
      } catch (com.esipeng.diameter.node.NotAnAnswerException localNotAnAnswerException1) {}
      return;
    }
    Session localSession = findSession(str);
    if (localSession == null) {
      this.logger.log(Level.FINE, "Cannot handle request - Session-Id '" + str + " does not denote a known session");
      localMessage.add(new com.esipeng.diameter.AVP_Unsigned32(268, 5002));
      node().addOurHostAndRealm(localMessage);
      com.esipeng.diameter.Utils.copyProxyInfo(paramMessage, localMessage);
      com.esipeng.diameter.Utils.setMandatory_RFC3588(localMessage);
      try {
        answer(localMessage, paramConnectionKey);
      } catch (com.esipeng.diameter.node.NotAnAnswerException localNotAnAnswerException2) {}
      return;
    }
    int i = localSession.handleRequest(paramMessage);
    localMessage.add(new com.esipeng.diameter.AVP_Unsigned32(268, i));
    node().addOurHostAndRealm(localMessage);
    com.esipeng.diameter.Utils.copyProxyInfo(paramMessage, localMessage);
    com.esipeng.diameter.Utils.setMandatory_RFC3588(localMessage);
    try {
      answer(localMessage, paramConnectionKey);
    }
    catch (com.esipeng.diameter.node.NotAnAnswerException localNotAnAnswerException3) {}
  }
  









  protected void handleAnswer(Message paramMessage, com.esipeng.diameter.node.ConnectionKey paramConnectionKey, Object paramObject)
  {
    if (paramMessage != null) {
      this.logger.log(Level.FINE, "Handling answer, command_code=" + paramMessage.hdr.command_code);
    } else {
      this.logger.log(Level.FINE, "Handling non-answer");
    }
    String str = extractSessionId(paramMessage);
    this.logger.log(Level.FINEST, "session-id=" + str);
    Session localSession; if (str != null) {
      localSession = findSession(str);
    } else {
      localSession = ((RequestState)paramObject).session;
    }
    if (localSession == null) {
      this.logger.log(Level.FINE, "Session '" + str + "' not found");
      return;
    }
    this.logger.log(Level.FINE, "Found session, dispatching (non-)answer to it");
    
    if (paramMessage != null) {
      localSession.handleAnswer(paramMessage, ((RequestState)paramObject).state);
    } else {
      localSession.handleNonAnswer(((RequestState)paramObject).command_code, ((RequestState)paramObject).state);
    }
  }
  







  public void sendRequest(Message paramMessage, Session paramSession, Object paramObject)
    throws com.esipeng.diameter.node.NotRoutableException, com.esipeng.diameter.node.NotARequestException
  {
    this.logger.log(Level.FINE, "Sending request (command_code=" + paramMessage.hdr.command_code + ") for session " + paramSession.sessionId());
    RequestState localRequestState = new RequestState();
    localRequestState.command_code = paramMessage.hdr.command_code;
    localRequestState.state = paramObject;
    localRequestState.session = paramSession;
    sendRequest(paramMessage, peers(paramMessage), localRequestState);
  }
  



  public Peer[] peers()
  {
    return this.peers;
  }
  





  public Peer[] peers(Message paramMessage)
  {
    return this.peers;
  }
  




  public void register(Session paramSession)
  {
    SessionAndTimeout localSessionAndTimeout = new SessionAndTimeout(paramSession);
    synchronized (this.map_session) {
      this.map_session.put(paramSession.sessionId(), localSessionAndTimeout);
      if (localSessionAndTimeout.timeout < this.earliest_timeout) {
        this.map_session.notify();
      }
    }
  }
  


  public void unregister(Session paramSession)
  {
    this.logger.log(Level.FINE, "Unregistering session " + paramSession.sessionId());
    synchronized (this.map_session) {
      SessionAndTimeout localSessionAndTimeout = (SessionAndTimeout)this.map_session.get(paramSession.sessionId());
      if (localSessionAndTimeout != null) {
        localSessionAndTimeout.deleted = true;
        if (this.earliest_timeout == Long.MAX_VALUE)
          this.map_session.notify();
        return;
      }
    }
    this.logger.log(Level.WARNING, "Could not find session " + paramSession.sessionId());
  }
  




  public void updateTimeouts(Session paramSession)
  {
    synchronized (this.map_session) {
      SessionAndTimeout localSessionAndTimeout = (SessionAndTimeout)this.map_session.get(paramSession.sessionId());
      if (localSessionAndTimeout == null)
        return;
      localSessionAndTimeout.timeout = paramSession.calcNextTimeout();
      if (localSessionAndTimeout.timeout < this.earliest_timeout)
        this.map_session.notify();
    }
  }
  
  private final Session findSession(String paramString) {
    synchronized (this.map_session) {
      SessionAndTimeout localSessionAndTimeout = (SessionAndTimeout)this.map_session.get(paramString);
      return (localSessionAndTimeout != null) && (!localSessionAndTimeout.deleted) ? localSessionAndTimeout.session : null;
    }
  }
  
  private final String extractSessionId(Message paramMessage) {
    if (paramMessage == null)
      return null;
    java.util.Iterator localIterator = paramMessage.iterator(263);
    if (!localIterator.hasNext())
      return null;
    return new com.esipeng.diameter.AVP_UTF8String((com.esipeng.diameter.AVP)localIterator.next()).queryValue();
  }
  
  private class TimerThread
    extends Thread {
    public TimerThread() { super(); }
    
    public void run() {
      synchronized (SessionManager.this.map_session) {
        while (!SessionManager.this.stop) {
          long l = System.currentTimeMillis();
          SessionManager.this.earliest_timeout = Long.MAX_VALUE;
          java.util.Iterator localIterator = SessionManager.this.map_session.entrySet().iterator();
          while (localIterator.hasNext())
          {

            java.util.Map.Entry localEntry = (java.util.Map.Entry)localIterator.next();
            if (((SessionAndTimeout)localEntry.getValue()).deleted) {
              localIterator.remove();
            }
            else {
              Session localSession = ((SessionAndTimeout)localEntry.getValue()).session;
              if (((SessionAndTimeout)localEntry.getValue()).timeout < l) {
                localSession.handleTimeout();
                ((SessionAndTimeout)localEntry.getValue()).timeout = localSession.calcNextTimeout();
              }
              SessionManager.this.earliest_timeout = Math.min(SessionManager.this.earliest_timeout, ((SessionAndTimeout)localEntry.getValue()).timeout);
            }
          }
          l = System.currentTimeMillis();
          try {
            if (SessionManager.this.earliest_timeout > l) {
              if (SessionManager.this.earliest_timeout == Long.MAX_VALUE) {
                SessionManager.this.map_session.wait();
              } else {
                SessionManager.this.map_session.wait(SessionManager.this.earliest_timeout - l);
              }
            }
          }
          catch (InterruptedException localInterruptedException) {}
        }
      }
    }
  }
}
