import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import org.json.simple.JSONObject;

import javax.swing.*;

public class Registration implements ConnectionListener, LoginChecker {

    @FXML private TextField username;
    @FXML private TextField password;
    @FXML private TextField password1;
    @FXML private Button btnRegister;

    @FXML private void initialize() {
        JSONObject message = new JSONObject();
        message.put(Commands.REQUEST, Commands.TEST);
        SceneManager.getInstance().send(this, message);
    }
    @FXML private void switchToAuthentication () { SceneManager.getInstance().switchSceneTo(SceneManager.Scenes.AUTHENTICATION); }



    @Override
    public void onConnect(Session session) { }

    @Override
    public void onInput(Session session, Object input) {

        Platform.runLater(()-> {

            if (input instanceof JSONObject) {

                JSONObject json = (JSONObject) input;
                Commands cmd = (Commands) json.get(Commands.REPLY);

                switch (cmd) {
                    case ADMITTED:
                        JOptionPane.showMessageDialog(null, "Вы успешно зарегистрированы!!!",
                                "Регистрация пользователя", JOptionPane.INFORMATION_MESSAGE);
                        SceneManager.getInstance().switchSceneTo(SceneManager.Scenes.WORK);
                        break;
                    case NOT_ADMITTED:
                    case FAIL:
                        JOptionPane.showMessageDialog(null,
                                json.getOrDefault(Commands.FAIL_DETAILS,
                                        "Невозможно зарегистрировать пользователя с указанными данными"),
                                "Регистрация пользователя", JOptionPane.WARNING_MESSAGE);
                        username.clear();
                        password.clear();
                        password1.clear();
                        break;
                    case OK:
                    case USERNAME_OK:
                        break;
                    default:
                        System.out.println("--------THIS SHOULD NOT HAPPEN - UNKNOWN COMMAND IN CLIENT'S REGISTRATION SCREEN");
                }
            } else System.out.println("--------THIS SHOULD NOT HAPPEN - " +
                    "UNKNOWN TYPE OF INCOMING MESSAGE IN CLIENT'S REGISTRATION SCREEN");
        });
    }

    @Override
    public void onDisconnect(Session session) { }

    @Override
    public void onException(Session session, Exception e) {
        JOptionPane.showMessageDialog(null, "Нет соединения с сервером",
                "Что-то пошло не так...", JOptionPane.WARNING_MESSAGE);
        e.printStackTrace();
    }


    @FXML
    private void checkNewUserName() {
        if (isUsernameOK(username.getText())) {
            checkNewUserName(null, username.getText());
        } else {
            JOptionPane.showConfirmDialog(null,
                    "Пожалуйста, придумайте имя пользователя, состоящее только из строчных букв латинского алфавита и цифр",
                    "Регистрация пользователя", JOptionPane.WARNING_MESSAGE);
            username.clear();
        }
    }


    @Override
    public void checkNewUserName(Session session, String name) {
        JSONObject message = new JSONObject();
        message.put(Commands.REQUEST, Commands.CHECK_NEW_USER_NAME);
        message.put(Commands.USERNAME, name);
        SceneManager.getInstance().send(this, message);
    }

    @FXML
    private void signUp() {
        if (!isUsernameOK(username.getText())) {
            JOptionPane.showConfirmDialog(null,
                    "Пожалуйста, придумайте имя пользователя, состоящее только из строчных букв латинского алфавита и цифр",
                    "Регистрация пользователя", JOptionPane.WARNING_MESSAGE);
            username.clear();
        } else if (!isPasswordOK(password.getText())) {
            JOptionPane.showConfirmDialog(null,
                    "Пароль должен содержать от 4 до 8 символов: букв латинского алфавита и цифр",
                    "Регистрация пользователя", JOptionPane.WARNING_MESSAGE);
            password.clear();
            password1.clear();
        } else if (!(password1.getText().equals(password.getText()))) {
            JOptionPane.showConfirmDialog(null,
                    "Пароли не совпадают - перепроверьте, пожалуйста, пароль",
                    "Регистрация пользователя", JOptionPane.WARNING_MESSAGE);
            password1.clear();
        } else {
            signUp(null, username.getText(), password.getText());
        }
    }

    @Override
    public void signUp(Session session, String user, String password) {
        JSONObject message = new JSONObject();
        message.put(Commands.REQUEST, Commands.SIGN_UP);
        message.put(Commands.USERNAME, user);
        message.put(Commands.PASSWORD, password);
        SceneManager.getInstance().send(this, message);
    }

    @FXML private void checkPassword () {
        String pwd = password.getText();
        if (!isPasswordOK(pwd)) {
            JOptionPane.showConfirmDialog(null,
                    "Пароль должен содержать от 4 до 8 символов: букв латинского алфавита и цифр",
                    "Регистрация пользователя", JOptionPane.WARNING_MESSAGE);
            password.clear();
        }
    }


    @Override
    public void signIn(Session session, String user, String password) { }



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
