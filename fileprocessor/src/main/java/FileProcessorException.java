import java.io.IOException;

class FileProcessorException extends IOException {

    FileProcessorException(IOException e) {
        super(e);
    }

    FileProcessorException (String message) {
        super(message);
    }
}
