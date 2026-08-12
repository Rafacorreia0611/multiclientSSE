package sse.state;

import java.util.LinkedHashMap;
import java.util.Map;

import sse.domain.update.EncryptedUpdateTuple;
import sse.domain.id.IndexAddress;

public final class InvertedIndexStore {

    private Map<IndexAddress, EncryptedUpdateTuple> entries;

    public InvertedIndexStore() {
        this.entries = new LinkedHashMap<>();
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
        return new LinkedHashMap<>(entries);
    }

    public void restore(Map<IndexAddress, EncryptedUpdateTuple> snapshot) {
        this.entries = new LinkedHashMap<>(snapshot);
    }
}
