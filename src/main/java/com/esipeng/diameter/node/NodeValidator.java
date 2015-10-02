package com.esipeng.diameter.node;

public abstract interface NodeValidator {
    public abstract AuthenticationResult authenticateNode(String paramString, Object paramObject);

    public abstract Capability authorizeNode(String paramString, NodeSettings paramNodeSettings, Capability paramCapability);

    public static class AuthenticationResult {
        public boolean known;
        public String error_message;
        public Integer result_code;
    }
}

