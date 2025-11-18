package ca.concordia.server;
import ca.concordia.filesystem.FileSystemManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class FileServer {

    private FileSystemManager fsManager;
    private int port;
    public FileServer(int port, String fileSystemName, int totalSize){
        try {
            // Initialize the FileSystemManager
            this.fsManager = new FileSystemManager(fileSystemName, totalSize);
            this.port = port;
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize FileSystemManager: " + e.getMessage(), e);
        }
    }

    public void start(){
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server started. Listening on port " + port + "...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Handling client: " + clientSocket);
                try (
                        BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                        PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)
                ) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("Received from client: " + line);
                        handleCommand(line.trim(), writer);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    try {
                        clientSocket.close();
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Could not start server on port " + port);
        }
    }

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
                    if (parts.length < 2) {
                        writer.println("ERROR: CREATE requires filename");
                        break;
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
                    break;
                    
                case "WRITE":
                    if (parts.length < 3) {
                        writer.println("ERROR: WRITE requires filename and content");
                        break;
                    }
                    try {
                        byte[] content = parts[2].getBytes();
                        fsManager.writeFile(parts[1], content);
                        writer.println("SUCCESS: Data written to file '" + parts[1] + "'");
                    } catch (Exception e) {
                        String errorMsg = e.getMessage().toLowerCase();
                        if (errorMsg.contains("not found") || errorMsg.contains("does not exist")) {
                            writer.println("ERROR: file " + parts[1] + " does not exist");
                        } else if (errorMsg.contains("not enough") || errorMsg.contains("blocks") || errorMsg.contains("too large")) {
                            writer.println("ERROR: file too large");
                        } else {
                            writer.println("ERROR: " + e.getMessage());
                        }
                    }
                    break;
                    
                case "READ":
                    if (parts.length < 2) {
                        writer.println("ERROR: READ requires filename");
                        break;
                    }
                    try {
                        byte[] data = fsManager.readFile(parts[1]);
                        String content = new String(data);
                        writer.println(content);
                    } catch (Exception e) {
                        String errorMsg = e.getMessage().toLowerCase();
                        if (errorMsg.contains("not found") || errorMsg.contains("does not exist")) {
                            writer.println("ERROR: file " + parts[1] + " does not exist");
                        } else {
                            writer.println("ERROR: " + e.getMessage());
                        }
                    }
                    break;
                    
                case "DELETE":
                    if (parts.length < 2) {
                        writer.println("ERROR: DELETE requires filename");
                        break;
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
                    break;
                    
                case "LIST":
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
                    break;
                    
                case "QUIT":
                    writer.println("SUCCESS: Disconnecting.");
                    return;
                    
                default:
                    writer.println("ERROR: Unknown command: " + command);
                    break;
            }
        } catch (Exception e) {
            writer.println("ERROR: " + e.getMessage());
        }
    }
}
