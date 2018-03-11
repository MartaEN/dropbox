import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class Work {

    static void uploadFile (File file) {
        Client.getInstance().send(file);
    }


    static void deleteFile(String name) {
        Client.getInstance().send("/delete " + name);
    }

    static void downloadFile(String fileName) {
        Client.getInstance().send("/download " + fileName);
    }

    static void saveFile(File input) {
        //TODO предупреждения о замене уже существующих файлов?
        //TODO выбор директории, куда сохранять?
        //TODO прогресс-бар загрузки?
        Path saveTo = ROOT.resolve(input.getName());
        try {
            Files.copy(input.toPath(), saveTo, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            JOptionPane.showConfirmDialog(null,
                    "Ошибка: файл не сохранен", "Ошибка соединения", JOptionPane.WARNING_MESSAGE);
            e.printStackTrace();
        }
    }

    static void renameFile(String oldName, String newName) {
        Client.getInstance().send(this, "/rename " + oldName + " " + newName);
    }

    static boolean fileNameIsValid (String fileName) {
        // TODO доделать проверку имени файла на корректность
        return fileName !=  null  && !fileName.isEmpty() && !fileName.contains(" ");
    }

}
