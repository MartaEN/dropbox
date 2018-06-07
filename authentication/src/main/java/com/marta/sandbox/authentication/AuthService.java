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
