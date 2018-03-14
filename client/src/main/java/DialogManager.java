import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextInputDialog;

public class DialogManager {

    static <P> Object showWarning(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING, message, ButtonType.OK);
        alert.setHeaderText("");
        alert.setTitle(title);
        alert.setContentText(message);
        return alert.showAndWait();
    }

    static String getInput(String title, String message) {
        TextInputDialog request = new TextInputDialog();
        request.setHeaderText("");
        request.setTitle(title);
        request.setContentText(message);
        request.showAndWait();
        return request.getResult();
    }

    static boolean reconfirmed (String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        alert.setHeaderText("");
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
        return alert.getResult().getButtonData() == ButtonBar.ButtonData.YES;
    }

}
