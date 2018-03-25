import org.json.simple.JSONObject;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

public class ClientHandler implements ConnectionListener, FileManager, SignInChecker, SignUpChecker {

    private final Server server;
    private final Session session;
    private final Path SERVER_ROOT;
    private Path activeDirectory;

    ClientHandler (Server server, Socket socket, Path root) {
        this.server = server;
        this.SERVER_ROOT = root;
        this.activeDirectory = root;
        this.session = new Session(this, socket, Session.SessionType.SERVER);
        server.getThreadPool().execute(session);
    }

    @Override
    public void onConnect(Session session) {
        server.log(session, "CONNECTED");
    }

    @Override
    public void onInput(Session session, Object input) {

        if (input instanceof JSONObject) {

            JSONObject json = (JSONObject)input;
            Commands cmd = (Commands)json.get(Commands.MESSAGE);

            switch (cmd) {
                case CHECK_NEW_USER_NAME:
                    checkNewUserName((String)json.get(Commands.USERNAME));
                    break;
                case CREATE_DIRECTORY:
                    createDirectory((String)json.get(Commands.DIRECTORY_NAME));
                    break;
                case DELETE:
                    delete((String)json.get(Commands.FILE_NAME));
                    break;
                case DIRECTORY_DOWN:
                    directoryDown((String)json.get(Commands.DIRECTORY_NAME));
                    break;
                case DIRECTORY_UP:
                    directoryUp();
                    break;
                case DOWNLOAD:
                    downloadFile((String)json.get(Commands.FILE_NAME));
                    break;
                case FILE:
                    appendFile((String)json.get(Commands.FILE),(long)json.get(Commands.SIZE), (long)json.get(Commands.BYTES),
                            (byte[])json.get(Commands.DATA));
                    break;
                case LIST_CONTENTS:
                    listFiles();
                    break;
                case RENAME:
                    rename((String)json.get(Commands.FILE_NAME), (String)json.get(Commands.NEW_FILE_NAME));
                    break;
                case SIGN_IN:
                    signIn((String)json.get(Commands.USERNAME), (String)json.get(Commands.PASSWORD));
                    break;
                case SIGN_UP:
                    signUp((String)json.get(Commands.USERNAME), (String)json.get(Commands.PASSWORD));
                    break;
                case TEST:
                    sendOK();
                    break;
                default:
                    server.log(session, "Server - THIS SHOULD NOT HAPPEN - UNKNOWN COMMAND FROM USER");
            }

        } else if (input instanceof File){
            uploadFile((File)input);

        } else {
            server.log(session, "Server - THIS SHOULD NOT HAPPEN - UNKNOWN TYPE OF INCOMING MESSAGE");
        }
    }

