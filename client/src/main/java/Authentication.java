import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import javax.swing.*;

public class Authentication implements ConnectionListener, LoginChecker {

    @FXML private TextField loginField;
    @FXML private PasswordField passwordField;
    @FXML private void initialize() {
        SceneManager.getInstance().send(this, "/test");
    }

    @FXML private void signIn() { signIn(null, loginField.getText(), passwordField.getText()); }
    @FXML private void switchToRegistration () { SceneManager.getInstance().switchSceneTo(SceneManager.Scenes.REGISTRATION); }

    @Override
    public void onConnect(Session session) { }

    @Override
    public void onInput(Session session, Object input) {


        Platform.runLater(()-> {
            if (input instanceof String) {
                String[] tokens = ((String) input).split(" ");
                switch (tokens[0]) {
                    case "/ok":
                        break;
                    case "/welcome":
                        SceneManager.getInstance().switchSceneTo(SceneManager.Scenes.WORK);
                        break;
                    case "/login_rejected":
                        //TODO
                        JOptionPane.showMessageDialog(null, "Неверные логин или пароль",
                                "Ошибка авторизация", JOptionPane.WARNING_MESSAGE);
                        passwordField.clear();
                        break;
                    case "/fail":
                        JOptionPane.showConfirmDialog(null,
                                tokens[1],
                                "Ошибка подключения", JOptionPane.WARNING_MESSAGE);
                        break;
                    default:
                        System.out.println("--------THIS SHOULD NOT HAPPEN " +
                                "- UNKNOWN COMMAND IN CLIENT'S AUTHENTICATION SCREEN");
                }
            }
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

    @Override
    public void signIn(Session session, String user, String password) {
        SceneManager.getInstance().send(this, "/signIn " + user + " " + password);
    }

    @Override
    public void signUp(Session session, String user, String password) { }

    @Override
    public void checkNewUserName(Session session, String name) { }

}
