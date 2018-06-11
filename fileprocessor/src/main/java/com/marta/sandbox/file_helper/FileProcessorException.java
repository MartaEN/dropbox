package com.marta.sandbox.file_helper;

import java.io.IOException;

public class FileProcessorException extends IOException {

    public FileProcessorException(IOException e) {
        super(e);
    }

    public FileProcessorException (String message) {
        super(message);
    }
}
