import java.io.Serializable;

public class MyFile implements Serializable {

    private FileType type;
    private String name;
    private int size;

    public enum FileType {
        FILE (" "), DIR (">");

        String value;

        FileType(String value) {
            this.value = value;
        }
    }

    public MyFile(FileType type, String name, int size) {
        this.type = type;
        this.name = name;
        this.size = size;
    }

    public FileType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public int getSize() {
        return size;
    }
}
