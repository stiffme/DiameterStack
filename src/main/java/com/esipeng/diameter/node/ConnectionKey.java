package com.esipeng.diameter.node;








public class ConnectionKey
{
  private static int i_seq = 0;
  
  private static final synchronized int nextI() { return i_seq++; }
  
  private int i;
  public ConnectionKey() {
    this.i = nextI(); }
  
  public int hashCode() { return this.i; }
  
  public boolean equals(Object paramObject) { if (this == paramObject)
      return true;
    if ((paramObject == null) || (paramObject.getClass() != getClass()))
      return false;
    return ((ConnectionKey)paramObject).i == this.i;
  }
}

