package gitlet;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Objects;
import java.util.Map;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Stack;
import java.util.Queue;
import java.util.LinkedList;

/** Driver class for Gitlet, the tiny stupid version-control system.
 *  @author Stephen Ip
 */
public class Main {
    /** Current Working Directory. */
    static final File CWD = new File(".");

    /** Main metadata folder. */
    static final File GITLET_FOLDER = new File(".gitlet");

    /** Blobs folder. */
    static final File BLOB_FOLDER = Utils.join(GITLET_FOLDER, "blobs");

    /** Commits folder. */
    static final File COMMIT_FOLDER = Utils.join(GITLET_FOLDER, "commits");

    /** Staging area folder. */
    static final File STAGING_AREA_FOLDER = Utils.join(GITLET_FOLDER,
            "staging_area");

    /** Addition folder. */
    static final File ADDITION_FOLDER = Utils.join(STAGING_AREA_FOLDER,
            "addition");

    /** Removal folder. */
    static final File REMOVAL_FOLDER = Utils.join(STAGING_AREA_FOLDER,
            "removal");

    /** Usage: java gitlet.Main ARGS, where ARGS contains
     *  <COMMAND> <OPERAND> .... */
    public static void main(String... args) throws IOException {
        if (args.length == 0) {
            exitWithError("Please enter a command.");
        }
        switch (args[0]) {
        case "init" -> setupPersistence(args);
        case "add" -> add(args);
        case "commit" -> commit(args);
        case "rm" -> rm(args);
        case "checkout" -> checkout(args);
        case "branch" -> branch(args);
        case "rm-branch" -> rmBranch(args);
        case "log" -> log(args);
        case "global-log" -> globalLog(args);
        case "find" -> find(args);
        case "status" -> status(args);
        case "reset" -> reset(args);
        case "merge" -> merge(args);
        case "add-remote" -> addRemote(args);
        case "rm-remote" -> rmRemote(args);
        case "push" -> push(args);
        case "fetch" -> fetch(args);
        case "pull" -> pull(args);
        default -> exitWithError("No command with that name exists.");
        }
    }

    public static void setupPersistence(String[] args) {
        validateNumArgs(args, 1);
        if (GITLET_FOLDER.exists()) {
            exitWithError("A Gitlet version-control system already exists "
                    + "in the current directory.");
        }
        GITLET_FOLDER.mkdir();
        STAGING_AREA_FOLDER.mkdir();
        ADDITION_FOLDER.mkdir();
        REMOVAL_FOLDER.mkdir();
        COMMIT_FOLDER.mkdir();
        BLOB_FOLDER.mkdir();
        new Pointers();
        new Remotes();
        new Commit("initial commit",
                null,
                null,
                null,
                null);
    }

    public static void add(String[] args) {
        validateGitletDir();
        validateNumArgs(args, 2);
        String fileName = args[1];
        File addFile = Utils.join(CWD, fileName);
        if (!addFile.exists()) {
            exitWithError("File does not exist.");
        }
        byte[] blob = Utils.readContents(addFile);
        String sha1hash = Utils.sha1(blob);
        Commit headCommit = getHeadCommit();
        if (headCommit.getFilesTracked() != null
                && headCommit.getFilesTracked().containsKey(fileName)) {
            if (Objects.equals(headCommit
                    .getFilesTracked()
                    .get(fileName), sha1hash)) {
                for (String file : Utils.plainFilenamesIn(ADDITION_FOLDER)) {
                    File additionFile = Utils.join(ADDITION_FOLDER, file);
                    Addition addition = Utils.readObject(additionFile,
                            Addition.class);
                    if (Objects.equals(addition.getFileName(), fileName)) {
                        Utils.join(ADDITION_FOLDER, file).delete();
                    }
                }
                for (String removalFileName
                        : Utils.plainFilenamesIn(REMOVAL_FOLDER)) {
                    File removalFile = Utils.join(REMOVAL_FOLDER,
                            removalFileName);
                    Removal removal = Utils.readObject(removalFile,
                            Removal.class);
                    if (Objects.equals(removal.getBlobName(), sha1hash)) {
                        removalFile.delete();
                    }
                }
                return;
            }
        }
        Utils.writeContents(Utils.join(BLOB_FOLDER, sha1hash), blob);
        new Addition(fileName, sha1hash);
    }

