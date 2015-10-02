package com.esipeng.diameter.node;


public class NodeSettings {
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


    public static class PortRange {
        public int min;


        public int max;


        public PortRange(int lowPort, int highPort)
                throws InvalidSettingException {
            if ((lowPort <= 0) || (lowPort > highPort) || (highPort >= 65536))
                throw new InvalidSettingException("Invalid port range, 0 < min <= max < 65536");
            this.min = lowPort;
            this.max = highPort;
        }
    }


    public NodeSettings(String hostId, String realm, int vendorId, Capability capability, int listenPort, String prodName, int firmwareVersion)
            throws InvalidSettingException {
        if (hostId == null)
            throw new InvalidSettingException("null host_id");
        int i = hostId.indexOf('.');
        if (i == -1)
            throw new InvalidSettingException("host_id must contains at least 2 dots");
        if (hostId.indexOf('.', i + 1) == -1)
            throw new InvalidSettingException("host_id must contains at least 2 dots");
        this.host_id = hostId;

        i = realm.indexOf('.');
        if (i == -1)
            throw new InvalidSettingException("realm must contain at least 1 dot");
        this.realm = realm;

        if (vendorId == 0)
            throw new InvalidSettingException("vendor_id must not be non-zero. (It must be your IANA-assigned \"SMI Network Management Private Enterprise Code\". See http://www.iana.org/assignments/enterprise-numbers)");
        this.vendor_id = vendorId;

        if (capability.isEmpty())
            throw new InvalidSettingException("Capabilities must be non-empty");
        this.capabilities = capability;
        if ((listenPort < 0) || (listenPort > 65535))
            throw new InvalidSettingException("listen-port must be 0..65535");
        this.port = listenPort;

        if (prodName == null)
            throw new InvalidSettingException("product-name cannot be null");
        this.product_name = prodName;
        this.firmware_revision = firmwareVersion;
        this.watchdog_interval = 30000L;
        this.idle_close_timeout = 604800000L;
    }

    public String hostId() {
        return this.host_id;
    }

    public String realm() {
        return this.realm;
    }

    public int vendorId() {
        return this.vendor_id;
    }

    public Capability capabilities() {
        return this.capabilities;
    }

    public int port() {
        return this.port;
    }

    public String productName() {
        return this.product_name;
    }

    public int firmwareRevision() {
        return this.firmware_revision;
    }


    public long watchdogInterval() {
        return this.watchdog_interval;
    }


    public void setWatchdogInterval(long interval)
            throws InvalidSettingException {
        if (interval < 6000L)
            throw new InvalidSettingException("watchdog interval must be at least 6 seconds. RFC3539 section 3.4.1 item 1");
        this.watchdog_interval = interval;
    }


    public long idleTimeout() {
        return this.idle_close_timeout;
    }


    public void setIdleTimeout(long timeout)
            throws InvalidSettingException {
        if (timeout < 0L)
            throw new InvalidSettingException("idle timeout cannot be negative");
        this.idle_close_timeout = timeout;
    }


    public Boolean useTCP() {
        return this.use_tcp;
    }


    public void setUseTCP(Boolean paramBoolean) {
        this.use_tcp = paramBoolean;
    }


    public Boolean useSCTP() {
        return this.use_sctp;
    }


    public void setUseSCTP(Boolean userSCTP) {
        this.use_sctp = userSCTP;
    }


    public void TCPPortRange(PortRange range)
            throws InvalidSettingException {
        this.port_range = range;
    }


    public void TCPPortRange(int low, int high)
            throws InvalidSettingException {
        this.port_range = new PortRange(low, high);
    }


    public PortRange TCPPortRange() {
        return this.port_range;
    }
}

