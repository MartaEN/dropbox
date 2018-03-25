import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseEvent;
import org.json.simple.JSONObject;

import java.io.*;
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

        requestFileListUpdate();
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
                Commands cmd = (Commands)json.get(Commands.MESSAGE);

                switch (cmd) {
                    case FAIL:
                        DialogManager.showWarning(
                                SceneManager.translate("error.operation-fail"),
                                (String) json.getOrDefault(Commands.FAIL_DETAILS, SceneManager.translate("error.smth-went-wrong")));
                        break;
                    case FILE:
                        appendFile((String)json.get(Commands.FILE),(long)json.get(Commands.SIZE), (long)json.get(Commands.BYTES),
                                (byte[])json.get(Commands.DATA));
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
                listFiles((MyFileList)input);

            } else {
                System.out.println("client - THIS SHOULD NOT HAPPEN - " +
                        "UNKNOWN TYPE OF INCOMING MESSAGE IN MAIN SCREEN: " + input);
                requestFileListUpdate();
            }
        });
    }

    @Override
    public void onDisconnect(Session session) {
        SceneManager.getInstance().logout();
    }

    @Override
    public void onException(Session session, Exception e) {
        SceneManager.getInstance().onException(e);
    }

    @FXML
    private void uploadFile() {

        String title = SceneManager.translate("title.select-file");

        File file = DialogManager.selectFile(title);

        if (file != null) {
            if (file.exists()) {
                if (file.isFile()) {
                    uploadFile(file);
                } else {
                    //TODO что делать с папками
                    DialogManager.showWarning(title, SceneManager.translate("message.cannot-upload-directory"));
                }
            } else {
                DialogManager.showWarning(title, SceneManager.translate("error.file-not-found"));
            }
        }
    }

    @Override
    public void uploadFile (File file) {
        SceneManager.getInstance().sendFile(file);
        requestFileListUpdate();
    }

    @FXML private void downloadFile () {
        MyFile selectedFile = table.getSelectionModel().getSelectedItem();
        if (selectedFile == null) return;
        downloadFile(selectedFile.getName());
    }

    @Override
    public void downloadFile(String fileName) {
        JSONObject message = new JSONObject();
        message.put(Commands.MESSAGE, Commands.DOWNLOAD);
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

    private void appendFile(String name, long size, long read, byte[] data) {
        System.out.println("Uploading "+name +": declared size "+size+", read so far: "+ read);
        Path path = ROOT.resolve(name);
        File file = new File(path.toString());
        file.setReadable(false);
        try (OutputStream output = new BufferedOutputStream(new FileOutputStream(file, true), data.length)) {
            output.write(data);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if(read == size) file.setReadable(true);
    }

    @FXML
    private void rename() {

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
            rename(selectedFile.getName(), newName);
        } else {
            DialogManager.showWarning(title, errorMessage);
        }
    }

    @Override
    public void rename(String oldName, String newName) {
        JSONObject message = new JSONObject();
        message.put(Commands.MESSAGE, Commands.RENAME);
        message.put(Commands.FILE_NAME, oldName);
        message.put(Commands.NEW_FILE_NAME, newName);
        SceneManager.getInstance().send(this, message);
    }

    @FXML
    private void delete () {

        MyFile selectedFile = table.getSelectionModel().getSelectedItem();

        if(selectedFile == null) return;

        String title;
        String message = SceneManager.translate("action.delete") + " " + selectedFile.getName()+"?";

        if(selectedFile.getType() == MyFile.FileType.FILE) {
            title = SceneManager.translate("title.delete-file");
        } else {
            title = SceneManager.translate("title.delete-directory");
        }

        if (DialogManager.reconfirmed(title, message)) delete(selectedFile.getName());
    }

    @Override
    public void delete(String name) {
        JSONObject message = new JSONObject();
        message.put(Commands.MESSAGE, Commands.DELETE);
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
            createDirectory(newDirectory);
        } else {
            DialogManager.showWarning(title, errorMessage);
        }
    }

    @Override
    public void createDirectory (String name) {
        JSONObject message = new JSONObject();
        message.put(Commands.MESSAGE, Commands.CREATE_DIRECTORY);
        message.put(Commands.DIRECTORY_NAME, name);
        SceneManager.getInstance().send(this, message);
    }

    @FXML
    @Override
    public void directoryUp() {
        JSONObject message = new JSONObject();
        message.put(Commands.MESSAGE, Commands.DIRECTORY_UP);
        SceneManager.getInstance().send(this, message);
    }

    @Override
    public void directoryDown(String directoryName) {
        JSONObject message = new JSONObject();
        message.put(Commands.MESSAGE, Commands.DIRECTORY_DOWN);
        message.put(Commands.DIRECTORY_NAME, directoryName);
        SceneManager.getInstance().send(this, message);
    }

    @Override
    public void listFiles (MyFileList input) {
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
        SceneManager.getInstance().send(this, message);
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