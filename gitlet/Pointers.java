package gitlet;

import java.io.File;
import java.io.Serializable;
import java.util.HashMap;

public class Pointers implements Serializable {

    public Pointers() {
        _pointers = new HashMap<>();
        _head = null;
        File commitFile = Utils.join(Main.COMMIT_FOLDER, "pointers");
        Utils.writeObject(commitFile, this);
    }

    public HashMap<String, Commit> getPointers() {
        return _pointers;
    }

    public void setPointer(String name, Commit commit) {
        _pointers.put(name, commit);
        File commitFile = Utils.join(Main.COMMIT_FOLDER, "pointers");
        Utils.writeObject(commitFile, this);
    }

    public Commit getCommit(String name) {
        return _pointers.get(name);
    }

    public void rmPointer(String name) {
        _pointers.remove(name);
        File commitFile = Utils.join(Main.COMMIT_FOLDER, "pointers");
        Utils.writeObject(commitFile, this);
    }

    public void setHead(String name) {
        _head = name;
        File commitFile = Utils.join(Main.COMMIT_FOLDER, "pointers");
        Utils.writeObject(commitFile, this);
    }

    public Commit getHeadCommit() {
        return _pointers.get(_head);
    }

    public String getHead() {
        return _head;
    }

    /** A mapping of branch names to commit objects. */
    private HashMap<String, Commit> _pointers;

    /** The name of the pointer that is currently the head. */
    private String _head;
}
