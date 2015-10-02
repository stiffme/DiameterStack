package com.esipeng.diameter.node;

public class DefaultNodeValidator
        implements NodeValidator {
    public AuthenticationResult authenticateNode(String hostId, Object authObject) {
        AuthenticationResult localAuthenticationResult = new AuthenticationResult();
        localAuthenticationResult.known = true;
        return localAuthenticationResult;
    }


    public Capability authorizeNode(String hostId, NodeSettings settings, Capability capability) {
        Capability localCapability = Capability.calculateIntersection(settings.capabilities(), capability);
        return localCapability;
    }
}

