// В проекте следующие модули: 
// common - общие классы и интерфейсы для сервера и клиента, включая адрес порта, список команд и т.п.
// server_basic
// server_netty
// server_dispatcher - эти три модуля представляют собой два варианта сервера, обычный и на нетти (см. примечание 1 ниже)
// authentication - авторизация и ведение списка пользователей
// fileprocessor - хелпер для выполнения операций с файлами, так себе полезный и, возможно, лишний
// downloadmanager - хелпер для организации загрузки файлов, очень полезный
// client - собственно клиент на fxml
//
//(Примечание 1) У меня уже был сервер от первой попытки, и мне хотелось по максимуму сохранить написанное
//(вообще оставить два варианта сервера). Поэтому: весь блок обработки входящего запроса я вынесла
// в отдельный модуль server_dispatcher, и им пользуются два серверных блока - server_basic и server_netty.
// Было весело, потому что пришлось писать адаптер для ChannelHandlerContext.
// Миграция server_basic вроде прошла успешно, а server_netty пока что падает в ситуациях, когда требуется
// передача нескольких ответных сообщений - пока разбираюсь с этим.
//
// Всe данные, передаваемые по сети, заворачиваются в объекты класса JSONObject (com.googlecode.json-simple).
// Это позволяет использовать один и тот же формат для разных сообщений, и ещё оооооочень удобно в распечатке
// при тестировании. Повторяющиеся команды и сообщения стандартизированы через enum Commands в модуле common.


//*******************************************************//
//               модуль common                           //
//*******************************************************//

//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~//
// ServerConstants - порт и т.п., нужные как серверу, так и клиенту

package com.marta.sandbox.dropbox.common.settings;

public interface ServerConstants {
    String SERVER_URL = "localhost";
    int PORT = 2018;
    int MAX_OBJ_SIZE = 1024 * 1024 * 100; // 100 mb
}

//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~//
// ConnectionListener - Интерфейс, определяющий, что должны уметь классы, желающие
// пользоваться сетевым соединением

package com.marta.sandbox.dropbox.common.session;

public interface ConnectionListener {

    void onConnect (Session session);
    void onInput(Session session, Object input);
    void onDisconnect(Session session);
    void onException(Session session, Exception e);

}


//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~//
// Sender - Интерфейс, определяющий, что должны уметь классы, собирающиеся
// отправлять что-либо по сети, он потом понадобится сервер-диспетчеру

package com.marta.sandbox.dropbox.common.session;

import java.io.File;

public interface Sender {
    <T> void send (T message);
    void sendFileInChunks(File file);
}

//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~//
// Session - Класс сессии - такой runnable, который описывает сетевое взаимодействие.
// Его запускает клиент и базовый сервер, нетти-сервер обходится без него.
// Однако для случая клиента, обращающегося к нетти-серверу, in и out
// заявлены как ObjectDecoderInputStream и ObjectEncoderOutputStream.
// Он работает с классами, реализующими интерфейс ConnectionListener.
// Также он реализует интерфейс Sender, то есть может отправить простое сообщение
// или файл, расписленный на куски. Для отсылки файла при этом поднимается отдельный поток,
// что позволяет отсылать файлы асинхронно (правильно тут слово стоит? в фоном режиме, короче)

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
    private final int BUFFER_SIZE = (int)Math.pow(2,20);

    private ObjectDecoderInputStream in;
    private ObjectEncoderOutputStream out;


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
                    in = new ObjectDecoderInputStream(socket.getInputStream());
                    out = new ObjectEncoderOutputStream(socket.getOutputStream());
                    break;
                case CLIENT:
                    out = new ObjectEncoderOutputStream(socket.getOutputStream());
                    in = new ObjectDecoderInputStream(socket.getInputStream());
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
            Thread.sleep(500); //TODO позорная заглушка, чтобы сообщение не отправлялось раньше запуска метода run()
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

    public void sendFileInChunks(File file) {
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
        return "Session: " + socket.getInetAddress() + ": " + socket.getPort();
    }
}

//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~//
// Commands - Словарик для общения клиента с сервером

package com.marta.sandbox.dropbox.common.messaging;

public enum Commands {

    MESSAGE,

    DELETE,
    RENAME,
    DOWNLOAD,
    FILE_NAME,
    NEW_FILE_NAME,
    DIRECTORY_NAME,

