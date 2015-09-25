package com.esipeng.diameter.node;










public class DefaultNodeValidator
  implements NodeValidator
{
  public AuthenticationResult authenticateNode(String paramString, Object paramObject)
  {
    AuthenticationResult localAuthenticationResult = new AuthenticationResult();
    localAuthenticationResult.known = true;
    return localAuthenticationResult;
  }
  



  public Capability authorizeNode(String paramString, NodeSettings paramNodeSettings, Capability paramCapability)
  {
    Capability localCapability = Capability.calculateIntersection(paramNodeSettings.capabilities(), paramCapability);
    return localCapability;
  }
}

