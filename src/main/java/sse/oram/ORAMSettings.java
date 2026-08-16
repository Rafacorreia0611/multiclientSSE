package sse.oram;

public final class ORAMSettings {

    public static final int DEFAULT_ORAM_ID = 0;
    public static final int DEFAULT_BUCKET_SIZE = 4;
    public static final int DEFAULT_BLOCK_SIZE = 512;
    public static final int DEFAULT_ORAM_CLIENT_ID_OFFSET = 1000;

    private final int oramId;
    private final int bucketSize;
    private final int blockSize;

    public ORAMSettings(int oramId, int bucketSize, int blockSize) {
        if (oramId < 0) {
            throw new IllegalArgumentException("oramId cannot be negative");
        }
        if (bucketSize <= 0) {
            throw new IllegalArgumentException("bucketSize must be greater than zero");
        }
        if (blockSize <= 0) {
            throw new IllegalArgumentException("blockSize must be greater than zero");
        }
        this.oramId = oramId;
        this.bucketSize = bucketSize;
        this.blockSize = blockSize;
    }

    public static ORAMSettings defaults() {
        return new ORAMSettings(DEFAULT_ORAM_ID, DEFAULT_BUCKET_SIZE, DEFAULT_BLOCK_SIZE);
    }

    public static int oramClientIdFor(int sseClientId) {
        if (sseClientId < 0) {
            throw new IllegalArgumentException("sseClientId cannot be negative");
        }
        if (sseClientId > Integer.MAX_VALUE - DEFAULT_ORAM_CLIENT_ID_OFFSET) {
            throw new IllegalArgumentException("sseClientId is too large to derive an ORAM client id");
        }
        return sseClientId + DEFAULT_ORAM_CLIENT_ID_OFFSET;
    }

    public int oramId() {
        return oramId;
    }

    public int bucketSize() {
        return bucketSize;
    }

    public int blockSize() {
        return blockSize;
    }

    public static int treeHeightForCapacity(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be greater than zero");
        }

        int height = 0;
        while (true) {
            int treeSize = treeSizeForHeight(height);
            if (treeSize >= capacity) {
                return height;
            }
            height++;
        }
    }

    private static int treeSizeForHeight(int height) {
        if (height >= 30) {
            throw new IllegalArgumentException("ORAM tree height is too large for integer addresses: " + height);
        }
        return (1 << (height + 1)) - 1;
    }

    @Override
    public String toString() {
        return "ORAMSettings[" +
                "oramId=" + oramId +
                ", bucketSize=" + bucketSize +
                ", blockSize=" + blockSize +
                ']';
    }
}
