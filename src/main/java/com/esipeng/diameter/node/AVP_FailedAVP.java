package com.esipeng.diameter.node;

import com.esipeng.diameter.AVP;

class AVP_FailedAVP extends com.esipeng.diameter.AVP_Grouped {
  private static AVP[] wrap(AVP paramAVP) {
    AVP[] arrayOfAVP = new AVP[1];
    arrayOfAVP[0] = paramAVP;
    return arrayOfAVP;
  }
  
  public AVP_FailedAVP(AVP paramAVP) { super(279, wrap(paramAVP)); }
}
