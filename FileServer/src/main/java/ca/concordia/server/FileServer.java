package ca.concordia.server;
import ca.concordia.filesystem.FileSystemManager;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class FileServer {

    private FileSystemManager fsManager;
    private int port;
    private ExecutorService threadPool;
    private boolean isRunning = false;
    
    public FileServer(int port, String fileSystemName, int totalSize){
        try {
            // Initialize the FileSystemManager
            this.fsManager = new FileSystemManager(fileSystemName, totalSize);
            this.port = port;
            // Create a cached thread pool that can handle thousands of connections
            this.threadPool = Executors.newCachedThreadPool();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize FileSystemManager: " + e.getMessage(), e);
        }
    }

    public void start(){
        isRunning = true;
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Multithreaded Server started. Listening on port " + port + "...");

            while (isRunning) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getRemoteSocketAddress());
                
                // Create a new ClientHandler and submit it to the thread pool
                ClientHandler clientHandler = new ClientHandler(clientSocket, fsManager);
                threadPool.submit(clientHandler);
                
                System.out.println("Client handler submitted to thread pool. Active threads: " 
                    + ((java.util.concurrent.ThreadPoolExecutor) threadPool).getActiveCount());
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Could not start server on port " + port);
        } finally {
            shutdown();
        }
    }
    
    /**
     * Gracefully shutdown the server and thread pool
     */
    public void shutdown() {
        isRunning = false;
        if (threadPool != null && !threadPool.isShutdown()) {
            System.out.println("Shutting down server...");
            threadPool.shutdown();
            try {
                // Wait up to 30 seconds for existing tasks to complete
                if (!threadPool.awaitTermination(30, TimeUnit.SECONDS)) {
                    // Force shutdown if tasks don't complete in time
                    threadPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                threadPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
            System.out.println("Server shutdown complete.");
        }
    }

}
