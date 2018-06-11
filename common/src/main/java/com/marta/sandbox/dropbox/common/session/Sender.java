package com.marta.sandbox.dropbox.common.session;

import java.io.File;

public interface Sender {
    <T> void send (T message);
    void sendFileInChunks(File file);
}
