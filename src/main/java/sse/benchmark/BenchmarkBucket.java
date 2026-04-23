package sse.benchmark;

public final class BenchmarkBucket {

    private final int minDocs;
    private final int maxDocs;

    public BenchmarkBucket(int minDocs, int maxDocs) {
        if (minDocs <= 0 || maxDocs <= 0) {
            throw new IllegalArgumentException("Bucket bounds must be positive");
        }
        if (minDocs > maxDocs) {
            throw new IllegalArgumentException("Bucket min cannot be greater than max");
        }
        this.minDocs = minDocs;
        this.maxDocs = maxDocs;
    }

    public boolean contains(int docCount) {
        return docCount >= minDocs && docCount <= maxDocs;
    }

    public boolean overlaps(BenchmarkBucket other) {
        return minDocs <= other.maxDocs && other.minDocs <= maxDocs;
    }

    public String label() {
        return minDocs + ":" + maxDocs;
    }
}