    FILE,
    DATA,
    BYTES_LEFT,
    SEQUENCE,

    LIST_CONTENTS,
    FILE_LIST,
    DIRECTORY_DOWN,
    DIRECTORY_UP,
    CREATE_DIRECTORY,

    SIGN_IN,
    SIGN_UP,
    CHECK_NEW_USER_NAME,
    USERNAME,
    PASSWORD,

    TEST,
    OK,
    FAIL,
    FAIL_DETAILS,
    ADMITTED,
    NOT_ADMITTED,
    USERNAME_OK

}

//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~//
// MyFile - Класс для описания информации о файле, отображаемой клиенту

package com.marta.sandbox.dropbox.common.messaging;

import java.io.Serializable;

public class MyFile implements Serializable {

    private FileType type;
    private String name;
    private int size;

    public enum FileType {
        FILE (" "), DIR (">");

        String value;

        FileType(String value) {
            this.value = value;
        }
    }

    public MyFile(FileType type, String name, int size) {
        this.type = type;
        this.name = name;
        this.size = size;
    }

    public FileType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public int getSize() {
        return size;
    }
}

//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~//
// MyFileList - Эта обёртка не помню зачем понадобилась - возможно, лишняя, перепроверю

package com.marta.sandbox.dropbox.common.messaging;

import java.io.Serializable;
import java.util.ArrayList;


public class MyFileList implements Serializable{

    private ArrayList<MyFile> fileList;

    public MyFileList(ArrayList<MyFile> fileList) {
        this.fileList = fileList;
    }

    public ArrayList<MyFile> getFileList() {
        return fileList;
    }
}


//*******************************************************//
//               модуль server_basic                     //
//*******************************************************//

//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~//
// Server - Собственно сам сервер

package com.marta.sandbox.dropbox.server_basic;

import com.marta.sandbox.authentication.AuthService;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import com.marta.sandbox.authentication.*;
import com.marta.sandbox.authentication.exceptions.*;
import com.marta.sandbox.dropbox.common.settings.ServerConstants;

public class Server implements ServerConstants {

    private final Path ROOT = Paths.get("_server_repository");
    private final String USER_DATABASE_NAME = "users.db";

    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private AuthService authService;

    ExecutorService getThreadPool() { return threadPool; }
    AuthService getAuthService() { return authService; }
    Path getServerDirectory () { return  ROOT; }

    public static void main(String[] args) {
        new Server();
    }

    Server() {

        // Для хранения файлов на сервере выделяем директорию.
        // В ней будет лежать файл базы данных пользователей для сервиса авторизации
        // и файлы пользователей, разложенные по папкам с именами логинов.
        // Шаг 1: проверяем наличие папки на сети, при отсутствии - создаем.
        if(Files.exists(ROOT, LinkOption.NOFOLLOW_LINKS)) {
            Logger.getGlobal().info("ROOT DIRECTORY " + ROOT);
        } else {
            try {
                Files.createDirectory(ROOT);
                Logger.getGlobal().warning("ATTENTION! --- NEW ROOT DIRECTORY CREATED " + ROOT);
            } catch (IOException e) {
                Logger.getGlobal().severe("FATAL ERROR! FAILED TO CREATE ROOT DIRECTORY. \nSERVER CLOSED");
                return;
            }
        }

        // Шаг 2: Запускаем службу авторизации
        try {
            authService = new SqliteAuthService(ROOT.resolve(USER_DATABASE_NAME).toAbsolutePath().toString());
            authService.start();
            Logger.getGlobal().info("AUTHORISATION SERVICE STARTED");
        } catch (AuthServiceException e) {
            Logger.getGlobal().severe("FATAL ERROR! FAILED TO START AUTHORISATION SERVICE. \nSERVER CLOSED");
            return;
        }

        // Шаг 3: Открываем пул потоков и запускаем сервер
        try {
            threadPool = Executors.newCachedThreadPool();
            serverSocket = new ServerSocket(PORT);
            Logger.getGlobal().info("SERVER STARTED");
            while (true) {
                Socket socket = serverSocket.accept();
                new ClientHandler(this, socket);
            }
        } catch (IOException e) {
            Logger.getGlobal().severe("ERROR CONNECTING TO SOCKET: " + e.getMessage());
        } finally {
            if(authService != null) authService.stop();
            if(threadPool != null) threadPool.shutdown();
            try {
                if (serverSocket!= null) serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            Logger.getGlobal().info("SERVER STOPPED");
        }
    }
}

//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~//
// ClientHandler - Клиент-хандлер. Он открывает сессию и реализует интерфейс ConnectionListener.
// Сам, в общем-то, ничего не делает, отдает всю работу ServerDispatcher'y
// (потому что он один общий на два варианта сервера).
// Этому диспетчеру он вместе со входящим сообщением отдает сессию как
// объект класса, реализующего интерфейс Sender - чтобы диспетчер мог по результатам
// обработки запроса что-то ответить клиенту.

package com.marta.sandbox.dropbox.server_basic;

import com.marta.sandbox.dropbox.common.session.ConnectionListener;
import com.marta.sandbox.dropbox.common.session.Session;
import com.marta.sandbox.dropbox.server_dispatcher.ServerDispatcher;

import java.net.Socket;
import java.util.logging.Logger;

public class ClientHandler implements ConnectionListener {

