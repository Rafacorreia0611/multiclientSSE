package sse.demo.client;

import java.nio.file.Path;
import java.nio.file.Paths;

import sse.vocabulary.VocabularyLoader;

public class Client {

    private static final int DEFAULT_CLIENT_ID = 100;
    private static final String USAGE = "Usage: Client [client-id] [--vocabulary PATH]";

    public static void main(String[] args) {
        int clientId = DEFAULT_CLIENT_ID;
        Path vocabularyPath = VocabularyLoader.DEFAULT_VOCABULARY_PATH;

        int index = 0;
        if (args.length > 0 && !args[0].startsWith("--")) {
            clientId = parseClientId(args[0]);
            index = 1;
        }
        while (index < args.length) {
            String arg = args[index];
            switch (arg) {
                case "--vocabulary":
                    vocabularyPath = Paths.get(readOptionValue(args, ++index, "--vocabulary"));
                    index++;
                    break;
                default:
                    throw new IllegalArgumentException("Unknown argument: " + arg + ". " + USAGE);
            }
        }

        ConfidentialClientAdapter clientAdapter = null;
        SSEClientHandler clientHandler = null;
        InteractiveClient interactiveClient = null;
        try {
            clientAdapter = new ConfidentialClientAdapter(clientId);
            clientHandler = new SSEClientHandler(clientAdapter, vocabularyPath);
            interactiveClient = new InteractiveClient(clientHandler);
            interactiveClient.run();
        } catch (Exception e) {
            throw new RuntimeException("Error running client", e);
        } finally {
            if (interactiveClient != null) {
                interactiveClient.close();
            }
            if (clientHandler != null) {
                clientHandler.close();
            }
            if (clientAdapter != null) {
                clientAdapter.close();
            }
        }

    }

    private static int parseClientId(String rawValue) {
        return parseNonNegativeInteger(rawValue, "client ID");
    }

    private static int parseNonNegativeInteger(String rawValue, String optionName) {
        try {
            int value = Integer.parseInt(rawValue);
            if (value < 0) {
                throw new IllegalArgumentException(optionName + " cannot be negative");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid " + optionName + ": " + rawValue + ". " + USAGE, e);
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
}
