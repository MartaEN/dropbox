import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;

import javax.swing.*;

public class SignUpScreen {

    @FXML private TextField username;
    @FXML private TextField password;
    @FXML private TextField password1;
    @FXML private Button btnRegister;

    @FXML private void switchToAuthentication () {
        Client.getInstance().switchSceneTo(Client.Scenes.AUTHENTICATION);
    }

    @FXML private void checkNewUserName() {
        SignUp.checkNewUserName(username.getText());
    }

    @FXML private void signUp() {
        SignUp.signUp( username.getText(), password.getText(), password1.getText());
    }

    @FXML private void checkPassword () {
        String pwd = password.getText();
        if (!SignUp.isPasswordOK(pwd)) {
            JOptionPane.showConfirmDialog(null,
                    "Пароль должен содержать от 4 до 8 символов: букв латинского алфавита и цифр",
                    "Регистрация пользователя", JOptionPane.WARNING_MESSAGE);
            password.clear();
        }
    }

    @FXML private void checkRepeatedPassword() {
        if (password1.getText().equals(password.getText())) {
            btnRegister.setDisable(false);
        }
    }
}
