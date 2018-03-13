import java.sql.*;

public class UsersDB implements UsersDOA {

    private final String DATABASE_URL;
    private Connection connection;
    private PreparedStatement authQuery;
    private PreparedStatement findUserQuery;
    private PreparedStatement registerNewUserQuery;

    UsersDB(String databaseURL) {
        this.DATABASE_URL = databaseURL;
    }

    @Override
    public String startService () throws RuntimeException {

        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + DATABASE_URL);
            prepareStatements();
            checkUsersTable();
            return ("AUTHENTICATION SERVICE: CONNECTED TO " + DATABASE_URL);
        } catch (SQLException e) {
            try {
                connection = DriverManager.getConnection("jdbc:sqlite:" + DATABASE_URL);
                createUsersTable();
                prepareStatements();
                checkUsersTable();
                return ("ATTENTION --- AUTHENTICATION SERVICE: NEW USER DATABASE CREATED [" + DATABASE_URL + "]");
            } catch (SQLException e1) {
                throw new RuntimeException(e.getMessage());
            }
        }
    }

    private void prepareStatements () throws SQLException {
        authQuery = connection.prepareStatement("SELECT * FROM users WHERE user = ? AND password = ? LIMIT 1");
        findUserQuery = connection.prepareStatement("SELECT * FROM users WHERE user = ? LIMIT 1");
        registerNewUserQuery = connection.prepareStatement("INSERT INTO users (user, password) VALUES (?, ?)");
    }

    @Override
    public synchronized boolean registerNewUser(String username, String password) {
        try {
            registerNewUserQuery.setString(1, username);
            registerNewUserQuery.setString(2, password);
            return registerNewUserQuery.executeUpdate() == 1;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean validateNewUserName(String username) {
        try {
            findUserQuery.setString(1, username);
            return !findUserQuery.executeQuery().next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean validateLogin(String username, String password) {
        try {
            authQuery.setString(1, username);
            authQuery.setString(2, password);
            return authQuery.executeQuery().next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void createUsersTable () throws SQLException {
        connection.createStatement().executeUpdate("CREATE TABLE IF NOT EXISTS users (" +
                "    id     INTEGER PRIMARY KEY AUTOINCREMENT," +
                "    user CHAR (20) NOT NULL UNIQUE," +
                "    password CHAR (12) NOT NULL); " +
                "    CREATE UNIQUE INDEX i_users ON users (user);");
    }

    private void checkUsersTable () throws SQLException {
        authQuery.setString(1, "test");
        authQuery.setString(2, "test");
        authQuery.executeQuery().next();
    }

    @Override
    public void stopService() {
        try {
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        try {
            authQuery.close();
            findUserQuery.close();
            registerNewUserQuery.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
