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

// Имплементация интерфейса AuthService с подключением к базе данных.
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
