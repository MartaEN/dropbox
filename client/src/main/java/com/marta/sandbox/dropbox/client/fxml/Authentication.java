package com.marta.sandbox.dropbox.client.fxml;

import com.marta.sandbox.dropbox.client.service.*;
import com.marta.sandbox.dropbox.common.messaging.Commands;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import org.json.simple.JSONObject;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Authentication implements InputListener {

    @FXML private TextField loginField;
    @FXML private PasswordField passwordField;

    @FXML private void initialize () {
        SceneManager.getInstance().registerListener(SceneManager.SceneType.AUTHENTICATION, this);
    }

    @FXML private void signIn() { signIn(loginField.getText(), passwordField.getText()); }
    @FXML private void switchToRegistration () { SceneManager.getInstance().switchSceneTo(SceneManager.SceneType.REGISTRATION); }

    @Override
    public void onInput(Object input) {

        Platform.runLater(()-> {
            if (input instanceof JSONObject) {

                JSONObject json = (JSONObject)input;
                Commands cmd = (Commands)json.get(Commands.MESSAGE);

                switch (cmd) {
                    case ADMITTED:
                        SceneManager.getInstance().switchSceneTo(SceneManager.SceneType.WORK);
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

    public void signIn(String user, String password) {
        JSONObject message = new JSONObject();
        message.put(Commands.MESSAGE, Commands.SIGN_IN);
        message.put(Commands.USERNAME, user);
        message.put(Commands.PASSWORD, hash(password));
        NetworkManager.getInstance().send(message);
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
