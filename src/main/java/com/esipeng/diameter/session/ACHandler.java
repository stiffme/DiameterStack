package com.esipeng.diameter.session;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.esipeng.diameter.AVP;
import com.esipeng.diameter.AVP_Time;
import com.esipeng.diameter.AVP_UTF8String;
import com.esipeng.diameter.AVP_Unsigned32;
import com.esipeng.diameter.AVP_Unsigned64;
import com.esipeng.diameter.InvalidAVPLengthException;
import com.esipeng.diameter.Message;
import com.esipeng.diameter.MessageHeader;
import com.esipeng.diameter.Utils;
import com.esipeng.diameter.node.NotARequestException;
import com.esipeng.diameter.node.NotRoutableException;





public class ACHandler
{
  private BaseSession base_session;
  private long subsession_sequencer;
  private int accounting_record_number;
  public String acct_multi_session_id;
  public Integer acct_application_id;
  private Map<Long, SubSession> subsessions;
  
  public static class SubSession
  {
    final long subsession_id;
    boolean start_sent;
    public long interim_interval;
    long next_interim;
    int most_recent_record_number;
    public Long acct_session_time;
    public Long acct_input_octets;
    public Long acct_output_octets;
    public Long acct_input_packets;
    public Long acct_output_packets;
    
    SubSession(long paramLong)
    {
      this.subsession_id = paramLong;
      this.interim_interval = Long.MAX_VALUE;
      this.next_interim = Long.MAX_VALUE;
      this.most_recent_record_number = -1;
    }
  }
  





  public ACHandler(BaseSession paramBaseSession)
  {
    this.base_session = paramBaseSession;
    this.accounting_record_number = 0;
    this.subsessions = new HashMap();
    this.subsession_sequencer = 0L;
    this.subsessions.put(Long.valueOf(this.subsession_sequencer), new SubSession(this.subsession_sequencer++));
  }
  



  public long calcNextTimeout()
  {
    long l = Long.MAX_VALUE;
    for (Entry localEntry : this.subsessions.entrySet()) {
      SubSession localSubSession = (SubSession)localEntry.getValue();
      l = Math.min(l, localSubSession.next_interim);
    }
    return l;
  }
  


  public void handleTimeout()
  {
    long l = System.currentTimeMillis();
    for (Entry localEntry : this.subsessions.entrySet()) {
      SubSession localSubSession = (SubSession)localEntry.getValue();
      if (localSubSession.next_interim <= l) {
        sendInterim(localSubSession);
      }
    }
  }
  






  public long createSubSession()
  {
    SubSession localSubSession = new SubSession(this.subsession_sequencer++);
    this.subsessions.put(Long.valueOf(localSubSession.subsession_id), localSubSession);
    return localSubSession.subsession_id;
  }
  



  public SubSession subSession(long paramLong)
  {
    return (SubSession)this.subsessions.get(Long.valueOf(paramLong));
  }
  



  public void startSubSession(long paramLong)
  {
    if (paramLong == 0L) return;
    SubSession localSubSession = subSession(paramLong);
    if (localSubSession == null) return;
    if (localSubSession.start_sent) return;
    sendStart(localSubSession);
  }
  




  public void stopSubSession(long paramLong)
  {
    if (paramLong == 0L) return;
    SubSession localSubSession = subSession(paramLong);
    if (localSubSession == null) return;
    sendStop(localSubSession);
    this.subsessions.remove(Long.valueOf(localSubSession.subsession_id));
  }
  



  public void startSession()
  {
    SubSession localSubSession = subSession(0L);
    if (localSubSession.start_sent) return;
    sendStart(localSubSession);
  }
  





  public void stopSession()
  {
    for (Object localObject = this.subsessions.entrySet().iterator(); ((Iterator)localObject).hasNext();) { Entry localEntry = (Entry)((Iterator)localObject).next();
      if (((SubSession)localEntry.getValue()).subsession_id != 0L)
        sendStop((SubSession)localEntry.getValue());
    }
    SubSession localObject = subSession(0L);
    sendStop((SubSession)localObject);
    this.subsessions.clear();
  }
  




  public void sendEvent()
  {
    sendEvent(0L, null);
  }
  


  public void sendEvent(AVP[] paramArrayOfAVP)
  {
    sendEvent(0L, paramArrayOfAVP);
  }
  


  public void sendEvent(long paramLong)
  {
    sendEvent(paramLong, null);
  }
  


  public void sendEvent(long paramLong, AVP[] paramArrayOfAVP)
  {
    SubSession localSubSession = subSession(0L);
    if (localSubSession == null) return;
    sendEvent(localSubSession, paramArrayOfAVP);
  }
  
