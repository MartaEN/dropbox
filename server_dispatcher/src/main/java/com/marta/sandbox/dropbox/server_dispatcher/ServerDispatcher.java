package com.marta.sandbox.dropbox.server_dispatcher;

import com.marta.sandbox.authentication.AuthService;
import com.marta.sandbox.authentication.exceptions.*;
import com.marta.sandbox.dropbox.common.messaging.Commands;
import com.marta.sandbox.dropbox.common.messaging.MyFile;
import com.marta.sandbox.dropbox.common.messaging.MyFileList;
import com.marta.sandbox.dropbox.common.session.Sender;
import com.marta.sandbox.dropbox.downloadmanager.DownloadManager;
import com.marta.sandbox.dropbox.downloadmanager.DownloadManagerException;
import com.marta.sandbox.file_helper.FileProcessor;
import com.marta.sandbox.file_helper.FileProcessorException;
import org.json.simple.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ServerDispatcher {

    private final AuthService AUTH_SERVICE;
    private final Path SERVER_DIRECTORY; // нужна, чтобы знать, куда пользователя уже не пускать
    private Path currentDirectory; // нужна, чтобы знать, в какой серверной папке работает пользователь
    private String login; // заведено про запас, пока не используется
    private boolean isAuth; // заведено про запас, пока не используется

    public ServerDispatcher (Path serverDirectory, AuthService authService) {
        this.AUTH_SERVICE = authService;
        this.SERVER_DIRECTORY = serverDirectory;
        this.currentDirectory = serverDirectory;
    }

    public void processMessageFromClient (Sender sender, Object input) {

        if (input instanceof JSONObject) {

            JSONObject json = (JSONObject)input;
            Commands cmd = (Commands)json.get(Commands.MESSAGE);

            switch (cmd) {
                case CHECK_NEW_USER_NAME:
                    checkNewUserName(sender, (String)json.get(Commands.USERNAME));
                    break;
                case CREATE_DIRECTORY:
                    createDirectory(sender, (String)json.get(Commands.DIRECTORY_NAME));
                    break;
                case DELETE:
                    delete(sender, (String)json.get(Commands.FILE_NAME));
                    break;
                case DIRECTORY_DOWN:
                    directoryDown(sender, (String)json.get(Commands.DIRECTORY_NAME));
                    break;
                case DIRECTORY_UP:
                    directoryUp(sender);
                    break;
                case DOWNLOAD:
                    downloadFile(sender, (String)json.get(Commands.FILE_NAME));
                    break;
                case FILE:
                    saveFile(sender,
                            (File)json.get(Commands.FILE),
                            (int)json.get(Commands.SEQUENCE),
                            (long)json.get(Commands.BYTES_LEFT),
                            (byte[])json.get(Commands.DATA));
                    break;
                case LIST_CONTENTS:
                    listFiles(sender);
                    break;
                case RENAME:
                    rename(sender, (String)json.get(Commands.FILE_NAME), (String)json.get(Commands.NEW_FILE_NAME));
                    break;
                case SIGN_IN:
                    signIn(sender, (String)json.get(Commands.USERNAME), (String)json.get(Commands.PASSWORD));
                    break;
                case SIGN_UP:
                    signUp(sender, (String)json.get(Commands.USERNAME), (String)json.get(Commands.PASSWORD));
                    break;
                case TEST:
                    sendOK(sender);
                    break;
                default:
                    Logger.getGlobal().warning("THIS SHOULD NOT HAPPEN - UNKNOWN COMMAND FROM USER");
            }

        } else {
            Logger.getGlobal().warning("THIS SHOULD NOT HAPPEN - UNKNOWN TYPE OF INCOMING MESSAGE");
        }
    }

    private void signIn(Sender sender, String user, String password) {
        JSONObject message = new JSONObject();
        if( AUTH_SERVICE.isLoginAccepted(user,password)) {
            currentDirectory = currentDirectory.resolve(user);
            if (FileProcessor.fileExists(currentDirectory)) {
                this.login = user;
                this.isAuth = true;
                message.put(Commands.MESSAGE, Commands.ADMITTED);
            } else {
                message.put(Commands.MESSAGE, Commands.FAIL);
                message.put(Commands.FAIL_DETAILS, "Ошибка подключения к удаленному репозиторию");
                Logger.getGlobal().severe("FAIL - USER DIRECTORY IS MISSING - " +
                        "USER [\" + user + \"] IS AUTHORIZED BUT CANNOT ACCESS HIS REPOSITORY !!!");
            }
        } else {
            message.put(Commands.MESSAGE, Commands.NOT_ADMITTED);
        }
        sender.send(message);
    }

    private void signUp(Sender sender, String user, String password) {
        JSONObject message = new JSONObject();
        try {
            AUTH_SERVICE.registerNewUser(user, password);
            currentDirectory = SERVER_DIRECTORY.resolve(user);
            FileProcessor.createDirectory(currentDirectory);
            this.login = user;
            this.isAuth = true;
            message.put(Commands.MESSAGE, Commands.ADMITTED);
        } catch (UserAlreadyExistsException e) {
            message.put(Commands.MESSAGE, Commands.FAIL);
            message.put(Commands.FAIL_DETAILS, "Пользователь с таким именем уже существует");
        } catch (DatabaseConnectionException e) {
            message.put(Commands.MESSAGE, Commands.FAIL);
            message.put(Commands.FAIL_DETAILS, "Ошибка соединения. Повторите попытку позднее");
        } catch (FileProcessorException e) {
            message.put(Commands.MESSAGE, Commands.FAIL);
            message.put(Commands.FAIL_DETAILS, "Ошибка соединения. Повторите попытку позднее");
            Logger.getGlobal().severe("FAIL - CANT CREATE USER DIRECTORY FOR USERNAME [" + user + "] !!!");
        }
        sender.send(message);
    }

    private void checkNewUserName(Sender sender, String name) {
        JSONObject message = new JSONObject();
        if (AUTH_SERVICE.isUserNameVacant(name)) {
            message.put(Commands.MESSAGE, Commands.USERNAME_OK);
        } else {
            message.put(Commands.MESSAGE, Commands.FAIL);
            message.put(Commands.FAIL_DETAILS, "Пользователь с таким именем уже есть");
        }
        sender.send(message);
    }

    private void saveFile(Sender sender, File file, int sequence, long bytesLeft, byte[] data) {

        System.out.println("Uploading "+file.getName() +": package no "+sequence+", bytes left: "+ bytesLeft);

        try {
            if(sequence == 1) DownloadManager.getInstance().enlistDownload(currentDirectory, file);
            DownloadManager.getInstance().download(file, sequence, data);
        } catch (DownloadManagerException e) {
            sendErrorMessage(sender, e.getMessage());
            return;
        }
        listFiles(sender);
    }

    private void downloadFile(Sender sender, String fileName) {
        File file = new File (currentDirectory.resolve(fileName).toString());
        if (file.exists()) {
            if (file.isFile()) sender.sendFileInChunks(file);
        } else {
            sendErrorMessage(sender, "File not found");
        }
    }

    private void rename (Sender sender, String oldName, String newName) {
        try {
            FileProcessor.rename(currentDirectory.resolve(oldName), newName);
        } catch (FileProcessorException e){
            sendErrorMessage(sender, e.getMessage());
        }
        listFiles(sender);
    }

    private void delete(Sender sender, String name) {
        try {
            FileProcessor.delete(currentDirectory.resolve(name));
        } catch (FileProcessorException e) {
            sendErrorMessage(sender, e.getMessage());
        }
        listFiles(sender);
    }

    private void createDirectory (Sender sender, String name) {
        try {
            FileProcessor.createDirectory(currentDirectory.resolve(name));
        } catch (FileProcessorException e) {
            sendErrorMessage(sender, e.getMessage());
        }
        listFiles(sender);
    }

    private void directoryUp(Sender sender) {
        if (!currentDirectory.getParent().equals(SERVER_DIRECTORY)) {
            currentDirectory = currentDirectory.getParent();
        }
        listFiles(sender);
    }

    private void directoryDown(Sender sender, String directoryName) {

        Path target = currentDirectory.resolve(directoryName);

        if(Files.exists(target) && Files.isDirectory(target)) {
            currentDirectory = target;
        } else {
            sendErrorMessage(sender,"Directory not found");
        }
        listFiles(sender);
    }

    private void listFiles (Sender sender) {

        List<MyFile> fileList = null;
        try {
            fileList = Files.list(currentDirectory).map(MyFile::new).collect(Collectors.toList());
        } catch ( IOException e) {
            //exception ignored - null list to be returned in case of exception
        }
        JSONObject message = new JSONObject();
        message.put(Commands.MESSAGE, Commands.FILE_LIST);
        message.put(Commands.FILE_LIST, new MyFileList(fileList));
        sender.send(message);
    }

    private void sendOK (Sender sender) {
        JSONObject message = new JSONObject();
        message.put(Commands.MESSAGE, Commands.OK);
        sender.send(message);
    }

    private void sendErrorMessage (Sender sender, String errorDescription) {
        JSONObject message = new JSONObject();
        message.put(Commands.MESSAGE, Commands.FAIL);
        message.put(Commands.FAIL_DETAILS, errorDescription);
        sender.send(message);
    }

    public void setAuth (boolean authState) {
        this.isAuth = authState;
    }
}
