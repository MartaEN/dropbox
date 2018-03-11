import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;

import javax.swing.*;

public class Registration implements ConnectionListener, LoginChecker {

    @FXML private TextField username;
    @FXML private TextField password;
    @FXML private TextField password1;
    @FXML private Button btnRegister;
    @FXML private void initialize() {
        SceneManager.getInstance().send(this, "/test");
    }
    @FXML private void switchToAuthentication () { SceneManager.getInstance().switchSceneTo(SceneManager.Scenes.AUTHENTICATION); }
    @FXML private void checkNewUserName() { checkNewUserName(null, username.getText());}
    @FXML private void signUp() { signUp(null, null, null);  }

    @FXML private void checkPassword () {
        String pwd = password.getText();
        if (!isPasswordOK(pwd)) {
            JOptionPane.showConfirmDialog(null,
                    "Пароль должен содержать от 4 до 8 символов: букв латинского алфавита и цифр",
                    "Регистрация пользователя", JOptionPane.WARNING_MESSAGE);
            password.clear();
        }
    }

    @FXML private void checkRepeatedPassword(KeyEvent keyEvent) {
        if (password1.getText().equals(password.getText())) {
            btnRegister.setDisable(false);
        }
    }


    @Override
    public void onConnect(Session session) { }

    @Override
    public void onInput(Session session, Object input) {

        switch ((String)input) {
            case "/username_ok":
                break;
            case "/username_rejected":
                JOptionPane.showConfirmDialog(null,
                        "Пользователь с таким именем уже есть",
                        "Регистрация пользователя", JOptionPane.WARNING_MESSAGE);
                username.clear();
                break;
            case "/login_rejected":
                //TODO
                JOptionPane.showMessageDialog(null, "Что-то пошло не так...",
                        "Регистрация пользователя", JOptionPane.WARNING_MESSAGE);
                username.clear();
                password.clear();
                password1.clear();
                break;
            case "/welcome":
                JOptionPane.showMessageDialog(null, "Вы успешно зарегистрированы!!!",
                        "Регистрация пользователя", JOptionPane.INFORMATION_MESSAGE);
                SceneManager.getInstance().switchSceneTo(SceneManager.Scenes.WORK);
                break;
            default:
                System.out.println("--------THIS SHOULD NOT HAPPEN - UNKNOWN COMMAND IN CLIENT'S REGISTRATION SCREEN");
        }
    }

    @Override
    public void onDisconnect(Session session) { }

    @Override
    public void onException(Session session, Exception e) {
        JOptionPane.showMessageDialog(null, "Нет соединения с сервером",
                "Что-то пошло не так...", JOptionPane.WARNING_MESSAGE);
        e.printStackTrace();
    }



    @Override
    public void signIn(Session session, String user, String password) { }

    @Override
    public void signUp(Session session, String user, String pwd) {
        if (isUsernameOK(username.getText())
                && isPasswordOK(password.getText())
                && (password1.getText().equals(password.getText()))) {
            SceneManager.getInstance().send(this,
                    "/signUp " + username.getText() + " " + password.getText());
        } else System.out.println("------SMTH WENT WRONG IN REGISTRATION SCREEN");
    }

    @Override
    public void checkNewUserName(Session session, String newName) {
        if (isUsernameOK(newName)) {
            SceneManager.getInstance().send(this, "/newUser " + username.getText());
        } else {
            JOptionPane.showConfirmDialog(null,
                    "Пожалуйста, придумайте имя пользователя, состоящее только из строчных букв латинского алфавита и цифр",
                    "Регистрация пользователя", JOptionPane.WARNING_MESSAGE);
            username.clear();
        }
    }

    private boolean isUsernameOK (String username) {
        return username.chars().allMatch(ch -> (ch >= 'a' && ch <= 'z')
                || (ch >= '0' && ch <= '9'));
    }

    private boolean isPasswordOK (String password) {
        if (password.length() > 3 && password.length() < 9)
            return password.chars().allMatch(ch ->
                    (ch >= 'a' && ch <= 'z')
                            || (ch >= 'A' && ch <= 'Z')
                            || (ch >= '0' && ch <= '9'));
        return false;
    }

}
