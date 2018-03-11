import com.marta.dropbox.connection.*;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseEvent;

import javax.swing.*;
import java.io.File;

public class WorkScreen {

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

        Client.getInstance().send("/list");
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
                Work.uploadFile(file);
            } else {
                //TODO что делать с папками
                JOptionPane.showConfirmDialog(null, errorDir, title, JOptionPane.WARNING_MESSAGE);
            }
        } else {
            JOptionPane.showConfirmDialog(null, errorNotFound, title, JOptionPane.WARNING_MESSAGE);
        }
    }


    @FXML
    private void downloadFile () {
        MyFile selectedFile = table.getSelectionModel().getSelectedItem();
        if (selectedFile == null) return;
        Work.downloadFile(selectedFile.getName());
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

        if (Work.fileNameIsValid(newName)) {
            Work.renameFile(selectedFile.getName(), newName);
        } else {
            JOptionPane.showConfirmDialog(null, errorMessage, title, JOptionPane.WARNING_MESSAGE);
        }
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
            message = "Удалить папку \"" + selectedFile.getName()+"\" со всем её содержимым?";
            title = "Удаление папки";
        }

        int userChoice = JOptionPane.showOptionDialog(null, message, title,
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, buttons, buttons[0]);

        if (userChoice == 0) Work.deleteFile(selectedFile.getName());

    }


    @FXML
    private void createDirectory () {

        String title = "Создание новой папки";
        String message = "Bведите имя папки: ";
        String errorMessage = "Ошибка: некорректное имя";

        String newDirectory = JOptionPane.showInputDialog(null, message, title, JOptionPane.QUESTION_MESSAGE);

        if (Work.fileNameIsValid(newDirectory)) {
            Client.getInstance().send("/newDir " + newDirectory);
        } else {
            JOptionPane.showConfirmDialog(null, errorMessage, title, JOptionPane.WARNING_MESSAGE);
        }
    }

    @FXML private void directoryUp() {
        Client.getInstance().send("/dirUp");
    }

    private void directoryDown(Session session, String directoryName) {
        Client.getInstance().send("/dirDown " + directoryName);
    }


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