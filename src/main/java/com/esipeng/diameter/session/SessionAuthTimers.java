package com.esipeng.diameter.session;







public class SessionAuthTimers
{
  private long latest_auth_time;
  




  private long next_reauth_time;
  




  private long auth_timeout;
  





  public void updateTimers(long paramLong1, long paramLong2, long paramLong3)
  {
    this.latest_auth_time = paramLong1;
    if (paramLong2 != 0L) {
      this.auth_timeout = (this.latest_auth_time + paramLong2 + paramLong3);
      if (paramLong3 != 0L) {
        this.next_reauth_time = (this.latest_auth_time + paramLong2);
      }
      else {
        this.next_reauth_time = Math.max(paramLong1 + paramLong2 / 2L, this.auth_timeout - 10L);
      }
    } else {
      this.next_reauth_time = Long.MAX_VALUE;
      this.auth_timeout = Long.MAX_VALUE;
    }
  }
  




  public long getNextReauthTime()
  {
    return this.next_reauth_time;
  }
  



  public long getMaxTimeout()
  {
    return this.auth_timeout;
  }
}

