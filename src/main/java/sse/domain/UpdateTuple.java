package sse.domain;

import java.io.Serializable;
import java.util.Objects;

public final class UpdateTuple implements Serializable {
    private final String docId;
    private final UpdateOp op;

    public UpdateTuple(String docId, UpdateOp op) {
        if (docId == null || op == null) {
            throw new IllegalArgumentException("docId or op cannot be null");
        }
        this.docId = docId;
        this.op = op;
    }

    public String docId() {
        return docId;
    }

    public UpdateOp op() {
        return op;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        UpdateTuple that = (UpdateTuple) o;
        return Objects.equals(docId, that.docId) && op == that.op;
    }

    @Override
    public int hashCode() {
        return Objects.hash(docId, op);
    }

    @Override
    public String toString() {
        return "UpdateTuple[" +
                "docId=" + docId +
                ", op=" + op +
                ']';
    }
}
