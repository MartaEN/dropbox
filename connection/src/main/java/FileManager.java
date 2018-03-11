import java.io.File;

public interface FileManager {

    void uploadFile (Session session, File file);
    void downloadFile (Session session, String name);
//    void downloadDirectory (Session session, String name);
    void saveFile (File file);
    void renameFile (Session session, String oldName, String newName);
    void deleteFile (Session session, String name);
    void createDirectory (Session session, String name);
    void directoryUp (Session session);
    void directoryDown (Session session, String name);
    void listFiles (Session session, MyFileList list);

}
