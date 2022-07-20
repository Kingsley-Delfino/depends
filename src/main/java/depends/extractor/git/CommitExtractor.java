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
    public void getChangedFilePath(RevCommit revCommit, List<String> currentFilePathList, List<String> currentSnapshotFilePathList, List<String> previousFilePathList, String projectPath) {
        RevCommit[] parentRevCommits = revCommit.getParents();
        if (parentRevCommits != null && parentRevCommits.length == 1) {
            Map<String, FileHeader> diffs = gitExtractor.getFileHeaderBetweenCommits(revCommit, parentRevCommits[0]);
            if (diffs.size() > 0) {
                Map<String, JSONArray> diffData = getDiffData(revCommit);
                for (Map.Entry<String, JSONArray> entry : diffData.entrySet()) {
                    JSONArray jsonArray = entry.getValue();
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject jsonObject = jsonArray.getJSONObject(i);
                        if (jsonObject.has("curPath")) {
                            String currentFilePath = jsonObject.getString("curPath");
                            if (!currentFilePath.equals("null") && FileUtil.isFileByFileSuffix(currentFilePath) && !FileUtil.isJavaTestFilter(currentFilePath)) {
                                currentSnapshotFilePathList.add(currentFilePath);
                                currentFilePathList.add(FileUtil.calculateFilePathFromSnapshot(currentFilePath, projectPath, true));
                            }
                        }
                        if (jsonObject.has("prevPath")) {
                            String previousFilePath = jsonObject.getString("prevPath");
                            if (!previousFilePath.equals("null") && FileUtil.isFileByFileSuffix(previousFilePath) && !FileUtil.isJavaTestFilter(previousFilePath)) {
                                previousFilePathList.add(FileUtil.calculateFilePathFromSnapshot(previousFilePath, projectPath, false));
                            }
                        }
                    }
                }
            }
        }
    }

    public Map<String, JSONArray> getDiffData(RevCommit revCommit) {
        CLDiffLocal CLDiffLocal = new CLDiffLocal();
        String outputDir = "/Users/kingsley/FDSE/GraduationProject/CLDiff/Java";
        return CLDiffLocal.getDiff(revCommit.getName(), gitExtractor.getGitPath(), outputDir);
    }
}