  private void sendStart(SubSession paramSubSession) {
    sendACR(makeACR(paramSubSession, 2));
    if (paramSubSession.interim_interval != Long.MAX_VALUE) {
      paramSubSession.next_interim = (System.currentTimeMillis() + paramSubSession.interim_interval);
    } else
      paramSubSession.next_interim = Long.MAX_VALUE;
  }
  
  private void sendInterim(SubSession paramSubSession) { sendACR(makeACR(paramSubSession, 3));
    if (paramSubSession.interim_interval != Long.MAX_VALUE) {
      paramSubSession.next_interim = (System.currentTimeMillis() + paramSubSession.interim_interval);
    } else
      paramSubSession.next_interim = Long.MAX_VALUE;
  }
  
  private void sendStop(SubSession paramSubSession) { sendACR(makeACR(paramSubSession, 4)); }
  
  private void sendEvent(SubSession paramSubSession, AVP[] paramArrayOfAVP) {
    Message localMessage = makeACR(paramSubSession, 1);
    if (paramArrayOfAVP != null) {
      for (AVP localAVP : paramArrayOfAVP)
        localMessage.add(localAVP);
    }
    sendACR(localMessage);
  }
  
  private Message makeACR(SubSession paramSubSession, int paramInt) {
    Message localMessage = new Message();
    localMessage.hdr.setRequest(true);
    localMessage.hdr.setProxiable(true);
    localMessage.hdr.application_id = this.base_session.authAppId();
    localMessage.hdr.command_code = 271;
    collectACRInfo(localMessage, paramSubSession, paramInt);
    Utils.setMandatory_RFC3588(localMessage);
    return localMessage;
  }
  




  public void handleACA(Message paramMessage)
  {
    if (paramMessage == null)
      return;
    int i;
    try {
      Iterator localIterator1 = paramMessage.iterator(485);
      if (!localIterator1.hasNext())
      {
        return;
      }
      
      i = new AVP_Unsigned32((AVP)localIterator1.next()).queryValue();
      for (Entry localEntry : this.subsessions.entrySet()) {
        if (((SubSession)localEntry.getValue()).most_recent_record_number == i)
        {
          ((SubSession)localEntry.getValue()).most_recent_record_number = -1;
          return;
        }
      }
    }
    catch (InvalidAVPLengthException localInvalidAVPLengthException) {}
  }
  
  private void sendACR(Message paramMessage)
  {
    try {
      this.base_session.sessionManager().sendRequest(paramMessage, this.base_session, null);
    }
    catch (NotARequestException localNotARequestException) {}catch (NotRoutableException localNotRoutableException)
    {
      this.base_session.sessionManager().logger.log(Level.INFO, "Could not send ACR for session " + this.base_session.sessionId() + " :" + localNotRoutableException.toString());
      
      handleACA(null);
    }
  }
  
















  public void collectACRInfo(Message paramMessage, SubSession paramSubSession, int paramInt)
  {
    this.base_session.addCommonStuff(paramMessage);
    
    paramMessage.add(new AVP_Unsigned32(480, paramInt));
    
    this.accounting_record_number += 1;
    paramMessage.add(new AVP_Unsigned32(485, this.accounting_record_number));
    paramSubSession.most_recent_record_number = this.accounting_record_number;
    
    if (this.acct_application_id != null) {
      paramMessage.add(new AVP_Unsigned32(259, this.acct_application_id.intValue()));
    }
    if (paramSubSession.subsession_id != 0L) {
      paramMessage.add(new AVP_Unsigned64(287, paramSubSession.subsession_id));
    }
    if (this.acct_multi_session_id != null) {
      paramMessage.add(new AVP_UTF8String(50, this.acct_multi_session_id));
    }
    if (paramSubSession.interim_interval != Long.MAX_VALUE) {
      paramMessage.add(new AVP_Unsigned32(85, (int)(paramSubSession.interim_interval / 1000L)));
    }
    paramMessage.add(new AVP_Time(55, (int)(System.currentTimeMillis() / 1000L)));
    

    if (paramInt != 2) {
      if (paramSubSession.acct_session_time != null)
        paramMessage.add(new AVP_Unsigned32(46, (int)(paramSubSession.acct_session_time.longValue() / 1000L)));
      if (paramSubSession.acct_input_octets != null)
        paramMessage.add(new AVP_Unsigned64(363, paramSubSession.acct_input_octets.longValue()));
      if (paramSubSession.acct_output_octets != null)
        paramMessage.add(new AVP_Unsigned64(364, paramSubSession.acct_output_octets.longValue()));
      if (paramSubSession.acct_input_packets != null)
        paramMessage.add(new AVP_Unsigned64(365, paramSubSession.acct_input_packets.longValue()));
      if (paramSubSession.acct_output_packets != null) {
        paramMessage.add(new AVP_Unsigned64(366, paramSubSession.acct_output_packets.longValue()));
      }
    }
  }
}

