package com.marta.sandbox.dropbox.common;

import org.json.simple.JSONObject;

import java.io.*;
import java.net.Socket;
import java.util.Arrays;

public class Session implements Runnable {

    private final Socket socket;
    private ConnectionListener connectionListener;
    private final int BUFFER_SIZE = (int)Math.pow(2,20);

    private ObjectInputStream in;
    private ObjectOutputStream out;

    public enum SessionType {
        SERVER, CLIENT
    }


    public void setConnectionListener (ConnectionListener listener) {
        this.connectionListener = listener;
    }

    public Session (ConnectionListener connectionListener, Socket socket, SessionType type) {
        this.socket = socket;
        this.connectionListener = connectionListener;
        try {
            switch (type) {
                // different for server and client int order to avoid deadlock
                case SERVER:
                    in = new ObjectInputStream(socket.getInputStream());
                    out = new ObjectOutputStream(socket.getOutputStream());
                    break;
                case CLIENT:
                    out = new ObjectOutputStream(socket.getOutputStream());
                    in = new ObjectInputStream(socket.getInputStream());
                    break;
            }
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
        } finally {
            connectionListener.onDisconnect(this);
        }
    }

    public synchronized <T> void send (T object) {
        System.out.println(this + ": sending "+object);
        try {
            Thread.sleep(500); //TODO заглушка, чтобы сообщение не отправлялось раньше запуска метода run()
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        try {
            out.writeObject(object);
            out.flush();
        } catch (IOException e) {
            connectionListener.onException(this, e);
            disconnect();
        }
    }

    public void sendFile (File file) {
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
                e.printStackTrace();
            }
        });
        t.start();
    }

    public synchronized void disconnect() {
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
        return "com.marta.sandbox.dropbox.common.Session: " + socket.getInetAddress() + ": " + socket.getPort();
    }
}
