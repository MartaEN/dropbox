import javafx.fxml.FXML;

public class Entrance {

    @FXML private void goToSignIn () {
        SceneManager.getInstance().switchSceneTo(SceneManager.Scenes.AUTHENTICATION);
    }

    @FXML private void goToSignUp () {
        SceneManager.getInstance().switchSceneTo(SceneManager.Scenes.REGISTRATION);
    }

}
