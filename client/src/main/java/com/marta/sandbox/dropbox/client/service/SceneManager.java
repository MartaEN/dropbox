package com.marta.sandbox.dropbox.client.service;

import com.marta.sandbox.dropbox.client.fxml.DialogManager;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.*;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

// класс-синглтон, управляющий переключением экранов
public class SceneManager {

    private static SceneManager thisInstance;
    private Stage primaryStage;
    private SceneType currentScene;
    private Scene authentication, registration, client;
    private ResourceBundle resourceBundle;
    private Map<SceneManager.SceneType, InputListener> listeners;

    public enum SceneType {
        AUTHENTICATION, REGISTRATION, WORK;
    }

    private SceneManager () {}

    public static SceneManager getInstance() {
        if (thisInstance == null) thisInstance = new SceneManager();
        return thisInstance;
    }

    // статический метод для "перевода" текстов в выбранную локаль
    public static String translate (String message) { return thisInstance.resourceBundle.getString(message); }

    // статический метод для получения ссылки на primaryStage
    public static Window getWindow () { return getInstance().primaryStage; }

    // при запуске загружаем локаль и экраны
    public void init (Stage primaryStage, Locale lang) {
        this.primaryStage = primaryStage;
        this.resourceBundle = ResourceBundle.getBundle("locales.Locale", lang);
        primaryStage.setTitle(resourceBundle.getString("title.app"));
        this.listeners = new HashMap<>();

        try {
            authentication = new Scene(FXMLLoader.load(getClass().getClassLoader().getResource("authentication.fxml"), resourceBundle), 450, 400);
            registration = new Scene(FXMLLoader.load(getClass().getClassLoader().getResource("registration.fxml"), resourceBundle), 450, 400);
            client = new Scene(FXMLLoader.load(getClass().getClassLoader().getResource("client.fxml"), resourceBundle), 450, 400);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // каждый экран при загрузке регистрируется в подписке на входящие сетевые сообщения,
    // и SceneManager по запросу NetworkManager'а сообщает последнему текущий экран-подписчик
    public void registerListener (SceneType scene, InputListener listener) {
        listeners.put(scene, listener);
    }

    InputListener getCurrentListener () {
        return listeners.get(currentScene);
    }

    // метод для переключения между экранами
    public void switchSceneTo (SceneType scene) {
        Platform.runLater(()-> {
           switch (scene) {
               case AUTHENTICATION:
                   primaryStage.setScene(authentication);
                   break;
               case REGISTRATION:
                   primaryStage.setScene(registration);
                   break;
               case WORK:
                   primaryStage.setScene(client);
                   NetworkManager.getInstance().requestFileListUpdate();
                   break;
                }
           currentScene = scene;
           primaryStage.show();
        });
    }

    void logout () {
        switchSceneTo(SceneType.AUTHENTICATION);
    }

    void showExceptionMessage () {
        Platform.runLater(() -> {
            DialogManager.showWarning(
                    SceneManager.translate("error.smth-went-wrong"),
                    SceneManager.translate("error.connection-failed"));
        });
    }

}
