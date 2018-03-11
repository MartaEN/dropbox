import java.io.File;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

public class ClientHandler implements ConnectionListener {

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
        server.log(session, "CONNECTION ATTEMPT");
    }

    @Override
    public void onInput(Session session, Object input) {

        if(input instanceof String) {

            System.out.println("Server - "+ session + ": incoming message identified as String: " + input);

            String[] tokens = ((String)input).split(" ");

            switch (tokens[0]) {

                case "/delete":
                    delete(tokens[1]);
                    break;
                case "/dirDown":
                    directoryDown(tokens[1]);
                    break;
                case "/dirUp":
                    directoryUp();
                    break;
                case "/download":
                    downloadFile(tokens[1]);
                    break;
                case "/list":
                    listFiles();
                    break;
                case "/newDir":
                    createDirectory(tokens[1]);
                    break;
                case "/newUser":
                    checkNewUserName(tokens[1]);
                    break;
                case "/rename":
                    rename(tokens[1], tokens[2]);
                    break;
                case "/signIn":
                    signIn(tokens[1], tokens[2]);
                    break;
                case "/signUp":
                    signUp(tokens[1], tokens[2]);
                    break;
                case "/test":
                    send("/ok");
                    break;
                default:
                    server.log(session, "Server - THIS SHOULD NOT HAPPEN - UNKNOWN COMMAND FROM USER");

            }

        } else if (input instanceof File){

            System.out.println("Server - "+ session + ": incoming message identified as File: " + input);
            uploadFile((File)input);

        } else {
            server.log(session, "Server - THIS SHOULD NOT HAPPEN - UNKNOWN TYPE OF INCOMING MESSAGE");
        }
    }

    @Override
    public void onDisconnect(Session session) {
    }

    @Override
    public void onException(Session session, Exception e) {
        server.log(session, "DISCONNECTED");
    }

    private void signIn(String user, String password) {
        if( server.getAuthService().loginAccepted(user,password)) {
            activeDirectory = activeDirectory.resolve(user);
            if (FileProcessor.fileExists(activeDirectory)) {
                send("/welcome");
                listFiles();
            } else {
                send("/fail ProblemConnectingToRepository");
                server.log(session,"ERROR - USER DIRECTORY IS MISSING - USER IS AUTHORIZED BUT CANNOT ACCESS HIS REPOSITORY !!!");
            }
        } else {
            send("/login_rejected");
        }
    }

    private void signUp(String user, String password) {
        if (server.getAuthService().registerNewUser(user, password)) {
            activeDirectory = SERVER_ROOT.resolve(user);
            try {
                FileProcessor.createDirectory(activeDirectory);
                send("/welcome");
                listFiles();
            } catch (FileProcessorException e) {
                send("/fail");
            }
        } else send("/login_rejected");
    }

    private void checkNewUserName(String name) {
        if (server.getAuthService().newUserNameAccepted(name))
            send("/username_ok");
        else send ("/username_rejected");
    }

    private void uploadFile(File input) {
        try {
            FileProcessor.saveFile(activeDirectory, input);
        } catch (FileProcessorException e) {
            send("/fail " + e.getMessage());
        }
        listFiles();
    }

    private void downloadFile(String fileName) {
        File file = new File (activeDirectory.resolve(fileName).toString());
        if (file.exists()) {
            if (file.isFile()) session.send(file);
        } else {
            send("/notfound");
        }
    }

    private void rename (String oldName, String newName) {
        try {
            FileProcessor.rename(activeDirectory.resolve(oldName), newName);
        } catch (FileProcessorException e){
            send("/fail " + e.getMessage());
        }
        listFiles();
    }

    private void delete(String name) {
        try {
            FileProcessor.delete(activeDirectory.resolve(name));
        } catch (FileProcessorException e) {
            send("/fail " + e.getMessage());
        }
        listFiles();
    }

    private void createDirectory (String name) {
        try {
            FileProcessor.createDirectory(activeDirectory.resolve(name));
        } catch (FileProcessorException e) {
            send("/fail " + e.getMessage());
        }
        listFiles();
    }

    private void directoryUp() {
        if (!activeDirectory.getParent().equals(SERVER_ROOT)) {
            activeDirectory = activeDirectory.getParent();
        }
        listFiles();
    }


    private void directoryDown(String directoryName) {

        Path target = activeDirectory.resolve(directoryName);

        if(Files.exists(target) && Files.isDirectory(target)) {
            activeDirectory = target;
            listFiles();
        } else {
            send("/notfound");
        }
    }

    private void listFiles() {

        ArrayList<MyFile> fileList = new ArrayList<>();

        for (File f: new File(activeDirectory.toString()).listFiles()) {
            fileList.add(new MyFile(f.isFile()? MyFile.FileType.FILE: MyFile.FileType.DIR,
                    f.getName(), (int)f.length()/1000));
        }
        session.send(new MyFileList(fileList));
    }


    private <T> void send (T message) {
        session.send(message);
    }

}
