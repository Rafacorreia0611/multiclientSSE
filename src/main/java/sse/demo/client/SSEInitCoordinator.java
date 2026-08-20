package sse.demo.client;

import java.nio.file.Path;

import sse.domain.setup.PreparedInitialization;
import sse.facade.SSEClientFacade;
import sse.oram.ORAMAdapter;
import sse.oram.ORAMSettings;

public final class SSEInitCoordinator {

    private final ConfidentialClientAdapter adapter;
    private final SSEClientFacade sseClientFacade;
    private final Path vocabularyPath;
    private final ORAMSettings oramSettings;
    private final ORAMAdapter oramAdapter;

    public SSEInitCoordinator(ConfidentialClientAdapter adapter,
                              SSEClientFacade sseClientFacade,
                              Path vocabularyPath,
                              ORAMSettings oramSettings,
                              ORAMAdapter oramAdapter) {
        if (adapter == null || sseClientFacade == null || vocabularyPath == null
                || oramSettings == null || oramAdapter == null) {
            throw new IllegalArgumentException(
                    "adapter, sseClientFacade, vocabularyPath, oramSettings, and oramAdapter cannot be null"
            );
        }
        this.adapter = adapter;
        this.sseClientFacade = sseClientFacade;
        this.vocabularyPath = vocabularyPath;
        this.oramSettings = oramSettings;
        this.oramAdapter = oramAdapter;
    }

    public void initializeOrConnect() {
        if (adapter.isInitialized()) {
            connectToExistingOram();
            System.out.println("State already initialized; ORAM is available.");
            return;
        }

        PreparedInitialization preparedInitialization = sseClientFacade.prepareInitialization(vocabularyPath);
        if (adapter.sendInitializeStateRequest(preparedInitialization.initializationMaterial())) {
            createOram(preparedInitialization.oramCapacity());
            System.out.println("State and ORAM initialized.");
        } else {
            connectToExistingOram();
            System.out.println("State was initialized by another client; ORAM is available.");
        }
    }

    private void createOram(int oramCapacity) {
        int treeHeight = ORAMSettings.treeHeightForCapacity(oramCapacity);
        System.out.println("Creating MVP-ORAM from vocabulary capacity " + oramCapacity
                + " using " + oramSettings + " and treeHeight=" + treeHeight);
        oramAdapter.create(treeHeight);
    }

    private void connectToExistingOram() {
        System.out.println("Waiting for existing MVP-ORAM id=" + oramSettings.oramId() + "...");
        oramAdapter.waitUntilAvailable();
    }
}
