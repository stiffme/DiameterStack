package com.esipeng.diameter.node;





public class NodeSettings
{
  private String host_id;
  



  private String realm;
  



  private int vendor_id;
  


  private Capability capabilities;
  


  private int port;
  


  private String product_name;
  


  private int firmware_revision;
  


  private long watchdog_interval;
  


  private long idle_close_timeout;
  


  private Boolean use_tcp;
  


  private Boolean use_sctp;
  


  private PortRange port_range;
  



  public static class PortRange
  {
    public int min;
    


    public int max;
    



    public PortRange(int paramInt1, int paramInt2)
      throws InvalidSettingException
    {
      if ((paramInt1 <= 0) || (paramInt1 > paramInt2) || (paramInt2 >= 65536))
        throw new InvalidSettingException("Invalid port range, 0 < min <= max < 65536");
      this.min = paramInt1;
      this.max = paramInt2;
    }
  }
  















  public NodeSettings(String paramString1, String paramString2, int paramInt1, Capability paramCapability, int paramInt2, String paramString3, int paramInt3)
    throws InvalidSettingException
  {
    if (paramString1 == null)
      throw new InvalidSettingException("null host_id");
    int i = paramString1.indexOf('.');
    if (i == -1)
      throw new InvalidSettingException("host_id must contains at least 2 dots");
    if (paramString1.indexOf('.', i + 1) == -1)
      throw new InvalidSettingException("host_id must contains at least 2 dots");
    this.host_id = paramString1;
    
    i = paramString2.indexOf('.');
    if (i == -1)
      throw new InvalidSettingException("realm must contain at least 1 dot");
    this.realm = paramString2;
    
    if (paramInt1 == 0)
      throw new InvalidSettingException("vendor_id must not be non-zero. (It must be your IANA-assigned \"SMI Network Management Private Enterprise Code\". See http://www.iana.org/assignments/enterprise-numbers)");
    this.vendor_id = paramInt1;
    
    if (paramCapability.isEmpty())
      throw new InvalidSettingException("Capabilities must be non-empty");
    this.capabilities = paramCapability;
    if ((paramInt2 < 0) || (paramInt2 > 65535))
      throw new InvalidSettingException("listen-port must be 0..65535");
    this.port = paramInt2;
    
    if (paramString3 == null)
      throw new InvalidSettingException("product-name cannot be null");
    this.product_name = paramString3;
    this.firmware_revision = paramInt3;
    this.watchdog_interval = 30000L;
    this.idle_close_timeout = 604800000L;
  }
  
  public String hostId()
  {
    return this.host_id;
  }
  
  public String realm()
  {
    return this.realm;
  }
  
  public int vendorId()
  {
    return this.vendor_id;
  }
  
  public Capability capabilities()
  {
    return this.capabilities;
  }
  
  public int port()
  {
    return this.port;
  }
  
  public String productName()
  {
    return this.product_name;
  }
  
  public int firmwareRevision()
  {
    return this.firmware_revision;
  }
  



  public long watchdogInterval()
  {
    return this.watchdog_interval;
  }
  




  public void setWatchdogInterval(long paramLong)
    throws InvalidSettingException
  {
    if (paramLong < 6000L)
      throw new InvalidSettingException("watchdog interval must be at least 6 seconds. RFC3539 section 3.4.1 item 1");
    this.watchdog_interval = paramLong;
  }
  


  public long idleTimeout()
  {
    return this.idle_close_timeout;
  }
  





  public void setIdleTimeout(long paramLong)
    throws InvalidSettingException
  {
    if (paramLong < 0L)
      throw new InvalidSettingException("idle timeout cannot be negative");
    this.idle_close_timeout = paramLong;
  }
  



  public Boolean useTCP()
  {
    return this.use_tcp;
  }
  









  public void setUseTCP(Boolean paramBoolean)
  {
    this.use_tcp = paramBoolean;
  }
  



  public Boolean useSCTP()
  {
    return this.use_sctp;
  }
  









  public void setUseSCTP(Boolean paramBoolean)
  {
    this.use_sctp = paramBoolean;
  }
  







  public void TCPPortRange(PortRange paramPortRange)
    throws InvalidSettingException
  {
    this.port_range = paramPortRange;
  }
  


  public void TCPPortRange(int paramInt1, int paramInt2)
    throws InvalidSettingException
  {
    this.port_range = new PortRange(paramInt1, paramInt2);
  }
  



  public PortRange TCPPortRange()
  {
    return this.port_range;
  }
}

