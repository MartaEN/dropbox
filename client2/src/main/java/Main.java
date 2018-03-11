import javafx.application.Application;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {

        Client.getInstance().init(primaryStage);

    }

    public static void main(String[] args) {
        launch(args);
    }


}