    public static void commit(String[] args) {
        validateGitletDir();
        validateNumArgs(args, 2);
        String commitMessage = args[1];
        if (commitMessage == null || commitMessage.equals("")) {
            exitWithError("Please enter a commit message.");
        }
        HashMap<String, String> filesTracking = new HashMap<>();
        HashMap<String, String> filesRemoveTracking = new HashMap<>();
        for (String fileName : Utils.plainFilenamesIn(ADDITION_FOLDER)) {
            File addFile = Utils.join(ADDITION_FOLDER, fileName);
            Addition addObject = Utils.readObject(addFile, Addition.class);
            filesTracking.put(addObject.getFileName(),
                    addObject.getBlobName());
            addFile.delete();
        }
        for (String fileName : Utils.plainFilenamesIn(REMOVAL_FOLDER)) {
            File removeFile = Utils.join(REMOVAL_FOLDER, fileName);
            Removal removeObject = Utils.readObject(removeFile,
                    Removal.class);
            filesRemoveTracking.put(
                    removeObject.getFileName(),
                    removeObject.getBlobName());
            removeFile.delete();
        }
        if (filesTracking.isEmpty() && filesRemoveTracking.isEmpty()) {
            exitWithError("No changes added to the commit.");
        }
        Commit oldHead = getHeadCommit();
        new Commit(commitMessage,
                filesTracking,
                filesRemoveTracking,
                oldHead,
                null);
    }

    public static void rm(String[] args) {
        validateGitletDir();
        validateNumArgs(args, 2);
        String fileName = args[1];
        File rmFile = Utils.join(CWD, fileName);
        boolean staged = false;
        boolean tracked = false;
        for (String fn : Utils.plainFilenamesIn(Utils.join(ADDITION_FOLDER))) {
            File additionFile = Utils.join(ADDITION_FOLDER, fn);
            Addition addition = Utils.readObject(additionFile, Addition.class);
            String additionFileName = addition.getFileName();
            if (Objects.equals(additionFileName, fileName)) {
                staged = true;
                additionFile.delete();
            }
        }
        Commit currCommit = getHeadCommit();
        if (currCommit.getFilesTracked() != null) {
            if (currCommit.getFilesTracked().get(fileName) != null) {
                tracked = true;
                new Removal(fileName,
                        currCommit.getFilesTracked().get(fileName));
                rmFile.delete();
            }
        }
        if (!staged && !tracked) {
            exitWithError("No reason to remove the file.");
        }
    }

    public static void checkout(String[] args) {
        validateGitletDir();
        if (Objects.equals(args[1], "--")) {
            checkoutFile(args);
        } else if (args.length == 4 && Objects.equals(args[2], "--")) {
            checkoutFileCommit(args);
        } else {
            checkoutBranch(args);
        }
    }

    public static void checkoutFile(String[] args) {
        validateNumArgs(args, 3);
        String fileName = args[2];
        File checkoutFile = Utils.join(CWD, fileName);
        Commit headCommit = getHeadCommit();
        HashMap<String, String> filesTracked = headCommit.getFilesTracked();
        if (filesTracked == null || filesTracked.get(fileName) == null) {
            exitWithError("File does not exist in that commit.");
        }
        String commitBlobName = filesTracked.get(fileName);
        File blobFile = Utils.join(BLOB_FOLDER, commitBlobName);
        byte[] commitBlob = Utils.readContents(blobFile);
        Utils.writeContents(checkoutFile, commitBlob);
    }

    public static void checkoutFileCommit(String[] args) {
        validateNumArgs(args, 4);
        String commitId = args[1];
        String fileName = args[3];
        File checkoutFile = Utils.join(CWD, fileName);
        for (String commitFileName
                : Utils.plainFilenamesIn(COMMIT_FOLDER)) {
            if (Objects.equals(commitFileName, commitId)
                    || commitFileName.startsWith(commitId)) {
                File commitFile = Utils.join(COMMIT_FOLDER, commitFileName);
                Commit commitObj = Utils.readObject(commitFile,
                        Commit.class);
                HashMap<String, String> filesTracked =
                        commitObj.getFilesTracked();
                if (filesTracked == null
                        || filesTracked.get(fileName) == null) {
                    exitWithError("File does not exist in that commit.");
                }
                String commitBlobName = filesTracked.get(fileName);
                File commitBlobFile = Utils.join(BLOB_FOLDER,
                        commitBlobName);
                byte[] commitBlob = Utils.readContents(commitBlobFile);
                Utils.writeContents(checkoutFile, commitBlob);
                return;
            }
        }
        exitWithError("No commit with that id exists.");
    }