    private final ServerDispatcher SERVER_DISPATCHER;

    ClientHandler(Server server, Socket socket) {
        Session session = new Session(this, socket, Session.SessionType.SERVER);
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

//*******************************************************//
//               модуль server_netty                     //
//*******************************************************//


//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~//
// ServerNetty - Собственно сам сервер. Первые два шага запуска - такие же, как в базовом сервере.

package com.marta.sandbox.dropbox.server_netty;

import com.marta.sandbox.authentication.AuthService;
import com.marta.sandbox.authentication.SqliteAuthService;
import com.marta.sandbox.authentication.exceptions.AuthServiceException;
import com.marta.sandbox.dropbox.common.settings.ServerConstants;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

public class ServerNetty implements ServerConstants {

    private final Path ROOT = Paths.get("_server_repository");
    private final String USER_DATABASE_NAME = "users.db";
    private AuthService authService;

    private void run() {

        // Для хранения файлов на сервере выделяем директорию.
        // В ней будет лежать файл базы данных пользователей для сервиса авторизации
        // и файлы пользователей, разложенные по папкам с именами логинов.
        // Шаг 1: проверяем наличие папки на сети, при отсутствии - создаем.
        if(Files.exists(ROOT, LinkOption.NOFOLLOW_LINKS)) {
            Logger.getGlobal().info("ROOT DIRECTORY " + ROOT);
        } else {
            try {
                Files.createDirectory(ROOT);
                Logger.getGlobal().warning("ATTENTION! --- NEW ROOT DIRECTORY CREATED " + ROOT);
            } catch (IOException e) {
                Logger.getGlobal().severe("FATAL ERROR! FAILED TO CREATE ROOT DIRECTORY. \nSERVER CLOSED");
                return;
            }
        }

        // Шаг 2: Запускаем службу авторизации
        try {
            authService = new SqliteAuthService(ROOT.resolve(USER_DATABASE_NAME).toAbsolutePath().toString());
            authService.start();
            Logger.getGlobal().info("AUTHORISATION SERVICE STARTED");
        } catch (AuthServiceException e) {
            Logger.getGlobal().severe("FATAL ERROR! FAILED TO START AUTHORISATION SERVICE. \nSERVER CLOSED");
            return;
        }

        // Шаг 3: Запускаем нетти-сервер
        EventLoopGroup mainGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(mainGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
//                     .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        protected void initChannel(SocketChannel socketChannel) {
                            socketChannel.pipeline().addLast(
                                    new ObjectEncoder(),
                                    new ObjectDecoder(MAX_OBJ_SIZE, ClassResolvers.cacheDisabled(null)),
                                    new ClientHandler(ROOT.toAbsolutePath(), authService)
                            );
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);
            ChannelFuture future = b.bind(ServerConstants.PORT).sync();
            future.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            mainGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
            authService.stop();
        }
    }

    public static void main(String[] args) {
        new ServerNetty().run();
    }
}

//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~//
// ClientHandler - Клиент-хандлер. Очень похож на предыдущий. Только диспетчеру в качестве Sender'а отдает
// адаптер к ChannelHandlerContext.
// И вот тут что-то не работает - одиночные ответные сообщения проскакивают отлично,
// а множественные валят программу. Пока разбираюсь.

package com.marta.sandbox.dropbox.server_netty;

import com.marta.sandbox.authentication.AuthService;
import com.marta.sandbox.dropbox.server_dispatcher.ServerDispatcher;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.net.InetAddress;
import java.nio.file.Path;
import java.util.logging.Logger;

public class ClientHandler extends ChannelInboundHandlerAdapter {

