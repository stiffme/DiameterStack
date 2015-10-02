package com.esipeng.diameter.node;

import com.esipeng.diameter.AVP;

class InvalidAVPValueException extends Exception {
    public AVP avp;

    public InvalidAVPValueException(AVP failedAVP) {
        this.avp = failedAVP;
    }
}

