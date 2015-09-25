package com.esipeng.diameter.session;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.esipeng.diameter.AVP;
import com.esipeng.diameter.AVP_Unsigned32;
import com.esipeng.diameter.InvalidAVPLengthException;
import com.esipeng.diameter.Message;
import com.esipeng.diameter.MessageHeader;
import com.esipeng.diameter.Utils;
import com.esipeng.diameter.node.NotARequestException;
import com.esipeng.diameter.node.NotRoutableException;

public class AASession
  extends BaseSession
{
  private static Logger logger = Logger.getLogger("com.esipeng.diameter.session.AASession");
  
  public AASession(int paramInt, SessionManager paramSessionManager) { super(paramInt, paramSessionManager); }
  





  public void handleAnswer(Message paramMessage, Object paramObject)
  {
    switch (paramMessage.hdr.command_code) {
    case 265: 
      handleAAA(paramMessage);
      break;
    default: 
      super.handleAnswer(paramMessage, paramObject);
    }
    
  }
  





  public void handleNonAnswer(int paramInt, Object paramObject)
  {
    switch (paramInt) {
    case 265: 
      if (authInProgress()) {
        authFailed(null);
      } else
        logger.log(Level.INFO, "Got a non-answer AA for session '" + sessionId() + "' when no reauth was progress.");
      break;
    default: 
      super.handleNonAnswer(paramInt, paramObject);
    }
    
  }
  






  public void handleAAA(Message paramMessage)
  {
    logger.log(Level.FINER, "Handling AAA");
    if (!authInProgress())
      return;
    authInProgress(false);
    if (state() == BaseSession.State.discon)
      return;
    int i = getResultCode(paramMessage);
    switch (i)
    {
    case 2001: 
      if (processAAAInfo(paramMessage)) {
        authSuccessful(paramMessage);
      } else {
        closeSession(paramMessage, 3);
      }
      break;
    
    case 1001: 
      sendAAR();
      break;
    case 5003: 
      logger.log(Level.INFO, "Authorization for session " + sessionId() + " rejected, closing session");
      if (state() == BaseSession.State.pending) {
        closeSession(paramMessage, 3);
      } else
        closeSession(paramMessage, 6);
      break;
    default: 
      logger.log(Level.INFO, "AAR failed, result_code=" + i);
      closeSession(paramMessage, 3);
    }
  }
  
  protected void startAuth()
  {
    sendAAR();
  }
  
  protected void startReauth() { sendAAR(); }
  
  private final void sendAAR()
  {
    logger.log(Level.FINE, "Considering sending AAR for " + sessionId());
    if (authInProgress())
      return;
    logger.log(Level.FINE, "Sending AAR for " + sessionId());
    authInProgress(true);
    Message localMessage = new Message();
    localMessage.hdr.setRequest(true);
    localMessage.hdr.setProxiable(true);
    localMessage.hdr.application_id = authAppId();
    localMessage.hdr.command_code = 265;
    collectAARInfo(localMessage);
    Utils.setMandatory_RFC3588(localMessage);
    try {
      sessionManager().sendRequest(localMessage, this, null);

    }
    catch (NotARequestException localNotARequestException) {}catch (NotRoutableException localNotRoutableException)
    {
      logger.log(Level.INFO, "Could not send AAR for session " + sessionId(), localNotRoutableException);
      authFailed(null);
    }
  }
  












  protected void collectAARInfo(Message paramMessage)
  {
    addCommonStuff(paramMessage);
    paramMessage.add(new AVP_Unsigned32(258, authAppId()));
  }
  





  protected boolean processAAAInfo(Message paramMessage)
  {
    logger.log(Level.FINE, "Processing AAA info");
    


    try
    {
      long l1 = 0L;
      AVP localAVP = paramMessage.find(291);
      if (localAVP != null)
        l1 = new AVP_Unsigned32(localAVP).queryValue() * 1000;
      long l2 = 0L;
      localAVP = paramMessage.find(276);
      if (localAVP != null)
        l2 = new AVP_Unsigned32(localAVP).queryValue() * 1000;
      localAVP = paramMessage.find(27);
      int i; if (localAVP != null) {
        i = new AVP_Unsigned32(localAVP).queryValue();
        updateSessionTimeout(i);
      }
      localAVP = paramMessage.find(277);
      if (localAVP != null) {
        i = new AVP_Unsigned32(localAVP).queryValue();
        stateMaintained(i == 0);
      }
      
      long l3 = System.currentTimeMillis();
      logger.log(Level.FINER, "Session " + sessionId() + ": now=" + l3 + "  auth_lifetime=" + l1 + " auth_grace_period=" + l2);
      this.session_auth_timers.updateTimers(l3, l1, l2);
      logger.log(Level.FINER, "getNextReauthTime=" + this.session_auth_timers.getNextReauthTime() + " getMaxTimeout=" + this.session_auth_timers.getMaxTimeout());
    } catch (InvalidAVPLengthException localInvalidAVPLengthException) {
      return false;
    }
    return true;
  }
}