    public static void checkoutBranch(String[] args) {
        validateNumArgs(args, 2);
        String branchName = args[1];
        Pointers pointers = getPointers();
        Commit branchCommit = pointers.getCommit(branchName);
        if (branchCommit == null) {
            exitWithError("No such branch exists.");
        } else if (Objects.equals(branchName, pointers.getHead())) {
            exitWithError("No need to checkout the current branch.");
        } else if (Objects.equals(branchCommit.getHash(),
                getHeadCommit().getHash())) {
            pointers.setHead(branchName);
            clearAdditionArea();
            clearRemovalArea();
            return;
        }
        Commit currCommit = getHeadCommit();
        if (branchCommit.getFilesTracked() != null) {
            untrackedFileCheck(branchCommit);
            for (Map.Entry<String, String> entry
                    : branchCommit.getFilesTracked().entrySet()) {
                String trackedFileName = entry.getKey();
                String blobName = entry.getValue();
                File branchFile = Utils.join(CWD, trackedFileName);
                File blobFile = Utils.join(BLOB_FOLDER, blobName);
                byte[] branchFileData = Utils.readContents(blobFile);
                Utils.writeContents(branchFile, branchFileData);
            }
        }
        pointers.setHead(branchName);
        for (Map.Entry<String, String> entry
                : currCommit.getFilesTracked().entrySet()) {
            String trackedFileName = entry.getKey();
            if (branchCommit.getFilesTracked() == null
                    || branchCommit.getFilesTracked()
                    .get(trackedFileName) == null) {
                Utils.join(CWD, trackedFileName).delete();
            }
        }
        clearAdditionArea();
        clearRemovalArea();
    }

    public static void log(String[] args) {
        validateGitletDir();
        validateNumArgs(args, 1);
        SimpleDateFormat simpleDateFormat =
                new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy Z");
        Commit curr = getHeadCommit();
        while (curr != null) {
            System.out.println("===");
            System.out.println("commit " + curr.getHash());
            System.out.println("Date: " + simpleDateFormat
                    .format(curr.getTimeStamp()));
            System.out.println(curr.getLogMessage());
            System.out.println();
            curr = curr.getParent();
        }
    }

    public static void globalLog(String[] args) {
        validateGitletDir();
        validateNumArgs(args, 1);
        SimpleDateFormat simpleDateFormat =
                new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy Z");
        for (String fileName : Utils.plainFilenamesIn(COMMIT_FOLDER)) {
            if (!Objects.equals(fileName, "pointers")) {
                File commitFile = Utils.join(COMMIT_FOLDER, fileName);
                Commit commit = Utils.readObject(commitFile, Commit.class);
                System.out.println("===");
                System.out.println("commit " + commit.getHash());
                System.out.println("Date: " + simpleDateFormat
                        .format(commit.getTimeStamp()));
                System.out.println(commit.getLogMessage());
                System.out.println();
            }
        }
    }

    public static void find(String[] args) {
        validateGitletDir();
        validateNumArgs(args, 2);
        String commitMessage = args[1];
        boolean found = false;
        for (String fileName : Utils.plainFilenamesIn(COMMIT_FOLDER)) {
            if (!Objects.equals(fileName, "pointers")) {
                File commitFile = Utils.join(COMMIT_FOLDER, fileName);
                Commit commit = Utils.readObject(commitFile,
                        Commit.class);
                if (Objects.equals(commit.getLogMessage(),
                        commitMessage)) {
                    System.out.println(commit.getHash());
                    found = true;
                }
            }
        }
        if (!found) {
            System.out.println("Found no commit with that message.");
        }
    }

