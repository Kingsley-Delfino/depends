package depends.utils;

import java.util.List;

public class FileUtil {
    public static boolean isFiltered(String filePath, String[] suffixes) {
        for (String suffix : suffixes) {
            if (filePath.endsWith(suffix)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isJavaTestFilter(String filePathName) {
        if (filePathName == null) {
            return true;
        }
        String path = filePathName.toLowerCase();
        path = path.replace("\\", "/");
        if (!path.endsWith(".java")) {
            return true;
        } else if (path.contains("test/")) {
            return true;
        } else {
            return path.endsWith("test.java") || path.endsWith("tests.java");
        }
    }

    public static boolean isFileByFileSuffix(String filePath) {
        boolean result = false;
        for (String fileSuffix : Constant.FILE_SUFFIX) {
            if (filePath.endsWith(fileSuffix)) {
                result = true;
                break;
            }
        }
        return result;
    }

    public static String calculateFilePathFromSnapshot(String snapshotFilePath, String projectPath, boolean isCurrent) {
        StringBuilder sb = new StringBuilder();
        String[] stringArray;
        if (isCurrent) {
            stringArray = snapshotFilePath.split("/curr/");
        } else {
            stringArray = snapshotFilePath.split("/prev/");
        }
        stringArray = stringArray[1].split("/");
        sb.append(projectPath);
        for (int i = 1; i < stringArray.length; i++) {
            sb.append("/");
            sb.append(stringArray[i]);
        }
        return sb.toString();
    }

    public static String getProjectPath(String filePath) {
        String[] array = filePath.split("/curr/");
        return array[0] + "/curr";
    }

    public static void getTestPath(List<String> testPathList, String[] pathArray) {
        for (String path : pathArray) {
            if (path.endsWith("/test")) {
                testPathList.add(path);
                break;
            }
        }
    }
}
