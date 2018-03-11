import javafx.application.Application;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception{

        SceneManager.getInstance().init(primaryStage);
        SceneManager.getInstance().switchSceneTo(SceneManager.Scenes.WELCOME);
    }

    public static void main(String[] args) {
        launch(args);
    }


}
