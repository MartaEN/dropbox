package com.marta.sandbox.dropbox.common.api;

import com.marta.sandbox.dropbox.common.messaging.MyFileList;

import java.io.File;

public interface FileManager {

    void uploadFile (File file);
    void downloadFile (String name);
//    void downloadDirectory (com.marta.sandbox.dropbox.common.session.Session session, String name);
    void saveFile (File file, int sequence, long bytesLeft, byte [] data);
    void rename(String oldName, String newName);
    void delete(String name);
    void createDirectory (String name);
    void directoryUp ();
    void directoryDown (String name);
    void listFiles (MyFileList list);

}
