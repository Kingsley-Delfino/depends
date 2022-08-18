package depends.extractor.git;

import depends.utils.FileUtil;
import edu.fdu.se.cldiff.CLDiffLocal;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;

public class CommitExtractor {

    public GitExtractor gitExtractor;

    public CommitExtractor(GitExtractor gitExtractor) {
        this.gitExtractor = gitExtractor;
    }

    /**
     * 修改类型：modified、removed、added、renamed
     *
     * @param revCommit                   当前commit
     * @param currentFilePathList         修改之后的文件路径集合
     * @param currentSnapshotFilePathList 修改之后的文件在临时文件夹中的路径集合
     * @param previousFilePathList        修改之前的文件路径集合
     * @param projectPath                 原有项目路径
     */
    public void getChangedFilePath(RevCommit revCommit, String projectPath, String CLDiffOutputPath, List<String> currentFilePathList, List<String> currentSnapshotFilePathList, List<String> previousFilePathList, List<String> removedFilePathList, List<String> addedFilePathList, List<String> modifiedFilePathList, Map<String, String> renamedFilePathMap) {
        RevCommit[] parentRevCommits = revCommit.getParents();
        if (parentRevCommits != null && parentRevCommits.length == 1) {
            Map<String, FileHeader> diffs = gitExtractor.getFileHeaderBetweenCommits(revCommit, parentRevCommits[0]);
            if (diffs.size() > 0) {
                Map<String, JSONArray> diffData = getDiffData(revCommit, CLDiffOutputPath);
                for (Map.Entry<String, JSONArray> entry : diffData.entrySet()) {
                    JSONArray jsonArray = entry.getValue();
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject jsonObject = jsonArray.getJSONObject(i);
                        if (jsonObject.has("action")) {
                            String previousFilePath = jsonObject.getString("prevPath");
                            String currentFilePath = jsonObject.getString("curPath");
                            if (!previousFilePath.equals("null") && FileUtil.isFileByFileSuffix(previousFilePath) && !FileUtil.isJavaTestFilter(previousFilePath)) {
                                previousFilePath = FileUtil.calculateFilePathFromSnapshot(previousFilePath, projectPath, false);
                                previousFilePathList.add(previousFilePath);
                            }
                            if (!currentFilePath.equals("null") && FileUtil.isFileByFileSuffix(currentFilePath) && !FileUtil.isJavaTestFilter(currentFilePath)) {
                                currentSnapshotFilePathList.add(currentFilePath);
                                currentFilePath = FileUtil.calculateFilePathFromSnapshot(currentFilePath, projectPath, true);
                                currentFilePathList.add(currentFilePath);
                            }
                            String action = jsonObject.getString("action");
                            switch (action) {
                                case "removed":
                                    removedFilePathList.add(previousFilePath);
                                    break;
                                case "added":
                                    addedFilePathList.add(currentFilePath);
                                    break;
                                case "modified":
                                    // currentFilePath == previousFilePath
                                    modifiedFilePathList.add(currentFilePath);
                                    break;
                                case "renamed":
                                    renamedFilePathMap.put(previousFilePath, currentFilePath);
                                    break;
                            }
                        }
                    }
                }
            }
        }
    }

    public Map<String, JSONArray> getDiffData(RevCommit revCommit, String CLDiffOutputPath) {
        CLDiffLocal CLDiffLocal = new CLDiffLocal();
        return CLDiffLocal.getDiff(revCommit.getName(), gitExtractor.getGitPath(), CLDiffOutputPath);
    }
}
