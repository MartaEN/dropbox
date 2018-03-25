import java.io.*;
import java.nio.file.Path;
import java.util.HashMap;

public class DownloadManager {

    private HashMap <File, Stats> downloads;

    private class Stats {
        File targetFile;
        int sequence;
        long bytesTotal;
        long bytesLeft;

        Stats(File targetFile, long size) {
            this.targetFile = targetFile;
            this.sequence = 0;
            this.bytesTotal = size;
            this.bytesLeft = size;
        }
    }

    private static final DownloadManager thisInstance = new DownloadManager();

    private DownloadManager () { this.downloads = new HashMap<>(); }

    public static DownloadManager getInstance () { return thisInstance; }

    public void enlistDownload (Path destinationDirectory, File file) throws DownloadManagerException {

        if(downloads.containsKey(file)) throw new DownloadManagerException("File already being downloaded");

        try {
            String newName = file.getName();
            File newFile = new File(destinationDirectory.resolve(newName).toString());
            int prefix = 1;
            while (!newFile.createNewFile()) {
                newFile = new File(destinationDirectory.resolve("(" + prefix + ")" + newName).toString());
                prefix++;
            }
            newFile.setReadable(false);
            System.out.println("File enlisted for download - source name: "+file.getName()+", target path: "
                    + destinationDirectory.resolve(newName).toString() + ", size: " + file.length());
            downloads.put(file, new Stats(newFile, file.length()));
        } catch (IOException e) {
            throw new DownloadManagerException(e);
        }
    }

    public void download (File file, int sequence, byte[] data) throws DownloadManagerException {

        if(!downloads.containsKey(file)) return;

        if(sequence != downloads.get(file).sequence + 1) {
            downloads.get(file).targetFile.delete();
            downloads.remove(file);
            throw new DownloadManagerException("Download failed for lost parts - please repeat");
        }

        try (OutputStream output = new BufferedOutputStream(new FileOutputStream(downloads.get(file).targetFile, true), data.length)) {
            output.write(data);
            downloads.get(file).sequence++;
            downloads.get(file).bytesLeft -= data.length;
        } catch (IOException e) {
            downloads.get(file).targetFile.delete();
            downloads.remove(file);
            throw new DownloadManagerException(e);
        }

        if (downloads.get(file).bytesLeft == 0) {
            downloads.get(file).targetFile.setReadable(true);
            downloads.remove(file);
        }

    }

}