    public static void status(String[] args) {
        validateGitletDir();
        validateNumArgs(args, 1);
        Pointers pointers = getPointers();
        System.out.println("=== Branches ===");
        System.out.println("*" + pointers.getHead());
        TreeMap<String, Commit> sortedPointers =
                new TreeMap<>(pointers.getPointers());
        for (Map.Entry<String, Commit> entry
                : sortedPointers.entrySet()) {
            String pointerName = entry.getKey();
            if (!Objects.equals(pointerName, pointers.getHead())) {
                System.out.println(pointerName);
            }
        }
        System.out.println();
        System.out.println("=== Staged Files ===");
        for (String fn : Utils.plainFilenamesIn(ADDITION_FOLDER)) {
            File additionFile = Utils.join(ADDITION_FOLDER, fn);
            Addition additionObj = Utils.readObject(additionFile,
                    Addition.class);
            System.out.println(additionObj.getFileName());
        }
        System.out.println();
        System.out.println("=== Removed Files ===");
        for (String fn : Utils.plainFilenamesIn(REMOVAL_FOLDER)) {
            File removalFile = Utils.join(REMOVAL_FOLDER, fn);
            Removal removalObj = Utils.readObject(removalFile,
                    Removal.class);
            System.out.println(removalObj.getFileName());
        }
        System.out.println();
        System.out.println("=== Modifications Not Staged For Commit ===");
        if (getHeadCommit().getFilesTracked() != null) {
            for (String fn : getHeadCommit().getFilesTracked().keySet()) {
                String res = modifiedNotStaged(fn);
                if (res != null) {
                    System.out.println(res);
                }
            }
        }
        System.out.println();
        System.out.println("=== Untracked Files ===");
        for (String fn : Utils.plainFilenamesIn(CWD)) {
            if (!isTracked(fn) && (!isStagedAdd(fn) || isStagedRem(fn))) {
                System.out.println(fn);
            }
        }
        System.out.println();
    }

    public static void reset(String[] args) {
        validateGitletDir();
        validateNumArgs(args, 2);
        String commitId = args[1];
        Commit resetCommit = null;
        for (String commitHash : Utils.plainFilenamesIn(COMMIT_FOLDER)) {
            if (!Objects.equals(commitHash, "pointers")) {
                if (Objects.equals(commitHash, commitId)) {
                    File commitFile = Utils.join(COMMIT_FOLDER, commitHash);
                    resetCommit = Utils.readObject(commitFile, Commit.class);
                }
            }
        }
        if (resetCommit == null) {
            exitWithError("No commit with that id exists.");
        }
        HashMap<String, String> filesTrackedReset = resetCommit
                .getFilesTracked();
        if (filesTrackedReset != null) {
            untrackedFileCheck(resetCommit);
            for (Map.Entry<String, String> entry
                    : filesTrackedReset.entrySet()) {
                String fileName = entry.getKey();
                String blobName = entry.getValue();
                File blobFile = Utils.join(BLOB_FOLDER, blobName);
                File cwdFile = Utils.join(CWD, fileName);
                Utils.writeContents(cwdFile, Utils.readContents(blobFile));
            }
        }
        Commit currCommit = getHeadCommit();
        HashMap<String, String> filesTracked = currCommit.getFilesTracked();
        if (filesTracked != null) {
            for (Map.Entry<String, String> entry : filesTracked.entrySet()) {
                String fileName = entry.getKey();
                if (filesTrackedReset == null
                        || !filesTrackedReset.containsKey(fileName)) {
                    Utils.join(CWD, fileName).delete();
                }
            }
        }
        clearAdditionArea();
        clearRemovalArea();
        Pointers pointers = getPointers();
        pointers.setPointer(pointers.getHead(), resetCommit);
    }

    public static void branch(String[] args) {
        validateGitletDir();
        validateNumArgs(args, 2);
        String branchName = args[1];
        Pointers pointers = getPointers();
        if (pointers.getCommit(branchName) != null) {
            exitWithError("A branch with that name already exists.");
        }
        pointers.setPointer(branchName, getHeadCommit());
    }

    public static void rmBranch(String[] args) {
        validateGitletDir();
        validateNumArgs(args, 2);
        Pointers pointers = getPointers();
        String branchName = args[1];
        if (pointers.getCommit(branchName) == null) {
            exitWithError("A branch with that name does not exist.");
        } else if (Objects.equals(pointers.getHead(), branchName)) {
            exitWithError("Cannot remove the current branch.");
        }
        pointers.rmPointer(branchName);
    }

