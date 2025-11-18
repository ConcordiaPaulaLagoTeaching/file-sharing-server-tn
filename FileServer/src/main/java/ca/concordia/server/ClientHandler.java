package ca.concordia.server;

import ca.concordia.filesystem.FileSystemManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * ClientHandler handles individual client connections in separate threads.
 * Each instance of this class manages one client connection and processes
 * all commands from that client until the connection is closed.
 */
public class ClientHandler implements Runnable {
    
    private final Socket clientSocket;
    private final FileSystemManager fsManager;
    private final String threadName;
    
    /**
     * Constructor for ClientHandler
     * @param clientSocket The socket connection to the client
     * @param fsManager The file system manager instance (thread-safe)
     */

    public ClientHandler(Socket clientSocket, FileSystemManager fsManager) {
        this.clientSocket = clientSocket;
        this.fsManager = fsManager;
        this.threadName = "ClientHandler-" + clientSocket.getRemoteSocketAddress();
    }
    
    @Override
    public void run() {
        System.out.println("[" + threadName + "] Started handling client: " + clientSocket.getRemoteSocketAddress());
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[" + threadName + "] Received command: " + line);
                handleCommand(line.trim(), writer);
                
                // If client sends QUIT command then break the loop
                if (line.trim().toUpperCase().equals("QUIT")) {
                    break;
                }
            }
            
        } catch (Exception e) {
            System.err.println("[" + threadName + "] Error handling client: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
                System.out.println("[" + threadName + "] Client connection closed");
            } catch (Exception e) {
                System.err.println("[" + threadName + "] Error closing client socket: " + e.getMessage());
            }
        }
    }
    
    /**
     * Handles individual commands from the client
     * @param line The command line received from client
     * @param writer The writer to send responses back to client
     */

    private void handleCommand(String line, PrintWriter writer) {
        try {

            if (line.isEmpty()) {
                writer.println("ERROR: Empty command");
                return;
            }
            
            String[] parts = line.split(" ", 3); // Split into max 3 parts to handle content with spaces
            String command = parts[0].toUpperCase();

            switch (command) {
                case "CREATE":
                    handleCreateCommand(parts, writer);
                    break;
                    
                case "WRITE":
                    handleWriteCommand(parts, writer);
                    break;
                    
                case "READ":
                    handleReadCommand(parts, writer);
                    break;
                    
                case "DELETE":
                    handleDeleteCommand(parts, writer);
                    break;
                    
                case "LIST":
                    handleListCommand(writer);
                    break;
                    
                case "QUIT":
                    writer.println("SUCCESS: Disconnecting...");
                    return;
                    
                default:
                    writer.println("ERROR: Unknown command: " + command);
                    break;
            }
        } catch (Exception e) {
            writer.println("ERROR: " + e.getMessage());
        }
    }
    
    private void handleCreateCommand(String[] parts, PrintWriter writer) {

        if (parts.length < 2) {

            writer.println("ERROR: CREATE requires filename");

            return;
        }
        try {
            fsManager.createFile(parts[1]);
            writer.println("SUCCESS: File '" + parts[1] + "' created.");

        } catch (Exception e) {
            if (e.getMessage().toLowerCase().contains("filename") && e.getMessage().toLowerCase().contains("long")) {

                writer.println("ERROR: filename too large");
            } else {

                writer.println("ERROR: " + e.getMessage());
            }
        }
    }
    

    private void handleWriteCommand(String[] parts, PrintWriter writer) {
        if (parts.length < 3) {
            writer.println("ERROR: WRITE requires filename and content");

            return;
        }
        try {

            byte[] content = parts[2].getBytes();

            fsManager.writeFile(parts[1], content);
            writer.println("SUCCESS: Data written to file '" + parts[1] + "'");

        } catch (Exception e) {
            String errorMsg = e.getMessage().toLowerCase();

            if (errorMsg.contains("not found") || errorMsg.contains("does not exist..")) {

                writer.println("ERROR: file " + parts[1] + " does not exist.");
            } else if (errorMsg.contains("not enough") || errorMsg.contains("blocks") || errorMsg.contains("too large")) {
                writer.println("ERROR: file too large");
            } else {
                writer.println("ERROR: " + e.getMessage());
            }
        }
    }
    
    private void handleReadCommand(String[] parts, PrintWriter writer) {
        if (parts.length < 2) {
            writer.println("ERROR: READ requires filename");
            return;
        }
        try {
            byte[] data = fsManager.readFile(parts[1]);
            String content = new String(data);
            writer.println(content);
        } catch (Exception e) {
            String errorMsg = e.getMessage().toLowerCase();
            if (errorMsg.contains("not   found") || errorMsg.contains("does not exist..")) {

                writer.println("ERROR: file " + parts[1] + " does not exist");
            } else {
                writer.println("ERROR: " + e.getMessage());
            }
        }
    }
    
    private void handleDeleteCommand(String[] parts, PrintWriter writer) {

        if (parts.length < 2) {
            writer.println("ERROR: DELETE requires filename");
            return;
            
        }
        try {
            fsManager.deleteFile(parts[1]);
            writer.println("SUCCESS: File '" + parts[1] + "' deleted.");
        } catch (Exception e) {
            String errorMsg = e.getMessage().toLowerCase();
            if (errorMsg.contains("not found") || errorMsg.contains("does not exist")) {
                writer.println("ERROR: file " + parts[1] + " does not exist");
            } else {
                writer.println("ERROR: " + e.getMessage());
            }
        }
    }
    
    private void handleListCommand(PrintWriter writer) {
        try {
            String[] files = fsManager.listFiles();
            if (files.length == 0) {
                writer.println("");
            } else {
                StringBuilder result = new StringBuilder();
                for (int i = 0; i < files.length; i++) {
                    result.append(files[i]);
                    if (i < files.length - 1) {
                        result.append(" ");
                    }
                }
                writer.println(result.toString());
            }
        } catch (Exception e) {
            writer.println("ERROR: " + e.getMessage());
        }
    }
}