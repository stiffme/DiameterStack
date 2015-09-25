package com.esipeng.diameter.session;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.esipeng.diameter.AVP;
import com.esipeng.diameter.AVP_UTF8String;
import com.esipeng.diameter.AVP_Unsigned32;
import com.esipeng.diameter.InvalidAVPLengthException;
import com.esipeng.diameter.Message;
import com.esipeng.diameter.MessageHeader;
import com.esipeng.diameter.Utils;
import com.esipeng.diameter.node.Node;
import com.esipeng.diameter.node.NodeSettings;
import com.esipeng.diameter.node.NotARequestException;
import com.esipeng.diameter.node.NotRoutableException;







public abstract class BaseSession
  implements Session
{
  private SessionManager session_manager;
  private String session_id;
  private State state;
  private int auth_app_id;
  private int session_timeout;
  private boolean state_maintained;
  private long first_auth_time;
  protected SessionAuthTimers session_auth_timers;
  private boolean auth_in_progress;
  
  public static enum State
  {
    idle, 
    pending, 
    open, 
    discon;
    






    private State() {}
  }
  






  public BaseSession(int paramInt, SessionManager paramSessionManager)
  {
    this.state = State.idle;
    this.auth_app_id = paramInt;
    this.session_manager = paramSessionManager;
    this.session_auth_timers = new SessionAuthTimers();
    this.state_maintained = true;
  }
  


  public final SessionManager sessionManager()
  {
    return this.session_manager;
  }
  



  public final String sessionId()
  {
    return this.session_id;
  }
  

  public final State state()
  {
    return this.state;
  }
  


  public final int authAppId()
  {
    return this.auth_app_id;
  }
  


  public final boolean authInProgress()
  {
    return this.auth_in_progress;
  }
  

  protected final void authInProgress(boolean paramBoolean)
  {
    this.auth_in_progress = paramBoolean;
  }
  




  public boolean stateMaintained()
  {
    return this.state_maintained;
  }
  


  protected void stateMaintained(boolean paramBoolean)
  {
    this.state_maintained = paramBoolean;
  }
  





  public long firstAuthTime()
  {
    return this.first_auth_time;
  }
  











  public int handleRequest(Message paramMessage)
  {
    switch (paramMessage.hdr.command_code) {
    case 258: 
      return handleRAR(paramMessage);
    case 274: 
      return handleASR(paramMessage);
    }
    return 3001;
  }
  







  public void handleAnswer(Message paramMessage, Object paramObject)
  {
    switch (paramMessage.hdr.command_code) {
    case 275: 
      handleSTA(paramMessage);
      break;
    default: 
      this.session_manager.logger.log(Level.WARNING, "Session '" + this.session_id + "' could not handle answer (command_code=" + paramMessage.hdr.command_code + ")");
    }
    
  }
  





  public void handleNonAnswer(int paramInt, Object paramObject)
  {
    switch (paramInt) {
    case 275: 
      handleSTA(null);
      break;
    default: 
      this.session_manager.logger.log(Level.WARNING, "Session '" + this.session_id + "' could not handle non-answer (command_code=" + paramInt + ")");
    }
    
  }
  





  protected int handleRAR(Message paramMessage)
  {
    if (!this.auth_in_progress)
      startReauth();
    return 2001;
  }
  




  protected int handleASR(Message paramMessage)
  {
    if (this.state_maintained) {
      closeSession(4);
    }
    else {
      State localState1 = this.state;
      State localState2 = State.idle;
      newStatePre(this.state, localState2, paramMessage, 0);
      this.state = localState2;
      this.session_manager.unregister(this);
      newStatePost(localState1, this.state, paramMessage, 0);
    }
    return 2001;
  }
  





  protected void authSuccessful(Message paramMessage)
  {
    if (state() == State.pending)
      this.first_auth_time = System.currentTimeMillis();
    State localState1 = this.state;
    State localState2 = State.open;
    newStatePre(localState1, localState2, paramMessage, 0);
    this.state = localState2;
    newStatePost(localState1, localState2, paramMessage, 0);
    sessionManager().updateTimeouts(this);
  }
  





  protected void authFailed(Message paramMessage)
  {
    this.auth_in_progress = false;
    this.session_manager.logger.log(Level.INFO, "Authentication/Authorization failed, closing session " + this.session_id);
    if (state() == State.pending) {
      closeSession(paramMessage, 4);
    } else {
      closeSession(paramMessage, 6);
    }
  }
  






  public void handleSTA(Message paramMessage)
  {
    State localState1 = this.state;
    State localState2 = State.idle;
    newStatePre(this.state, localState2, paramMessage, 0);
    this.session_manager.unregister(this);
    this.state = localState2;
    newStatePost(localState1, localState2, paramMessage, 0);
  }
  













  public long calcNextTimeout()
  {
    long l = Long.MAX_VALUE;
    if (this.state == State.open) {
      if (this.session_timeout != 0)
        l = Math.min(l, this.first_auth_time + this.session_timeout * 1000);
      if (!this.auth_in_progress) {
        l = Math.min(l, this.session_auth_timers.getNextReauthTime());
      } else
        l = Math.min(l, this.session_auth_timers.getMaxTimeout());
    }
    return l;
  }
  




  public void handleTimeout()
  {
    if (this.state == State.open) {
      long l = System.currentTimeMillis();
      if ((this.session_timeout != 0) && (l >= this.first_auth_time + this.session_timeout * 1000)) {
        this.session_manager.logger.log(Level.FINE, "Session-Timeout has expired, closing session");
        closeSession(null, 8);
        return;
      }
      if (l >= this.session_auth_timers.getMaxTimeout()) {
        this.session_manager.logger.log(Level.FINE, "authorization-lifetime has expired, closing session");
        closeSession(null, 6);
        return;
      }
      if (l >= this.session_auth_timers.getNextReauthTime()) {
        this.session_manager.logger.log(Level.FINE, "authorization-lifetime(+grace-period) has expired, sending re-authorization");
        startReauth();
        sessionManager().updateTimeouts(this);
        return;
      }
    }
  }
  









  public void newStatePre(State paramState1, State paramState2, Message paramMessage, int paramInt) {}
  








  public void newStatePost(State paramState1, State paramState2, Message paramMessage, int paramInt) {}
  








  public void openSession()
    throws InvalidStateException
  {
    if (this.state != State.idle)
      throw new InvalidStateException("Session cannot be opened unless it is idle");
    if (this.session_id != null)
      throw new InvalidStateException("Sessions cannot be reused");
    this.session_id = makeNewSessionId();
    State localState = State.pending;
    newStatePre(this.state, localState, null, 0);
    this.session_manager.register(this);
    this.state = localState;
    newStatePost(State.idle, localState, null, 0);
    startAuth();
  }
  





  public void closeSession(int paramInt)
  {
    closeSession(null, paramInt);
  }
  



  protected void closeSession(Message paramMessage, int paramInt)
  {
    switch (this.state) {
    case idle: 
      
    case pending: 
      newStatePre(State.pending, State.discon, paramMessage, paramInt);
      sendSTR(paramInt);
      this.state = State.discon;
      newStatePost(State.pending, this.state, paramMessage, paramInt);
      break;
    case open: 
      if (this.state_maintained) {
        newStatePre(State.open, State.discon, paramMessage, paramInt);
        sendSTR(paramInt);
        this.state = State.discon;
        newStatePost(State.open, this.state, paramMessage, paramInt);
      } else {
        newStatePre(State.open, State.idle, paramMessage, paramInt);
        this.state = State.idle;
        this.session_manager.unregister(this);
        newStatePost(State.open, this.state, paramMessage, paramInt);
      }
      break;
    case discon: 
      
    }
    
  }
  





  protected abstract void startAuth();
  





  protected abstract void startReauth();
  




  protected void updateSessionTimeout(int paramInt)
  {
    this.session_timeout = paramInt;
    this.session_manager.updateTimeouts(this);
  }
  
  private final void sendSTR(int paramInt) {
    this.session_manager.logger.log(Level.FINE, "Sending STR for session " + this.session_id);
    Message localMessage = new Message();
    localMessage.hdr.setRequest(true);
    localMessage.hdr.setProxiable(true);
    localMessage.hdr.application_id = authAppId();
    localMessage.hdr.command_code = 275;
    collectSTRInfo(localMessage, paramInt);
    Utils.setMandatory_RFC3588(localMessage);
    try {
      this.session_manager.sendRequest(localMessage, this, null);

    }
    catch (NotARequestException localNotARequestException) {}catch (NotRoutableException localNotRoutableException)
    {
      handleSTA(null);
    }
  }
  








  protected void collectSTRInfo(Message paramMessage, int paramInt)
  {
    addCommonStuff(paramMessage);
    paramMessage.add(new AVP_Unsigned32(258, authAppId()));
    paramMessage.add(new AVP_Unsigned32(295, paramInt));
  }
  






  protected String getDestinationRealm()
  {
    return this.session_manager.settings().realm();
  }
  








  protected String getSessionIdOptionalPart()
  {
    return null;
  }
  






  protected static final int getResultCode(Message paramMessage)
  {
    AVP localAVP = paramMessage.find(268);
    if (localAVP != null) {
      try {
        return new AVP_Unsigned32(localAVP).queryValue();
      } catch (InvalidAVPLengthException localInvalidAVPLengthException) {
        return -1;
      }
    }
    return -1;
  }
  






  public void addCommonStuff(Message paramMessage)
  {
    paramMessage.add(new AVP_UTF8String(263, this.session_id));
    paramMessage.add(new AVP_UTF8String(264, this.session_manager.settings().hostId()));
    paramMessage.add(new AVP_UTF8String(296, this.session_manager.settings().realm()));
    paramMessage.add(new AVP_UTF8String(283, getDestinationRealm()));
  }
  
  private final String makeNewSessionId() {
    return this.session_manager.node().makeNewSessionId(getSessionIdOptionalPart());
  }
}

