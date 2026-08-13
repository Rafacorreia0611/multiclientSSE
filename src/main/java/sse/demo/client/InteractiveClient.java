package sse.demo.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import sse.domain.update.KeywordUpdate;
import sse.domain.update.UpdateOp;

public final class InteractiveClient {

    private final SseClientHandler client;
    private final BufferedReader userIn;

    public InteractiveClient(SseClientHandler client) {
        this.client = client;
        this.userIn = new BufferedReader(new InputStreamReader(System.in));
    }

    public void run() {
        client.initializeState();

        while (true) {
            System.out.println(printMenu());
            String input = readUserLine();

            if (input == null) {
                System.out.println("Input closed. Exiting.");
                return;
            }

            String normalized = input.trim().toLowerCase();
            if ("1".equals(normalized) || "search".equals(normalized) || "s".equals(normalized)) {
                handleSearch();
            } else if ("2".equals(normalized) || "add".equals(normalized) || "a".equals(normalized)) {
                handleUpdate(true);
            } else if ("3".equals(normalized) || "delete".equals(normalized) || "d".equals(normalized)) {
                handleUpdate(false);
            } else if ("4".equals(normalized) || "exit".equals(normalized) || "e".equals(normalized)
                    || "q".equals(normalized) || "quit".equals(normalized)) {
                System.out.println("Exiting.");
                return;
            } else {
                System.out.println("Invalid option: " + input);
            }
        }
    }

    public void close() {
        try {
            userIn.close();
        } catch (IOException e) {
            System.err.println("Error closing user input reader: " + e.getMessage());
        }
    }

    private String printMenu() {
        return "Operations:\n"
                + "1) Search\n"
                + "2) Add\n"
                + "3) Delete\n"
                + "4) Exit";
    }

    private void handleSearch() {
        System.out.println("Search");
        System.out.println("Keyword:");
        String keyword = readUserLine();
        if (keyword == null) {
            System.out.println("Input closed. Back to menu.");
            return;
        }

        List<String> docIds;
        try {
            docIds = client.search(keyword);
        } catch (IllegalArgumentException e) {
            System.out.println("Invalid search: " + e.getMessage());
            return;
        }
        if (docIds.isEmpty()) {
            System.out.println("No results for '" + keyword + "'.");
            return;
        }
        System.out.println("Results for '" + keyword + "': " + docIds);
    }

    private void handleUpdate(boolean isAdd) {
        System.out.println(isAdd ? "Add" : "Delete");
        List<KeywordUpdate> updates = new ArrayList<KeywordUpdate>();
        UpdateOp operation = isAdd ? UpdateOp.ADD : UpdateOp.DEL;

        while (true) {
            System.out.println("Keyword:");
            String keyword = readUserLine();
            if (keyword == null) {
                System.out.println("Input closed. Back to menu.");
                return;
            }

            System.out.println("Doc IDs separated by comma:");
            String docIdsInput = readUserLine();
            if (docIdsInput == null) {
                System.out.println("Input closed. Back to menu.");
                return;
            }

            try {
                updates.add(new KeywordUpdate(keyword, parseDocIds(docIdsInput), operation));
            } catch (IllegalArgumentException e) {
                System.out.println("Invalid update: " + e.getMessage());
                continue;
            }

            System.out.println("Add another keyword to this update? [y/N]");
            String continueInput = readUserLine();
            if (continueInput == null) {
                System.out.println("Input closed. Back to menu.");
                return;
            }
            String normalized = continueInput.trim().toLowerCase();
            if (!"y".equals(normalized) && !"yes".equals(normalized)) {
                break;
            }
        }

        client.update(updates);
    }

    private String readUserLine() {
        try {
            return userIn.readLine();
        } catch (IOException e) {
            throw new RuntimeException("Error reading input from terminal", e);
        }
    }

    private List<String> parseDocIds(String input) {
        List<String> docIds = new ArrayList<String>();
        for (String rawDocId : Arrays.asList(input.split(","))) {
            String docId = rawDocId.trim();
            if (!docId.isEmpty()) {
                docIds.add(docId);
            }
        }
        if (docIds.isEmpty()) {
            throw new IllegalArgumentException("at least one doc ID is required");
        }
        return docIds;
    }
}
