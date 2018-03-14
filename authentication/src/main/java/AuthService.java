public class AuthService {

    private final UsersDOA userList;

    AuthService (String dbURL) {
        userList = new UsersDB(dbURL);
    }

    String start () {
        try {
            return userList.startService();
        } catch (Exception e) {
            throw new AuthServiceException("ERROR INITIALIZING USER AUTHENTICATION SERVICE:\n\t"+e.getMessage());
        }
    }

    boolean loginAccepted (String username, String password) {
        return userList.validateLogin(username, password);
    }

    boolean newUserNameAccepted(String username) {
        return userList.validateNewUserName(username);
    }

    boolean registerNewUser (String username, String password) {
        return userList.registerNewUser(username, password);
    }

    void stop () {
        userList.stopService();
    }
}