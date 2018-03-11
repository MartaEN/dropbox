public interface UsersDOA {
    void startService () throws RuntimeException;
    void stopService ();
    boolean registerNewUser (String username, String password);
    boolean validateNewUserName (String username);
    boolean validateLogin (String username, String password);
}
