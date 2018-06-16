package com.marta.sandbox.dropbox.common.messaging;

import java.io.Serializable;
import java.nio.file.Path;

public class MyFile implements Serializable {

    private FileType type;
    private String name;
    private int size;

    public enum FileType {
        FILE (" "), DIR (">");

        String value;

        FileType(String value) {
            this.value = value;
        }
    }

    public MyFile (Path path) {
        if (path.toFile().exists()) {
            this.type = path.toFile().isDirectory() ? FileType.DIR : FileType.FILE;
            this.name = path.getFileName().toString();
            this.size = (int) (path.toFile().length() / 1024);
        }
    }

    public FileType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public int getSize() {
        return size;
    }
}
