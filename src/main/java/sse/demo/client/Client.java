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
        SseClientHandler clientHandler = null;
        InteractiveClient interactiveClient = null;
        try {
            clientAdapter = new ConfidentialClientAdapter(clientId);
            clientHandler = new SseClientHandler(clientAdapter, vocabularyPath);
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
        }

    }

    private static int parseClientId(String rawValue) {
        try {
            return Integer.parseInt(rawValue);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid client ID: " + rawValue + ". " + USAGE, e);
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
