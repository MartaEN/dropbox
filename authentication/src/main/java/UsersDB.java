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
    public void startService () throws RuntimeException {

        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + DATABASE_URL);
            prepareStatements();
        } catch (SQLException e) {
            throw new RuntimeException(e.getMessage());
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
