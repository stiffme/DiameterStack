package com.esipeng.diameter.node;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Random;







class ConnectionTimers
{
  long last_activity;
  long last_real_activity;
  long last_in_dw;
  boolean dw_outstanding;
  long cfg_watchdog_timer;
  long watchdog_timer_with_jitter;
  long cfg_idle_close_timeout;
  private static Random random = null;
  
  private static synchronized long generateJitter() { if (random == null) {
      String localObject = System.getProperty("com.esipeng.diameter.node.jitter_prng");
      if (localObject == null)
        localObject = "SHA1PRNG";
      if (!((String)localObject).equals("bogus")) {
        try {
          random = SecureRandom.getInstance("SHA1PRNG");
        }
        catch (NoSuchAlgorithmException localNoSuchAlgorithmException) {}
      }
      if (random == null) {
        random = new Random();
      }
    }
    


    byte[] localObject2 = new byte[2];
    random.nextBytes((byte[])localObject2);
    
    int i = localObject2[0] * 256 + localObject2[1];
    if (i < 0) i += 65536;
    i %= 4001;
    i -= 2000;
    return i;
  }
  
  public ConnectionTimers(long paramLong1, long paramLong2)
  {
    this.last_activity = System.currentTimeMillis();
    this.last_real_activity = System.currentTimeMillis();
    this.last_in_dw = System.currentTimeMillis();
    this.dw_outstanding = false;
    this.cfg_watchdog_timer = paramLong1;
    this.watchdog_timer_with_jitter = (this.cfg_watchdog_timer + generateJitter());
    this.cfg_idle_close_timeout = paramLong2;
  }
  

  public void markDWR() { this.last_in_dw = System.currentTimeMillis(); }
  
  public void markDWA() {
    this.last_in_dw = System.currentTimeMillis();
    this.dw_outstanding = false;
  }
  
  public void markActivity() { this.last_activity = System.currentTimeMillis(); }
  
  public void markCER() {
    this.last_activity = System.currentTimeMillis();
  }
  
  public void markRealActivity() { this.last_real_activity = this.last_activity; }
  
  public void markDWR_out() {
    this.dw_outstanding = true;
    this.last_activity = System.currentTimeMillis();
    this.watchdog_timer_with_jitter = (this.cfg_watchdog_timer + generateJitter());
  }
  
  public static enum timer_action {
    none, 
    disconnect_no_cer, 
    disconnect_idle, 
    disconnect_no_dw, 
    dwr;
    
    private timer_action() {} }
  
  public long calcNextTimeout(boolean paramBoolean) { if (!paramBoolean)
    {
      return this.last_activity + this.watchdog_timer_with_jitter;
    }
    
    long l1;
    
    if (!this.dw_outstanding) {
      l1 = this.last_activity + this.watchdog_timer_with_jitter;
    } else {
      l1 = this.last_activity + this.watchdog_timer_with_jitter + this.cfg_watchdog_timer;
    }
    if (this.cfg_idle_close_timeout != 0L)
    {
      long l2 = this.last_real_activity + this.cfg_idle_close_timeout;
      if (l2 < l1)
        return l2;
    }
    return l1;
  }
  
  public timer_action calcAction(boolean paramBoolean) {
    long l = System.currentTimeMillis();
    
    if (!paramBoolean) {
      if (l >= this.last_activity + this.watchdog_timer_with_jitter)
      {
        return timer_action.disconnect_no_cer;
      }
      return timer_action.none;
    }
    
    if ((this.cfg_idle_close_timeout != 0L) && 
      (l >= this.last_real_activity + this.cfg_idle_close_timeout))
    {
      return timer_action.disconnect_idle;
    }
    


    if (l >= this.last_activity + this.watchdog_timer_with_jitter) {
      if (!this.dw_outstanding) {
        return timer_action.dwr;
      }
      if (l >= this.last_activity + this.cfg_watchdog_timer + this.cfg_watchdog_timer)
      {
        return timer_action.disconnect_no_dw;
      }
    }
    
    return timer_action.none;
  }
}

