import java.io.Serializable;
import java.util.ArrayList;


public class MyFileList implements Serializable{

    private ArrayList<MyFile> fileList;

    public MyFileList(ArrayList<MyFile> fileList) {
        this.fileList = fileList;
    }

    public ArrayList<MyFile> getFileList() {
        return fileList;
    }
}
