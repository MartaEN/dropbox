package com.marta.sandbox.dropbox.common.messaging;

import java.io.Serializable;
import java.util.List;


public class MyFileList implements Serializable{

    private List<MyFile> fileList;

    public MyFileList(List<MyFile> fileList) {
        this.fileList = fileList;
    }

    public List<MyFile> getFileList() {
        return fileList;
    }
}
