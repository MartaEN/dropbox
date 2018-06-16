package com.marta.sandbox.dropbox.common.session;

import com.marta.sandbox.dropbox.common.messaging.Commands;
import io.netty.handler.codec.serialization.ObjectDecoderInputStream;
import io.netty.handler.codec.serialization.ObjectEncoderOutputStream;
import org.json.simple.JSONObject;

import java.io.*;
import java.net.Socket;

import java.util.Arrays;

public class Session implements Runnable, Sender {

    private final Socket socket;
    private ConnectionListener connectionListener;
    private final int BUFFER_SIZE = 1024 * 1024;

    private ObjectDecoderInputStream in;
    private ObjectEncoderOutputStream out;

    public Session (ConnectionListener connectionListener, Socket socket) {
        this.socket = socket;
        this.connectionListener = connectionListener;
        try {
            in = new ObjectDecoderInputStream(socket.getInputStream());
            out = new ObjectEncoderOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run () {
        try {
            connectionListener.onConnect(this);
            while (!Thread.currentThread().isInterrupted()) {
                connectionListener.onInput(this, in.readObject());
            }
        } catch (IOException | ClassNotFoundException e) {
            connectionListener.onException(this, e);
            disconnect();
        }
    }

    public synchronized <T> void send (T object) {
        System.out.println(this + ": sending "+object);
        try {
            out.writeObject(object);
            out.flush();
        } catch (IOException e) {
            connectionListener.onException(this, e);
            disconnect();
        }
    }

    public void sendFileInChunks(File file) {

        // TODO перестало работать с разбиением на куски, отсылает только если кусок один (файл меньше 1 Мб)
        Thread t = new Thread (() -> {
            System.out.println(this + ": sending "+file.getName());
            try (InputStream input = new BufferedInputStream(new FileInputStream(file), BUFFER_SIZE)) {
                byte [] buffer = new byte [BUFFER_SIZE];
                long size = file.length();
                int bytesRead;
                int sequence = 0;
                while ((bytesRead = input.read(buffer))!=-1) {
                    JSONObject message = new JSONObject();
                    message.put(Commands.MESSAGE, Commands.FILE);
                    message.put(Commands.FILE, file);
                    message.put(Commands.SEQUENCE, ++sequence);
                    message.put(Commands.BYTES_LEFT, size-=bytesRead);
                    if(bytesRead == buffer.length) message.put (Commands.DATA, buffer);
                    else message.put (Commands.DATA, Arrays.copyOf(buffer,bytesRead));
                    send(message);
                }
            } catch (IOException e) {
                connectionListener.onException(this, e);
                disconnect();
            }
        });
        t.start();
    }

    private synchronized void disconnect() {
        connectionListener.onDisconnect(this);
        Thread.currentThread().interrupt();
        try {
            in.close();
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public String toString() {
        return "Session: " + socket.getInetAddress() + ": " + socket.getPort();
    }
}
