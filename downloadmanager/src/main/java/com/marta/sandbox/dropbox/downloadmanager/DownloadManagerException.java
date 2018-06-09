package com.marta.sandbox.dropbox.downloadmanager;

import java.io.IOException;

public class DownloadManagerException extends IOException{

    DownloadManagerException(IOException e) {
        super(e);
    }

    DownloadManagerException (String message) {
        super(message);
    }
}
