package ca.concordia;

import java.io.*;
import java.net.Socket;

/**
 * SimpleWebClient demonstrates the benefits of multithreading by connecting to the server
 * and waiting for one minute before sending a request. This helps show how multiple
 * clients can be handled concurrently.
 */
public class SimpleWebClient {
    
    private String serverHost;
    private int serverPort;
    private String clientName;
    
    public SimpleWebClient(String serverHost, int serverPort, String clientName) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.clientName = clientName;
    }
    
    
    public void connectAndWait() {
        try (Socket socket = new Socket(serverHost, serverPort);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in))) {
            
            System.out.println("[" + clientName + "] Connected to server at " + serverHost + ":" + serverPort);
            
            // Wait for one minute before sending request (as mentioned in requirements)
            System.out.println("[" + clientName + "] Waiting for 1 minute before sending request...");
            Thread.sleep(60000); // 1 minute = 60000 milliseconds
            
            // After waiting, send some commands to test the server
            System.out.println("[" + clientName + "] Sending commands to server...");
            
            // Create a file
            String filename = clientName + "_test.txt";
            writer.println("CREATE " + filename);
            System.out.println("[" + clientName + "] Sent: CREATE " + filename);
            System.out.println("[" + clientName + "] Response: " + reader.readLine());
            
            // Write to the file
            String content = "Hello from " + clientName + " at " + System.currentTimeMillis();
            writer.println("WRITE " + filename + " " + content);
            System.out.println("[" + clientName + "] Sent: WRITE " + filename + " " + content);
            System.out.println("[" + clientName + "] Response: " + reader.readLine());
            
            // Read the file
            writer.println("READ " + filename);
            System.out.println("[" + clientName + "] Sent: READ " + filename);
            System.out.println("[" + clientName + "] Response: " + reader.readLine());
            
            // List files
            writer.println("LIST");
            System.out.println("[" + clientName + "] Sent: LIST");
            System.out.println("[" + clientName + "] Response: " + reader.readLine());
            
            // Quit
            writer.println("QUIT");
            System.out.println("[" + clientName + "] Sent: QUIT");
            System.out.println("[" + clientName + "] Response: " + reader.readLine());
            
            System.out.println("[" + clientName + "] Finished and disconnected");
            
        } catch (IOException e) {
            System.err.println("[" + clientName + "] IO Error: " + e.getMessage());
        } catch (InterruptedException e) {
            System.err.println("[" + clientName + "] Interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }
    
    public static void main(String[] args) {
        String host = "localhost";
        int port = 12345;
        String clientName = "SimpleWebClient";
        
        if (args.length >= 1) {
            clientName = args[0];
        }
        if (args.length >= 2) {
            host = args[1];
        }
        if (args.length >= 3) {
            port = Integer.parseInt(args[2]);
        }
        
        SimpleWebClient client = new SimpleWebClient(host, port, clientName);
        client.connectAndWait();
    }
}