    public static void merge(String[] args) {
        validateGitletDir();
        validateNumArgs(args, 2);
        String branchName = args[1];
        Commit branchCommit = getPointers().getCommit(branchName);
        preMergeCheck(branchCommit, branchName);
        Commit headCommit = getHeadCommit();
        Commit splitPoint = findSplitPoint(headCommit, branchCommit);
        if (!preMergeCheck2(headCommit, branchCommit, branchName, splitPoint)) {
            return;
        }
        ArrayList<String> allFilesMerge = getAllMergeFiles(headCommit,
                branchCommit, splitPoint);
        untrackedFileCheck(branchCommit);
        boolean mergeConflict = false;
        for (String file : allFilesMerge) {
            String result = null;
            String contentsSplit = splitPoint.getFilesTracked() != null
                    ? splitPoint.getFilesTracked().get(file) : null;
            String contentsHead = headCommit.getFilesTracked().get(file);
            String contentsOther = branchCommit.getFilesTracked().get(file);
            boolean modifiedInOther = !Objects.equals(contentsOther,
                    contentsSplit);
            boolean modifiedInHead = !Objects.equals(contentsHead,
                    contentsSplit);
            boolean notInSplit = contentsSplit == null;
            boolean notInOther = contentsOther == null;
            boolean notInHead = contentsHead == null;
            if ((notInSplit && notInOther && !notInHead)
                    || (modifiedInHead && !modifiedInOther)) {
                result = contentsHead;
            } else if ((notInSplit && notInHead && !notInOther)
                    || (modifiedInOther && !modifiedInHead)) {
                result = contentsOther;
            } else if ((!modifiedInHead && notInOther)
                    || (!modifiedInOther && notInHead)) {
                result = null;
            } else if (modifiedInHead && modifiedInOther) {
                if (Objects.equals(contentsHead, contentsOther)) {
                    result = contentsOther;
                } else {
                    mergeConflict = true;
                }
            }
            if (mergeConflict) {
                handleMergeConflict(contentsHead, contentsOther, file);
            } else if (!Objects.equals(contentsHead, result)) {
                File headFile = Utils.join(CWD, file);
                if (result == null) {
                    handleMergeRemoval(file, headFile);
                } else {
                    handleMergeAddition(file, headFile, result);
                }
            }
        }
        mergeCommit(branchCommit, branchName, headCommit, mergeConflict);
    }

    public static void addRemote(String[] args) {
        validateGitletDir();
        validateNumArgs(args, 3);
        String remoteName = args[1];
        String remoteDir = args[2];
        Remotes remotes = getRemotes();
        if (remotes.getRemotes().containsKey(remoteName)) {
            exitWithError("A remote with that name already exists.");
        }
        remotes.setRemote(remoteName, remoteDir.replace("/", File.separator));
    }

    public static void rmRemote(String[] args) {
        validateGitletDir();
        validateNumArgs(args, 2);
        String remoteName = args[1];
        Remotes remotes = getRemotes();
        if (!remotes.getRemotes().containsKey(remoteName)) {
            exitWithError("A remote with that name does not exist.");
        }
        remotes.rmRemote(remoteName);
    }

