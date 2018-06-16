package com.marta.sandbox.dropbox.server_basic;

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
import com.marta.sandbox.dropbox.common.settings.ServerConstants;

public class Server implements ServerConstants {

    private final Path ROOT = Paths.get("_server_repository");
    private final String USER_DATABASE_NAME = "users.db";

    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private AuthService authService;

    ExecutorService getThreadPool() { return threadPool; }
    AuthService getAuthService() { return authService; }
    Path getServerDirectory () { return  ROOT; }

    public static void main(String[] args) {
        new Server();
    }

    Server() {

        // Для хранения файлов на сервере выделяем директорию.
        // В ней будет лежать файл базы данных пользователей для сервиса авторизации
        // и файлы пользователей, разложенные по папкам с именами логинов.
        // Шаг 1: проверяем наличие папки на сети, при отсутствии - создаем.
        if(Files.exists(ROOT, LinkOption.NOFOLLOW_LINKS)) {
            Logger.getGlobal().info("ROOT DIRECTORY " + ROOT);
        } else {
            try {
                Files.createDirectory(ROOT);
                Logger.getGlobal().warning("ATTENTION! --- NEW ROOT DIRECTORY CREATED " + ROOT);
            } catch (IOException e) {
                Logger.getGlobal().severe("FATAL ERROR! FAILED TO CREATE ROOT DIRECTORY. \nSERVER CLOSED");
                return;
            }
        }

        // Шаг 2: Запускаем службу авторизации
        try {
            authService = new SqliteAuthService(ROOT.resolve(USER_DATABASE_NAME).toAbsolutePath().toString());
            authService.start();
            Logger.getGlobal().info("AUTHORISATION SERVICE STARTED");
        } catch (AuthServiceException e) {
            Logger.getGlobal().severe("FATAL ERROR! FAILED TO START AUTHORISATION SERVICE. \nSERVER CLOSED");
            return;
        }

        // Шаг 3: Открываем пул потоков и запускаем сервер
        try {
            threadPool = Executors.newCachedThreadPool();
            serverSocket = new ServerSocket(PORT);
            Logger.getGlobal().info("SERVER STARTED");
            while (true) {
                Socket socket = serverSocket.accept();
                new ClientHandler(this, socket);
            }
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