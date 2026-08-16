package sse.populatedb;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.Paths;

import sse.demo.client.ConfidentialClientAdapter;
import sse.vocabulary.VocabularyLoader;
import vss.facade.SecretSharingException;

public final class PopulateDB {

    private static final int DEFAULT_CLIENT_ID = 101;
    private static final Path DEFAULT_INPUT = Paths.get("datasets", "processed", "enron", "keyword_to_docids.ndjson");
    private static final int DEFAULT_BATCH_SIZE = 1_000;
    private static final String USAGE =
            "Usage: PopulateDB [--client-id N] [--input PATH] [--batch-size N] [--vocabulary PATH]";

    public static void main(String[] args) {
        int clientId = DEFAULT_CLIENT_ID;
        Path inputPath = DEFAULT_INPUT;
        Path vocabularyPath = VocabularyLoader.DEFAULT_VOCABULARY_PATH;
        int batchSize = DEFAULT_BATCH_SIZE;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--client-id":
                    clientId = parsePositiveInteger(readOptionValue(args, ++i, "--client-id"), "--client-id");
                    break;
                case "--input":
                    inputPath = Paths.get(readOptionValue(args, ++i, "--input"));
                    break;
                case "--batch-size":
                    batchSize = parsePositiveInteger(readOptionValue(args, ++i, "--batch-size"), "--batch-size");
                    break;
                case "--vocabulary":
                    vocabularyPath = Paths.get(readOptionValue(args, ++i, "--vocabulary"));
                    break;
                default:
                    throw new IllegalArgumentException("Unknown argument: " + arg + ". " + USAGE);
            }
        }
        validateInputPath(inputPath);

        ConfidentialClientAdapter adapter = null;
        PopulateDBHandler populateDBHandler = null;
        try {
            adapter = new ConfidentialClientAdapter(clientId);
            populateDBHandler = new PopulateDBHandler(adapter, batchSize, vocabularyPath);
            PopulateDBHandler.PopulationSummary summary = populateDBHandler.populate(inputPath);
            System.out.println("PopulateDB finished.");
            System.out.println("Processed keywords: " + summary.processedKeywords());
            System.out.println("Processed doc IDs: " + summary.processedDocIds());
            System.out.println("Sent bulk batches: " + summary.sentBatches());
        } catch (SecretSharingException e) {
            throw new RuntimeException("Error creating PopulateDB client", e);
        } finally {
            if (populateDBHandler != null) {
                populateDBHandler.close();
            }
            if (adapter != null) {
                adapter.close();
            }
        }
    }

    private static void validateInputPath(Path inputPath) {
        if (inputPath == null) {
            throw new IllegalArgumentException("input path cannot be null");
        }
        if (!Files.exists(inputPath) || !Files.isRegularFile(inputPath)) {
            throw new IllegalArgumentException("NDJSON file does not exist: " + inputPath.toAbsolutePath());
        }
    }

    private static String readOptionValue(String[] args, int valueIndex, String optionName) {
        if (valueIndex >= args.length) {
            throw new IllegalArgumentException("Missing value for " + optionName + ". " + USAGE);
        }
        String value = args[valueIndex];
        if (value.startsWith("--")) {
            throw new IllegalArgumentException("Missing value for " + optionName + ". " + USAGE);
        }
        return value;
    }

    private static int parsePositiveInteger(String rawValue, String optionName) {
        try {
            int value = Integer.parseInt(rawValue);
            if (value <= 0) {
                throw new IllegalArgumentException(optionName + " must be greater than zero");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid " + optionName + " value: " + rawValue, e);
        }
    }
}
