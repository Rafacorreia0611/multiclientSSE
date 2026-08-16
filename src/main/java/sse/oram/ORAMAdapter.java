package sse.oram;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import oram.client.MemoryUpdater;
import oram.client.ORAMObject;
import oram.client.manager.MultiServerORAMManager;
import oram.client.manager.ORAMManager;

public final class ORAMAdapter implements AutoCloseable {

    private static final long DEFAULT_RETRY_DELAY_MS = 250L;
    private static final long DEFAULT_WAIT_TIMEOUT_MS = 30_000L;
    private static final int SMOKE_TEST_ADDRESS = 0;

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

    public byte[] read(int address) {
        ensureConnected();
        return oram.readMemory(address);
    }

    public byte[] write(int address, byte[] content) {
        ensureConnected();
        if (content == null) {
            throw new IllegalArgumentException("content cannot be null");
        }
        return oram.writeMemory(address, content);
    }

    public byte[] update(int address, MemoryUpdater updater) {
        ensureConnected();
        if (updater == null) {
            throw new IllegalArgumentException("updater cannot be null");
        }
        return oram.updateMemory(address, updater);
    }

    public void runSmokeTest() {
        ensureConnected();

        System.out.println("Running MVP-ORAM smoke test at address " + SMOKE_TEST_ADDRESS + "...");
        byte[] firstValue = "mvp-oram-smoke".getBytes(StandardCharsets.UTF_8);
        byte[] updatedValue = "mvp-oram-smoke-updated".getBytes(StandardCharsets.UTF_8);

        byte[] oldValue = write(SMOKE_TEST_ADDRESS, firstValue);
        System.out.println("ORAM smoke write old value: " + printable(oldValue));

        byte[] readValue = read(SMOKE_TEST_ADDRESS);
        System.out.println("ORAM smoke read value: " + printable(readValue));

        byte[] previousValue = update(SMOKE_TEST_ADDRESS, new MemoryUpdater() {
            @Override
            public byte[] update(byte[] oldContent) {
                return updatedValue;
            }
        });
        System.out.println("ORAM smoke update previous value: " + printable(previousValue));

        byte[] finalValue = read(SMOKE_TEST_ADDRESS);
        System.out.println("ORAM smoke final value: " + printable(finalValue));

        if (!Arrays.equals(updatedValue, finalValue)) {
            throw new IllegalStateException("MVP-ORAM smoke test failed");
        }
        System.out.println("MVP-ORAM smoke test passed.");
    }

    private void ensureConnected() {
        if (oram == null) {
            throw new IllegalStateException("ORAM adapter is not connected");
        }
    }

    private String printable(byte[] value) {
        if (value == null) {
            return "null";
        }
        return new String(value, StandardCharsets.UTF_8);
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
