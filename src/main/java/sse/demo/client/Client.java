package sse.demo.client;

public class Client {

    private static final int DEFAULT_CLIENT_ID = 100;
    public static void main(String[] args) {
        int clientId = DEFAULT_CLIENT_ID;
        if (args.length > 0) {
            try {
                clientId = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("Invalid client ID provided, using default: " + DEFAULT_CLIENT_ID);
            }
        }

        ConfidentialClientAdapter clientAdapter = null;
        SseClientHandler clientHandler = null;
        InteractiveClient interactiveClient = null;
        try {
            clientAdapter = new ConfidentialClientAdapter(clientId);
            clientHandler = new SseClientHandler(clientAdapter);
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
}
