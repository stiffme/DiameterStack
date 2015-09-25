package com.esipeng.diameter.node;

import java.io.IOException;
import java.io.PrintStream;

import com.esipeng.diameter.Message;




public class SimpleSyncClient
  extends NodeManager
{
  private Peer[] peers;
  
  public SimpleSyncClient(NodeSettings paramNodeSettings, Peer[] paramArrayOfPeer)
  {
    super(paramNodeSettings);
    this.peers = paramArrayOfPeer;
  }
  





  public void start()
    throws IOException, UnsupportedTransportProtocolException
  {
    super.start();
    for (Peer localPeer : this.peers) {
      node().initiateConnection(localPeer, true);
    }
  }
  







  protected void handleAnswer(Message paramMessage, ConnectionKey paramConnectionKey, Object paramObject)
  {
    SyncCall localSyncCall = (SyncCall)paramObject;
    synchronized (localSyncCall) {
      localSyncCall.answer = paramMessage;
      localSyncCall.answer_ready = true;
      localSyncCall.notify();
    }
  }
  




  public Message sendRequest(Message paramMessage)
  {
    return sendRequest(paramMessage, -1L);
  }
  






  public Message sendRequest(Message paramMessage, long paramLong)
  {
    SyncCall localSyncCall = new SyncCall();
    localSyncCall.answer_ready = false;
    localSyncCall.answer = null;
    
    long l1 = System.currentTimeMillis() + paramLong;
    try
    {
      sendRequest(paramMessage, this.peers, localSyncCall, paramLong);
      
      synchronized (localSyncCall) {
        if (paramLong >= 0L) {
          long l2 = System.currentTimeMillis();
          long l3 = l1 - l2;
          if (l3 > 0L)
            while ((System.currentTimeMillis() < l1) && (!localSyncCall.answer_ready))
              localSyncCall.wait(l3);
        } else {
          while (!localSyncCall.answer_ready)
            localSyncCall.wait();
        }
      }
    } catch (NotRoutableException localNotRoutableException) { System.out.println("SimpleSyncClient.sendRequest(): not routable");
    }
    catch (InterruptedException localInterruptedException) {}catch (NotARequestException localNotARequestException) {}
    

    return localSyncCall.answer;
  }
  
  private static class SyncCall
  {
    boolean answer_ready;
    Message answer;
  }
}

