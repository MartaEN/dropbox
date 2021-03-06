package com.marta.sandbox.file_helper;

import java.io.*;
import java.nio.file.*;

public class FileProcessor {

    private FileProcessor () {}

    public static void saveFile(Path destinationDirectory, File file) throws FileProcessorException {
        String newName = file.getName();
        while(Files.exists(destinationDirectory.resolve(newName))) {
            newName = "копия-"+newName;
        }
        try {
            Files.copy(file.toPath(), destinationDirectory.resolve(newName.toString()));
        } catch (IOException e) {
            throw new FileProcessorException(e);
        }
    }

    public static void rename (Path oldFile, String newFile) throws FileProcessorException {
        try {
            Files.move(oldFile, oldFile.resolveSibling(newFile));
        } catch (IOException e) {
            throw new FileProcessorException(e);
        }
    }

    public static void delete (Path path) throws FileProcessorException {
        try {
            Files.delete(path);
        } catch (IOException e) {
            throw new FileProcessorException(e);
        }
    }

    public static void createDirectory (Path path) throws FileProcessorException {
        try {
            Files.createDirectory(path);
        } catch (IOException e) {
            throw new FileProcessorException(e);
        }
    }

    public static boolean fileExists (Path path) {
        return Files.exists(path, LinkOption.NOFOLLOW_LINKS);
    }
}
