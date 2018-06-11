package com.marta.sandbox.dropbox.common.api;

public interface SignUpChecker {

    void signUp(String user, String password);
    void checkNewUserName(String name);

}
