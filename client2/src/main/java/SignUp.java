import javax.swing.*;

public class SignUp {

    public void onInput(Object input) {

        switch ((String)input) {
            case "/username_ok":
            case "/ok":
                break;
            case "/username_rejected":
                JOptionPane.showConfirmDialog(null,
                        "Пользователь с таким именем уже есть",
                        "Регистрация пользователя", JOptionPane.WARNING_MESSAGE);
                username.clear();
                break;
            case "/login_rejected":
            case "/fail":
                //TODO
                JOptionPane.showMessageDialog(null, "Что-то пошло не так...",
                        "Регистрация пользователя", JOptionPane.WARNING_MESSAGE);
                username.clear();
                password.clear();
                password1.clear();
                break;
            case "/welcome":
                JOptionPane.showMessageDialog(null, "Вы успешно зарегистрированы!!!",
                        "Регистрация пользователя", JOptionPane.INFORMATION_MESSAGE);
                SceneManager.getInstance().switchSceneTo(SceneManager.Scenes.WORK);
                break;
            default:
                System.out.println("--------THIS SHOULD NOT HAPPEN - UNKNOWN COMMAND IN CLIENT'S REGISTRATION SCREEN");
        }
    }

    public static void signUp(String username, String password, String password1) {
        if (isUsernameOK(username)
                && isPasswordOK(password)
                && (password1.equals(password))) {
            Client.getInstance().send(Client.Scenes.REGISTRATION,"/signUp " + username + " " + password);
        } else System.out.println("------SMTH WENT WRONG IN REGISTRATION SCREEN");
    }

    public static void checkNewUserName(String newName) {
        if (isUsernameOK(newName)) {
            Client.getInstance().send(Client.Scenes.REGISTRATION, "/newUser " + newName);
        } else {
            JOptionPane.showConfirmDialog(null,
                    "Пожалуйста, придумайте имя пользователя, состоящее только из строчных букв латинского алфавита и цифр",
                    "Регистрация пользователя", JOptionPane.WARNING_MESSAGE);
//            username.clear();
        }
    }

    static boolean isUsernameOK (String username) {
        return username.chars().allMatch(ch -> (ch >= 'a' && ch <= 'z')
                || (ch >= '0' && ch <= '9'));
    }

    static boolean isPasswordOK (String password) {
        if (password.length() > 3 && password.length() < 9)
            return password.chars().allMatch(ch ->
                    (ch >= 'a' && ch <= 'z')
                            || (ch >= 'A' && ch <= 'Z')
                            || (ch >= '0' && ch <= '9'));
        return false;
    }
}
