package com.marta.sandbox.dropbox.client.service;

import com.marta.sandbox.dropbox.common.messaging.Commands;
import com.marta.sandbox.dropbox.common.session.ConnectionListener;
import com.marta.sandbox.dropbox.common.session.Session;
import com.marta.sandbox.dropbox.common.settings.ServerConstants;
import org.json.simple.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.Socket;

// класс-синглтон, управляющий сетевым подключением
public class NetworkManager implements ServerConstants, ConnectionListener {

    private static NetworkManager thisInstance;
    private Socket socket;
    private Session session;

    private NetworkManager () { }
    public static NetworkManager getInstance () {
        if (thisInstance == null) thisInstance = new NetworkManager();
        return thisInstance;
    }

    // подключение к сети и запуск сессии
    private void connect () {
        try {
            socket = new Socket(SERVER_URL, PORT);
            session = new Session(this, socket);
            Thread t = new Thread(session);
            t.setDaemon(true);
            t.start();
        } catch (IOException e) {
            SceneManager.getInstance().showExceptionMessage();
        }
    }

    // Ниже четыре метода - имплементация интерфейса ConnectionListener для работы с сессией -
    // реализуют то, что требуется на стороне клиента при подключении, получении входящего сообщения,
    // вылете исключения и отключении. Сокет и потоки управляются классом сессии непосредственно
    @Override
    public void onConnect(Session session) { }

    @Override
    public void onInput(Session session, Object input) {
        SceneManager.getInstance().getCurrentListener().onInput(input);
    }

    @Override
    public void onDisconnect(Session session) {
        SceneManager.getInstance().logout();
    }

    @Override
    public void onException(Session session, Exception e) {
        SceneManager.getInstance().showExceptionMessage();
    }

    // отсылка сообщений на сервер
    public <T> void send (T message) {
        if (socket == null || socket.isClosed()) connect();
        session.send(message);
    }

    public void sendFile (File file) {
        if(socket == null || socket.isClosed()) connect();
        session.sendFileInChunks(file);
    }

    void requestFileListUpdate() {
        JSONObject message = new JSONObject();
        message.put(Commands.MESSAGE, Commands.LIST_CONTENTS);
        NetworkManager.getInstance().send(message);
    }
}