    private final ServerDispatcher SERVER_DISPATCHER;

    ClientHandler(Path serverDirectory, AuthService authService) {
        this.SERVER_DISPATCHER = new ServerDispatcher(serverDirectory, authService);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        Logger.getGlobal().info("CLIENT CONNECTED: " + InetAddress.getLocalHost().getHostName());
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        SERVER_DISPATCHER.processMessageFromClient(new ChannelHandlerContextAdapter(ctx), msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        SERVER_DISPATCHER.setAuth(false);
        ctx.flush();
        ctx.close();
    }
}

//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~//
// ChannelHandlerContextAdapter - Адаптер к ClientHandlerContext, по замыслу позволяющий диспетчеру
// работать с ним так же, как с сессией в базовом варианте (по факту пока не вполне позволяющий)

package com.marta.sandbox.dropbox.server_netty;

import com.marta.sandbox.dropbox.common.messaging.Commands;
import com.marta.sandbox.dropbox.common.settings.ServerConstants;
import com.marta.sandbox.dropbox.common.session.Sender;
import io.netty.channel.ChannelHandlerContext;
import org.json.simple.JSONObject;

import java.io.*;
import java.util.Arrays;

public class ChannelHandlerContextAdapter implements ServerConstants, Sender {

    private ChannelHandlerContext ctx;

