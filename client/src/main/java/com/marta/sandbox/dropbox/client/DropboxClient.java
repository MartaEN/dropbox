package com.marta.sandbox.dropbox.client;

import com.marta.sandbox.dropbox.client.service.SceneManager;
import javafx.application.Application;
import javafx.stage.Stage;

import java.util.Locale;

public class DropboxClient extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception{
        SceneManager.getInstance().init(primaryStage, new Locale("en"));
        SceneManager.getInstance().switchSceneTo(SceneManager.SceneType.AUTHENTICATION);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
