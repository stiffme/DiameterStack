package com.esipeng.diameter.node;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.StringTokenizer;

public class Peer
{
  private String host;
			private String realAddress;
  private int port;
  private boolean secure;
  TransportProtocol transport_protocol;
  public Capability capabilities;
  
  public static enum TransportProtocol
  {
    tcp, 
    sctp;
    

    private TransportProtocol() {}
  }
  

  @Deprecated
  public Peer(InetAddress paramInetAddress)
  {
    this(paramInetAddress, TransportProtocol.tcp);
  }
  




  @Deprecated
  public Peer(InetAddress paramInetAddress, TransportProtocol paramTransportProtocol)
  {
    this(paramInetAddress, 3868, paramTransportProtocol);
  }
  



  @Deprecated
  public Peer(InetAddress paramInetAddress, int paramInt)
  {
    this(paramInetAddress, paramInt, TransportProtocol.tcp);
  }
  





  @Deprecated
  public Peer(InetAddress paramInetAddress, int paramInt, TransportProtocol paramTransportProtocol)
  {
    this.host = paramInetAddress.getHostAddress();
    this.port = paramInt;
    this.secure = false;
    this.transport_protocol = paramTransportProtocol;
  }
  




  public Peer(String paramString)
    throws EmptyHostNameException
  {
    this(paramString, 3868);
  }
public String getRealAddress() {
	return realAddress;
}
public void setRealAddress(String realAddress) {
	this.realAddress = realAddress;
}
  





  public Peer(String paramString, int paramInt)
    throws EmptyHostNameException
  {
    this(paramString, paramInt, TransportProtocol.tcp);
  }
  







  public Peer(String paramString, int paramInt, TransportProtocol paramTransportProtocol)
    throws EmptyHostNameException
  {
    if (paramString.length() == 0)
      throw new EmptyHostNameException();
    this.host = paramString;
    this.port = paramInt;
    this.secure = false;
    this.transport_protocol = paramTransportProtocol;
  }
  


  @Deprecated
  public Peer(java.net.InetSocketAddress paramInetSocketAddress)
  {
    this.host = paramInetSocketAddress.getAddress().getHostAddress();
    this.port = paramInetSocketAddress.getPort();
    this.secure = false;
    this.transport_protocol = TransportProtocol.tcp;
  }
  





  public Peer(URI paramURI)
    throws UnsupportedURIException
  {
    if ((paramURI.getScheme() != null) && (!paramURI.getScheme().equals("aaa")) && (!paramURI.getScheme().equals("aaas")))
      throw new UnsupportedURIException("Only aaa: schemes are supported");
    if (paramURI.getUserInfo() != null)
      throw new UnsupportedURIException("userinfo not supported in Diameter URIs");
    if ((paramURI.getPath() != null) && (!paramURI.getPath().equals("")))
      throw new UnsupportedURIException("path not supported in Diameter URIs");
    this.host = paramURI.getHost();
    this.port = paramURI.getPort();
    if (this.port == -1) this.port = 3868;
    this.secure = paramURI.getScheme().equals("aaas");
    this.transport_protocol = TransportProtocol.tcp;
  }
  
  public Peer(Peer paramPeer) {
    this.host = paramPeer.host;
    this.port = paramPeer.port;
    this.secure = paramPeer.secure;
    if (paramPeer.capabilities != null)
      this.capabilities = new Capability(paramPeer.capabilities);
    this.transport_protocol = paramPeer.transport_protocol;
  }
  





  public URI uri()
  {
    try
    {
      return new URI(this.secure ? "aaas" : "aaa", null, this.host, this.port, null, null, null);
    } catch (URISyntaxException localURISyntaxException) {}
    return null;
  }
  




  public static Peer fromURIString(String paramString)
    throws UnsupportedURIException
  {
    int i = paramString.indexOf(';');
    String str1 = null;
    if (i != -1) {
      str1 = paramString.substring(i + 1);
      paramString = paramString.substring(0, i);
    }
    URI localURI;
    try {
      localURI = new URI(paramString);
    } catch (URISyntaxException localURISyntaxException) {
      throw new UnsupportedURIException(localURISyntaxException);
    }
    Peer localPeer = new Peer(localURI);
    if (str1 != null) {
      StringTokenizer localStringTokenizer1 = new StringTokenizer(str1, ";");
      while (localStringTokenizer1.hasMoreTokens()) {
        String str2 = localStringTokenizer1.nextToken();
        StringTokenizer localStringTokenizer2 = new StringTokenizer(str2, "=");
        if (localStringTokenizer2.hasMoreTokens())
        {
          String str3 = localStringTokenizer2.nextToken();
          if ((str3.equals("transport")) && 
          
            (localStringTokenizer2.hasMoreTokens()))
          {
            String str4 = localStringTokenizer2.nextToken();
            if (str4.equals("sctp")) {
              localPeer.transport_protocol = TransportProtocol.sctp;
            } else if (str4.equals("tcp")) {
              localPeer.transport_protocol = TransportProtocol.tcp;
            } else
              throw new UnsupportedURIException("Unknown transport-protocol: " + str4);
          }
        } } }
    return localPeer;
  }
  
  public String host() {
    return this.host;
  }
  
  public void host(String paramString) { this.host = paramString; }
  
  public int port() {
    return this.port;
  }
  
  public void port(int paramInt) { this.port = paramInt; }
  
  public boolean secure() {
    return this.secure;
  }
  
  public void secure(boolean paramBoolean) { this.secure = paramBoolean; }
  


  public TransportProtocol transportProtocol()
  {
    return this.transport_protocol;
  }
  

  public void transportProtocol(TransportProtocol paramTransportProtocol)
  {
    this.transport_protocol = paramTransportProtocol;
  }
  
  public String toString() {
    return (this.secure ? "aaas" : "aaa") + "://" + this.host + ":" + Integer.valueOf(this.port).toString() + (this.transport_protocol == TransportProtocol.tcp ? "" : ";transport=sctp");
  }
  





  public int hashCode()
  {
    return this.port + this.host.hashCode();
  }
  
  public boolean equals(Object paramObject) {
    if (this == paramObject)
      return true;
    if ((paramObject == null) || (paramObject.getClass() != getClass()))
      return false;
    Peer localPeer = (Peer)paramObject;
    return (this.port == localPeer.port) && (this.host.equals(localPeer.host));
  }
}
