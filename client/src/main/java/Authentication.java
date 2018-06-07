import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import org.json.simple.JSONObject;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Authentication implements ConnectionListener, SignInChecker {

    @FXML private TextField loginField;
    @FXML private PasswordField passwordField;
    @FXML private void initialize() {
        JSONObject message = new JSONObject();
        message.put(Commands.MESSAGE, Commands.TEST);
        SceneManager.getInstance().send(this, message);
    }

    @FXML private void signIn() { signIn(loginField.getText(), passwordField.getText()); }
    @FXML private void switchToRegistration () { SceneManager.getInstance().switchSceneTo(SceneManager.Scenes.REGISTRATION); }

    @Override
    public void onConnect(Session session) { }

    @Override
    public void onInput(Session session, Object input) {

        Platform.runLater(()-> {
            if (input instanceof JSONObject) {

                JSONObject json = (JSONObject)input;
                Commands cmd = (Commands)json.get(Commands.MESSAGE);

                switch (cmd) {
                    case ADMITTED:
                        SceneManager.getInstance().switchSceneTo(SceneManager.Scenes.WORK);
                        break;
                    case NOT_ADMITTED:
                        DialogManager.showWarning(
                                SceneManager.translate("message.not-admitted"),
                                SceneManager.translate("message.wrong-username-or-password"));
                        passwordField.clear();
                        break;
                    case FAIL:
                        DialogManager.showWarning(
                                SceneManager.translate("error.connection-failed"),
                                SceneManager.translate("error.smth-went-wrong"));
                        break;
                    case OK:
                        break;
                    default:
                        System.out.println("--------THIS SHOULD NOT HAPPEN " +
                                "- UNKNOWN COMMAND IN CLIENT'S AUTHENTICATION SCREEN");
                }
            } else System.out.println("--------THIS SHOULD NOT HAPPEN - " +
                    "UNKNOWN TYPE OF INCOMING MESSAGE IN CLIENT'S AUTHENTICATION SCREEN");
        });
    }

    @Override
    public void onDisconnect(Session session) { }

    @Override
    public void onException(Session session, Exception e) {
        SceneManager.getInstance().onException(e);
    }

    @Override
    public void signIn(String user, String password) {
        JSONObject message = new JSONObject();
        message.put(Commands.MESSAGE, Commands.SIGN_IN);
        message.put(Commands.USERNAME, user);
        message.put(Commands.PASSWORD, hash(password));
        SceneManager.getInstance().send(this, message);
    }

    private String hash (String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(input.getBytes());
            return new String(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            return input;
        }
    }

}
