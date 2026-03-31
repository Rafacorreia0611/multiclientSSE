package sse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import sse.crypto.Prf;
import sse.model.EncryptedUpdateTuple;
import sse.model.EpochSearchKey;
import sse.model.IndexAddress;
import sse.model.KeywordToken;
import sse.model.PlainSnapshotData;
import sse.model.SearchToken;
import sse.model.State;
import sse.model.UpdateToken;
import vss.secretsharing.VerifiableShare;

public class SseServer {
    
    private Map<KeywordToken, Integer> searchCounter;
    private Map<KeywordToken, Integer> updateCounter;
    private Map<KeywordToken, List<IndexAddress>> dbCache;
    private Map<KeywordToken, Integer> nextSearchIndex;
    private Map<IndexAddress, EncryptedUpdateTuple> invertedIndex;
    private Map<IndexAddress, VerifiableShare> updateTupleShares;
    private VerifiableShare tokenGenKeyShare;

    public SseServer() {
        this.searchCounter = new HashMap<>();
        this.updateCounter = new HashMap<>();
        this.dbCache = new HashMap<>();
        this.nextSearchIndex = new HashMap<>();
        this.invertedIndex = new HashMap<>();
        this.updateTupleShares = new HashMap<>();
        this.tokenGenKeyShare = null;
    }

    // token_ws and token_w are Base64 encoded strings
    public Map<EncryptedUpdateTuple, VerifiableShare> searchQuery(SearchToken searchToken) {

        EpochSearchKey epochSearchKey = searchToken.epochSearchKey();
        KeywordToken keywordToken = searchToken.keywordToken();

        // Check if the search counter for the token_w is 0, if so, nextSearchIndex should be initialized to 1
        if (!searchCounter.containsKey(keywordToken)) {
            searchCounter.put(keywordToken, 0);
            nextSearchIndex.put(keywordToken, 1);
        }
        Map<EncryptedUpdateTuple, VerifiableShare> ret = new LinkedHashMap<>();
        // Retrive the updates, where the address is keeped in the cache, and add them to the result list
        List<IndexAddress> cachedAddresses = dbCache.getOrDefault(keywordToken, new LinkedList<>());
        if (!cachedAddresses.isEmpty()) {
            for (IndexAddress address : cachedAddresses) {
                EncryptedUpdateTuple update = invertedIndex.get(address);
                VerifiableShare updateTupleKey = updateTupleShares.get(address);
                if (update != null) {
                    ret.put(update, updateTupleKey);
                }
            }
        }
        if (searchToken.searchCounter() != searchCounter.get(keywordToken)) {
            return ret;
        }
        // Iterate over from the nextSearchIndex to the current updateCounter
        byte[] epochSearchKeyBytes = epochSearchKey.value();
        int currentUpdateCounter = updateCounter.getOrDefault(keywordToken, 0);
        int nextIndex = nextSearchIndex.get(keywordToken);
        List<IndexAddress> newAddresses = new LinkedList<>();
        for (int i = nextIndex; i <= currentUpdateCounter; i++) {
            // Get the address of the i-th update for token_ws from the inverted index
            byte[] address = Prf.prf(epochSearchKeyBytes, Integer.toString(i));
            IndexAddress indexAddress = new IndexAddress(address);
            EncryptedUpdateTuple update = invertedIndex.get(indexAddress);
            if (update != null) {
                VerifiableShare updateTupleKey = updateTupleShares.get(indexAddress);
                ret.put(update, updateTupleKey);
                newAddresses.add(indexAddress);
            }
        }
        // Update the nextSearchIndex to the current updateCounter + 1
        nextSearchIndex.put(keywordToken, currentUpdateCounter + 1);
        // Update the cache with the new addresses
        cachedAddresses.addAll(newAddresses);
        dbCache.put(keywordToken, cachedAddresses);
        // Update the search counter for the token_w
        searchCounter.put(keywordToken, searchCounter.get(keywordToken) + 1);
        // Return the list of updates
        return ret;
    }