    ChannelHandlerContextAdapter(ChannelHandlerContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public <T> void send(T message) {
        ctx.writeAndFlush(message);
    }

    @Override
    public void sendFileInChunks(File file) {
            Thread t = new Thread (() -> {
                System.out.println(this + ": sending "+file.getName());
                try (InputStream input = new BufferedInputStream(new FileInputStream(file), MAX_OBJ_SIZE)) {
                    byte [] buffer = new byte [MAX_OBJ_SIZE];
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
                        ctx.write(message);
                    }
                    ctx.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            t.start();
    }
}


//*******************************************************//
//               модуль server_dispatcher                //
//*******************************************************//

//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~//
// ServerDispatcher - Единственный класс в этом модуле.
// Его задача - обработать входящие сообщения от клиента

package com.marta.sandbox.dropbox.server_dispatcher;

import com.marta.sandbox.authentication.AuthService;
import com.marta.sandbox.authentication.exceptions.*;
import com.marta.sandbox.dropbox.common.messaging.Commands;
import com.marta.sandbox.dropbox.common.messaging.MyFile;
import com.marta.sandbox.dropbox.common.messaging.MyFileList;
import com.marta.sandbox.dropbox.common.session.Sender;
import com.marta.sandbox.dropbox.downloadmanager.DownloadManager;
import com.marta.sandbox.dropbox.downloadmanager.DownloadManagerException;
import com.marta.sandbox.file_helper.FileProcessor;
import com.marta.sandbox.file_helper.FileProcessorException;
import org.json.simple.JSONObject;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.logging.Logger;

public class ServerDispatcher {

    private final AuthService AUTH_SERVICE;
    private final Path SERVER_DIRECTORY; // нужна, чтобы знать, куда пользователя уже не пускать
    private Path currentDirectory; // нужна, чтобы знать, в какой серверной папке работает пользователь
    private String login; // заведено про запас, пока не используется
    private boolean isAuth; // заведено про запас, пока не используется

    public ServerDispatcher (Path serverDirectory, AuthService authService) {
        this.AUTH_SERVICE = authService;
        this.SERVER_DIRECTORY = serverDirectory;
        this.currentDirectory = serverDirectory;
    }

    public void processMessageFromClient (Sender sender, Object input) {

        if (input instanceof JSONObject) {

            JSONObject json = (JSONObject)input;
            Commands cmd = (Commands)json.get(Commands.MESSAGE);

            switch (cmd) {
                case CHECK_NEW_USER_NAME:
                    checkNewUserName(sender, (String)json.get(Commands.USERNAME));
                    break;
                case CREATE_DIRECTORY:
                    createDirectory(sender, (String)json.get(Commands.DIRECTORY_NAME));
                    break;
                case DELETE:
                    delete(sender, (String)json.get(Commands.FILE_NAME));
                    break;
                case DIRECTORY_DOWN:
                    directoryDown(sender, (String)json.get(Commands.DIRECTORY_NAME));
                    break;
                case DIRECTORY_UP:
                    directoryUp(sender);
                    break;
                case DOWNLOAD:
                    downloadFile(sender, (String)json.get(Commands.FILE_NAME));
                    break;
                case FILE:
                    saveFile(sender,
                            (File)json.get(Commands.FILE),
                            (int)json.get(Commands.SEQUENCE),
                            (long)json.get(Commands.BYTES_LEFT),
                            (byte[])json.get(Commands.DATA));
                    break;
                case LIST_CONTENTS:
                    listFiles(sender);
                    break;
                case RENAME:
                    rename(sender, (String)json.get(Commands.FILE_NAME), (String)json.get(Commands.NEW_FILE_NAME));
                    break;
                case SIGN_IN:
                    signIn(sender, (String)json.get(Commands.USERNAME), (String)json.get(Commands.PASSWORD));
                    break;
                case SIGN_UP:
                    signUp(sender, (String)json.get(Commands.USERNAME), (String)json.get(Commands.PASSWORD));
                    break;
                case TEST:
                    sendOK(sender);
                    break;
                default:
                    Logger.getGlobal().warning("THIS SHOULD NOT HAPPEN - UNKNOWN COMMAND FROM USER");
            }

        } else {
            Logger.getGlobal().warning("THIS SHOULD NOT HAPPEN - UNKNOWN TYPE OF INCOMING MESSAGE");
        }
    }

    private void signIn(Sender sender, String user, String password) {
        JSONObject message = new JSONObject();
        if( AUTH_SERVICE.isLoginAccepted(user,password)) {
            currentDirectory = currentDirectory.resolve(user);
            if (FileProcessor.fileExists(currentDirectory)) {
                this.login = user;
                this.isAuth = true;
                message.put(Commands.MESSAGE, Commands.ADMITTED);
            } else {
                message.put(Commands.MESSAGE, Commands.FAIL);
                message.put(Commands.FAIL_DETAILS, "Ошибка подключения к удаленному репозиторию");
                Logger.getGlobal().severe("FAIL - USER DIRECTORY IS MISSING - " +
                        "USER [\" + user + \"] IS AUTHORIZED BUT CANNOT ACCESS HIS REPOSITORY !!!");
            }
        } else {
            message.put(Commands.MESSAGE, Commands.NOT_ADMITTED);
        }
        sender.send(message);
        listFiles(sender);
    }

    private void signUp(Sender sender, String user, String password) {
        JSONObject message = new JSONObject();
        try {
            AUTH_SERVICE.registerNewUser(user, password);
            currentDirectory = SERVER_DIRECTORY.resolve(user);
            FileProcessor.createDirectory(currentDirectory);
            this.login = user;
            this.isAuth = true;
            message.put(Commands.MESSAGE, Commands.ADMITTED);
        } catch (UserAlreadyExistsException e) {
            message.put(Commands.MESSAGE, Commands.FAIL);
            message.put(Commands.FAIL_DETAILS, "Пользователь с таким именем уже существует");
        } catch (DatabaseConnectionException e) {
            message.put(Commands.MESSAGE, Commands.FAIL);
            message.put(Commands.FAIL_DETAILS, "Ошибка соединения. Повторите попытку позднее");
        } catch (FileProcessorException e) {
            message.put(Commands.MESSAGE, Commands.FAIL);
            message.put(Commands.FAIL_DETAILS, "Ошибка соединения. Повторите попытку позднее");
            Logger.getGlobal().severe("FAIL - CANT CREATE USER DIRECTORY FOR USERNAME [" + user + "] !!!");
        }
        sender.send(message);
    }

    private void checkNewUserName(Sender sender, String name) {
        JSONObject message = new JSONObject();
        if (AUTH_SERVICE.isUserNameVacant(name)) {
            message.put(Commands.MESSAGE, Commands.USERNAME_OK);
        } else {
            message.put(Commands.MESSAGE, Commands.FAIL);
            message.put(Commands.FAIL_DETAILS, "Пользователь с таким именем уже есть");
        }
        sender.send(message);
    }

    private void saveFile(Sender sender, File file, int sequence, long bytesLeft, byte[] data) {

        System.out.println("Uploading "+file.getName() +": package no "+sequence+", bytes left: "+ bytesLeft);

        try {
            if(sequence == 1) DownloadManager.getInstance().enlistDownload(currentDirectory, file);
            DownloadManager.getInstance().download(file, sequence, data);
        } catch (DownloadManagerException e) {
            sendErrorMessage(sender, e.getMessage());
            return;
        }
        listFiles(sender);
    }

    private void downloadFile(Sender sender, String fileName) {
        File file = new File (currentDirectory.resolve(fileName).toString());
        if (file.exists()) {
            if (file.isFile()) sender.sendFileInChunks(file);
        } else {
            sendErrorMessage(sender, "File not found");
        }
    }

    private void rename (Sender sender, String oldName, String newName) {
        try {
            FileProcessor.rename(currentDirectory.resolve(oldName), newName);
        } catch (FileProcessorException e){
            sendErrorMessage(sender, e.getMessage());
        }
        listFiles(sender);
    }

    private void delete(Sender sender, String name) {
        try {
            FileProcessor.delete(currentDirectory.resolve(name));
        } catch (FileProcessorException e) {
            sendErrorMessage(sender, e.getMessage());
        }
        listFiles(sender);
    }

    private void createDirectory (Sender sender, String name) {
        try {
            FileProcessor.createDirectory(currentDirectory.resolve(name));
        } catch (FileProcessorException e) {
            sendErrorMessage(sender, e.getMessage());
        }
        listFiles(sender);
    }

    private void directoryUp(Sender sender) {
        if (!currentDirectory.getParent().equals(SERVER_DIRECTORY)) {
            currentDirectory = currentDirectory.getParent();
        }
        listFiles(sender);
    }

    private void directoryDown(Sender sender, String directoryName) {

        Path target = currentDirectory.resolve(directoryName);

        if(Files.exists(target) && Files.isDirectory(target)) {
            currentDirectory = target;
        } else {
            sendErrorMessage(sender,"Directory not found");
        }
        listFiles(sender);
    }

    private void listFiles (Sender sender) {

        ArrayList<MyFile> fileList = new ArrayList<>();

        for (File f: new File(currentDirectory.toString()).listFiles()) {
            fileList.add(new MyFile(f.isFile()? MyFile.FileType.FILE: MyFile.FileType.DIR,
                    f.getName(), (int)f.length()/1024));
        }

        JSONObject message = new JSONObject();
        message.put(Commands.MESSAGE, Commands.FILE_LIST);
        message.put(Commands.FILE_LIST, new MyFileList(fileList));
        sender.send(message);
    }

    private void sendOK (Sender sender) {
        JSONObject message = new JSONObject();
        message.put(Commands.MESSAGE, Commands.OK);
        sender.send(message);
    }

    private void sendErrorMessage (Sender sender, String errorDescription) {
        JSONObject message = new JSONObject();
        message.put(Commands.MESSAGE, Commands.FAIL);
        message.put(Commands.FAIL_DETAILS, errorDescription);
        sender.send(message);
    }

    public void setAuth (boolean authState) {
        this.isAuth = authState;
    }
}

//*******************************************************//
//               модуль authentication                   //
//*******************************************************//

//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~//
// Весь не привожу, смотрели его как-то, привожу только торчащий наружу интерфейс

package com.marta.sandbox.authentication;

import java.util.List;
import com.marta.sandbox.authentication.exceptions.*;

public interface AuthService {

    void start () throws AuthServiceException;
    void stop ();
    boolean isLoginAccepted(String username, String password);
    String getNickByLoginPass(String username, String password);
    boolean isUserNameVacant(String username);
    void registerNewUser (String username, String password, String nick) throws UserAlreadyExistsException, DatabaseConnectionException;
    void registerNewUser (String username, String password) throws UserAlreadyExistsException, DatabaseConnectionException;
    void deleteUser (String username, String password) throws DatabaseConnectionException;
    List<String> listRegisteredUsers();
}

//*******************************************************//
//               модуль fileprocessor                    //
//*******************************************************//

//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~//
// Не очень много пока пользы от этого модуля, в основном - обёртка над nio
// Единственная дельная кастомизация - решает проблему совпадения имён файлов при сохранении

package com.marta.sandbox.file_helper;

import java.io.*;
import java.nio.file.*;

public class FileProcessor {

    private FileProcessor () {}

    public static void saveFile(Path destinationDirectory, File file) throws FileProcessorException {
        String newName = file.getName();
        while(Files.exists(destinationDirectory.resolve(newName))) {
            newName = "копия-"+newName;
        }
        try {
            Files.copy(file.toPath(), destinationDirectory.resolve(newName.toString()));
        } catch (IOException e) {
            throw new FileProcessorException(e);
        }
    }

    public static void rename (Path oldFile, String newFile) throws FileProcessorException {
        try {
            Files.move(oldFile, oldFile.resolveSibling(newFile));
        } catch (IOException e) {
            throw new FileProcessorException(e);
        }
    }

    public static void delete (Path path) throws FileProcessorException {
        try {
            Files.delete(path);
        } catch (IOException e) {
            throw new FileProcessorException(e);
        }
    }

    public static void createDirectory (Path path) throws FileProcessorException {
        try {
            Files.createDirectory(path);
        } catch (IOException e) {
            throw new FileProcessorException(e);
        }
    }

    public static boolean fileExists (Path path) {
        return Files.exists(path, LinkOption.NOFOLLOW_LINKS);
    }
}


//*******************************************************//
//               модуль downloadmanager                  //
//*******************************************************//

//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~//
// От этого модуля пользы намного больше. 
// Туту класс-синглтон, который ведет список текущих загрузок и собирает файлы из полученных частей

import java.io.*;
import java.nio.file.Path;
import java.util.HashMap;

public class DownloadManager {

    private HashMap <File, Stats> downloads;

    private class Stats {
        File targetFile;
        int sequence;
        long bytesTotal;
        long bytesLeft;

        Stats(File targetFile, long size) {
            this.targetFile = targetFile;
            this.sequence = 0;
            this.bytesTotal = size;
            this.bytesLeft = size;
        }
    }

    private static final DownloadManager thisInstance = new DownloadManager();

    private DownloadManager () { this.downloads = new HashMap<>(); }

    public static DownloadManager getInstance () { return thisInstance; }

    public void enlistDownload (Path destinationDirectory, File file) throws DownloadManagerException {

        if(downloads.containsKey(file)) throw new DownloadManagerException("File already being downloaded");

        try {
            String newName = file.getName();
            File newFile = new File(destinationDirectory.resolve(newName).toString());
            int prefix = 1;
            while (!newFile.createNewFile()) {
                newFile = new File(destinationDirectory.resolve("(" + prefix + ")" + newName).toString());
                prefix++;
            }
            newFile.setReadable(false);
            System.out.println("File enlisted for download - source name: "+file.getName()+", target path: "
                    + destinationDirectory.resolve(newName).toString() + ", size: " + file.length());
            downloads.put(file, new Stats(newFile, file.length()));
        } catch (IOException e) {
            throw new DownloadManagerException(e);
        }
    }

    public void download (File file, int sequence, byte[] data) throws DownloadManagerException {

        if(!downloads.containsKey(file)) return;

        if(sequence != downloads.get(file).sequence + 1) {
            downloads.get(file).targetFile.delete();
            downloads.remove(file);
            throw new DownloadManagerException("Download failed for lost parts - please repeat");
        }

        try (OutputStream output = new BufferedOutputStream(new FileOutputStream(downloads.get(file).targetFile, true), data.length)) {
            output.write(data);
            downloads.get(file).sequence++;
            downloads.get(file).bytesLeft -= data.length;
        } catch (IOException e) {
            downloads.get(file).targetFile.delete();
            downloads.remove(file);
            throw new DownloadManagerException(e);
        }

        if (downloads.get(file).bytesLeft == 0) {
            downloads.get(file).targetFile.setReadable(true);
            downloads.remove(file);
        }

    }

}


//*******************************************************//
//               модуль client                           //
//*******************************************************//

Клиент в активной доработке, его не привожу
