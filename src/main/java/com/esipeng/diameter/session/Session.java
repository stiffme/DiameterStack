package com.esipeng.diameter.session;

import com.esipeng.diameter.Message;

public abstract interface Session
{
  public abstract String sessionId();
  
  public abstract int handleRequest(Message paramMessage);
  
  public abstract void handleAnswer(Message paramMessage, Object paramObject);
  
  public abstract void handleNonAnswer(int paramInt, Object paramObject);
  
  public abstract long calcNextTimeout();
  
  public abstract void handleTimeout();
}

