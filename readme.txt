// ... и всё равно не успела. Работает всё, кроме самого нужного - пересылка файлов кусками.
// (Файл до 1Мб благополучно пролезает одним куском). Буду допиливать. На защиту завтра не планирую.
// Про баг рассказала, а фичи проекта такие:
// - Сервера пыталась сделать два - обычный и нетти. Для этого вся обработка входящих сообщений
//   от клиента вынесена в один общий класс ServerDispatcher
// - Авторизация отдельным модулем (включая регистрацию новых пользователей)
// - Клиент с поддержкой интернационализации (локаль пока задается программно, при старте клиента)


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
// или файл, расписленный на куски. Для отсылки файла при этом поднимается отдельный поток.

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
                e.printStackTrace();
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
import java.nio.file.Path;

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

    public MyFile (Path path) {
        if (path.toFile().exists()) {
            this.type = path.toFile().isDirectory() ? FileType.DIR : FileType.FILE;
            this.name = path.getFileName().toString();
            this.size = (int) (path.toFile().length() / 1024);
        }
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
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        protected void initChannel(SocketChannel socketChannel) {
                            socketChannel.pipeline().addLast(
                                    new ObjectDecoder(MAX_OBJ_SIZE, ClassResolvers.cacheDisabled(null)),
                                    new ObjectEncoder(),
                                    new ClientHandler(ROOT.toAbsolutePath(), authService)
                            );
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);
            Logger.getGlobal().info("SERVER STARTED");
            ChannelFuture future = b.bind(ServerConstants.PORT).sync();
            future.channel().closeFuture().sync();
        } catch (Exception e) {
            Logger.getGlobal().severe("FAILED TO START SERVER: " + e.getMessage() + "\nSERVER STOPPED");
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

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

        List<MyFile> fileList = null;
        try {
            fileList = Files.list(currentDirectory).map(MyFile::new).collect(Collectors.toList());
        } catch ( IOException e) {
            //exception ignored - null list to be returned in case of exception
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
// AuthService - интерфейс

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

//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~//
// SqliteAuthService - Имплементация интерфейса AuthService с подключением к базе данных.

package com.marta.sandbox.authentication;

import com.marta.sandbox.authentication.exceptions.AuthServiceException;
import com.marta.sandbox.authentication.exceptions.DatabaseConnectionException;
import com.marta.sandbox.authentication.exceptions.UserAlreadyExistsException;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.LinkedList;
import java.util.List;

public class SqliteAuthService implements AuthService {

    private final String DATABASE_URL;
    private Connection connection;
    private List<PreparedStatement> preparedStatements;
    private PreparedStatement authQuery;
    private PreparedStatement findUserQuery;
    private PreparedStatement registerNewUserQuery;
    private PreparedStatement deleteUserQuery;
    private PreparedStatement listAllUsersQuery;

    public SqliteAuthService(String databaseURL) {
        this.DATABASE_URL = databaseURL;
    }

    // запуск службы - проверка наличия базы, её создание в случае необходимости,
    // подключение, заготовка PreparedStatements, заполнение тестовыми записями
    public synchronized void start() throws DatabaseConnectionException {
        try {
            this.connection = DriverManager.getConnection("jdbc:sqlite:"+DATABASE_URL);
            checkCreateUsersTable();
            prepareStatements();
            testPrefill(); // TODO убрать по завершении разработки
        } catch (SQLException | AuthServiceException e) {
            throw new DatabaseConnectionException();
        }
    }

    // Тестовые данные для базы - только на стадии разработки
    private synchronized void testPrefill() throws AuthServiceException {
        String [] [] testUsers = {
                {"login1",  "pass1", "Rick"},
                {"login2",  "pass2", "Morty"},
                {"login3",  "pass3", "Bet"}};
        try {
            for (int i = 0; i < 3; i++) {
                if (isUserNameVacant(testUsers[i][0]))
                    registerNewUser(testUsers[i][0], hash(testUsers[i][1]), testUsers[i][2]);
                if(Files.notExists(Paths.get(DATABASE_URL).getParent().resolve(testUsers[i][0])))
                    Files.createDirectory(Paths.get(DATABASE_URL).getParent().resolve(testUsers[i][0]));
            }
        } catch (Exception e) { }
    }

    private void checkCreateUsersTable() throws SQLException {
        connection.createStatement().executeUpdate("CREATE TABLE IF NOT EXISTS users (" +
                "    user CHAR (20) NOT NULL UNIQUE," +
                "    password CHAR (12) NOT NULL, " +
                "    nick CHAR (20) NOT NULL);" +
                "    CREATE UNIQUE INDEX IF NOT EXISTS i_users ON users (user);");
    }

    private void prepareStatements () throws SQLException {

        preparedStatements = new LinkedList<>();

        authQuery = connection.prepareStatement("SELECT * FROM users WHERE user = ? AND password = ? LIMIT 1");
        preparedStatements.add(authQuery);

        findUserQuery = connection.prepareStatement("SELECT * FROM users WHERE user = ? LIMIT 1");
        preparedStatements.add(findUserQuery);

        registerNewUserQuery = connection.prepareStatement("INSERT INTO users (user, password, nick) VALUES (?, ?, ?)");
        preparedStatements.add(registerNewUserQuery);

        deleteUserQuery = connection.prepareStatement("DELETE FROM users WHERE user = ?");
        preparedStatements.add(deleteUserQuery);

        listAllUsersQuery = connection.prepareStatement("SELECT user FROM users");
        preparedStatements.add(listAllUsersQuery);
    }

    // остановка службы - закрытие стейтментов и подключения
    public void stop() {
        try {
            for (PreparedStatement ps: preparedStatements) ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        try {
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Возвращаем ник по логину и паролю,
    // или null, если пара логин/пароль не найдена
    public synchronized String getNickByLoginPass(String login, String pass) {
        try {
            authQuery.setString(1, login);
            authQuery.setString(2, pass);
            ResultSet resultSet = authQuery.executeQuery();
            if (resultSet.next()) {
                return resultSet.getString("nick");
            }
            return null;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    // все методы ниже синхронизированы, т.к. работают через один и тот же набор PreparedStatements

    // проверяем, есть ли в базе записи с указанным логином и (хеш-)паролем.
    // если запрос возвращается пустой или происходит ошибка - отвечаем false
    public synchronized boolean isLoginAccepted(String username, String password) {
        try {
            authQuery.setString(1, username);
            authQuery.setString(2, password);
            return authQuery.executeQuery().next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // проверяем, не занят ли в базе указанный логин
    // метод вынесен отдельно от регистрации, чтобы иметь возможность проверить на стадии заполнения формы
    public synchronized boolean isUserNameVacant(String username) {
        try {
            findUserQuery.setString(1, username);
            return !findUserQuery.executeQuery().next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // регистрация нового пользователя
    public synchronized void registerNewUser(String username, String password, String nick) throws UserAlreadyExistsException, DatabaseConnectionException {
        if (isUserNameVacant(username)) {
            try {
                registerNewUserQuery.setString(1, username);
                registerNewUserQuery.setString(2, password);
                registerNewUserQuery.setString(3, nick);
                registerNewUserQuery.executeUpdate();
            } catch (SQLException e) {
                throw new DatabaseConnectionException();
            }
        } else throw new UserAlreadyExistsException();
    }

    public synchronized void registerNewUser(String username, String password) throws UserAlreadyExistsException, DatabaseConnectionException {
        if (isUserNameVacant(username)) {
            try {
                registerNewUserQuery.setString(1, username);
                registerNewUserQuery.setString(2, password);
                registerNewUserQuery.setString(3, username);
                registerNewUserQuery.executeUpdate();
            } catch (SQLException e) {
                throw new DatabaseConnectionException();
            }
        } else throw new UserAlreadyExistsException();
    }

    // удаление пользователя (с паролем для проверки полномочий на удаление)
    public synchronized void deleteUser (String username, String password) throws DatabaseConnectionException {
        if (isLoginAccepted(username, password)) {
            try {
                deleteUserQuery.setString(1, username);
                if (deleteUserQuery.executeUpdate() < 1) throw new DatabaseConnectionException();
            } catch (SQLException e) {
                throw new DatabaseConnectionException();
            }
        }
    }

    // список всех зарегистрированных в базе пользователей
    public List<String> listRegisteredUsers() {
        List <String> list = new LinkedList<>();
        try {
            ResultSet resultSet = listAllUsersQuery.executeQuery();
            while (resultSet.next()) list.add(resultSet.getString(1));
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    private String hash (String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(input.getBytes());
            return new String(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            return input;
        }
    }
}

//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~//
// Исключения - для большей информативности

package com.marta.sandbox.authentication.exceptions;

public class AuthServiceException extends Exception {}
public class DatabaseConnectionException extends AuthServiceException {}
public class UserAlreadyExistsException extends AuthServiceException {}


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
// DownloadManager - класс-синглтон, который ведет список текущих загрузок и собирает файлы из полученных частей
// Только он не рассчитан на асинхронную передачу, а переделать я не успела

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

//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~//
// DropboxClient - запускающий класс

package com.marta.sandbox.dropbox.client;

import com.marta.sandbox.dropbox.client.service.SceneManager;
import javafx.application.Application;
import javafx.stage.Stage;

import java.util.Locale;

public class DropboxClient extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception{
        SceneManager.getInstance().init(primaryStage, new Locale("en"));
        SceneManager.getInstance().switchSceneTo(SceneManager.SceneType.AUTHENTICATION);
    }

    public static void main(String[] args) {
        launch(args);
    }
}


//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~//
// SceneManager - класс-синглтон, управляющий переключением экранов

package com.marta.sandbox.dropbox.client.service;

import com.marta.sandbox.dropbox.client.fxml.DialogManager;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.*;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

public class SceneManager {

    private static SceneManager thisInstance;
    private Stage primaryStage;
    private SceneType currentScene;
    private Scene authentication, registration, client;
    private ResourceBundle resourceBundle;
    private Map<SceneManager.SceneType, InputListener> listeners;

    public enum SceneType {
        AUTHENTICATION, REGISTRATION, WORK;
    }

    private SceneManager () {}

    public static SceneManager getInstance() {
        if (thisInstance == null) thisInstance = new SceneManager();
        return thisInstance;
    }

    // статический метод для "перевода" текстов в выбранную локаль
    public static String translate (String message) { return thisInstance.resourceBundle.getString(message); }

    // статический метод для получения ссылки на primaryStage
    public static Window getWindow () { return getInstance().primaryStage; }

    // при запуске загружаем локаль и экраны
    public void init (Stage primaryStage, Locale lang) {
        this.primaryStage = primaryStage;
        this.resourceBundle = ResourceBundle.getBundle("locales.Locale", lang);
        primaryStage.setTitle(resourceBundle.getString("title.app"));
        this.listeners = new HashMap<>();

        try {
            authentication = new Scene(FXMLLoader.load(getClass().getClassLoader().getResource("authentication.fxml"), resourceBundle), 450, 400);
            registration = new Scene(FXMLLoader.load(getClass().getClassLoader().getResource("registration.fxml"), resourceBundle), 450, 400);
            client = new Scene(FXMLLoader.load(getClass().getClassLoader().getResource("client.fxml"), resourceBundle), 450, 400);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // каждый экран при загрузке регистрируется в подписке на входящие сетевые сообщения,
    // и SceneManager по запросу NetworkManager'а сообщает последнему текущий экран-подписчик
    public void registerListener (SceneType scene, InputListener listener) {
        listeners.put(scene, listener);
    }

    InputListener getCurrentListener () {
        return listeners.get(currentScene);
    }

    // метод для переключения между экранами
    public void switchSceneTo (SceneType scene) {
        Platform.runLater(()-> {
           switch (scene) {
               case AUTHENTICATION:
                   primaryStage.setScene(authentication);
                   break;
               case REGISTRATION:
                   primaryStage.setScene(registration);
                   break;
               case WORK:
                   primaryStage.setScene(client);
                   NetworkManager.getInstance().requestFileListUpdate();
                   break;
                }
           currentScene = scene;
           primaryStage.show();
        });
    }

    void logout () {
        switchSceneTo(SceneType.AUTHENTICATION);
    }

    void showExceptionMessage () {
        Platform.runLater(() -> {
            DialogManager.showWarning(
                    SceneManager.translate("error.smth-went-wrong"),
                    SceneManager.translate("error.connection-failed"));
        });
    }

}

//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~//
// NetworkManager - класс-синглтон, управляющий сетевым подключением

package com.marta.sandbox.dropbox.client.service;

import com.marta.sandbox.dropbox.common.messaging.Commands;
import com.marta.sandbox.dropbox.common.session.ConnectionListener;
import com.marta.sandbox.dropbox.common.session.Session;
import com.marta.sandbox.dropbox.common.settings.ServerConstants;
import org.json.simple.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.Socket;

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

//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~//
// InputListener - интерфейс для организации подписки экранов на входящие сетевые сообщения

package com.marta.sandbox.dropbox.client.service;

public interface InputListener {
    void onInput (Object input);
}

//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~//
// В приложении три экрана (авторизация, регистрация, рабочий экран, поэтому контроллера тоже три.
// В каждом - FXML-обработка событий формы + реализация метода onInput для обработка входящих сообщений
// Authentication - контроллер экрана авторизации

package com.marta.sandbox.dropbox.client.fxml;

import com.marta.sandbox.dropbox.client.service.*;
import com.marta.sandbox.dropbox.common.messaging.Commands;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import org.json.simple.JSONObject;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Authentication implements InputListener {

    @FXML private TextField loginField;
    @FXML private PasswordField passwordField;

    @FXML private void initialize () {
        SceneManager.getInstance().registerListener(SceneManager.SceneType.AUTHENTICATION, this);
    }

    @FXML private void signIn() { signIn(loginField.getText(), passwordField.getText()); }
    @FXML private void switchToRegistration () { SceneManager.getInstance().switchSceneTo(SceneManager.SceneType.REGISTRATION); }

    @Override
    public void onInput(Object input) {

        Platform.runLater(()-> {
            if (input instanceof JSONObject) {

                JSONObject json = (JSONObject)input;
                Commands cmd = (Commands)json.get(Commands.MESSAGE);

                switch (cmd) {
                    case ADMITTED:
                        SceneManager.getInstance().switchSceneTo(SceneManager.SceneType.WORK);
                        break;
                    case NOT_ADMITTED:
                        DialogManager.showWarning(
                                SceneManager.translate("message.not-admitted"),
                                SceneManager.translate("message.wrong-username-or-password"));
                        passwordField.clear();
                        break;
                    case FAIL:
                        DialogManager.showWarning(
                                SceneManager.translate("error.connection-failed"),
                                SceneManager.translate("error.smth-went-wrong"));
                        break;
                    case OK:
                        break;
                    default:
                        System.out.println("--------THIS SHOULD NOT HAPPEN " +
                                "- UNKNOWN COMMAND IN CLIENT'S AUTHENTICATION SCREEN");
                }
            } else System.out.println("--------THIS SHOULD NOT HAPPEN - " +
                    "UNKNOWN TYPE OF INCOMING MESSAGE IN CLIENT'S AUTHENTICATION SCREEN");
        });
    }

    public void signIn(String user, String password) {
        JSONObject message = new JSONObject();
        message.put(Commands.MESSAGE, Commands.SIGN_IN);
        message.put(Commands.USERNAME, user);
        message.put(Commands.PASSWORD, hash(password));
        NetworkManager.getInstance().send(message);
    }

    private String hash (String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(input.getBytes());
            return new String(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            return input;
        }
    }
}

//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~//
// Registration - контроллер экрана регистрации ноых пользователей

package com.marta.sandbox.dropbox.client.fxml;

import com.marta.sandbox.dropbox.client.service.*;
import com.marta.sandbox.dropbox.common.messaging.Commands;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import org.json.simple.JSONObject;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Registration implements InputListener {

    @FXML private TextField username;
    @FXML private TextField password;
    @FXML private TextField password1;

    @FXML private void initialize () {
        SceneManager.getInstance().registerListener(SceneManager.SceneType.REGISTRATION, this);
    }

    @FXML private void switchToAuthentication () { SceneManager.getInstance().switchSceneTo(SceneManager.SceneType.AUTHENTICATION); }

    public void onInput(Object input) {

        Platform.runLater(()-> {

            if (input instanceof JSONObject) {

                JSONObject json = (JSONObject) input;
                Commands cmd = (Commands) json.get(Commands.MESSAGE);

                switch (cmd) {
                    case ADMITTED:
                        DialogManager.showWarning(
                                SceneManager.translate("message.sign-up"),
                                SceneManager.translate("message.sign-up-success"));
                        SceneManager.getInstance().switchSceneTo(SceneManager.SceneType.WORK);
                        break;
                    case NOT_ADMITTED:
                    case FAIL:
                        DialogManager.showWarning(
                                SceneManager.translate("message.sign-up"),
                                (String)json.getOrDefault(Commands.FAIL_DETAILS, SceneManager.translate("message.sign-up-fail")));
                        username.clear();
                        password.clear();
                        password1.clear();
                        break;
                    case OK:
                    case USERNAME_OK:
                        break;
                    default:
                        System.out.println("--------THIS SHOULD NOT HAPPEN - UNKNOWN COMMAND IN CLIENT'S REGISTRATION SCREEN");
                }
            } else System.out.println("--------THIS SHOULD NOT HAPPEN - " +
                    "UNKNOWN TYPE OF INCOMING MESSAGE IN CLIENT'S REGISTRATION SCREEN");
        });
    }

    @FXML
    private void checkNewUserName() {
        if (isUsernameOK(username.getText())) {
            checkNewUserName(username.getText());
        } else {
            DialogManager.showWarning(
                    SceneManager.translate("message.sign-up"),
                    SceneManager.translate("message.sign-up-username-rules"));
            username.clear();
        }
    }


    public void checkNewUserName(String name) {
        JSONObject message = new JSONObject();
        message.put(Commands.MESSAGE, Commands.CHECK_NEW_USER_NAME);
        message.put(Commands.USERNAME, name);
        NetworkManager.getInstance().send(message);
    }

    @FXML
    private void signUp() {
        if (!isUsernameOK(username.getText())) {
            DialogManager.showWarning(
                    SceneManager.translate("message.sign-up"),
                    SceneManager.translate("message.sign-up-username-rules"));
            username.clear();
        } else if (!isPasswordOK(password.getText())) {
            DialogManager.showWarning(
                    SceneManager.translate("message.sign-up"),
                    SceneManager.translate("message.sign-up-password-rules"));
            password.clear();
            password1.clear();
        } else if (!(password1.getText().equals(password.getText()))) {
            DialogManager.showWarning(
                    SceneManager.translate("message.sign-up"),
                    SceneManager.translate("message.sign-up-password-repeat"));
            password1.clear();
        } else {
            JSONObject message = new JSONObject();
            message.put(Commands.MESSAGE, Commands.SIGN_UP);
            message.put(Commands.USERNAME, username.getText());
            message.put(Commands.PASSWORD, hash(password.getText()));
            NetworkManager.getInstance().send(message);
        }
    }

    @FXML private void checkPassword () {
        String pwd = password.getText();
        if (!isPasswordOK(pwd)) {
            DialogManager.showWarning(
                    SceneManager.translate("message.sign-up"),
                    SceneManager.translate("message.sign-up-password-rules"));
            password.clear();
        }
    }

    private boolean isUsernameOK (String username) {
        return username.chars().allMatch(ch -> (ch >= 'a' && ch <= 'z')
                || (ch >= '0' && ch <= '9'));
    }

    private boolean isPasswordOK (String password) {
        if (password.length() > 3 && password.length() < 9)
            return password.chars().allMatch(ch ->
                    (ch >= 'a' && ch <= 'z')
                            || (ch >= 'A' && ch <= 'Z')
                            || (ch >= '0' && ch <= '9'));
        return false;
    }

    private String hash (String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(input.getBytes());
            return new String(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            return input;
        }
    }
}

//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~//
// Client - контроллер рабочего экрана

package com.marta.sandbox.dropbox.client.fxml;

import com.marta.sandbox.dropbox.client.service.*;
import com.marta.sandbox.dropbox.common.messaging.Commands;
import com.marta.sandbox.dropbox.common.messaging.MyFile;
import com.marta.sandbox.dropbox.common.messaging.MyFileList;
import com.marta.sandbox.dropbox.downloadmanager.DownloadManager;
import com.marta.sandbox.dropbox.downloadmanager.DownloadManagerException;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseEvent;
import org.json.simple.JSONObject;

import java.io.*;
import java.nio.file.*;
import java.util.HashSet;

public class Client implements InputListener {

    @FXML private Button btnDelete;
    @FXML private Button btnRename;
    @FXML private Button btnDownload;
    @FXML private TableView<MyFile> table;
    @FXML private TableColumn<MyFile, String> colType;
    @FXML private TableColumn<MyFile, String> colName;
    @FXML private TableColumn<MyFile, String> colSize;
    private HashSet<File> uploads;

    @FXML private void initialize() {

        SceneManager.getInstance().registerListener(SceneManager.SceneType.WORK, this);

        colType.setCellValueFactory(new PropertyValueFactory<MyFile, String>("type"));
        colName.setCellValueFactory(new PropertyValueFactory<MyFile, String>("name"));
        colSize.setCellValueFactory(new PropertyValueFactory<MyFile, String>("size"));

        if(!Files.exists(ROOT, LinkOption.NOFOLLOW_LINKS)) {
            try {
                Files.createDirectory(ROOT);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        uploads = new HashSet<>();

    }

    private final Path ROOT = Paths.get("_client_downloads"); // TODO добавить возможность выбора клиентом

    @Override
    public void onInput(Object input) {

        Platform.runLater(()-> {
            if (input instanceof JSONObject) {

                JSONObject json = (JSONObject)input;
                Commands cmd = (Commands)json.get(Commands.MESSAGE);

                switch (cmd) {
                    case FAIL:
                        DialogManager.showWarning(
                                SceneManager.translate("error.operation-fail"),
                                (String) json.getOrDefault(Commands.FAIL_DETAILS, SceneManager.translate("error.smth-went-wrong")));
                        break;
                    case FILE:
                        saveFile((File)json.get(Commands.FILE),(int)json.get(Commands.SEQUENCE), (long)json.get(Commands.BYTES_LEFT),
                                (byte[])json.get(Commands.DATA));
                        break;
                    case FILE_LIST:
                        listFiles((MyFileList)json.get(Commands.FILE_LIST));
                        break;
                    case OK:
                        break;
                    default:
                        System.out.println("client - THIS SHOULD NOT HAPPEN - UNKNOWN COMMAND FROM SERVER: " + input);
                }

            } else {
                System.out.println("client - THIS SHOULD NOT HAPPEN - " +
                        "UNKNOWN TYPE OF INCOMING MESSAGE IN MAIN SCREEN: " + input);
                requestFileListUpdate();
            }
        });
    }

    @FXML
    private void uploadFile() {

        String title = SceneManager.translate("title.select-file");

        File file = DialogManager.selectFile(title);

        title = SceneManager.translate("title.upload");

        if (file == null) return;

        if (!file.exists()) {
            DialogManager.showWarning(title, SceneManager.translate("error.file-not-found"));
            return;
        }

        if (!file.isFile()) {
            //TODO что делать с папками
            DialogManager.showWarning(title, SceneManager.translate("message.cannot-upload-directory"));
            return;
        }

        if (uploads.contains(file)) {
            //TODO как снимать отметку о нахождении в загрузке
            DialogManager.showWarning(title, SceneManager.translate("message.already-being-uploaded"));
            return;
        }

        uploads.add(file);
        NetworkManager.getInstance().sendFile(file);
        requestFileListUpdate();

    }

    @FXML private void downloadFile () {
        MyFile selectedFile = table.getSelectionModel().getSelectedItem();
        if (selectedFile == null) return;

        downloadFile(selectedFile.getName());
    }

    private void downloadFile(String fileName) {
        JSONObject message = new JSONObject();
        message.put(Commands.MESSAGE, Commands.DOWNLOAD);
        message.put(Commands.FILE_NAME, fileName);
        NetworkManager.getInstance().send(message);
    }


    private void saveFile(File file, int sequence, long bytesLeft, byte[] data) {

        System.out.println("Downloading "+file.getName() +": package no "+sequence+", bytes left: "+ bytesLeft);

        try {
            if(sequence == 1) DownloadManager.getInstance().enlistDownload(ROOT, file);
            DownloadManager.getInstance().download(file, sequence, data);
        } catch (DownloadManagerException e) {
            DialogManager.showWarning(SceneManager.translate("title.download"), e.getMessage());
        }
    }


    @FXML
    private void rename() {

        MyFile selectedFile = table.getSelectionModel().getSelectedItem();

        if(selectedFile == null) return;

        String title;
        String messageText;
        String errorMessage;

        if (selectedFile.getType() == MyFile.FileType.FILE) {
            title = SceneManager.translate("title.rename-file") + " " + selectedFile.getName();
            messageText = SceneManager.translate("prompt.new-file-name");
            errorMessage = SceneManager.translate("error.file-name");
        } else {
            title = SceneManager.translate("title.rename-directory") + " " + selectedFile.getName();
            messageText = SceneManager.translate("prompt.new-directory-name");
            errorMessage = SceneManager.translate("error.directory-name");
        }

        String newName = DialogManager.getInput(title, messageText);

        if (fileNameIsValid(newName)) {
            JSONObject message = new JSONObject();
            message.put(Commands.MESSAGE, Commands.RENAME);
            message.put(Commands.FILE_NAME, selectedFile.getName());
            message.put(Commands.NEW_FILE_NAME, newName);
            NetworkManager.getInstance().send(message);
        } else {
            DialogManager.showWarning(title, errorMessage);
        }
    }

    @FXML
    private void delete () {

        MyFile selectedFile = table.getSelectionModel().getSelectedItem();

        if(selectedFile == null) return;

        String title;
        String messageText = SceneManager.translate("action.delete") + " " + selectedFile.getName()+"?";

        if(selectedFile.getType() == MyFile.FileType.FILE) {
            title = SceneManager.translate("title.delete-file");
        } else {
            title = SceneManager.translate("title.delete-directory");
        }

        if (DialogManager.reconfirmed(title, messageText)) {
            JSONObject message = new JSONObject();
            message.put(Commands.MESSAGE, Commands.DELETE);
            message.put(Commands.FILE_NAME, selectedFile.getName());
            NetworkManager.getInstance().send(message);
        }
    }


    @FXML
    private void createDirectory () {

        String title = SceneManager.translate("action.new-folder");
        String messageText = SceneManager.translate("prompt.new-folder");
        String errorMessage = SceneManager.translate("error.directory-name");

        String newDirectory = DialogManager.getInput(title, messageText);

        if (fileNameIsValid(newDirectory)) {
            JSONObject message = new JSONObject();
            message.put(Commands.MESSAGE, Commands.CREATE_DIRECTORY);
            message.put(Commands.DIRECTORY_NAME, newDirectory);
            NetworkManager.getInstance().send(message);
        } else {
            DialogManager.showWarning(title, errorMessage);
        }
    }


    @FXML
    public void directoryUp() {
        JSONObject message = new JSONObject();
        message.put(Commands.MESSAGE, Commands.DIRECTORY_UP);
        NetworkManager.getInstance().send(message);
    }


    private void directoryDown(String directoryName) {
        JSONObject message = new JSONObject();
        message.put(Commands.MESSAGE, Commands.DIRECTORY_DOWN);
        message.put(Commands.DIRECTORY_NAME, directoryName);
        NetworkManager.getInstance().send(message);
    }


    private void listFiles (MyFileList input) {
        table.setItems(FXCollections.observableArrayList(input.getFileList()));
    }

    @FXML private void clickOnFile(MouseEvent mouseEvent) {

        // одиночный клик - обновить доступность кнопок
        if (mouseEvent.getClickCount() == 1) selectFile();

        // двойной клик - для папок: переход по каталогу, для файлов: загрузка после подтверждения
        else if (mouseEvent.getClickCount() == 2) {
            selectFile();
            MyFile selectedFile = table.getSelectionModel().getSelectedItem();

            if (selectedFile == null) return;

            if (selectedFile.getType() == MyFile.FileType.DIR) {
                directoryDown(selectedFile.getName());
            } else {
                if (DialogManager.reconfirmed(SceneManager.translate("title.download"),
                        SceneManager.translate("prompt.download"))) {
                    downloadFile(selectedFile.getName());
                }
            }
        }
    }

    private void requestFileListUpdate() {
        JSONObject message = new JSONObject();
        message.put(Commands.MESSAGE, Commands.LIST_CONTENTS);
        NetworkManager.getInstance().send(message);
    }

    private boolean fileNameIsValid (String fileName) {
        // TODO доделать проверку имени файла на корректность
        return fileName !=  null  && !fileName.isEmpty();
    }

    private void selectFile () {
        // TODO а как снять выделение выбранного файла?
        MyFile selectedFile = table.getSelectionModel().getSelectedItem();
        if (selectedFile == null) {
            btnDelete.setDisable(true);
            btnRename.setDisable(true);
            btnDownload.setDisable(true);
        } else {
            btnDelete.setDisable(false);
            btnRename.setDisable(false);
            if (selectedFile.getType() == MyFile.FileType.DIR )
                 btnDownload.setDisable(true);
            else btnDownload.setDisable(false);
        }
    }
}

//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~//
// DialogManager - класс-хелпер для выдачи всплывающих окон

package com.marta.sandbox.dropbox.client.fxml;

import com.marta.sandbox.dropbox.client.service.SceneManager;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextInputDialog;
import javafx.stage.FileChooser;

import java.io.File;

public class DialogManager {

    public static <P> Object showWarning(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING, message, ButtonType.OK);
        alert.setHeaderText("");
        alert.setTitle(title);
        alert.setContentText(message);
        return alert.showAndWait();
    }

    static String getInput(String title, String message) {
        TextInputDialog request = new TextInputDialog();
        request.setHeaderText("");
        request.setTitle(title);
        request.setContentText(message);
        request.showAndWait();
        return request.getResult();
    }

    static File selectFile (String title) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(title);
        return fileChooser.showOpenDialog(SceneManager.getWindow());
    }

    static boolean reconfirmed (String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        alert.setHeaderText("");
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
        return alert.getResult().getButtonData() == ButtonBar.ButtonData.YES;
    }
}

//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~//
// В ресурсах - authentication.fxml

<?xml version="1.0" encoding="UTF-8"?>

<?import javafx.scene.control.Button?>
<?import javafx.scene.control.Hyperlink?>
<?import javafx.scene.control.Label?>
<?import javafx.scene.control.PasswordField?>
<?import javafx.scene.control.TextField?>
<?import javafx.scene.layout.AnchorPane?>
<?import javafx.scene.text.Font?>

<AnchorPane prefHeight="400.0" prefWidth="450.0" xmlns="http://javafx.com/javafx/8.0.121" xmlns:fx="http://javafx.com/fxml/1" fx:controller="com.marta.sandbox.dropbox.client.fxml.Authentication">
   <children>
      <Label layoutX="32.0" layoutY="41.0" text="%title.authentication">
         <font>
            <Font name="System Bold" size="14.0" />
         </font>
      </Label>
      <Label layoutX="32.0" layoutY="60.0" text="test run:  try login1 + pass1, login2 + pass2" textFill="RED" />
      <TextField fx:id="loginField" layoutX="169.0" layoutY="91.0" prefHeight="25.0" prefWidth="200.0" />
      <PasswordField fx:id="passwordField" layoutX="169.0" layoutY="136.0" onAction="#signIn" prefHeight="25.0" prefWidth="200.0" />
      <Label layoutX="32.0" layoutY="95.0" prefHeight="17.0" prefWidth="149.0" text="%user.name" />
      <Label layoutX="32.0" layoutY="140.0" prefHeight="17.0" prefWidth="123.0" text="%user.password" />
      <Button layoutX="236.0" layoutY="188.0" mnemonicParsing="false" onAction="#signIn" prefHeight="25.0" prefWidth="132.0" text="%action.sign-in" />
      <Hyperlink layoutX="118.0" layoutY="225.0" onAction="#switchToRegistration" prefHeight="23.0" prefWidth="249.0" text="%prompt.sign-up" />
   </children>
</AnchorPane>

//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~//
// В ресурсах - registration.fxml

<?xml version="1.0" encoding="UTF-8"?>

<?import javafx.scene.control.Button?>
<?import javafx.scene.control.Hyperlink?>
<?import javafx.scene.control.Label?>
<?import javafx.scene.control.PasswordField?>
<?import javafx.scene.control.TextField?>
<?import javafx.scene.layout.AnchorPane?>
<?import javafx.scene.text.Font?>

<AnchorPane prefHeight="400.0" prefWidth="450.0" xmlns="http://javafx.com/javafx/8.0.121" xmlns:fx="http://javafx.com/fxml/1" fx:controller="com.marta.sandbox.dropbox.client.fxml.Registration">
    <children>
        <Label layoutX="32.0" layoutY="41.0" text="%title.registration">
        <font>
            <Font name="System Bold" size="14.0" />
        </font>
        </Label>
        <TextField fx:id="username" layoutX="169.0" layoutY="91.0" onAction="#checkNewUserName" prefHeight="25.0" prefWidth="181.0" />
        <PasswordField fx:id="password" layoutX="169.0" layoutY="136.0" onAction="#checkPassword" prefHeight="25.0" prefWidth="181.0" />
        <PasswordField fx:id="password1" layoutX="169.0" layoutY="181.0" onAction="#signUp" prefHeight="25.0" prefWidth="181.0" />
        <Label layoutX="32.0" layoutY="95.0" prefHeight="17.0" prefWidth="149.0" text="%user.name" />
        <Label layoutX="32.0" layoutY="140.0" prefHeight="17.0" prefWidth="123.0" text="%user.password" />
        <Label layoutX="32.0" layoutY="185.0" prefHeight="17.0" prefWidth="123.0" text="%user.password-2" />
        <Button fx:id="btnRegister" layoutX="220.0" layoutY="235.0" mnemonicParsing="false" onAction="#signUp" prefHeight="25.0" prefWidth="130.0" text="%action.sign-in" />
        <Hyperlink layoutX="97.0" layoutY="270.0" onAction="#switchToAuthentication" prefHeight="23.0" prefWidth="254.0" text="%prompt.sign-in" textAlignment="RIGHT" />
    </children>

</AnchorPane>

//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~//
// В ресурсах - client.fxml
// Отражаю только файлы на сервере (локальные пользователь и так посмотрит,
// зато кнопок больше - переименовать, удалить. Плюс переходы вверх-вниз по директориям

<?xml version="1.0" encoding="UTF-8"?>

<?import javafx.scene.control.Button?>
<?import javafx.scene.control.TableColumn?>
<?import javafx.scene.control.TableView?>
<?import javafx.scene.control.ToolBar?>
<?import javafx.scene.layout.VBox?>

<VBox alignment="center" prefHeight="400.0" prefWidth="450.0" xmlns="http://javafx.com/javafx/8.0.121" xmlns:fx="http://javafx.com/fxml/1" fx:controller="com.marta.sandbox.dropbox.client.fxml.Client">
    <children>
        <ToolBar fx:id="menuPanel" prefHeight="40.0" prefWidth="400.0">
            <items>
                <Button mnemonicParsing="false" onAction="#directoryUp" text="&lt;" />
                <Button mnemonicParsing="false" onAction="#createDirectory" text="%action.new-folder" />
                <Button fx:id="btnDownload" disable="true" mnemonicParsing="false" onAction="#downloadFile" text="%action.download" />
                <Button fx:id="btnRename" disable="true" mnemonicParsing="false" onAction="#rename" text="%action.rename" />
                <Button fx:id="btnDelete" disable="true" mnemonicParsing="false" onAction="#delete" text="%action.delete" />
                <Button mnemonicParsing="false" onAction="#uploadFile" text="%action.upload" />
            </items>
        </ToolBar>
        <TableView fx:id="table" accessibleRole="LIST_VIEW" onMouseClicked="#clickOnFile" prefHeight="360.0" prefWidth="400.0">
            <columns>
                <TableColumn fx:id="colType" editable="false" prefWidth="60.0" text="%file.type" />
                <TableColumn fx:id="colName" editable="false" prefWidth="300.0" text="%file.name" />
                <TableColumn fx:id="colSize" editable="false" prefWidth="90.0" text="%file.size" />
            </columns>
        </TableView>
    </children>
</VBox>

//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~//
// В ресурсах - Locale_en.properties

file.type=>
file.name=Name
file.size=Size, KB
title.app=Yet Another Dropbox
title.authentication=Sign in for registered users
title.registration=Sign up for new users
action.sign-in=Sign in
action.sign-up=Sign up
action.new-folder=New Folder
action.rename=Rename
action.delete=Delete
action.download=Download
action.upload=Upload
user.name=Username
user.password=Password
user.password-2=Repeat password
prompt.sign-in=Sign in for registered users
prompt.sign-up=First time here? Create an account!
message.wrong-username-or-password=Wrong username or password
message.not-admitted=com.marta.sandbox.dropbox.client.fxml.Authentication failed
error.smth-went-wrong=Something went wrong...
error.connection-failed=Connection failed
message.sign-up=New account creation
message.sign-up-success=Sign-up successful!
message.sign-up-fail=Please try different username
message.sign-up-username-rules=Your username should only contain lowercase latin letters and numbers
message.sign-up-password-rules=Password should only contain latin letters and numbers\
 and be 4 to 8 symbols long
message.sign-up-password-repeat=The passwords do not match - please recheck
error.operation-fail=Operation failed
title.upload=Uploading
title.rename-file=Rename file
title.rename-directory=Rename directory
prompt.path=Enter path to the file
message.cannot-upload-directory=Can't upload directories, please specify a file!
error.file-not-found=File not found
prompt.new-file-name=Enter new file name
prompt.new-directory-name=Enter new directory name
error.file-name=Incorrect file name
error.directory-name=Incorrect directory name
title.delete-file=Delete file
title.delete-directory=Delete directory
prompt.new-folder=Enter directory name
title.download=Downloading
prompt.download=Download file to local disk?
error.error=Error
title.select-file=Please select file
message.already-being-uploaded=File is already being uploaded

//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~//
// В ресурсах - Locale_en.properties

file.type=>
file.name=Имя
file.size=Размер, КБ
title.app=Как бы дропбокс
title.authentication=Вход для зарегистророванных пользователей
title.registration=Регистрация нового пользователя
action.sign-up=Зарегистрироваться
action.new-folder=Создать папку
action.rename=Переименовать
action.delete=Удалить
action.download=Скачать
action.upload=Закачать
user.name=Имя пользователя
user.password=Пароль
user.password-2=Повторите пароль
prompt.sign-in=Уже зарегистрированы?
prompt.sign-up=Ещё не зарегистрированы?
action.sign-in=Войти
message.wrong-username-or-password=Неверные логин или пароль
message.not-admitted=Ошибка авторизации
error.smth-went-wrong=Что-то пошло не так...
error.connection-failed=Ошибка подключения
message.sign-up=Регистрация пользователя
message.sign-up-success=Вы успешно зарегистрированы!
message.sign-up-fail=Невозможно зарегистрировать пользователя с указанными данными
message.sign-up-username-rules=Пожалуйста, придумайте имя пользователя, состоящее только из строчных букв латинского алфавита и цифр
message.sign-up-password-rules=Пароль должен содержать только буквы латинского \
алфавита и цифры и быть от 4 до 8 символов длиной
message.sign-up-password-repeat=Пароли не совпадают - перепроверьте, пожалуйста, пароль
error.operation-fail=Не удалось выполнить операцию
title.upload=Загрузка файла в удаленное хранилище
title.rename-file=Переименование файла
title.rename-directory=Переименование папки
prompt.path=Введите путь к файлу
message.cannot-upload-directory=Не умею закачивать папки, только файлы!
error.file-not-found=Файл не найден
prompt.new-file-name=Введите новое имя файла
prompt.new-directory-name=Введите новое имя папки
error.file-name=Некорректное имя файла
error.directory-name=Некорректное имя папки
title.delete-file=Удаление файла
title.delete-directory=Удаление папки
prompt.new-folder=Введите имя папки
title.download=Загрузка файла из удаленного хранилища
prompt.download=Загрузить файл на локальный диск?
error.error=Ошибка
title.select-file=Выберите файл
message.already-being-uploaded=Файл уже загружается