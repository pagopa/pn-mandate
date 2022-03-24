package it.pagopa.pn.mandate.mapper;

import java.util.NoSuchElementException;

public enum DelegationState  {
    pending(10, "pending"),
    active(20, "active"),
    revoked(30, "revoked"),
    rejected(40, "rejected"),
    expired(50, "expired");
    
    private final int value;
    private final String valuestr;

    DelegationState(final int newValue, final String newValueStr) {
        value = newValue;
        valuestr = newValueStr;
    }

    
    public int getValue() {
        return value;
    }

    public String getValueConst(){
        return valuestr;
    }

    // boilerplate
    public static DelegationState fromValue(int val) {
        for (DelegationState v : DelegationState.class.getEnumConstants()) {
            if (v.getValue() == val)
                return v;
        }        
       throw new NoSuchElementException();
    }

    public static DelegationState fromValueConst(String val) {
        for (DelegationState v : DelegationState.class.getEnumConstants()) {
            if (v.getValueConst().equals(val))
                return v;
        }        
       throw new NoSuchElementException();
    }
}