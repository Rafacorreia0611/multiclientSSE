package sse.oram;

import oram.client.ORAMObject;
import oram.client.manager.MultiServerORAMManager;
import oram.client.manager.ORAMManager;
import sse.domain.state.KeywordBlock;
import sse.domain.state.KeywordState;

public final class ORAMAdapter implements AutoCloseable {

    private static final long DEFAULT_RETRY_DELAY_MS = 250L;
    private static final long DEFAULT_WAIT_TIMEOUT_MS = 30_000L;

    private final ORAMSettings settings;
    private final ORAMManager manager;
    private ORAMObject oram;

    public ORAMAdapter(ORAMSettings settings, int oramClientId) {
        if (settings == null) {
            throw new IllegalArgumentException("settings cannot be null");
        }
        this.settings = settings;
        this.manager = new MultiServerORAMManager(oramClientId);
    }

    public void create(int treeHeight) {
        ORAMObject created = manager.createORAM(
                settings.oramId(),
                treeHeight,
                settings.bucketSize(),
                settings.blockSize()
        );
        if (created == null) {
            throw new IllegalStateException("Failed to create ORAM " + settings.oramId());
        }
        this.oram = created;
        System.out.println("Created MVP-ORAM id=" + settings.oramId()
                + " treeHeight=" + treeHeight
                + " bucketSize=" + settings.bucketSize()
                + " blockSize=" + settings.blockSize());
    }

    public void waitUntilAvailable() {
        long deadline = System.currentTimeMillis() + DEFAULT_WAIT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            ORAMObject existing = manager.getORAM(settings.oramId());
            if (existing != null) {
                this.oram = existing;
                System.out.println("Connected to existing MVP-ORAM id=" + settings.oramId());
                return;
            }
            sleep(DEFAULT_RETRY_DELAY_MS);
        }
        throw new IllegalStateException("Timed out waiting for MVP-ORAM " + settings.oramId());
    }

    public KeywordBlock readKeywordBlock(int address) {
        ensureConnected();
        return KeywordBlock.deserialize(oram.readMemory(address));
    }

    public KeywordBlock acquireKeywordLock(int address) {
        ensureConnected();
        byte[] oldContent = oram.updateMemory(address, currentContent -> {
            KeywordBlock oldBlock = KeywordBlock.deserialize(currentContent);
            if (oldBlock.locked()) {
                return null;
            }
            return oldBlock.withLock(true).serialize();
        });
        return KeywordBlock.deserialize(oldContent);
    }

    public KeywordBlock releaseKeywordLock(int address) {
        ensureConnected();
        byte[] oldContent = oram.updateMemory(address, currentContent -> {
            KeywordBlock oldBlock = KeywordBlock.deserialize(currentContent);
            return oldBlock.withLock(false).serialize();
        });
        return KeywordBlock.deserialize(oldContent);
    }

    public KeywordBlock publishKeywordState(int address, KeywordState keywordState) {
        if (keywordState == null) {
            throw new IllegalArgumentException("keywordState cannot be null");
        }
        ensureConnected();
        byte[] oldContent = oram.updateMemory(address, currentContent ->
                new KeywordBlock(false, keywordState).serialize());
        return KeywordBlock.deserialize(oldContent);
    }

    private void ensureConnected() {
        if (oram == null) {
            throw new IllegalStateException("ORAM adapter is not connected");
        }
    }

    private void sleep(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for ORAM", e);
        }
    }

    @Override
    public void close() {
        manager.close();
    }
}
