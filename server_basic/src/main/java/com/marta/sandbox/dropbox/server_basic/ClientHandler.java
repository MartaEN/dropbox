package com.marta.sandbox.dropbox.server_basic;

import com.marta.sandbox.dropbox.common.session.ConnectionListener;
import com.marta.sandbox.dropbox.common.session.Session;
import com.marta.sandbox.dropbox.server_dispatcher.ServerDispatcher;

import java.net.Socket;
import java.util.logging.Logger;

public class ClientHandler implements ConnectionListener {

    private final ServerDispatcher SERVER_DISPATCHER;

    ClientHandler(Server server, Socket socket) {
        Session session = new Session(this, socket);
        server.getThreadPool().execute(session);
        this.SERVER_DISPATCHER = new ServerDispatcher(server.getServerDirectory(), server.getAuthService());
    }

    @Override
    public void onConnect(Session session) {
        Logger.getGlobal().info(session+ ": CONNECTED");
    }

    @Override
    public void onInput(Session session, Object input) {
        SERVER_DISPATCHER.processMessageFromClient(session, input);
    }

    @Override
    public void onDisconnect(Session session) {
        Logger.getGlobal().info(session + ": DISCONNECTED");
    }

    @Override
    public void onException(Session session, Exception e) {
        Logger.getGlobal().info(session + ": EXCEPTION");
    }

}
