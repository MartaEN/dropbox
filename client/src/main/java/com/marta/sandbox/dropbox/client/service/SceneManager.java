package com.marta.sandbox.dropbox.client.service;

import com.marta.sandbox.dropbox.client.fxml.Authentication;
import com.marta.sandbox.dropbox.client.fxml.Client;
import com.marta.sandbox.dropbox.client.fxml.DialogManager;
import com.marta.sandbox.dropbox.client.fxml.Registration;
import com.marta.sandbox.dropbox.common.session.ConnectionListener;
import com.marta.sandbox.dropbox.common.session.Session;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.*;
import java.net.Socket;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

public class SceneManager implements ConnectionListener {

    private static SceneManager thisInstance;
    private Stage primaryStage;
    private SceneType currentScene;
    private Scene authentication, registration, client, test;
    private Map <SceneType, ConnectionListener> listeners;
    private boolean closed;

    private final String IP_ADDRESS = "localhost";
    private final int PORT = 2018;
    private Session session;
    private Socket socket;
    private ResourceBundle resourceBundle;

    @Override
    public void onConnect(Session session) {

    }

    @Override
    public void onInput(Session session, Object input) {
        listeners.get(currentScene).onInput(session, input);
    }

    @Override
    public void onDisconnect(Session session) {
        logout();
    }

    @Override
    public void onException(Session session, Exception e) {
        if(!closed) {
            e.printStackTrace();
            Platform.runLater(() -> {
                DialogManager.showWarning(
                        SceneManager.translate("error.smth-went-wrong"),
                        SceneManager.translate("error.connection-failed"));
            });
        }
    }

    public enum SceneType {
        AUTHENTICATION, REGISTRATION, WORK;
    }

    private SceneManager () {}

    public static SceneManager getInstance() {
        if (thisInstance == null) thisInstance = new SceneManager();
        return thisInstance;
    }

    public static String translate (String message) { return thisInstance.resourceBundle.getString(message); }

    public static Window getWindow () { return getInstance().primaryStage; }

    public void init (Stage primaryStage, Locale lang) {
        this.primaryStage = primaryStage;
        this.resourceBundle = ResourceBundle.getBundle("locales.Locale", lang);
        primaryStage.setTitle(resourceBundle.getString("title.app"));
        this.closed = false;
        this.listeners = new HashMap<>();

        try {
            authentication = new Scene(FXMLLoader.load(getClass().getClassLoader().getResource("authentication.fxml"), resourceBundle), 450, 400);
            registration = new Scene(FXMLLoader.load(getClass().getClassLoader().getResource("registration.fxml"), resourceBundle), 450, 400);
            client = new Scene(FXMLLoader.load(getClass().getClassLoader().getResource("client.fxml"), resourceBundle), 450, 400);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

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
                   break;
                }
           currentScene = scene;
           primaryStage.show();
        });
    }

    public void registerListener (SceneType scene, ConnectionListener listener) {
        listeners.put(scene, listener);
    }

    // отсылка сообщения на сервер с проверкой соединения
    public <T> void send (ConnectionListener sender, T message) {

        // если сокет не поднят
        if (socket == null || socket.isClosed()) {
            // и при этом сообщение идет из рабочего окна - вызываем обработку исключения в связи с обрывом соединения
            if ( sender.getClass() == Client.class) {
                sender.onException(session, new IOException("Connection lost"));
            // для окон авторизации и регистрации поднимаем сокет, открываем сессию, отсылаем сообщение
            } else if (sender.getClass() == Authentication.class || sender.getClass() == Registration.class) {
                try {
                    socket = new Socket(IP_ADDRESS, PORT);
                    session = new Session(this, socket, Session.SessionType.CLIENT);
                    new Thread(session).start();
                    session.send(message);
                } catch (IOException e) {
                    sender.onException(session, e);
                }
            }
        // если сокет поднят - перепроверяем слушателя и отсылаем сообщение
        } else {
            session.setConnectionListener(sender);
            session.send(message);
        }
    }

    public void sendFile (File file) {
        session.sendFileInChunks(file);
    }

    public void logout () {
        if(!closed) switchSceneTo(SceneType.AUTHENTICATION);
    }

    public void disconnect() {
        System.out.println("DISCONNECTING");
        closed = true;
        if (session != null) session.disconnect();
    }

}
