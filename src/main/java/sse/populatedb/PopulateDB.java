package sse.populatedb;

import java.nio.file.Path;
import java.nio.file.Paths;

import sse.demo.client.ConfidentialClientAdapter;
import vss.facade.SecretSharingException;

public final class PopulateDB {

    private static final int DEFAULT_CLIENT_ID = 101;
    private static final Path DEFAULT_INPUT = Paths.get("datasets", "processed", "enron", "keyword_to_docids.ndjson");

    public static void main(String[] args) {
        int clientId = DEFAULT_CLIENT_ID;
        Path inputPath = DEFAULT_INPUT;

        if (args.length > 0) {
            try {
                clientId = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                inputPath = Paths.get(args[0]);
            }
        }
        if (args.length > 1) {
            inputPath = Paths.get(args[1]);
        }
        if (args.length > 2) {
            throw new IllegalArgumentException("Usage: PopulateDB [clientId] [inputNdjsonPath]");
        }

        ConfidentialClientAdapter adapter = null;
        try {
            adapter = new ConfidentialClientAdapter(clientId);
            PopulateDBHandler populateDBHandler = new PopulateDBHandler(adapter);
            PopulateDBHandler.PopulationSummary summary = populateDBHandler.populate(inputPath);
            System.out.println("PopulateDB finished.");
            System.out.println("Processed keywords: " + summary.processedKeywords());
            System.out.println("Processed doc IDs: " + summary.processedDocIds());
            System.out.println("Sent bulk batches: " + summary.sentBatches());
        } catch (SecretSharingException e) {
            throw new RuntimeException("Error creating PopulateDB client", e);
        } finally {
            if (adapter != null) {
                adapter.close();
            }
        }
    }
}
