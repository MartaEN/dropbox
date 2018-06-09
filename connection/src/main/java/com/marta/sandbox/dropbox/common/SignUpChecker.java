package com.marta.sandbox.dropbox.common;

public interface SignUpChecker {

    void signUp(String user, String password);
    void checkNewUserName(String name);

}
