package com.esipeng.diameter.node;

public class ConnectionKey {
    private static int i_seq = 0;

    private static final synchronized int nextI() {
        return i_seq++;
    }

    private int i;

    public ConnectionKey() {
        this.i = nextI();
    }

    public int hashCode() {
        return this.i;
    }

    public boolean equals(Object other) {
        if (this == other)
            return true;
        if ((other == null) || (other.getClass() != getClass()))
            return false;
        return ((ConnectionKey) other).i == this.i;
    }
}

