import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import org.json.simple.JSONObject;

public class Authentication implements ConnectionListener, LoginChecker {

    @FXML private TextField loginField;
    @FXML private PasswordField passwordField;
    @FXML private void initialize() {
        JSONObject message = new JSONObject();
        message.put(Commands.REQUEST, Commands.TEST);
        SceneManager.getInstance().send(this, message);
    }

    @FXML private void signIn() { signIn(null, loginField.getText(), passwordField.getText()); }
    @FXML private void switchToRegistration () { SceneManager.getInstance().switchSceneTo(SceneManager.Scenes.REGISTRATION); }

    @Override
    public void onConnect(Session session) { }

    @Override
    public void onInput(Session session, Object input) {

        Platform.runLater(()-> {
            if (input instanceof JSONObject) {

                JSONObject json = (JSONObject)input;
                Commands cmd = (Commands)json.get(Commands.REPLY);

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
        DialogManager.showWarning(
                SceneManager.translate("error.connection-failed"),
                SceneManager.translate("error.smth-went-wrong"));
        e.printStackTrace();
    }

    @Override
    public void signIn(Session session, String user, String password) {
        JSONObject message = new JSONObject();
        message.put(Commands.REQUEST, Commands.SIGN_IN);
        message.put(Commands.USERNAME, user);
        message.put(Commands.PASSWORD, password);
        SceneManager.getInstance().send(this, message);
    }

    @Override
    public void signUp(Session session, String user, String password) { }

    @Override
    public void checkNewUserName(Session session, String name) { }

}
