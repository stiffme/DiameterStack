package com.esipeng.diameter.node;

import com.esipeng.diameter.AVP;

import static com.esipeng.diameter.ProtocolConstants.*;

class AVP_FailedAVP extends com.esipeng.diameter.AVP_Grouped {
    private static AVP[] wrap(AVP paramAVP) {
        AVP[] arrayOfAVP = new AVP[1];
        arrayOfAVP[0] = paramAVP;
        return arrayOfAVP;
    }

    public AVP_FailedAVP(AVP paramAVP) {
        super(DI_FAILED_AVP, wrap(paramAVP));
    }
}
