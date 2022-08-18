/*
MIT License

Copyright (c) 2018-2019 Gang ZHANG

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

package depends.extractor;

import java.io.IOException;
import java.util.*;

import depends.entity.PackageEntity;
import depends.extractor.git.CommitExtractor;
import depends.extractor.git.GitExtractor;
import depends.relations.Relation;
import multilang.depends.util.file.FolderCollector;
import org.eclipse.jgit.revwalk.RevCommit;

import depends.entity.Entity;
import depends.entity.FileEntity;
import depends.entity.repo.BuiltInType;
import depends.entity.repo.EntityRepo;
import depends.entity.repo.InMemoryEntityRepo;
import depends.matrix.core.DependencyMatrix;
import depends.relations.ImportLookupStrategy;
import depends.relations.Inferer;
import multilang.depends.util.file.FileTraversal;
import multilang.depends.util.file.FileUtil;

import static depends.utils.FileUtil.*;

abstract public class AbstractLangProcessor {
    /**
     * The name of the lang
     */
    public abstract String supportedLanguage();

    /**
     * The file suffixes in the lang
     */
    public abstract String[] fileSuffixes();

    /**
     * Strategy of how to lookup types and entities in the lang.
     */
    public abstract ImportLookupStrategy getImportLookupStrategy();

    /**
     * The builtInType of the lang.
     */
    public abstract BuiltInType getBuiltInType();

    /**
     * The language specific file parser
     */
    protected abstract FileParser createFileParser(String fileFullPath);

    public Inferer inferer;
    protected EntityRepo entityRepo;
    DependencyMatrix dependencyMatrix;
    private String projectPath;
    protected String snapshotProjectPath;
    public List<String> includePaths;
    public List<String> excludePaths;
    private Set<UnsolvedBindings> potentialExternalDependencies;
    private boolean isCallAsImpl;

    public AbstractLangProcessor(boolean eagerExpressionResolve) {
        entityRepo = new InMemoryEntityRepo();
        inferer = new Inferer(entityRepo, getImportLookupStrategy(), getBuiltInType(), eagerExpressionResolve);
    }

    public void initial(String inputDir, List<String> includePaths, List<String> excludePaths, boolean isCallAsImpl, boolean isCollectUnsolvedBindings, boolean isDuckTypingDeduce) {
        this.projectPath = inputDir;
        this.snapshotProjectPath = inputDir;
        this.includePaths = includePaths;
        this.excludePaths = excludePaths;
        this.isCallAsImpl = isCallAsImpl;
        this.inferer.setCollectUnsolvedBindings(isCollectUnsolvedBindings);
        this.inferer.setDuckTypingDeduce(isDuckTypingDeduce);
    }

    /**
     * The process steps of build dependencies.
     * Step 1: parse all files, add entities and expression into repositories;
     * Step 2: resolve bindings of files (if not resolved yet);
     * Step 3: identify dependencies.
     */
    public void buildDependencies() {
        String since = "2022-04-04 00:00:00";
        String until = "2022-07-05 23:59:59";
        GitExtractor gitExtractor = new GitExtractor("/Users/kingsley/FDSE/multi-dependency/CodeSource/Java/commons-io");
        String CLDiffOutputPath = "/Users/kingsley/FDSE/GraduationProject/CLDiff/Java";
        List<RevCommit> commits = gitExtractor.getRangeCommits(since, until, false);
        if (!commits.isEmpty()) {
            gitExtractor.checkOutToCommit(commits.get(commits.size() - 1), this.projectPath);
        }
        buildDependenciesForInitialVersion();
        buildDependenciesForIncrementalVersion(gitExtractor, commits, CLDiffOutputPath);
    }

    public EntityRepo buildDependenciesForInitialVersion() {
        // for this search (isAutoInclude = true)
        buildIncludeDirection(true);
        parseAllFiles();
        markAllEntitiesScope();
        resolveBindings(this.entityRepo.getFileEntities());
        System.gc();
        System.runFinalization();
        return this.entityRepo;
    }

    public void buildDependenciesForIncrementalVersion(GitExtractor gitExtractor, List<RevCommit> commits, String CLDiffOutputPath) {
        CommitExtractor commitExtractor = new CommitExtractor(gitExtractor);
        for (int i = commits.size() - 2; i >= 0; i--) {
            RevCommit commit = commits.get(i);
            Map<String, Entity> previousFileEntityMap = new HashMap<>();
            Map<String, Entity> currentFileEntityMap = new HashMap<>();
            List<String> removedFilePathList = new ArrayList<>();
            List<String> addedFilePathList = new ArrayList<>();
            List<String> modifiedFilePathList = new ArrayList<>();
            // key-value: previousFilePath-currentFilePath
            Map<String, String> renamedFilePathMap = new HashMap<>();
            buildDependenciesForIncrementalVersion(commitExtractor, commit, CLDiffOutputPath, previousFileEntityMap, currentFileEntityMap, removedFilePathList, addedFilePathList, modifiedFilePathList, renamedFilePathMap);
        }
    }

    public void buildDependenciesForIncrementalVersion(CommitExtractor commitExtractor, RevCommit commit, String CLDiffOutputPath, Map<String, Entity> previousFileEntityMap, Map<String, Entity> currentFileEntityMap, List<String> removedFilePathList, List<String> addedFilePathList, List<String> modifiedFilePathList, Map<String, String> renamedFilePathMap) {
        System.out.println("\nCommit: " + commit.getName());
        List<String> previousFilePathList = new ArrayList<>();
        List<String> currentFilePathList = new ArrayList<>();
        List<String> currentSnapshotFilePathList = new ArrayList<>();
        // 对于那些依赖于发生了修改的文件的实体，需要对其进行重新扫描，让其依赖于新的文件
        List<Entity> dependsOnPreviousFileEntityList = new ArrayList<>();
        commitExtractor.getChangedFilePath(commit, this.projectPath, CLDiffOutputPath, currentFilePathList, currentSnapshotFilePathList, previousFilePathList, removedFilePathList, addedFilePathList, modifiedFilePathList, renamedFilePathMap);
        if (!previousFilePathList.isEmpty()) {
            // 寻找修改之前的文件实体
            findFileEntityByFileNameFromEntityRepository(previousFilePathList, previousFileEntityMap);
            // 寻找依赖于修改的文件的实体（包括子实体）
            findDependsOnPreviousFileEntity(previousFileEntityMap.values(), dependsOnPreviousFileEntityList);
            // 将修改的文件实体和其子实体从总的数据库中移除
            for (Entity entity : previousFileEntityMap.values()) {
                removePreviousEntity(entity);
            }
        }
        if (!currentFilePathList.isEmpty()) {
            this.snapshotProjectPath = getProjectPath(currentSnapshotFilePathList.get(0));
            this.includePaths.clear();
            buildIncludeDirection(true);
            this.excludePaths = new ArrayList<>();
            getTestPath(this.excludePaths, this.includePaths);
            parseAllFiles();
            // 寻找修改之后的文件实体
            findFileEntityByFileNameFromEntityRepository(currentSnapshotFilePathList, currentFileEntityMap);
            // 修改新文件实体的路径
            updateNewFileEntityPath(currentFileEntityMap);
            // 若新文件所在包在此之前就已经存在，则删除新建的包，并将新文件加到旧包中
            for (Entity entity : currentFileEntityMap.values()) {
                removeCurrentParentEntity(entity);
            }
        }
        // 重新确定依赖，包括修改的文件和依赖修改的文件的文件
        Collection<Entity> entityCollection = new ArrayList<>();
        entityCollection.addAll(dependsOnPreviousFileEntityList);
        entityCollection.addAll(currentFileEntityMap.values());
        if (!entityCollection.isEmpty()) {
            // 重新设置scope为true，以便可以再次确定依赖
            for (Entity entity : entityCollection) {
                entity.setInScope(true);
            }
            // 确定依赖
            resolveBindings(entityCollection);
        }
        System.gc();
        System.runFinalization();
    }

    private void markAllEntitiesScope() {
        this.entityRepo.getFileEntities().forEach(entity -> {
            Entity file = entity.getAncestorOfType(FileEntity.class);
            try {
                if (!file.getQualifiedName().startsWith(this.snapshotProjectPath)) {
                    entity.setInScope(false);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void findFileEntityByFileNameFromEntityRepository(List<String> filePathList, Map<String, Entity> fileEntityMap) {
        Collection<Entity> fileEntityCollection = this.entityRepo.getFileEntities();
        for (Entity fileEntity : fileEntityCollection) {
            Entity file = fileEntity.getAncestorOfType(FileEntity.class);
            if (filePathList.contains(file.getQualifiedName())) {
                fileEntityMap.put(file.getQualifiedName(), file);
            }
        }
    }

    private void findDependsOnPreviousFileEntity(Collection<Entity> previousFileEntityCollection, List<Entity> dependsOnPreviousFileEntityList) {
        List<Entity> previousEntityList = new ArrayList<>();
        getAllEntity(previousFileEntityCollection, previousEntityList);
        Collection<Entity> fileEntityCollection = this.entityRepo.getFileEntities();
        for (Entity fileEntity : fileEntityCollection) {
            Entity file = fileEntity.getAncestorOfType(FileEntity.class);
            if (isDependsOnPreviousEntity(file, previousEntityList)) {
                dependsOnPreviousFileEntityList.add(file);
            }
        }
    }

    private void getAllEntity(Collection<Entity> entityCollection, List<Entity> allEntityList) {
        for (Entity entity : entityCollection) {
            allEntityList.add(entity);
            getAllEntity(entity.getChildren(), allEntityList);
        }
    }

    private boolean isDependsOnPreviousEntity(Entity entity, List<Entity> previousEntityList) {
        if (previousEntityList.contains(entity)) {
            return false;
        }
        for (Relation relation : entity.getRelations()) {
            if (previousEntityList.contains(relation.getEntity())) {
                return true;
            }
        }
        for (Entity childEntity : entity.getChildren()) {
            if (isDependsOnPreviousEntity(childEntity, previousEntityList)) {
                return true;
            }
        }
        return false;
    }

    private void updateNewFileEntityPath(Map<String, Entity> currentFileEntityMap) {
        List<Entity> fileEntityList = new ArrayList<>(currentFileEntityMap.values());
        currentFileEntityMap.clear();
        for (Entity fileEntity : fileEntityList) {
            String newFilePath = calculateFilePathFromSnapshot(fileEntity.getQualifiedName(), this.projectPath, true);
            this.entityRepo.updateEntityPath(fileEntity, newFilePath);
            currentFileEntityMap.put(newFilePath, fileEntity);
        }
    }

    private void removePreviousEntity(Entity entity) {
        if (entity instanceof FileEntity) {
            Entity parentEntity = entity.getParent();
            if (parentEntity instanceof PackageEntity) {
                parentEntity.getChildren().remove(entity);
                parentEntity.removeVisible(entity.getQualifiedName());
            }
        }
        this.entityRepo.removeEntity(entity);
        for (Entity childEntity : entity.getChildren()) {
            removePreviousEntity(childEntity);
        }
    }

    private void removeCurrentParentEntity(Entity entity) {
        if (entity instanceof FileEntity) {
            Entity parentEntity = entity.getParent();
            if (parentEntity instanceof PackageEntity) {
                Collection<Entity> entityCollection = this.entityRepo.getAllEntities();
                for (Entity e : entityCollection) {
                    if (e instanceof PackageEntity && e.getQualifiedName().equals(parentEntity.getQualifiedName()) && !e.getId().equals(parentEntity.getId())) {
                        e.addChild(entity);
                        entity.setParent(e);
                        this.entityRepo.putEntityByName(e, e.getQualifiedName());
                        break;
                    }
                }
            }
        }
    }

    public void resolveBindings(Collection<Entity> entityCollection) {
        this.potentialExternalDependencies = inferer.resolveAllBindings(this.isCallAsImpl, entityCollection, this);
        if (getExternalDependencies().size() > 0) {
            System.out.println("There are " + getExternalDependencies().size() + " items are potential external dependencies.");
        }
    }

    private void parseAllFiles() {
        System.out.println("Start parsing files...");
        FileTraversal fileTransversal = new FileTraversal(file -> {
            String fileFullPath = file.getAbsolutePath();
            fileFullPath = FileUtil.uniqFilePath(fileFullPath);
            if (fileFullPath.startsWith(this.snapshotProjectPath)) {
                parseFile(fileFullPath);
            }
        });
        fileTransversal.extensionFilter(this.fileSuffixes());
        fileTransversal.setExcludePaths(this.excludePaths);
        fileTransversal.travers(this.snapshotProjectPath);
        System.out.println("All files parsed successfully...");
    }

    protected void parseFile(String fileFullPath) {
        FileParser fileParser = createFileParser(fileFullPath);
        try {
            System.out.println("Parsing " + fileFullPath + "...");
            fileParser.parse();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("Error occoured during parse file " + fileFullPath);
            e.printStackTrace();
        }
    }

    public List<String> includePaths() {
        return this.includePaths;
    }

    public DependencyMatrix getDependencies() {
        return this.dependencyMatrix;
    }

    public EntityRepo getEntityRepo() {
        return this.entityRepo;
    }

    public abstract List<String> supportedRelations();

    public Set<UnsolvedBindings> getExternalDependencies() {
        return this.potentialExternalDependencies;
    }

    public String getRelationMapping(String relation) {
        return relation;
    }

    public void buildIncludeDirection(boolean isAutoInclude) {
        if (isAutoInclude) {
            FolderCollector includePathCollector = new FolderCollector();
            this.includePaths = includePathCollector.getFolders(this.snapshotProjectPath);
        }
    }
}
