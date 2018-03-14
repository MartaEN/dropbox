import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseEvent;
import org.json.simple.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;

public class Client implements ConnectionListener, FileManager {

    @FXML private Button btnDelete;
    @FXML private Button btnRename;
    @FXML private Button btnDownload;
    @FXML private TableView<MyFile> table;
    @FXML private TableColumn<MyFile, String> colType;
    @FXML private TableColumn<MyFile, String> colName;
    @FXML private TableColumn<MyFile, String> colSize;

    @FXML private void initialize() {

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

        JSONObject message = new JSONObject();
        message.put(Commands.REQUEST, Commands.LIST_CONTENTS);
        SceneManager.getInstance().send(this, message);
    }

    private final Path ROOT = Paths.get("_client_downloads"); // TODO добавить возможность выбора клиентом

    @Override
    public void onConnect(Session session) { }

    @Override
    public void onInput(Session session, Object input) {

        Platform.runLater(()-> {

            // обрабатываем служебные сообщения с сервера
            if (input instanceof JSONObject) {

                JSONObject json = (JSONObject)input;
                Commands cmd = (Commands)json.get(Commands.REPLY);

                switch (cmd) {
                    case FAIL:
                        DialogManager.showWarning(
                                SceneManager.translate("error.operation-fail"),
                                (String) json.getOrDefault(Commands.FAIL_DETAILS, SceneManager.translate("error.smth-went-wrong")));
                        break;
                    case OK:
                        break;
                    default:
                        System.out.println("client - THIS SHOULD NOT HAPPEN - UNKNOWN COMMAND FROM SERVER: " + input);
                }

            // или обрабатываем входящие файлы
            } else if (input instanceof File) {
                saveFile((File) input);

            // или обрабатываем полученный список файлов
            } else if (input instanceof MyFileList){
                listFiles(session, (MyFileList)input);

            } else {
                System.out.println("client - THIS SHOULD NOT HAPPEN - " +
                        "UNKNOWN TYPE OF INCOMING MESSAGE IN MAIN SCREEN: " + input);
                JSONObject message = new JSONObject();
                message.put(Commands.REQUEST, Commands.LIST_CONTENTS);
                SceneManager.getInstance().send(this, message);
            }
        });
    }

    @Override
    public void onDisconnect(Session session) {
        System.out.println("client - inside onDisconnect method");
        SceneManager.getInstance().disconnect();
    }

    @Override
    public void onException(Session session, Exception e) {
        e.printStackTrace();
        DialogManager.showWarning(
                SceneManager.translate("error.connection-failed"),
                SceneManager.translate("error.smth-went-wrong"));
        SceneManager.getInstance().disconnect();
    }

    @FXML
    private void uploadFile() {

        String title = SceneManager.translate("title.upload");

        String path = DialogManager.getInput(title, SceneManager.translate("prompt.path"));

        File file = new File (path);

        if (file.exists()) {
            if(file.isFile()) {
                uploadFile(null, file);
            } else {
                //TODO что делать с папками
                DialogManager.showWarning(title, SceneManager.translate("message.cannot-upload-directory"));
            }
        } else {
            DialogManager.showWarning(title, SceneManager.translate("error.file-not-found"));
        }
    }

    @Override
    public void uploadFile (Session session, File file) {
        SceneManager.getInstance().send(this, file);
    }


    @FXML private void downloadFile () {
        MyFile selectedFile = table.getSelectionModel().getSelectedItem();
        if (selectedFile == null) return;
        downloadFile(null, selectedFile.getName());
    }

    @Override
    public void downloadFile(Session session, String fileName) {
        JSONObject message = new JSONObject();
        message.put(Commands.REQUEST, Commands.DOWNLOAD);
        message.put(Commands.FILE_NAME, fileName);
        SceneManager.getInstance().send(this,message);
    }