    public static void push(String[] args) throws IOException {
        validateGitletDir();
        validateNumArgs(args, 3);
        String remoteName = args[1];
        String remoteBranchName = args[2];
        Remotes remotes = getRemotes();
        File remoteDir = remotes.getDirectory(remoteName);
        if (remoteDir == null || !remoteDir.exists()) {
            exitWithError("Remote directory not found.");
        }
        File remoteBlobsFile = Utils.join(remoteDir, "blobs");
        File remoteCommitsFile = Utils.join(remoteDir, "commits");
        File remotePointersFile = Utils.join(remoteCommitsFile, "pointers");
        Pointers remotePointers = Utils.readObject(remotePointersFile,
                Pointers.class);
        Commit remoteHeadBranch = remotePointers.getCommit(remoteBranchName);
        if (remoteHeadBranch == null) {
            remotePointers.setPointer(remoteBranchName,
                    remotePointers.getHeadCommit());
        }
        Commit localHeadBranch = getHeadCommit();
        Set<String> localHeadHistory = getAncestors(localHeadBranch);
        if (!localHeadHistory.contains(remoteHeadBranch.getHash())) {
            exitWithError("Please pull down remote changes before pushing.");
        }
        for (String fn : Utils.plainFilenamesIn(BLOB_FOLDER)) {
            File localFile = Utils.join(BLOB_FOLDER, fn);
            File remoteFile = Utils.join(remoteBlobsFile, fn);
            Files.copy(localFile.toPath(), remoteFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        }
        for (String fn : Utils.plainFilenamesIn(COMMIT_FOLDER)) {
            File localFile = Utils.join(COMMIT_FOLDER, fn);
            File remoteFile = Utils.join(remoteCommitsFile, fn);
            Files.copy(localFile.toPath(), remoteFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        }
        remotePointers.setPointer(remotePointers.getHead(), getHeadCommit());
    }

    public static void fetch(String[] args) throws IOException {
        validateGitletDir();
        validateNumArgs(args, 3);
        String remoteName = args[1];
        String remoteBranchName = args[2];
        Remotes remotes = getRemotes();
        File remoteDir = remotes.getDirectory(remoteName);
        if (remoteDir == null || !remoteDir.exists()) {
            exitWithError("Remote directory not found.");
        }
        File remoteBlobsFile = Utils.join(remoteDir, "blobs");
        File remoteCommitsFile = Utils.join(remoteDir, "commits");
        File remotePointersFile = Utils.join(remoteCommitsFile, "pointers");
        Pointers remotePointers = Utils.readObject(remotePointersFile,
                Pointers.class);
        if (!remotePointers.getPointers().containsKey(remoteBranchName)) {
            exitWithError("That remote does not have that branch.");
        }
        Commit remoteBranch = remotePointers.getCommit(remoteBranchName);
        Set<String> remoteBranchHistory = getAncestors(remoteBranch);
        for (String commitHash : remoteBranchHistory) {
            File commitFile = Utils.join(remoteCommitsFile, commitHash);
            Commit commit = Utils.readObject(commitFile, Commit.class);
            File localFileCommit = Utils.join(COMMIT_FOLDER, commitHash);
            Files.copy(commitFile.toPath(), localFileCommit.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
            if (commit.getFilesTracked() != null) {
                for (String blobName : commit.getFilesTracked().values()) {
                    File blobFile = Utils.join(remoteBlobsFile, blobName);
                    File localFileBlob = Utils.join(BLOB_FOLDER, blobName);
                    Files.copy(blobFile.toPath(), localFileBlob.toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        String branchName = remoteName + "/" + remoteBranchName;
        getPointers().setPointer(branchName, remoteBranch);
    }

    public static void pull(String[] args) throws IOException {
        validateGitletDir();
        validateNumArgs(args, 3);
        String remoteName = args[1];
        String remoteBranchName = args[2];
        fetch(new String[]{"fetch", remoteName, remoteBranchName});
        merge(new String[]{"merge", remoteName + "/" + remoteBranchName});
    }

    private static void preMergeCheck(
            Commit branchCommit, String branchName) {
        if (branchCommit == null) {
            exitWithError("A branch with that name does not exist");
        }
        if (Objects.equals(branchName, getPointers().getHead())) {
            exitWithError("Cannot merge a branch with itself.");
        }
        if (!Utils.plainFilenamesIn(ADDITION_FOLDER).isEmpty()
                || !Utils.plainFilenamesIn(REMOVAL_FOLDER).isEmpty()) {
            exitWithError("You have uncommitted changes.");
        }
    }

    private static boolean preMergeCheck2(Commit headCommit,
                                      Commit branchCommit,
                                      String branchName,
                                      Commit splitPoint) {
        if (Objects.equals(splitPoint.getHash(), branchCommit.getHash())) {
            System.out.println("Given branch is an ancestor "
                    + "of the current branch.");
            return false;
        } else if (Objects.equals(splitPoint.getHash(),
                headCommit.getHash())) {
            checkout(new String[]{"checkout", branchName});
            System.out.println("Current branch fast-forwarded.");
            return false;
        }
        return true;
    }

    private static Commit findSplitPoint(Commit head, Commit branch) {
        Set<String> visitedParents = getAncestors(branch);
        Queue<Commit> otherParents = new LinkedList<>();
        otherParents.add(head);
        while (!otherParents.isEmpty()) {
            Commit parentHead = otherParents.poll();
            String parentHeadString = parentHead.getHash();
            if (visitedParents.contains(parentHeadString)) {
                File parentHeadFile = Utils.join(COMMIT_FOLDER,
                        parentHeadString);
                return Utils.readObject(parentHeadFile, Commit.class);
            }
            if (parentHead.getParent() != null) {
                otherParents.add(parentHead.getParent());
            }
            if (parentHead.getBranchparent() != null) {
                otherParents.add(parentHead.getBranchparent());
            }
        }
        return null;
    }

    private static Set<String> getAncestors(Commit branch) {
        Stack<Commit> work = new Stack<>();
        Set<String> ancestors = new HashSet<>();
        work.add(branch);
        while (!work.empty()) {
            Commit parent = work.pop();
            ancestors.add(parent.getHash());
            if (parent.getParent() != null) {
                work.push(parent.getParent());
            }
            if (parent.getBranchparent() != null) {
                work.push(parent.getBranchparent());
            }
        }
        return ancestors;
    }

    private static ArrayList<String> getAllMergeFiles(Commit headCommit,
                                                     Commit branchCommit,
                                                     Commit splitPoint) {
        ArrayList<String> allFilesMerge = new ArrayList<>();
        if (splitPoint.getFilesTracked() != null) {
            allFilesMerge.addAll(splitPoint.getFilesTracked().keySet());
        }
        allFilesMerge.addAll(headCommit.getFilesTracked().keySet());
        allFilesMerge.addAll(branchCommit.getFilesTracked().keySet());
        Set<String> set = new HashSet<>(allFilesMerge);
        allFilesMerge.clear();
        allFilesMerge.addAll(set);
        return allFilesMerge;
    }

    private static void handleMergeConflict(String contentsHead,
                                           String contentsOther,
                                           String file) {
        File conflictFile = Utils.join(CWD, file);
        String dataHead = contentsHead != null
                ? Utils.readContentsAsString(Utils.join(BLOB_FOLDER,
                contentsHead))
                : "";
        String dataBranch = contentsOther != null
                ? Utils.readContentsAsString(Utils.join(BLOB_FOLDER,
                contentsOther))
                : "";
        Utils.writeContents(conflictFile,
                "<<<<<<< HEAD\n"
                        + dataHead
                        + "=======\n"
                        + dataBranch
                        + ">>>>>>>\n");
        byte[] conflictFileData = Utils.readContents(conflictFile);
        String sha1hash = Utils.sha1(conflictFileData);
        Utils.writeContents(Utils.join(BLOB_FOLDER, sha1hash),
                conflictFileData);
        new Addition(file, sha1hash);
    }

    private static void handleMergeRemoval(String file, File headFile) {
        Commit currCommit = getHeadCommit();
        if (currCommit.getFilesTracked() != null) {
            if (headFile.delete()) {
                new Removal(file, currCommit.getFilesTracked()
                        .get(file));
            }
        }
    }

    private static void handleMergeAddition(String file, File headFile,
                                      String result) {
        File resultBlob = Utils.join(BLOB_FOLDER, result);
        byte[] blob = Utils.readContents(resultBlob);
        String sha1hash = Utils.sha1(blob);
        Utils.writeContents(headFile, blob);
        new Addition(file, sha1hash);
    }

    private static void mergeCommit(Commit branchCommit,
                                   String branchName,
                                   Commit headCommit,
                                   boolean mergeConflict) {
        HashMap<String, String> filesTracking = new HashMap<>();
        HashMap<String, String> filesRemoveTracking = new HashMap<>();
        for (String fileName : Utils.plainFilenamesIn(ADDITION_FOLDER)) {
            File addFile = Utils.join(ADDITION_FOLDER, fileName);
            Addition addObject = Utils.readObject(addFile, Addition.class);
            filesTracking.put(addObject.getFileName(), addObject.getBlobName());
            addFile.delete();
        }
        for (String fileName : Utils.plainFilenamesIn(REMOVAL_FOLDER)) {
            File removeFile = Utils.join(REMOVAL_FOLDER, fileName);
            Removal removeObject = Utils.readObject(removeFile, Removal.class);
            filesRemoveTracking.put(removeObject.getFileName(),
                    removeObject.getBlobName());
            removeFile.delete();
        }
        new Commit("Merged " + branchName + " into "
                + getPointers().getHead() + ".",
                filesTracking,
                filesRemoveTracking,
                headCommit,
                branchCommit);
        if (mergeConflict) {
            System.out.println("Encountered a merge conflict.");
        }
    }

    private static String modifiedNotStaged(String filename) {
        if (isTracked(filename)
                && isModified(filename)
                && !isStagedAdd(filename)) {
            return "" + filename + " (modified)";
        }
        if (isStagedAdd(filename)) {
            File file = Utils.join(CWD, filename);
            if (!file.exists()) {
                return "" + filename + " (deleted)";
            }
            File additionFile = null;
            for (String fn : Utils.plainFilenamesIn(ADDITION_FOLDER)) {
                File aFile = Utils.join(ADDITION_FOLDER, fn);
                Addition addition = Utils.readObject(aFile, Addition.class);
                if (Objects.equals(addition.getFileName(), filename)) {
                    additionFile = aFile;
                }
            }
            Addition addition = Utils.readObject(additionFile, Addition.class);
            File blobFile = Utils.join(BLOB_FOLDER, addition.getBlobName());
            byte[] blobData = Utils.readContents(blobFile);
            byte[] fileData = Utils.readContents(file);
            if (!Utils.sha1(blobData).equals(Utils.sha1(fileData))) {
                return "" + filename + " (modified)";
            }
        }
        if (!isStagedRem(filename) && isTracked(filename)) {
            File file = Utils.join(CWD, filename);
            if (!file.exists()) {
                return "" + filename + " (deleted)";
            }
        }
        return null;
    }

    private static boolean isModified(String filename) {
        File file = Utils.join(CWD, filename);
        if (!file.exists()
                || getHeadCommit().getFilesTracked() == null) {
            return false;
        }
        String blobName = getHeadCommit().getFilesTracked().get(filename);
        File blobFile = Utils.join(BLOB_FOLDER, blobName);
        byte[] blobData = Utils.readContents(blobFile);
        byte[] fileData = Utils.readContents(file);
        return !Utils.sha1(blobData).equals(Utils.sha1(fileData));
    }

    private static boolean isStagedAdd(String filename) {
        for (String fn : Utils.plainFilenamesIn(ADDITION_FOLDER)) {
            File additionFile = Utils.join(ADDITION_FOLDER, fn);
            Addition addition = Utils.readObject(additionFile, Addition.class);
            if (Objects.equals(addition.getFileName(), filename)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isStagedRem(String filename) {
        for (String fn : Utils.plainFilenamesIn(REMOVAL_FOLDER)) {
            File removalFile = Utils.join(REMOVAL_FOLDER, fn);
            Removal removal = Utils.readObject(removalFile, Removal.class);
            if (Objects.equals(removal.getFileName(), filename)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTracked(String filename) {
        Commit commitHead = getHeadCommit();
        HashMap<String, String> filesTracked = commitHead.getFilesTracked();
        return filesTracked != null && filesTracked.get(filename) != null;
    }

    public static Pointers getPointers() {
        File pointersFile = Utils.join(COMMIT_FOLDER, "pointers");
        return Utils.readObject(pointersFile, Pointers.class);
    }

    private static Commit getHeadCommit() {
        Pointers pointers = getPointers();
        return pointers.getHeadCommit();
    }

    private static void clearAdditionArea() {
        for (String fn : Utils.plainFilenamesIn(ADDITION_FOLDER)) {
            Utils.join(ADDITION_FOLDER, fn).delete();
        }
    }

    private static void clearRemovalArea() {
        for (String fn : Utils.plainFilenamesIn(REMOVAL_FOLDER)) {
            Utils.join(REMOVAL_FOLDER, fn).delete();
        }
    }

    private static void untrackedFileCheck(Commit branch) {
        for (Map.Entry<String, String> entry
                : branch.getFilesTracked().entrySet()) {
            String trackedFileName = entry.getKey();
            if (Utils.join(CWD, trackedFileName).exists()
                && !isTracked(trackedFileName)) {
                exitWithError("There is an untracked file in the way; "
                        + "delete it, or add and commit it first.");
            }
        }
    }

    public static Remotes getRemotes() {
        File remotesFile = Utils.join(GITLET_FOLDER, "remotes");
        return Utils.readObject(remotesFile, Remotes.class);
    }

    private static void exitWithError(String message) {
        if (message != null && !message.equals("")) {
            System.out.println(message);
        }
        System.exit(0);
    }

    private static void validateNumArgs(String[] args, int n) {
        if (args.length != n) {
            System.out.println("Incorrect operands.");
            System.exit(0);
        }
    }

    private static void validateGitletDir() {
        if (!GITLET_FOLDER.exists()) {
            System.out.println("Not in an initialized Gitlet directory.");
            System.exit(0);
        }
    }
}