    private void appendFile(String name, long size, long read, byte[] data) {
        System.out.println("Uploading "+name +": declared size "+size+", read so far: "+ read);
        Path path = activeDirectory.resolve(name);
        File file = new File(path.toString());
        file.setReadable(false);
        try (OutputStream output = new BufferedOutputStream(new FileOutputStream(file, true), data.length)) {
                output.write(data);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if(read == size) file.setReadable(true);
    }

    @Override
    public void onDisconnect(Session session) {
    }

    @Override
    public void onException(Session session, Exception e) {
        server.log(session, "DISCONNECTED");
    }

    @Override
    public void signIn(String user, String password) {
        JSONObject message = new JSONObject();
        if( server.getAuthService().loginAccepted(user,password)) {
            activeDirectory = activeDirectory.resolve(user);
            if (FileProcessor.fileExists(activeDirectory)) {
                message.put(Commands.MESSAGE, Commands.ADMITTED);
            } else {
                message.put(Commands.MESSAGE, Commands.FAIL);
                message.put(Commands.FAIL_DETAILS, "Ошибка подключения к удаленному репозиторию");
                server.log(session,"FAIL - USER DIRECTORY IS MISSING - " +
                        "USER IS AUTHORIZED BUT CANNOT ACCESS HIS REPOSITORY !!!");
            }
        } else {
            message.put(Commands.MESSAGE, Commands.NOT_ADMITTED);
        }
        send(message);
        //TODO почему-то, если не отослать еще одно сообщение, ближайшее следующее считается окном авторизации, а не главным
        sendOK();
    }

    @Override
    public void signUp(String user, String password) {
        JSONObject message = new JSONObject();
        if (server.getAuthService().registerNewUser(user, password)) {
            activeDirectory = SERVER_ROOT.resolve(user);
            try {
                FileProcessor.createDirectory(activeDirectory);
                message.put(Commands.MESSAGE, Commands.ADMITTED);
            } catch (FileProcessorException e) {
                message.put(Commands.MESSAGE, Commands.FAIL);
                message.put(Commands.FAIL_DETAILS, "Ошибка соединения. Повторите попытку позднее");
                server.log(session,"FAIL - CANT CREATE USER DIRECTORY FOR USERNAME [" + user + "] !!!");
            }
        } else {
            message.put(Commands.MESSAGE, Commands.NOT_ADMITTED);
        }
        send(message);
        //TODO почему-то, если не отослать еще одно сообщение, ближайшее следующее считается окном регистрации, а не главным
        sendOK();
    }

    @Override
    public void checkNewUserName(String name) {
        JSONObject message = new JSONObject();
        if (server.getAuthService().newUserNameAccepted(name)) {
            message.put(Commands.MESSAGE, Commands.USERNAME_OK);
        } else {
            message.put(Commands.MESSAGE, Commands.FAIL);
            message.put(Commands.FAIL_DETAILS, "Пользователь с таким именем уже есть");
        }
        send(message);
    }

    @Override
    public void uploadFile(File input) {
        try {
            FileProcessor.saveFile(activeDirectory, input);
        } catch (FileProcessorException e) {
            JSONObject message = new JSONObject();
            message.put(Commands.MESSAGE, Commands.FAIL);
            message.put(Commands.FAIL_DETAILS, e.getMessage());
            send(message);
        }
        listFiles();
    }

    @Override
    public void saveFile (File file) {
        uploadFile(file);
    }

    @Override
    public void downloadFile(String fileName) {
        File file = new File (activeDirectory.resolve(fileName).toString());
        if (file.exists()) {
            if (file.isFile()) session.sendFile(file);
        } else {
            JSONObject message = new JSONObject();
            message.put(Commands.MESSAGE, Commands.FAIL);
            message.put(Commands.FAIL_DETAILS, "Файл не найден");
            send(message);
        }
    }

    @Override
    public void rename (String oldName, String newName) {
        try {
            FileProcessor.rename(activeDirectory.resolve(oldName), newName);
        } catch (FileProcessorException e){
            JSONObject message = new JSONObject();
            message.put(Commands.MESSAGE, Commands.FAIL);
            message.put(Commands.FAIL_DETAILS, e.getMessage());
            send(message);
        }
        listFiles();
    }

    @Override
    public void delete(String name) {
        try {
            FileProcessor.delete(activeDirectory.resolve(name));
        } catch (FileProcessorException e) {
            JSONObject message = new JSONObject();
            message.put(Commands.MESSAGE, Commands.FAIL);
            message.put(Commands.FAIL_DETAILS, e.getMessage());
            send(message);
        }
        listFiles();
    }

    @Override
    public void createDirectory (String name) {
        try {
            FileProcessor.createDirectory(activeDirectory.resolve(name));
        } catch (FileProcessorException e) {
            JSONObject message = new JSONObject();
            message.put(Commands.MESSAGE, Commands.FAIL);
            message.put(Commands.FAIL_DETAILS, e.getMessage());
            send(message);
        }
        listFiles();
    }

    @Override
    public void directoryUp() {
        if (!activeDirectory.getParent().equals(SERVER_ROOT)) {
            activeDirectory = activeDirectory.getParent();
        }
        listFiles();
    }

    @Override
    public void directoryDown(String directoryName) {

        Path target = activeDirectory.resolve(directoryName);

        if(Files.exists(target) && Files.isDirectory(target)) {
            activeDirectory = target;
        } else {
            JSONObject message = new JSONObject();
            message.put(Commands.MESSAGE, Commands.FAIL);
            message.put(Commands.FAIL_DETAILS, "Путь не найден");
            send(message);
        }
        listFiles();
    }

    private void listFiles () {

        ArrayList<MyFile> fileList = new ArrayList<>();

        for (File f: new File(activeDirectory.toString()).listFiles()) {
            fileList.add(new MyFile(f.isFile()? MyFile.FileType.FILE: MyFile.FileType.DIR,
                    f.getName(), (int)f.length()/1024));
        }
        session.send(new MyFileList(fileList));
    }

    @Override
    public void listFiles(MyFileList list) { listFiles(); }

    private void sendOK () {
        JSONObject message = new JSONObject();
        message.put(Commands.MESSAGE, Commands.OK);
        session.send(message);
    }

    private <T> void send (T message) {
        session.send(message);
    }

}
