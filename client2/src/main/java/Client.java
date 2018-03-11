import com.marta.dropbox.connection.ConnectionListener;
import com.marta.dropbox.connection.MyFileList;
import com.marta.dropbox.connection.Session;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Client implements ConnectionListener {

    private static Client thisInstance;
    private Stage primaryStage;
    private Scene welcomeScreen, signInScreen, signUpScreen, workScreen;
    private ConnectionListener signIn, signUp, work;

    enum Scenes { WELCOME, AUTHENTICATION, REGISTRATION, WORK; }

    private final String IP_ADDRESS = "localhost";
    private final int PORT = 2018;
    private final Path ROOT = Paths.get("_client_downloads"); // TODO добавить возможность выбора клиентом
    private Session session;
    private Socket socket;

    private Client () { }

    static Client getInstance () {
        return thisInstance == null ? new Client () : thisInstance;
    }

    void init (Stage primaryStage) {
        this.primaryStage = primaryStage;
        primaryStage.setTitle("Yet Another Dropbox");

        if(!Files.exists(ROOT, LinkOption.NOFOLLOW_LINKS)) {
            try {
                Files.createDirectory(ROOT);
            } catch (IOException e) {
                onException(session, e);
            }
        }
    }

    @Override
    public void onConnect(Session session) { }

    @Override
    public void onInput(Session session, Object input) {

        Platform.runLater(()-> {

            if (input instanceof String) {

                System.out.println("client - incoming message identified as String: "+ input);

                String[] tokens = ((String) input).split(" ");
                switch (tokens[0]) {
                    case "/welcome":
                        switchSceneTo(Scenes.WORK);
                        break;
                    case "/notfound":
                        JOptionPane.showConfirmDialog(null,
                                "Файл не найден",
                                "Ошибка сервера", JOptionPane.WARNING_MESSAGE);
                        break;
                    case "/ok":
                        break;
                    default:
                        System.out.println("client - THIS SHOULD NOT HAPPEN - UNKNOWN COMMAND FROM SERVER: " + input);
                }

                // или обрабатываем входящие файлы
            } else if (input instanceof File) {
                System.out.println("client - incoming message identified as File" + input);
                FileProcessor.saveFile(ROOT, (File) input);

                // или обрабатываем полученный список файлов
            } else if (input instanceof MyFileList){
                System.out.println("client - incoming message identified as MyFileList: " + input);
                listFiles(session, (MyFileList)input);

            } else {
                System.out.println("client - THIS SHOULD NOT HAPPEN - UNKNOWN TYPE OF INCOMING MESSAGE: " + input);
                //TODO
            }
        });
    }

    @Override
    public void onDisconnect(Session session) {

    }

    @Override
    public void onException(Session session, Exception e) {
        e.printStackTrace();
        JOptionPane.showMessageDialog(null, "Потеряно соединение с сервером",
                "Что-то пошло не так...", JOptionPane.WARNING_MESSAGE);
        Client.getInstance().disconnect();
    }


    public void switchSceneTo (Scenes scene) {
        Platform.runLater(()-> {
            try {
                switch (scene) {
                    case WELCOME:
                        welcomeScreen = new Scene(new FXMLLoader(getClass()
                                .getResource("resources/entrance.fxml")).load(), 400, 400);
                        primaryStage.setScene(welcomeScreen);
                        break;
                    case AUTHENTICATION:
                        signInScreen = new Scene(new FXMLLoader(getClass()
                                .getResource("resources/signInScreen.fxml")).load(), 400, 400);
                        primaryStage.setScene(signInScreen);
                        break;
                    case REGISTRATION:
                        signUpScreen = new Scene(new FXMLLoader(getClass()
                                .getResource("resources/signUpScreen.fxml")).load(), 400, 400);
                        primaryStage.setScene(signUpScreen);
                        break;
                    case WORK:
                        workScreen = new Scene(new FXMLLoader(getClass()
                                .getResource("resources/workScreen.fxml")).load(), 400, 400);
                        primaryStage.setScene(workScreen);
                        break;
                }
            } catch (IOException e) {
                //TODO
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
            if ( sender.getClass() == WorkScreen.class) {
                sender.onException(session, new IOException("Connection lost"));
                // для окон авторизации и регистрации поднимаем сокет, открываем сессию, отсылаем сообщение
            } else if (sender.getClass() == SignInScreen.class || sender.getClass() == SignUpScreen.class) {
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
