package sse.demo.server;

public class Server {
    public static void main(String[] args) {
        try{
            int processId = Integer.parseInt(args[0]);
            new ConfidentialSseServer(processId);
        } catch (NumberFormatException e) {
            System.err.println("Invalid process ID: " + args[0]);
        }
    }
}
