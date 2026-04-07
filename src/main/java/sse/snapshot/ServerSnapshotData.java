package sse.snapshot;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import sse.domain.IndexAddress;
import vss.secretsharing.VerifiableShare;

public final class ServerSnapshotData implements Serializable {
    private final SsePlainSnapshotData sseSnapshotData;
    private final int activeClientId;

    public ServerSnapshotData(SsePlainSnapshotData sseSnapshotData, int activeClientId) {
        this.sseSnapshotData = sseSnapshotData;
        this.activeClientId = activeClientId;
    }

    public SsePlainSnapshotData sseSnapshotData() {
        return sseSnapshotData;
    }

    public int activeClientId() {
        return activeClientId;
    }

    public byte[] serialize() {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutput out = new ObjectOutputStream(bos)) {
            out.writeObject(this);
            out.flush();
            bos.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Error serializing snapshot", e);
        }
    }

    public static ServerSnapshotData deserialize(byte[] plainData) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(plainData);
             ObjectInput in = new ObjectInputStream(bis)) {
            return (ServerSnapshotData) in.readObject();
        } catch (IOException e) {
            throw new RuntimeException("Error deserializing snapshot", e);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Error deserializing snapshot", e);
        }
    }

    public VerifiableShare tokenGenKeyShare(VerifiableShare[] shares) {
        if (!sseSnapshotData.hasTokenGenKeyShare()) {
            return null;
        }
        if (shares == null || shares.length == 0) {
            throw new IllegalStateException("Snapshot is missing token generation key share");
        }
        return shares[0];
    }

    public VerifiableShare updateCounterKeyShare(VerifiableShare[] shares) {
        if (!sseSnapshotData.hasUpdateCounterKeyShare()) {
            return null;
        }
        int index = sseSnapshotData.hasTokenGenKeyShare() ? 1 : 0;
        if (shares == null || index >= shares.length) {
            throw new IllegalStateException("Snapshot is missing update counter key share");
        }
        return shares[index];
    }

    public Map<IndexAddress, VerifiableShare> updateTupleShares(VerifiableShare[] shares) {
        int index = 0;
        if (sseSnapshotData.hasTokenGenKeyShare()) {
            index++;
        }
        if (sseSnapshotData.hasUpdateCounterKeyShare()) {
            index++;
        }
        Map<IndexAddress, VerifiableShare> result = new HashMap<IndexAddress, VerifiableShare>();
        for (IndexAddress address : sseSnapshotData.updateTupleShareOrder()) {
            if (shares == null || index >= shares.length) {
                throw new IllegalStateException("Snapshot is missing update tuple shares");
            }
            result.put(address, shares[index++]);
        }
        if (shares != null && index != shares.length) {
            throw new IllegalStateException("Snapshot contains unexpected extra shares");
        }
        return result;
    }
}
