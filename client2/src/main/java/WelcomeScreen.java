import javafx.fxml.FXML;

public class WelcomeScreen {

    @FXML private void goToSignIn () {
        Client.getInstance().switchSceneTo(Client.Scenes.AUTHENTICATION);
    }

    @FXML private void goToSignUp () {
        Client.getInstance().switchSceneTo(Client.Scenes.REGISTRATION);
    }

}
