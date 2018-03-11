import java.io.*;
import java.net.Socket;

public class Session implements Runnable {

    private final Socket socket;
    private ConnectionListener connectionListener;

    private ObjectInputStream in;
    private ObjectOutputStream out;

    public enum SessionType {
        SERVER, CLIENT
    }


    public void setConnectionListener (ConnectionListener listener) {
        this.connectionListener = listener;
    }

    public Session (ConnectionListener connectionListener, Socket socket, SessionType type) {
        System.out.println("inside session constructor ");
        this.socket = socket;
        this.connectionListener = connectionListener;
        try {
            switch (type) {
                // разный порядок для сервера и клиента во избежание дедлока
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

        System.out.println("session " + this + " inside run method");

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

    private synchronized void disconnect() {
        Thread.currentThread().interrupt();
        try {
            socket.close();
        } catch (IOException e) {
            connectionListener.onException(this, e);
        }
    }

    @Override
    public String toString() {
        return "Session: " + socket.getInetAddress() + ": " + socket.getPort();
    }
}
