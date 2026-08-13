package sse.snapshot;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import sse.domain.update.EncryptedUpdateTuple;
import sse.domain.id.IndexAddress;
import sse.domain.state.EncryptedKeywordAddressMap;
import sse.domain.state.KeywordState;
import sse.domain.id.KeywordToken;
import vss.secretsharing.VerifiableShare;

public final class SsePlainSnapshotData implements Serializable {
    private final Map<KeywordToken, KeywordState> keywordStates;
    private final byte[] encodedTrapdoorPublicKey;
    private final EncryptedKeywordAddressMap encryptedKeywordAddressMap;
    private final int activeClientId;
    private final int blockedStateRequestsWhileActive;
    private final boolean setupInProgress;
    private final Map<IndexAddress, EncryptedUpdateTuple> invertedIndex;
    private final List<IndexAddress> updateTupleShareOrder;
    private final boolean hasMasterKeyShare;
    private final boolean hasTrapdoorPrivateKeyShare;

    public SsePlainSnapshotData(Map<KeywordToken, KeywordState> keywordStates,
                                byte[] encodedTrapdoorPublicKey,
                                EncryptedKeywordAddressMap encryptedKeywordAddressMap,
                                int activeClientId,
                                int blockedStateRequestsWhileActive,
                                boolean setupInProgress,
                                Map<IndexAddress, EncryptedUpdateTuple> invertedIndex,
                                List<IndexAddress> updateTupleShareOrder,
                                boolean hasMasterKeyShare,
                                boolean hasTrapdoorPrivateKeyShare) {
        this.keywordStates = new LinkedHashMap<>(keywordStates);
        this.encodedTrapdoorPublicKey = encodedTrapdoorPublicKey == null ? null : encodedTrapdoorPublicKey.clone();
        this.encryptedKeywordAddressMap = encryptedKeywordAddressMap;
        this.activeClientId = activeClientId;
        this.blockedStateRequestsWhileActive = blockedStateRequestsWhileActive;
        this.setupInProgress = setupInProgress;
        this.invertedIndex = new LinkedHashMap<>(invertedIndex);
        this.updateTupleShareOrder = updateTupleShareOrder;
        this.hasMasterKeyShare = hasMasterKeyShare;
        this.hasTrapdoorPrivateKeyShare = hasTrapdoorPrivateKeyShare;
    }

    public Map<KeywordToken, KeywordState> keywordStates() {
        return keywordStates;
    }

    public byte[] encodedTrapdoorPublicKey() {
        return encodedTrapdoorPublicKey == null ? null : encodedTrapdoorPublicKey.clone();
    }

    public EncryptedKeywordAddressMap encryptedKeywordAddressMap() {
        return encryptedKeywordAddressMap;
    }

    public int activeClientId() {
        return activeClientId;
    }

    public int blockedStateRequestsWhileActive() {
        return blockedStateRequestsWhileActive;
    }

    public boolean setupInProgress() {
        return setupInProgress;
    }

    public Map<IndexAddress, EncryptedUpdateTuple> invertedIndex() {
        return invertedIndex;
    }

    public List<IndexAddress> updateTupleShareOrder() {
        return updateTupleShareOrder;
    }

    public boolean hasMasterKeyShare() {
        return hasMasterKeyShare;
    }

    public boolean hasTrapdoorPrivateKeyShare() {
        return hasTrapdoorPrivateKeyShare;
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

    public static SsePlainSnapshotData deserialize(byte[] plainData) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(plainData);
             ObjectInput in = new ObjectInputStream(bis)) {
            return (SsePlainSnapshotData) in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException("Error deserializing snapshot", e);
        }
    }

    public VerifiableShare masterKeyShare(VerifiableShare[] shares) {
        if (!hasMasterKeyShare) {
            return null;
        }
        if (shares == null || shares.length == 0) {
            throw new IllegalStateException("Snapshot is missing master key share");
        }
        return shares[0];
    }

    public VerifiableShare trapdoorPrivateKeyShare(VerifiableShare[] shares) {
        if (!hasTrapdoorPrivateKeyShare) {
            return null;
        }
        int index = hasMasterKeyShare ? 1 : 0;
        if (shares == null || index >= shares.length) {
            throw new IllegalStateException("Snapshot is missing trapdoor private key share");
        }
        return shares[index];
    }

    public Map<IndexAddress, VerifiableShare> updateTupleShares(VerifiableShare[] shares) {
        int index = 0;
        if (hasMasterKeyShare) {
            index++;
        }
        if (hasTrapdoorPrivateKeyShare) {
            index++;
        }
        Map<IndexAddress, VerifiableShare> result = new LinkedHashMap<IndexAddress, VerifiableShare>();
        for (IndexAddress address : updateTupleShareOrder) {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SsePlainSnapshotData that = (SsePlainSnapshotData) o;
        return activeClientId == that.activeClientId &&
                blockedStateRequestsWhileActive == that.blockedStateRequestsWhileActive &&
                setupInProgress == that.setupInProgress &&
                hasMasterKeyShare == that.hasMasterKeyShare &&
                hasTrapdoorPrivateKeyShare == that.hasTrapdoorPrivateKeyShare &&
                Objects.equals(keywordStates, that.keywordStates) &&
                Arrays.equals(encodedTrapdoorPublicKey, that.encodedTrapdoorPublicKey) &&
                Objects.equals(encryptedKeywordAddressMap, that.encryptedKeywordAddressMap) &&
                Objects.equals(invertedIndex, that.invertedIndex) &&
                Objects.equals(updateTupleShareOrder, that.updateTupleShareOrder);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(keywordStates, activeClientId, blockedStateRequestsWhileActive,
                setupInProgress, encryptedKeywordAddressMap, invertedIndex, updateTupleShareOrder,
                hasMasterKeyShare, hasTrapdoorPrivateKeyShare);
        result = 31 * result + Arrays.hashCode(encodedTrapdoorPublicKey);
        return result;
    }

    @Override
    public String toString() {
        return "SsePlainSnapshotData[" +
                "keywordStates=" + keywordStates +
                ", encodedTrapdoorPublicKeyLength=" +
                (encodedTrapPublicKeyLength()) +
                ", encryptedKeywordAddressMap=" + encryptedKeywordAddressMap +
                ", activeClientId=" + activeClientId +
                ", blockedStateRequestsWhileActive=" + blockedStateRequestsWhileActive +
                ", setupInProgress=" + setupInProgress +
                ", invertedIndex=" + invertedIndex +
                ", updateTupleShareOrder=" + updateTupleShareOrder +
                ", hasMasterKeyShare=" + hasMasterKeyShare +
                ", hasTrapdoorPrivateKeyShare=" + hasTrapdoorPrivateKeyShare +
                ']';
    }

    private int encodedTrapPublicKeyLength() {
        return encodedTrapdoorPublicKey == null ? 0 : encodedTrapdoorPublicKey.length;
    }
}
