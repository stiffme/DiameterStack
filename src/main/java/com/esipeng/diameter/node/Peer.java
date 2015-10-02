package com.esipeng.diameter.node;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.StringTokenizer;

public class Peer {
    private String host;
    private String realAddress;
    private int port;
    private boolean secure;
    TransportProtocol transport_protocol;
    public Capability capabilities;

    public static enum TransportProtocol {
        tcp,
        sctp;


        private TransportProtocol() {
        }
    }


    @Deprecated
    public Peer(InetAddress inetAddress) {
        this(inetAddress, TransportProtocol.tcp);
    }


    @Deprecated
    public Peer(InetAddress inetAddress, TransportProtocol transport) {
        this(inetAddress, 3868, transport);
    }


    @Deprecated
    public Peer(InetAddress inetAddress, int port) {
        this(inetAddress, port, TransportProtocol.tcp);
    }


    @Deprecated
    public Peer(InetAddress inetAddress, int port, TransportProtocol transport) {
        this.host = inetAddress.getHostAddress();
        this.port = port;
        this.secure = false;
        this.transport_protocol = transport;
    }


    public Peer(String hostId)
            throws EmptyHostNameException {
        this(hostId, 3868);
    }

    public String getRealAddress() {
        return realAddress;
    }

    public void setRealAddress(String realAddress) {
        this.realAddress = realAddress;
    }


    public Peer(String hostId, int port)
            throws EmptyHostNameException {
        this(hostId, port, TransportProtocol.tcp);
    }


    public Peer(String hostId, int port, TransportProtocol transport)
            throws EmptyHostNameException {
        if (hostId.length() == 0)
            throw new EmptyHostNameException();
        this.host = hostId;
        this.port = port;
        this.secure = false;
        this.transport_protocol = transport;
    }


    @Deprecated
    public Peer(java.net.InetSocketAddress inetAddress) {
        this.host = inetAddress.getAddress().getHostAddress();
        this.port = inetAddress.getPort();
        this.secure = false;
        this.transport_protocol = TransportProtocol.tcp;
    }


    public Peer(URI uri)
            throws UnsupportedURIException {
        if ((uri.getScheme() != null) && (!uri.getScheme().equals("aaa")) && (!uri.getScheme().equals("aaas")))
            throw new UnsupportedURIException("Only aaa: schemes are supported");
        if (uri.getUserInfo() != null)
            throw new UnsupportedURIException("userinfo not supported in Diameter URIs");
        if ((uri.getPath() != null) && (!uri.getPath().equals("")))
            throw new UnsupportedURIException("path not supported in Diameter URIs");
        this.host = uri.getHost();
        this.port = uri.getPort();
        if (this.port == -1) this.port = 3868;
        this.secure = uri.getScheme().equals("aaas");
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


    public URI uri() {
        try {
            return new URI(this.secure ? "aaas" : "aaa", null, this.host, this.port, null, null, null);
        } catch (URISyntaxException localURISyntaxException) {
        }
        return null;
    }


    public static Peer fromURIString(String uriString)
            throws UnsupportedURIException {
        int i = uriString.indexOf(';');
        String str1 = null;
        if (i != -1) {
            str1 = uriString.substring(i + 1);
            uriString = uriString.substring(0, i);
        }
        URI uri;
        try {
            uri = new URI(uriString);
        } catch (URISyntaxException localURISyntaxException) {
            throw new UnsupportedURIException(localURISyntaxException);
        }
        Peer peer = new Peer(uri);
        if (str1 != null) {
            StringTokenizer localStringTokenizer1 = new StringTokenizer(str1, ";");
            while (localStringTokenizer1.hasMoreTokens()) {
                String str2 = localStringTokenizer1.nextToken();
                StringTokenizer localStringTokenizer2 = new StringTokenizer(str2, "=");
                if (localStringTokenizer2.hasMoreTokens()) {
                    String str3 = localStringTokenizer2.nextToken();
                    if ((str3.equals("transport")) &&

                            (localStringTokenizer2.hasMoreTokens())) {
                        String str4 = localStringTokenizer2.nextToken();
                        if (str4.equals("sctp")) {
                            peer.transport_protocol = TransportProtocol.sctp;
                        } else if (str4.equals("tcp")) {
                            peer.transport_protocol = TransportProtocol.tcp;
                        } else
                            throw new UnsupportedURIException("Unknown transport-protocol: " + str4);
                    }
                }
            }
        }
        return peer;
    }

    public String host() {
        return this.host;
    }

    public void host(String paramString) {
        this.host = paramString;
    }

    public int port() {
        return this.port;
    }

    public void port(int paramInt) {
        this.port = paramInt;
    }

    public boolean secure() {
        return this.secure;
    }

    public void secure(boolean paramBoolean) {
        this.secure = paramBoolean;
    }


    public TransportProtocol transportProtocol() {
        return this.transport_protocol;
    }


    public void transportProtocol(TransportProtocol transport) {
        this.transport_protocol = transport;
    }

    public String toString() {
        return (this.secure ? "aaas" : "aaa") + "://" + this.host + ":" + Integer.valueOf(this.port).toString() + (this.transport_protocol == TransportProtocol.tcp ? "" : ";transport=sctp");
    }


    public int hashCode() {
        return this.port + this.host.hashCode();
    }

    public boolean equals(Object otherObj) {
        if (this == otherObj)
            return true;
        if ((otherObj == null) || (otherObj.getClass() != getClass()))
            return false;
        Peer peer = (Peer) otherObj;
        return (this.port == peer.port) && (this.host.equals(peer.host));
    }
}
