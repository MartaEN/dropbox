import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.Socket;

public class SceneManager {

    private static final SceneManager thisInstance = new SceneManager();
    private Stage primaryStage;
    private Scene welcome, authentication, registration, client, test;

    final String IP_ADDRESS = "localhost";
    final int PORT = 2018;
    private Session session;
    private Socket socket;

    public enum Scenes {
        WELCOME, AUTHENTICATION, REGISTRATION, WORK;
    }

    private SceneManager () {}

    public static SceneManager getInstance() { return thisInstance; }

    public void init (Stage primaryStage) {
        this.primaryStage = primaryStage;
        primaryStage.setTitle("Yet Another Dropbox");
    }

    public void switchSceneTo (Scenes scene) {
        Platform.runLater(()-> {
            try {
                switch (scene) {
                    case WELCOME:
                        welcome = new Scene(new FXMLLoader(getClass().getResource("entrance.fxml")).load(), 400, 400);
                        primaryStage.setScene(welcome);
                        break;
                    case AUTHENTICATION:
                        authentication = new Scene(new FXMLLoader(getClass().getResource("authentication.fxml")).load(), 400, 400);
                        primaryStage.setScene(authentication);
                        break;
                    case REGISTRATION:
                        registration = new Scene(new FXMLLoader(getClass().getResource("registration.fxml")).load(), 400, 400);
                        primaryStage.setScene(registration);
                        break;
                    case WORK:
                        client = new Scene(new FXMLLoader(getClass().getResource("client.fxml")).load(), 400, 400);
                        primaryStage.setScene(client);
                        break;
                }
            } catch (IOException e) {
                //TODO
                disconnect();
                e.printStackTrace();
            }
            primaryStage.show();
        });
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
                    session = new Session(sender, socket, Session.SessionType.CLIENT);
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

    public void disconnect () {

        switchSceneTo(Scenes.WELCOME);

        if (socket == null || socket.isClosed())  return;
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
