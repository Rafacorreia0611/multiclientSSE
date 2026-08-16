package sse.domain.setup;

public final class PreparedInitialization {

    private final InitializationMaterial initializationMaterial;
    private final int oramCapacity;

    public PreparedInitialization(InitializationMaterial initializationMaterial, int oramCapacity) {
        if (initializationMaterial == null) {
            throw new IllegalArgumentException("initializationMaterial cannot be null");
        }
        if (oramCapacity <= 0) {
            throw new IllegalArgumentException("oramCapacity must be greater than zero");
        }
        this.initializationMaterial = initializationMaterial;
        this.oramCapacity = oramCapacity;
    }

    public InitializationMaterial initializationMaterial() {
        return initializationMaterial;
    }

    public int oramCapacity() {
        return oramCapacity;
    }
}
