package com.marta.sandbox.dropbox.common;

public interface ConnectionListener {

    void onConnect (Session session);
    void onInput(Session session, Object input);
    void onDisconnect(Session session);
    void onException(Session session, Exception e);

}
