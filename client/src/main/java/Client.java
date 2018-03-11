import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseEvent;

import javax.swing.*;
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

        SceneManager.getInstance().send(this, "/list");
    }

    private final Path ROOT = Paths.get("_client_downloads"); // TODO добавить возможность выбора клиентом

    @Override
    public void onConnect(Session session) { }

    @Override
    public void onInput(Session session, Object input) {

        Platform.runLater(()-> {

            // обрабатываем служебные сообщения с сервера
            if (input instanceof String) {

                System.out.println("client - incoming message identified as String: "+ input);

                String[] tokens = ((String) input).split(" ");
                switch (tokens[0]) {
                    case "/notfound":
                        JOptionPane.showConfirmDialog(null,
                                "Файл не найден",
                                "Не удалось выполнить операцию", JOptionPane.WARNING_MESSAGE);
                        break;
                    case "/ok":
                        break;
                    case "/fail":
                        JOptionPane.showConfirmDialog(null,
                                tokens[1],
                                "Не удалось выполнить операцию", JOptionPane.WARNING_MESSAGE);
                        break;
                    default:
                        System.out.println("client - THIS SHOULD NOT HAPPEN - UNKNOWN COMMAND FROM SERVER: " + input);
                }

            // или обрабатываем входящие файлы
            } else if (input instanceof File) {
                System.out.println("client - incoming message identified as File" + input);
                saveFile((File) input);

            // или обрабатываем полученный список файлов
            } else if (input instanceof MyFileList){
                System.out.println("client - incoming message identified as MyFileList: " + input);
                listFiles(session, (MyFileList)input);

            } else {
                System.out.println("client - THIS SHOULD NOT HAPPEN - UNKNOWN TYPE OF INCOMING MESSAGE: " + input);
                //TODO
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
        JOptionPane.showMessageDialog(null, "Потеряно соединение с сервером",
                "Ошибка", JOptionPane.WARNING_MESSAGE);
        SceneManager.getInstance().disconnect();
    }

    @FXML
    private void uploadFile() {

        String title = "Загрузка файла в удаленное хранилище";
        String message = "Введите путь к файлу: ";
        String errorDir = "Папка загружена быть не может, укажите путь к файлу";
        String errorNotFound = "Файл не найден";

        String path = JOptionPane.showInputDialog(null, message, title, JOptionPane.QUESTION_MESSAGE);

        File file = new File (path);

        if (file.exists()) {
            if(file.isFile()) {
                uploadFile(null, file);
            } else {
                //TODO что делать с папками
                JOptionPane.showConfirmDialog(null, errorDir, title, JOptionPane.WARNING_MESSAGE);
            }
        } else {
            JOptionPane.showConfirmDialog(null, errorNotFound, title, JOptionPane.WARNING_MESSAGE);
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
        SceneManager.getInstance().send(this,"/download " + fileName);
    }

    @Override
    public void saveFile(File input) {
        try {
            FileProcessor.saveFile(ROOT, input);
        } catch (FileProcessorException e) {
            JOptionPane.showConfirmDialog(null,
                    "Ошибка сохранения файла",
                    "Не удалось выполнить операцию", JOptionPane.WARNING_MESSAGE);
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
            title = "Переименование файла " + selectedFile.getName();
            message = "Bведите новое имя файла: ";
            errorMessage = "Некорректное имя файла";
        } else {
            title = "Переименование папки " + selectedFile.getName();
            message = "Bведите новое имя папки: ";
            errorMessage = "Некорректное имя папки";
        }

        String newName = JOptionPane.showInputDialog(null, message, title, JOptionPane.QUESTION_MESSAGE);

        if (fileNameIsValid(newName)) {
            renameFile(null, selectedFile.getName(), newName);
        } else {
            JOptionPane.showConfirmDialog(null, errorMessage, title, JOptionPane.WARNING_MESSAGE);
        }
    }

    @Override
    public void renameFile(Session session, String oldName, String newName) {
        SceneManager.getInstance().send(this, "/rename " + oldName + " " + newName);
    }

    @FXML
    private void deleteFile () {

        MyFile selectedFile = table.getSelectionModel().getSelectedItem();

        if(selectedFile == null) return;

        String message;
        String title;
        String [] buttons = { "Да", "Нет"};

        if(selectedFile.getType() == MyFile.FileType.FILE) {
            message = "Удалить " + selectedFile.getName()+"?";
            title = "Удаление файла";
        } else {
            message = "Удалить " + selectedFile.getName()+"?";
            title = "Удаление папки";
        }

        int userChoice = JOptionPane.showOptionDialog(null, message, title,
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, buttons, buttons[0]);

        if (userChoice == 0) deleteFile(null, selectedFile.getName());

    }

    @Override
    public void deleteFile(Session session, String name) {
        SceneManager.getInstance().send(this,"/delete " + name);
    }


    @FXML
    private void createDirectory () {

        String title = "Создание новой папки";
        String message = "Bведите имя папки: ";
        String errorMessage = "Ошибка: некорректное имя";

        String newDirectory = JOptionPane.showInputDialog(null, message, title, JOptionPane.QUESTION_MESSAGE);

        if (fileNameIsValid(newDirectory)) {
            createDirectory(null, newDirectory);
        } else {
            JOptionPane.showConfirmDialog(null, errorMessage, title, JOptionPane.WARNING_MESSAGE);
        }
    }

    @Override
    public void createDirectory (Session session, String name) {
        SceneManager.getInstance().send(this,"/newDir " + name);
    }

    @FXML private void directoryUp() {
        directoryUp(null);
    }

    @Override
    public void directoryUp(Session session) {
        SceneManager.getInstance().send(this, "/dirUp");
    }

    @Override
    public void directoryDown(Session session, String directoryName) {
        SceneManager.getInstance().send(this, "/dirDown " + directoryName);
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
                if (JOptionPane.showConfirmDialog(null,
                        "Загрузить выбранный файл на локальный диск?", "Загрузка файла",
                        JOptionPane.OK_CANCEL_OPTION) == 0)   {
                    downloadFile(null, selectedFile.getName());
                }
            }
        }
    }

    private boolean fileNameIsValid (String fileName) {
        // TODO доделать проверку имени файла на корректность
        return fileName !=  null  && !fileName.isEmpty() && !fileName.contains(" ");
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
            btnDownload.setDisable(false);
        }
    }
}