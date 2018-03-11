public interface LoginChecker {

    void signIn(Session session, String user, String password);
    void signUp(Session session, String user, String password);
    void checkNewUserName(Session session, String name);

}
