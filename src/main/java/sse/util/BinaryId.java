package sse.util;

import java.io.Serializable;
import java.util.Arrays;

public abstract class BinaryId implements Serializable {
        
    private final byte[] value;

    protected BinaryId(byte[] value) {
        if (value == null) throw new IllegalArgumentException("value cannot be null");
        this.value = value.clone();
    }

    public final byte[] value() {
        return value.clone();
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BinaryId other = (BinaryId) o;
        return Arrays.equals(value, other.value);
    }

    @Override
    public final int hashCode() {
        return 31 * getClass().hashCode() + Arrays.hashCode(value);
    }
}
