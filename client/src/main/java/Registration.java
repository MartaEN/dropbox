import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import org.json.simple.JSONObject;

public class Registration implements ConnectionListener, SignUpChecker {

    @FXML private TextField username;
    @FXML private TextField password;
    @FXML private TextField password1;

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
                        DialogManager.showWarning(
                                SceneManager.translate("message.sign-up"),
                                SceneManager.translate("message.sign-up-success"));
                        SceneManager.getInstance().switchSceneTo(SceneManager.Scenes.WORK);
                        break;
                    case NOT_ADMITTED:
                    case FAIL:
                        DialogManager.showWarning(
                                SceneManager.translate("message.sign-up"),
                                (String)json.getOrDefault(Commands.FAIL_DETAILS, SceneManager.translate("message.sign-up-fail")));
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
        SceneManager.getInstance().onException(e);
    }


    @FXML
    private void checkNewUserName() {
        if (isUsernameOK(username.getText())) {
            checkNewUserName(username.getText());
        } else {
            DialogManager.showWarning(
                    SceneManager.translate("message.sign-up"),
                    SceneManager.translate("message.sign-up-username-rules"));
            username.clear();
        }
    }


    @Override
    public void checkNewUserName(String name) {
        JSONObject message = new JSONObject();
        message.put(Commands.REQUEST, Commands.CHECK_NEW_USER_NAME);
        message.put(Commands.USERNAME, name);
        SceneManager.getInstance().send(this, message);
    }

    @FXML
    private void signUp() {
        if (!isUsernameOK(username.getText())) {
            DialogManager.showWarning(
                    SceneManager.translate("message.sign-up"),
                    SceneManager.translate("message.sign-up-username-rules"));
            username.clear();
        } else if (!isPasswordOK(password.getText())) {
            DialogManager.showWarning(
                    SceneManager.translate("message.sign-up"),
                    SceneManager.translate("message.sign-up-password-rules"));
            password.clear();
            password1.clear();
        } else if (!(password1.getText().equals(password.getText()))) {
            DialogManager.showWarning(
                    SceneManager.translate("message.sign-up"),
                    SceneManager.translate("message.sign-up-password-repeat"));
            password1.clear();
        } else {
            signUp(username.getText(), password.getText());
        }
    }

    @Override
    public void signUp(String user, String password) {
        JSONObject message = new JSONObject();
        message.put(Commands.REQUEST, Commands.SIGN_UP);
        message.put(Commands.USERNAME, user);
        message.put(Commands.PASSWORD, password);
        SceneManager.getInstance().send(this, message);
    }

    @FXML private void checkPassword () {
        String pwd = password.getText();
        if (!isPasswordOK(pwd)) {
            DialogManager.showWarning(
                    SceneManager.translate("message.sign-up"),
                    SceneManager.translate("message.sign-up-password-rules"));
            password.clear();
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
