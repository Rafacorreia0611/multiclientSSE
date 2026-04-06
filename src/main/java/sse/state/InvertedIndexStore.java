package sse.state;

import java.util.HashMap;
import java.util.Map;

import sse.domain.EncryptedUpdateTuple;
import sse.domain.IndexAddress;

public final class InvertedIndexStore {

    private Map<IndexAddress, EncryptedUpdateTuple> entries;

    public InvertedIndexStore() {
        this.entries = new HashMap<>();
    }

    public boolean contains(IndexAddress address) {
        return entries.containsKey(address);
    }

    public EncryptedUpdateTuple get(IndexAddress address) {
        return entries.get(address);
    }

    public void put(IndexAddress address, EncryptedUpdateTuple tuple) {
        entries.put(address, tuple);
    }

    public Map<IndexAddress, EncryptedUpdateTuple> snapshot() {
        return new HashMap<>(entries);
    }

    public void restore(Map<IndexAddress, EncryptedUpdateTuple> snapshot) {
        this.entries = new HashMap<>(snapshot);
    }
}
