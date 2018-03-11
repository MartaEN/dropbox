import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {

    private final int PORT = 2018;
    private final Path ROOT = Paths.get("_server_repository");
    private final String USERS_DB_NAME = "users.db";

    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private AuthService authService;

    public ExecutorService getThreadPool() { return threadPool; }
    public AuthService getAuthService() { return authService; }

    Server() {

        try {
            FileProcessor.createDirectoryIfNotExists(ROOT);
            authService = new AuthService(ROOT.resolve(USERS_DB_NAME).toString());
            threadPool = Executors.newCachedThreadPool();
            serverSocket = new ServerSocket(PORT);
            log("SERVER STARTED");

            while (true) {
                Socket socket = serverSocket.accept();
                new ClientHandler (this, socket, ROOT);
            }

        } catch (FileProcessorException e) {
            log("ERROR INITIALIZING ROOT DIRECTORY: " + e.getMessage());
        } catch (IOException e) {
            log("ERROR CONNECTING TO SOCKET: " + e.getMessage());
        } catch (AuthServiceException e) {
            log(e.getMessage());
        } finally {
            if(authService != null) authService.stop();
            if(threadPool != null) threadPool.shutdown();
            try {
                if (serverSocket!= null) serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            log("SERVER STOPPED");
        }
    }

    private void log (String message) {
        System.out.println(time()+ " " + message);
    }

    void log (Session session, String message) {
        System.out.println(time()+ " " + session.toString() +": " + message);
    }

    private String time () {
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
        return (sdf.format(new Date(System.currentTimeMillis()))+" ");
    }
}