    @Override
    public void saveFile(File input) {
        try {
            FileProcessor.saveFile(ROOT, input);
        } catch (FileProcessorException e) {
            DialogManager.showWarning(
                    SceneManager.translate("error.error"),
                    SceneManager.translate("error.operation-fail"));
        }
    }

    @FXML
    private void renameFile () {

        MyFile selectedFile = table.getSelectionModel().getSelectedItem();

        if(selectedFile == null) return;

        String title;
        String message;
        String errorMessage;

        if (selectedFile.getType() == MyFile.FileType.FILE) {
            title = SceneManager.translate("title.rename-file") + " " + selectedFile.getName();
            message = SceneManager.translate("prompt.new-file-name");
            errorMessage = SceneManager.translate("error.file-name");
        } else {
            title = SceneManager.translate("title.rename-directory") + " " + selectedFile.getName();
            message = SceneManager.translate("prompt.new-directory-name");
            errorMessage = SceneManager.translate("error.directory-name");
        }

        String newName = DialogManager.getInput(title, message);

        if (fileNameIsValid(newName)) {
            renameFile(null, selectedFile.getName(), newName);
        } else {
            DialogManager.showWarning(title, errorMessage);
        }
    }

    @Override
    public void renameFile(Session session, String oldName, String newName) {
        JSONObject message = new JSONObject();
        message.put(Commands.REQUEST, Commands.RENAME);
        message.put(Commands.FILE_NAME, oldName);
        message.put(Commands.NEW_FILE_NAME, newName);
        SceneManager.getInstance().send(this, message);
    }

    @FXML
    private void deleteFile () {

        MyFile selectedFile = table.getSelectionModel().getSelectedItem();

        if(selectedFile == null) return;

        String title;
        String message = SceneManager.translate("action.delete") + " " + selectedFile.getName()+"?";

        if(selectedFile.getType() == MyFile.FileType.FILE) {
            title = SceneManager.translate("title.delete-file");
        } else {
            title = SceneManager.translate("title.delete-directory");
        }

        if (DialogManager.reconfirmed(title, message)) deleteFile(null, selectedFile.getName());
    }

    @Override
    public void deleteFile(Session session, String name) {
        JSONObject message = new JSONObject();
        message.put(Commands.REQUEST, Commands.DELETE);
        message.put(Commands.FILE_NAME, name);
        SceneManager.getInstance().send(this, message);
    }


    @FXML
    private void createDirectory () {

        String title = SceneManager.translate("action.new-folder");
        String message = SceneManager.translate("prompt.new-folder");
        String errorMessage = SceneManager.translate("error.directory-name");

        String newDirectory = DialogManager.getInput(title, message);

        if (fileNameIsValid(newDirectory)) {
            createDirectory(null, newDirectory);
        } else {
            DialogManager.showWarning(title, errorMessage);
        }
    }

    @Override
    public void createDirectory (Session session, String name) {
        JSONObject message = new JSONObject();
        message.put(Commands.REQUEST, Commands.CREATE_DIRECTORY);
        message.put(Commands.DIRECTORY_NAME, name);
        SceneManager.getInstance().send(this, message);
    }

    @FXML private void directoryUp() {
        directoryUp(null);
    }

    @Override
    public void directoryUp(Session session) {
        JSONObject message = new JSONObject();
        message.put(Commands.REQUEST, Commands.DIRECTORY_UP);
        SceneManager.getInstance().send(this, message);
    }

    @Override
    public void directoryDown(Session session, String directoryName) {
        JSONObject message = new JSONObject();
        message.put(Commands.REQUEST, Commands.DIRECTORY_DOWN);
        message.put(Commands.DIRECTORY_NAME, directoryName);
        SceneManager.getInstance().send(this, message);
    }

    @Override
    public void listFiles (Session session, MyFileList input) {
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
                directoryDown(null, selectedFile.getName());
            } else {
                if (DialogManager.reconfirmed(SceneManager.translate("title.download"),
                        SceneManager.translate("prompt.download"))) {
                    downloadFile(null, selectedFile.getName());
                }
            }
        }
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