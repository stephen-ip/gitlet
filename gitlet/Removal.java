package gitlet;

import java.io.File;
import java.io.Serializable;

public class Removal implements Serializable {

    public Removal(String fileName, String blobName) {
        _fileName = fileName;
        _blobName = blobName;
        int dotIndex = _fileName.lastIndexOf('.');
        String fileNameCleaned = (dotIndex == -1) ? _fileName
                : _fileName.substring(0, dotIndex)
                + _fileName.substring(dotIndex + 1);
        File additionFile = Utils.join(Main.REMOVAL_FOLDER, fileNameCleaned);
        Utils.writeObject(additionFile, this);
    }

    public String getFileName() {
        return _fileName;
    }

    public String getBlobName() {
        return _blobName;
    }

    /** Removal object's file name. */
    private final String _fileName;

    /** Removal object's blob name. */
    private final String _blobName;
}
