package depends.extractor.git;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import com.google.common.collect.Lists;
import depends.utils.Constant;
import depends.utils.FileUtil;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffAlgorithm;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.AndRevFilter;
import org.eclipse.jgit.revwalk.filter.CommitTimeRevFilter;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;

public class GitExtractor {

    private Repository repository;

    private Git git;

    public GitExtractor(String gitProjectPath) {
        try {
            repository = FileRepositoryBuilder.create(new File(gitProjectPath, ".git"));
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        git = new Git(repository);
    }

    public List<RevCommit> getAllCommits() {
        try {
            Iterable<RevCommit> commits = git.log().setRevFilter(RevFilter.NO_MERGES).call();
            return Lists.newArrayList(commits.iterator());
        } catch (GitAPIException e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    public List<RevCommit> getRangeCommits(String since, String until, boolean removeMerge) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(Constant.TIMESTAMP);
        try {
            Date sinceDate = simpleDateFormat.parse(since);
            Date untilDate = simpleDateFormat.parse(until);
            RevFilter between = CommitTimeRevFilter.between(sinceDate, untilDate);
            RevFilter filter = removeMerge ? AndRevFilter.create(between, RevFilter.NO_MERGES) : between;
            Iterable<RevCommit> commits = git.log().setRevFilter(filter).call();
            return Lists.newArrayList(commits.iterator());
        } catch (ParseException | GitAPIException e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    public boolean deleteDir(File directory) {
        if (directory.isDirectory()) {
            String[] childrenDirectory = directory.list();
            if (childrenDirectory != null) {
                for (String childDirectory : childrenDirectory) {
                    boolean isSuccess = deleteDir(new File(directory, childDirectory));
                    if (!isSuccess) {
                        return false;
                    }
                }
            }
        }
        return directory.delete();
    }

    public Map<String, FileHeader> getFileHeaderBetweenCommits(RevCommit revCommit, RevCommit parentRevCommit) {
        AbstractTreeIterator currentTreeParser = prepareTreeParser(revCommit.getName());
        AbstractTreeIterator prevTreeParser = prepareTreeParser(parentRevCommit.getName());
        OutputStream outputStream = DisabledOutputStream.INSTANCE;
        DiffFormatter formatter = null;
        List<DiffEntry> diffs = new ArrayList<>();
        try {
            formatter = new DiffFormatter(outputStream);
            formatter.setRepository(git.getRepository());
            formatter.setDetectRenames(true);
            formatter.setDiffComparator(RawTextComparator.WS_IGNORE_ALL);
            formatter.setDiffAlgorithm(DiffAlgorithm.getAlgorithm(DiffAlgorithm.SupportedAlgorithm.HISTOGRAM));
            diffs = formatter.scan(prevTreeParser, currentTreeParser);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Map<String, FileHeader> result = new HashMap<>();
        for (DiffEntry diff : diffs) {
            String newPath = diff.getNewPath();
            String oldPath = diff.getOldPath();
            String changeType = diff.getChangeType().name();
            String currentPath = DiffEntry.ChangeType.DELETE.name().equals(changeType) ? oldPath : newPath;
            if (FileUtil.isFiltered(currentPath, Constant.FILE_SUFFIX)) {
                continue;
            }
            //不考虑测试相关文件
            if (FileUtil.isJavaTestFilter(currentPath)) {
                continue;
            }
            try {
                FileHeader fileHeader = formatter.toFileHeader(diff);
                result.put(currentPath, fileHeader);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    private CanonicalTreeParser prepareTreeParser(String objectId) {
        CanonicalTreeParser treeParser = new CanonicalTreeParser();
        try (RevWalk revWalk = new RevWalk(repository)) {
            RevCommit commit = revWalk.parseCommit(ObjectId.fromString(objectId));
            RevTree tree = revWalk.parseTree(commit.getTree().getId());
            ObjectReader oldReader = repository.newObjectReader();
            treeParser.reset(oldReader, tree.getId());
            revWalk.dispose();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return treeParser;
    }

    public String getGitPath() {
        return repository.getDirectory().getAbsolutePath();
    }

    public void checkOutToCommit(RevCommit revCommit, String projectPath) {
        try {
            deleteDir(new File(projectPath));
            RevTree revTree = revCommit.getTree();
            TreeWalk treeWalk = new TreeWalk(this.repository);
            treeWalk.addTree(revTree);
            treeWalk.setRecursive(true);
            while (treeWalk.next()) {
                String filePath = treeWalk.getPathString();
                if (!FileUtil.isFileByFileSuffix(filePath) || FileUtil.isJavaTestFilter(filePath)) {
                    continue;
                }
                ObjectId objectId = treeWalk.getObjectId(0);
                ObjectLoader objectLoader = this.repository.open(objectId);
                String content = new String(objectLoader.getBytes(), StandardCharsets.UTF_8);
                saveFileContent(content, filePath, projectPath);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveFileContent(String content, String filePath, String projectPath) {
        String fileDirectory = projectPath + "/";
        String[] filePathString = filePath.split("/");
        String fileName = filePathString[filePathString.length - 1];
        String filePathWithoutName = filePath.substring(0, filePath.length() - fileName.length() - 1);
        File file = new File(fileDirectory + filePathWithoutName);
        if (!file.exists()) {
            boolean isCreateSuccess = file.mkdirs();
            if (!isCreateSuccess) {
                return;
            }
        }
        try {
            BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(fileDirectory + filePath));
            bufferedWriter.write(content);
            bufferedWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
