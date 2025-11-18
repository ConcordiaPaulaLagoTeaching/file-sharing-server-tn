package ca.concordia;

import java.io.*;
import java.net.Socket;

/**
 * MultiClientTest creates multiple concurrent clients to test the multithreaded server.
 * This demonstrates how the server can handle multiple clients simultaneously.
 */
public class MultiClientTest {
    
    public static void main(String[] args) {
        String host = "localhost";
        int port = 12345;
        int numClients = 3; // Number of concurrent clients to create
        
        if (args.length >= 1) {
            numClients = Integer.parseInt(args[0]);
        }
        
        System.out.println("Starting " + numClients + " concurrent clients to test multithreading...");
        
        // Create and start multiple client threads
        Thread[] clientThreads = new Thread[numClients];
        
        for (int i = 0; i < numClients; i++) {
            final int clientId = i + 1;
            clientThreads[i] = new Thread(new TestClient(host, port, "Client-" + clientId));
            clientThreads[i].start();
        }
        
        // Wait for all client threads to complete
        for (Thread thread : clientThreads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Main thread interrupted while waiting for client threads");
                break;
            }
        }
        
        System.out.println("All clients finished.");
    }
    
    static class TestClient implements Runnable {
        private String host;
        private int port;
        private String clientName;
        
        public TestClient(String host, int port, String clientName) {
            this.host = host;
            this.port = port;
            this.clientName = clientName;
        }
        
        @Override
        public void run() {
            try (Socket socket = new Socket(host, port);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {
                
                System.out.println("[" + clientName + "] Connected at " + System.currentTimeMillis());
                
                // Wait different amounts of time for each client to show concurrent processing
                Thread.sleep(clientName.equals("Client-1") ? 5000 : 2000);
                
                // Each client performs operations
                String filename = "c" + clientName.charAt(clientName.length()-1) + ".txt"; // Short filename (e.g., "c1.txt")
                
                // CREATE
                writer.println("CREATE " + filename);
                String response = reader.readLine();
                System.out.println("[" + clientName + "] CREATE response: " + response);
                
                // WRITE
                writer.println("WRITE " + filename + " Data from " + clientName + " at " + System.currentTimeMillis());
                response = reader.readLine();
                System.out.println("[" + clientName + "] WRITE response: " + response);
                
                // READ
                writer.println("READ " + filename);
                response = reader.readLine();
                System.out.println("[" + clientName + "] READ response: " + response);
                
                // LIST
                writer.println("LIST");
                response = reader.readLine();
                System.out.println("[" + clientName + "] LIST response: " + response);
                
                // QUIT
                writer.println("QUIT");
                response = reader.readLine();
                System.out.println("[" + clientName + "] QUIT response: " + response);
                
                System.out.println("[" + clientName + "] Finished at " + System.currentTimeMillis());
                
            } catch (IOException e) {
                System.err.println("[" + clientName + "] IO Error: " + e.getMessage());
            } catch (InterruptedException e) {
                System.err.println("[" + clientName + "] Interrupted: " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        }
    }
}