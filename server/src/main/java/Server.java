import com.marta.sandbox.authentication.AuthService;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import com.marta.sandbox.authentication.*;
import com.marta.sandbox.authentication.exceptions.*;

public class Server {

    private final int PORT = 2018;
    private final Path ROOT = Paths.get("_server_repository");
    private final String USERS_DB_NAME = "users.db";

    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private AuthService authService;

    public ExecutorService getThreadPool() { return threadPool; }
    public AuthService getAuthService() { return authService; }

    public static void main(String[] args) {
        new Server();
    }

    Server() {

        try {
            // check root directory
            if(FileProcessor.fileExists(ROOT)) {
                Logger.getGlobal().info("ROOT DIRECTORY " + ROOT);
            } else {
                FileProcessor.createDirectory(ROOT);
                Logger.getGlobal().warning("ATTENTION! --- NEW ROOT DIRECTORY CREATED " + ROOT);
            }
            // start authentication service
            authService = new SqliteAuthService(ROOT.resolve("users").toAbsolutePath().toString());
            authService.start();
            Logger.getGlobal().info("AUTHORISATION SERVICE STARTED");
            // open thread pool
            threadPool = Executors.newCachedThreadPool();
            // open server socket
            serverSocket = new ServerSocket(PORT);
            // ready
            Logger.getGlobal().info("SERVER STARTED");

            while (true) {
                Socket socket = serverSocket.accept();
                new ClientHandler (this, socket, ROOT);
            }

        } catch (FileProcessorException e) {
            Logger.getGlobal().severe("ERROR INITIALIZING ROOT DIRECTORY: " + e.getMessage());
        } catch (AuthServiceException e) {
            Logger.getGlobal().severe("FATAL ERROR! FAILED TO START AUTHORISATION SERVICE. \nSERVER CLOSED");
        } catch (IOException e) {
            Logger.getGlobal().severe("ERROR CONNECTING TO SOCKET: " + e.getMessage());
        } finally {
            if(authService != null) authService.stop();
            if(threadPool != null) threadPool.shutdown();
            try {
                if (serverSocket!= null) serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            Logger.getGlobal().info("SERVER STOPPED");
        }
    }
}