import javafx.fxml.FXML;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class SignInScreen {

    @FXML private TextField loginField;
    @FXML private PasswordField passwordField;

    @FXML private void signIn() {
        Client.send("/signIn " + loginField.getText() + " " + passwordField.getText());
    }

    @FXML private void switchToRegistration () {
        Client.getInstance().switchSceneTo(Client.Scenes.REGISTRATION);
    }
}