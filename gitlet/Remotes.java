package gitlet;

import java.io.File;
import java.io.Serializable;
import java.util.HashMap;

public class Remotes implements Serializable {

    public Remotes() {
        _remotes = new HashMap<>();
        File remotesFile = Utils.join(Main.GITLET_FOLDER, "remotes");
        Utils.writeObject(remotesFile, this);
    }

    public HashMap<String, File> getRemotes() {
        return _remotes;
    }

    public void setRemote(String name, String directory) {
        _remotes.put(name, new File(directory));
        File remotesFile = Utils.join(Main.GITLET_FOLDER, "remotes");
        Utils.writeObject(remotesFile, this);
    }

    public File getDirectory(String name) {
        return _remotes.get(name);
    }

    public void rmRemote(String name) {
        _remotes.remove(name);
        File remotesFile = Utils.join(Main.GITLET_FOLDER, "remotes");
        Utils.writeObject(remotesFile, this);
    }

    /** The mapping of the name of a remote to its .gitlet directory. */
    private HashMap<String, File> _remotes;
}
