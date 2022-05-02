package gitlet;

import java.io.File;
import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class Commit implements Serializable {

    public Commit(String logMessage,
                  HashMap<String, String> filesTracking,
                  HashMap<String, String> filesRemoveTracking,
                  Commit parent, Commit branchParent) {
        _logMessage = logMessage;
        _timestamp = new Date();
        if (parent != null && parent._filesTracking != null) {
            _filesTracking = parent.getFilesTracked();
            _filesTracking.putAll(filesTracking);
        } else {
            _filesTracking = filesTracking;
        }
        if (filesRemoveTracking != null) {
            for (Map.Entry<String, String> entry
                    : filesRemoveTracking.entrySet()) {
                String key = entry.getKey();
                _filesTracking.remove(key);
            }
        }
        _parent = parent;
        _branchParent = branchParent;
        _hash = Utils.sha1(Utils.serialize(this));
        File commitFile = Utils.join(Main.COMMIT_FOLDER, _hash);
        Utils.writeObject(commitFile, this);
        Pointers pointers = Main.getPointers();
        if (pointers.getHead() == null) {
            pointers.setPointer("master", this);
            pointers.setHead("master");
        } else {
            pointers.setPointer(pointers.getHead(), this);
        }
    }

    public String getHash() {
        return _hash;
    }

    public String getLogMessage() {
        return _logMessage;
    }

    public Date getTimeStamp() {
        return _timestamp;
    }

    public Commit getParent() {
        return _parent;
    }

    public HashMap<String, String> getFilesTracked() {
        return _filesTracking;
    }

    public Commit getBranchparent() {
        return _branchParent;
    }

    /** The commit object's hash (filename). */
    private final String _hash;

    /** The commit log message. */
    private final String _logMessage;

    /** The time the commit was made. */
    private final Date _timestamp;

    /** The files that are being tracked in this commit. */
    private final HashMap<String, String> _filesTracking;

    /** The (first) parent commit object of this commit. */
    private final Commit _parent;

    /** The branch parent commit object of this commit. */
    private final Commit _branchParent;
}