    public void updateQuery(UpdateToken updateToken, VerifiableShare tokenGenKeyShare) {

        IndexAddress address = updateToken.address();
        EncryptedUpdateTuple encryptedTuple = updateToken.encryptedTuple();
        Map<KeywordToken, Integer> newUpdateCounter = updateToken.updateCounter();
        
        // Update the inverted index with the new update
        if (invertedIndex.containsKey(address)) {
            throw new IllegalArgumentException("Address already exists in the inverted index");
        }
        invertedIndex.put(address, encryptedTuple);
        updateTupleShares.put(address, tokenGenKeyShare);
        // Update the update counter with the new update counter
        updateCounter.putAll(newUpdateCounter);
    }

    public State getState(String op) {
        if (op == null || (!op.equalsIgnoreCase("search") && !op.equalsIgnoreCase("update"))) {
            throw new IllegalArgumentException("Operation must be either 'search' or 'update'");
        }
        if (op.equalsIgnoreCase("search")) {
            return new State(searchCounter);
        } else {
            return new State(searchCounter, updateCounter);
        }
    }

    public VerifiableShare getTokenGenKey() {
        return tokenGenKeyShare;
    }

    private static SecretKey generateRandomHmacKey() {
        KeyGenerator keyGen = null;
        try {
            keyGen = KeyGenerator.getInstance("HmacSHA256");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("HmacSHA256 not available in this JVM/provider", e);
        }
        keyGen.init(256);
        return keyGen.generateKey();
    }

    public Boolean initializeTokenGenKey(VerifiableShare vss){
        if (this.tokenGenKeyShare != null) {
            return false; // Key already initialized
        }
        this.tokenGenKeyShare = vss;
        return true;
    }

    public PlainSnapshotData getPlainSnapshotData() {
        return new PlainSnapshotData(
                new HashMap<>(searchCounter),
                new HashMap<>(updateCounter),
                copyDbCache(dbCache),
                new HashMap<>(nextSearchIndex),
                new HashMap<>(invertedIndex),
                new ArrayList<>(updateTupleShares.keySet()),
                tokenGenKeyShare != null
        );
    }

    public VerifiableShare[] getSnapshotShares(List<IndexAddress> updateTupleShareOrder, boolean includeTokenGenKeyShare) {
        int size = updateTupleShareOrder.size() + (includeTokenGenKeyShare ? 1 : 0);
        if (size == 0) {
            return new VerifiableShare[0];
        }

        VerifiableShare[] shares = new VerifiableShare[size];
        int index = 0;
        if (includeTokenGenKeyShare) {
            shares[index++] = tokenGenKeyShare;
        }
        for (IndexAddress address : updateTupleShareOrder) {
            shares[index++] = updateTupleShares.get(address);
        }
        return shares;
    }

    public void installSnapshot(PlainSnapshotData snapshotData, VerifiableShare tokenGenKeyShare,
                                Map<IndexAddress, VerifiableShare> updateTupleShares) {
        if (snapshotData == null) {
            throw new IllegalArgumentException("snapshotData cannot be null");
        }
        this.searchCounter = new HashMap<>(snapshotData.searchCounter());
        this.updateCounter = new HashMap<>(snapshotData.updateCounter());
        this.dbCache = copyDbCache(snapshotData.dbCache());
        this.nextSearchIndex = new HashMap<>(snapshotData.nextSearchIndex());
        this.invertedIndex = new HashMap<>(snapshotData.invertedIndex());
        this.updateTupleShares = new HashMap<>(updateTupleShares);
        this.tokenGenKeyShare = tokenGenKeyShare;
    }

    private Map<KeywordToken, List<IndexAddress>> copyDbCache(Map<KeywordToken, List<IndexAddress>> source) {
        Map<KeywordToken, List<IndexAddress>> copy = new HashMap<>(source.size());
        for (Map.Entry<KeywordToken, List<IndexAddress>> entry : source.entrySet()) {
            copy.put(entry.getKey(), new LinkedList<>(entry.getValue()));
        }
        return copy;
    }
}
