package com.marta.sandbox.dropbox.client.fxml;

import com.marta.sandbox.dropbox.client.service.*;
import com.marta.sandbox.dropbox.common.messaging.Commands;
import com.marta.sandbox.dropbox.common.messaging.MyFile;
import com.marta.sandbox.dropbox.common.messaging.MyFileList;
import com.marta.sandbox.dropbox.downloadmanager.DownloadManager;
import com.marta.sandbox.dropbox.downloadmanager.DownloadManagerException;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseEvent;
import org.json.simple.JSONObject;

import java.io.*;
import java.nio.file.*;
import java.util.HashSet;

public class Client implements InputListener {

    @FXML private Button btnDelete;
    @FXML private Button btnRename;
    @FXML private Button btnDownload;
    @FXML private TableView<MyFile> table;
    @FXML private TableColumn<MyFile, String> colType;
    @FXML private TableColumn<MyFile, String> colName;
    @FXML private TableColumn<MyFile, String> colSize;
    private HashSet<File> uploads;

    @FXML private void initialize() {

        SceneManager.getInstance().registerListener(SceneManager.SceneType.WORK, this);

        colType.setCellValueFactory(new PropertyValueFactory<MyFile, String>("type"));
        colName.setCellValueFactory(new PropertyValueFactory<MyFile, String>("name"));
        colSize.setCellValueFactory(new PropertyValueFactory<MyFile, String>("size"));

        if(!Files.exists(ROOT, LinkOption.NOFOLLOW_LINKS)) {
            try {
                Files.createDirectory(ROOT);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        uploads = new HashSet<>();

    }

    private final Path ROOT = Paths.get("_client_downloads"); // TODO добавить возможность выбора клиентом

    @Override
    public void onInput(Object input) {

        Platform.runLater(()-> {
            if (input instanceof JSONObject) {

                JSONObject json = (JSONObject)input;
                Commands cmd = (Commands)json.get(Commands.MESSAGE);

                switch (cmd) {
                    case FAIL:
                        DialogManager.showWarning(
                                SceneManager.translate("error.operation-fail"),
                                (String) json.getOrDefault(Commands.FAIL_DETAILS, SceneManager.translate("error.smth-went-wrong")));
                        break;
                    case FILE:
                        saveFile((File)json.get(Commands.FILE),(int)json.get(Commands.SEQUENCE), (long)json.get(Commands.BYTES_LEFT),
                                (byte[])json.get(Commands.DATA));
                        break;
                    case FILE_LIST:
                        listFiles((MyFileList)json.get(Commands.FILE_LIST));
                        break;
                    case OK:
                        break;
                    default:
                        System.out.println("client - THIS SHOULD NOT HAPPEN - UNKNOWN COMMAND FROM SERVER: " + input);
                }

            } else {
                System.out.println("client - THIS SHOULD NOT HAPPEN - " +
                        "UNKNOWN TYPE OF INCOMING MESSAGE IN MAIN SCREEN: " + input);
                requestFileListUpdate();
            }
        });
    }

    @FXML
    private void uploadFile() {

        String title = SceneManager.translate("title.select-file");

        File file = DialogManager.selectFile(title);

        title = SceneManager.translate("title.upload");

        if (file == null) return;

        if (!file.exists()) {
            DialogManager.showWarning(title, SceneManager.translate("error.file-not-found"));
            return;
        }

        if (!file.isFile()) {
            //TODO что делать с папками
            DialogManager.showWarning(title, SceneManager.translate("message.cannot-upload-directory"));
            return;
        }

        if (uploads.contains(file)) {
            //TODO как снимать отметку о нахождении в загрузке
            DialogManager.showWarning(title, SceneManager.translate("message.already-being-uploaded"));
            return;
        }

        uploads.add(file);
        NetworkManager.getInstance().sendFile(file);
        requestFileListUpdate();

    }

    @FXML private void downloadFile () {
        MyFile selectedFile = table.getSelectionModel().getSelectedItem();
        if (selectedFile == null) return;

        downloadFile(selectedFile.getName());
    }

    private void downloadFile(String fileName) {
        JSONObject message = new JSONObject();
        message.put(Commands.MESSAGE, Commands.DOWNLOAD);
        message.put(Commands.FILE_NAME, fileName);
        NetworkManager.getInstance().send(message);
    }


    private void saveFile(File file, int sequence, long bytesLeft, byte[] data) {

        System.out.println("Downloading "+file.getName() +": package no "+sequence+", bytes left: "+ bytesLeft);

        try {
            if(sequence == 1) DownloadManager.getInstance().enlistDownload(ROOT, file);
            DownloadManager.getInstance().download(file, sequence, data);
        } catch (DownloadManagerException e) {
            DialogManager.showWarning(SceneManager.translate("title.download"), e.getMessage());
        }
    }


    @FXML
    private void rename() {

        MyFile selectedFile = table.getSelectionModel().getSelectedItem();

        if(selectedFile == null) return;

        String title;
        String messageText;
        String errorMessage;

        if (selectedFile.getType() == MyFile.FileType.FILE) {
            title = SceneManager.translate("title.rename-file") + " " + selectedFile.getName();
            messageText = SceneManager.translate("prompt.new-file-name");
            errorMessage = SceneManager.translate("error.file-name");
        } else {
            title = SceneManager.translate("title.rename-directory") + " " + selectedFile.getName();
            messageText = SceneManager.translate("prompt.new-directory-name");
            errorMessage = SceneManager.translate("error.directory-name");
        }

        String newName = DialogManager.getInput(title, messageText);

        if (fileNameIsValid(newName)) {
            JSONObject message = new JSONObject();
            message.put(Commands.MESSAGE, Commands.RENAME);
            message.put(Commands.FILE_NAME, selectedFile.getName());
            message.put(Commands.NEW_FILE_NAME, newName);
            NetworkManager.getInstance().send(message);
        } else {
            DialogManager.showWarning(title, errorMessage);
        }
    }

    @FXML
    private void delete () {

        MyFile selectedFile = table.getSelectionModel().getSelectedItem();

        if(selectedFile == null) return;

        String title;
        String messageText = SceneManager.translate("action.delete") + " " + selectedFile.getName()+"?";

        if(selectedFile.getType() == MyFile.FileType.FILE) {
            title = SceneManager.translate("title.delete-file");
        } else {
            title = SceneManager.translate("title.delete-directory");
        }

        if (DialogManager.reconfirmed(title, messageText)) {
            JSONObject message = new JSONObject();
            message.put(Commands.MESSAGE, Commands.DELETE);
            message.put(Commands.FILE_NAME, selectedFile.getName());
            NetworkManager.getInstance().send(message);
        }
    }


    @FXML
    private void createDirectory () {

        String title = SceneManager.translate("action.new-folder");
        String messageText = SceneManager.translate("prompt.new-folder");
        String errorMessage = SceneManager.translate("error.directory-name");

        String newDirectory = DialogManager.getInput(title, messageText);

        if (fileNameIsValid(newDirectory)) {
            JSONObject message = new JSONObject();
            message.put(Commands.MESSAGE, Commands.CREATE_DIRECTORY);
            message.put(Commands.DIRECTORY_NAME, newDirectory);
            NetworkManager.getInstance().send(message);
        } else {
            DialogManager.showWarning(title, errorMessage);
        }
    }


    @FXML
    public void directoryUp() {
        JSONObject message = new JSONObject();
        message.put(Commands.MESSAGE, Commands.DIRECTORY_UP);
        NetworkManager.getInstance().send(message);
    }


    private void directoryDown(String directoryName) {
        JSONObject message = new JSONObject();
        message.put(Commands.MESSAGE, Commands.DIRECTORY_DOWN);
        message.put(Commands.DIRECTORY_NAME, directoryName);
        NetworkManager.getInstance().send(message);
    }


    private void listFiles (MyFileList input) {
        table.setItems(FXCollections.observableArrayList(input.getFileList()));
    }

    @FXML private void clickOnFile(MouseEvent mouseEvent) {

        // одиночный клик - обновить доступность кнопок
        if (mouseEvent.getClickCount() == 1) selectFile();

        // двойной клик - для папок: переход по каталогу, для файлов: загрузка после подтверждения
        else if (mouseEvent.getClickCount() == 2) {
            selectFile();
            MyFile selectedFile = table.getSelectionModel().getSelectedItem();

            if (selectedFile == null) return;

            if (selectedFile.getType() == MyFile.FileType.DIR) {
                directoryDown(selectedFile.getName());
            } else {
                if (DialogManager.reconfirmed(SceneManager.translate("title.download"),
                        SceneManager.translate("prompt.download"))) {
                    downloadFile(selectedFile.getName());
                }
            }
        }
    }

    private void requestFileListUpdate() {
        JSONObject message = new JSONObject();
        message.put(Commands.MESSAGE, Commands.LIST_CONTENTS);
        NetworkManager.getInstance().send(message);
    }

    private boolean fileNameIsValid (String fileName) {
        // TODO доделать проверку имени файла на корректность
        return fileName !=  null  && !fileName.isEmpty();
    }

    private void selectFile () {
        // TODO а как снять выделение выбранного файла?
        MyFile selectedFile = table.getSelectionModel().getSelectedItem();
        if (selectedFile == null) {
            btnDelete.setDisable(true);
            btnRename.setDisable(true);
            btnDownload.setDisable(true);
        } else {
            btnDelete.setDisable(false);
            btnRename.setDisable(false);
            if (selectedFile.getType() == MyFile.FileType.DIR )
                 btnDownload.setDisable(true);
            else btnDownload.setDisable(false);
        }
    }
}