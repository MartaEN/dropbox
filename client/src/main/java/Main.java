import javafx.application.Application;
import javafx.stage.Stage;

import java.util.Locale;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception{

        SceneManager.getInstance().init(primaryStage, new Locale("en"));
        SceneManager.getInstance().switchSceneTo(SceneManager.Scenes.WELCOME);
    }


    @Override
    public void stop() throws Exception {
        super.stop();
        SceneManager.getInstance().disconnect();
    }

    public static void main(String[] args) {
        launch(args);
    }


}
