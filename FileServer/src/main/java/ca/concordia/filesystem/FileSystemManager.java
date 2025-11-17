package ca.concordia.filesystem;

import ca.concordia.filesystem.datastructures.FEntry;
import ca.concordia.filesystem.datastructures.FNode;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class FileSystemManager {

    private final int MAXFILES = 5;
    private final int MAXBLOCKS = 10;
    private static FileSystemManager instance = null;
    private final RandomAccessFile disk;
    private final ReentrantLock globalLock = new ReentrantLock();


    private static final int BLOCK_SIZE = 128;

    private FEntry[] inodeTable; // Array of inodes
    private boolean[] freeBlockList; // Bitmap for free blocks
    private FNode[] blockNodes; // Block chain nodes



    public FileSystemManager(String filename, int totalSize) throws Exception {
        // Initialize the file system manager with a file
        if(instance == null) {
            // Initialize the file system
            File diskFile = new File(filename);
            this.disk = new RandomAccessFile(diskFile, "rw");
            this.disk.setLength(totalSize);
            
            // Initialize data structures
            this.inodeTable = new FEntry[MAXFILES];
            this.freeBlockList = new boolean[MAXBLOCKS];
            this.blockNodes = new FNode[MAXBLOCKS];
            
            // Initialize free block list (all blocks are free initially)
            for(int i = 0; i < MAXBLOCKS; i++) {
                freeBlockList[i] = true;
                blockNodes[i] = new FNode(i);
            }
            
            instance = this;
        } else {
            throw new IllegalStateException("FileSystemManager is already initialized");
        }
    }



    public void createFile(String fileName) throws Exception {
        globalLock.lock();
        try {
            // Validate filename length
            if (fileName.length() > 11) {
                throw new Exception("Filename too long. Maximum 11 characters allowed.");
            }
            

            // Check if file already exists
            for (int i = 0; i < MAXFILES; i++) {
                if (inodeTable[i] != null && inodeTable[i].getFilename().equals(fileName)) {
                    throw new Exception("File already exists: " + fileName);
                }
            }
            
            // Find free inode
            int freeInodeIndex = -1;
            for (int i = 0; i < MAXFILES; i++) {
                if (inodeTable[i] == null) {
                    freeInodeIndex = i;
                    break;
                }
            }
            
            if (freeInodeIndex == -1) {
                throw new Exception("No free inodes available. Maximum files: " + MAXFILES);
            }
            
            // Find free block for the file (initially empty, so we'll allocate when needed)
            // Create the file entry with no blocks allocated yet
            inodeTable[freeInodeIndex] = new FEntry(fileName, (short) 0, (short) -1);
            

        } finally {
            globalLock.unlock();
        }
    }



    public byte[] readFile(String fileName) throws Exception {
        globalLock.lock();
        try {
            // Finding the file
            FEntry fileEntry = findFile(fileName);
            if (fileEntry == null) {

                throw new Exception("file is not found:" + fileName);
            }
            
            if (fileEntry.getFilesize() == 0) {
                return new byte[0];
            }
            
            byte[] data = new byte[fileEntry.getFilesize()];
            int bytesRead = 0;
            short currentBlock = fileEntry.getFirstBlock();
            
            while (currentBlock != -1 && bytesRead < fileEntry.getFilesize()) {

                // Read from current block

                disk.seek(currentBlock * BLOCK_SIZE);
                int bytesToRead = Math.min(BLOCK_SIZE, fileEntry.getFilesize() - bytesRead);
                disk.read(data, bytesRead, bytesToRead);
                bytesRead += bytesToRead;
                
                // Move to next block

                if (blockNodes[currentBlock].hasNext()) {

                    currentBlock = (short) blockNodes[currentBlock].getNext();
                } else {
                    break;
                }
            }
            
            return data;
        } finally {
            globalLock.unlock();
        }
    }




    public void writeFile(String fileName, byte[] data) throws Exception {
        globalLock.lock();
        try {
            // Find the file
            FEntry fileEntry = findFile(fileName);
            if (fileEntry == null) {
                throw new Exception("File does not exist: " + fileName);
            }
            
            // Calculating the blocks needed
            int blocksNeeded = (data.length + BLOCK_SIZE - 1) / BLOCK_SIZE;
            
            // Check if we have enough free blocks (excluding current file blocks)

            int availableBlocks = 0;
            for (int i = 0; i < MAXBLOCKS; i++) {
                if (freeBlockList[i]) {
                    availableBlocks++;
                }
            }
            
            // Add current file's blocks to available count

            if (fileEntry.getFirstBlock() != -1) {
                availableBlocks += countFileBlocks(fileEntry.getFirstBlock());
            }
            
            if (blocksNeeded > availableBlocks) {
                throw new Exception("Not enough free blocks available");
            }
            
            // Store old block chain for rollback

            short oldFirstBlock = fileEntry.getFirstBlock();
            short oldFileSize = fileEntry.getFilesize();
            
            // Deallocate existing blocks if any

            if (fileEntry.getFirstBlock() != -1) {
                deallocateBlocks(fileEntry.getFirstBlock());
            }
            
            if (blocksNeeded > 0) {
                // Allocate blocks
                List<Integer> allocatedBlocks = allocateBlocks(blocksNeeded);
                if (allocatedBlocks.size() < blocksNeeded) {
                    // Rollback: restore old blocks
                    fileEntry.setFirstBlock(oldFirstBlock);
                    fileEntry.setFilesize(oldFileSize);
                    throw new Exception("Not enough free blocks available");
                }
                
                // Setting up the block chain
                fileEntry.setFirstBlock((short) (int) allocatedBlocks.get(0));
                for (int i = 0; i < allocatedBlocks.size() - 1; i++) {

                    blockNodes[allocatedBlocks.get(i)].setNext(allocatedBlocks.get(i + 1));

                }
                blockNodes[allocatedBlocks.get(allocatedBlocks.size() - 1)].setNext(-1);
                
                // Writing data to blocks

                int bytesWritten = 0;
                for (int blockIndex : allocatedBlocks) {
                    disk.seek(blockIndex * BLOCK_SIZE);
                    int bytesToWrite = Math.min(BLOCK_SIZE, data.length - bytesWritten);
                    disk.write(data, bytesWritten, bytesToWrite);

                    bytesWritten += bytesToWrite;
                }
            } else {
                fileEntry.setFirstBlock((short) -1);
            }
            
            // Update file size

            fileEntry.setFilesize((short) data.length);
            
        } finally {
            globalLock.unlock();
        }
    }

    public void deleteFile(String fileName) throws Exception {
        globalLock.lock();
        try {
            // Find the file
            int fileIndex = -1;
            for (int i = 0; i < MAXFILES; i++) {
                if (inodeTable[i] != null && inodeTable[i].getFilename().equals(fileName)) {
                    fileIndex = i;
                    break;
                }
            }
            
            if (fileIndex == -1) {
                throw new Exception("File does not exist: " + fileName);
            }
            
            FEntry fileEntry = inodeTable[fileIndex];
            
            // Deallocate blocks
            if (fileEntry.getFirstBlock() != -1) {
                deallocateBlocks(fileEntry.getFirstBlock());
            }
            
            // Remove from inode table
            inodeTable[fileIndex] = null;
            
        } finally {
            globalLock.unlock();
        }
    }



    public String[] listFiles() throws Exception {
        globalLock.lock();
        try {
            List<String> fileNames = new ArrayList<>();
            for (int i = 0; i < MAXFILES; i++) {
                if (inodeTable[i] != null) {
                    fileNames.add(inodeTable[i].getFilename());

                }
            }
            return fileNames.toArray(new String[0]);

        } finally {
            globalLock.unlock();
        }
    }

    private FEntry findFile(String fileName) {


        for (int i = 0; i < MAXFILES; i++) {
            
            if (inodeTable[i] != null && inodeTable[i].getFilename().equals(fileName)) {
                return inodeTable[i];
            }
        }
        return null;
    }

    private List<Integer> allocateBlocks(int count) {
        List<Integer> allocated = new ArrayList<>();
        for (int i = 0; i < MAXBLOCKS && allocated.size() < count; i++) {
            if (freeBlockList[i]) {
                freeBlockList[i] = false;
                allocated.add(i);
            }
        }
        return allocated;
    }

    private void deallocateBlocks(int firstBlock) {
        int currentBlock = firstBlock;
        byte[] zeros = new byte[BLOCK_SIZE];
        
        while (currentBlock != -1) {
            try {
            
                disk.seek(currentBlock * BLOCK_SIZE);
                disk.write(zeros);
            } catch (Exception e) {

                // Continue deallocating even if overwrite fails
            }
            
            freeBlockList[currentBlock] = true;
            int nextBlock = blockNodes[currentBlock].getNext();
            blockNodes[currentBlock].setNext(-1);
            currentBlock = nextBlock;
        }
    }


    private int countFileBlocks(int firstBlock) {
        int count = 0;
        int currentBlock = firstBlock;
        while (currentBlock != -1) {
            count++;
            currentBlock = blockNodes[currentBlock].getNext();
        }

        return count;
    }
}
