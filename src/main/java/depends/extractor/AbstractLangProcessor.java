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

import java.io.File;
import java.io.IOException;
import java.util.*;

import depends.entity.PackageEntity;
import depends.extractor.git.CommitExtractor;
import depends.extractor.git.GitExtractor;
import depends.relations.Relation;
import multilang.depends.util.file.FolderCollector;
import org.codehaus.plexus.util.FileUtils;
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
    protected String inputSrcPath;
    public String[] includeDirs;
    private Set<UnsolvedBindings> potentialExternalDependencies;
    private List<String> includePaths;
    public List<String> excludePaths;

    public AbstractLangProcessor(boolean eagerExpressionResolve) {
        entityRepo = new InMemoryEntityRepo();
        inferer = new Inferer(entityRepo, getImportLookupStrategy(), getBuiltInType(), eagerExpressionResolve);
    }

    /**
     * The process steps of build dependencies.
     * Step 1: parse all files, add entities and expression into repositories;
     * Step 2: resolve bindings of files (if not resolved yet);
     * Step 3: identify dependencies.
     */
    public void buildDependencies(String inputDir, String[] includeDir, boolean isCollectUnsolvedBindings, boolean isDuckTypingDeduce, List<String> excludePaths) {
        this.projectPath = inputDir;
        this.inputSrcPath = inputDir;
        this.includeDirs = includeDir;
        this.excludePaths = excludePaths;
        this.inferer.setCollectUnsolvedBindings(isCollectUnsolvedBindings);
        this.inferer.setDuckTypingDeduce(isDuckTypingDeduce);
        String since = "2022-04-04 00:00:00";
        String until = "2022-07-05 23:59:59";
        GitExtractor gitExtractor = new GitExtractor("/Users/kingsley/FDSE/multi-dependency/CodeSource/Java/commons-io");
        List<RevCommit> commits = gitExtractor.getRangeCommits(since, until, false);
        if (!commits.isEmpty()) {
            gitExtractor.checkOutToCommit(commits.get(commits.size() - 1), this.projectPath);
        }
        buildDependenciesForInitialVersion();
        buildDependenciesForIncrementalVersion(gitExtractor, commits);
    }

    public void buildDependenciesForInitialVersion() {
        // for this search (isAutoInclude = true)
        buildIncludeDirection(true);
        parseAllFiles();
        markAllEntitiesScope();
        // for java (callAsImpl = false)
        resolveBindings(false, this.entityRepo.getFileEntities());
        System.gc();
        System.runFinalization();
    }

    public void buildDependenciesForIncrementalVersion(GitExtractor gitExtractor, List<RevCommit> commits) {
        CommitExtractor commitExtractor = new CommitExtractor(gitExtractor);
        for (int i = commits.size() - 2; i >= 0; i--) {
            RevCommit commit = commits.get(i);
            System.out.println("\nCommit: " + commit.getName());
            List<String> currentFilePathList = new ArrayList<>();
            List<String> currentSnapshotFilePathList = new ArrayList<>();
            List<String> previousFilePathList = new ArrayList<>();
            List<Entity> currentFileEntityList = new ArrayList<>();
            List<Entity> previousFileEntityList = new ArrayList<>();
            // those file entities that depend on previous file entities, we need to identify dependencies of them to current file entities
            List<Entity> dependsOnPreviousFileEntityList = new ArrayList<>();
            commitExtractor.getChangedFilePath(commit, currentFilePathList, currentSnapshotFilePathList, previousFilePathList, this.projectPath);
            if (!previousFilePathList.isEmpty()) {
                // find previous file entities
                findFileEntityByFileNameFromEntityRepository(previousFilePathList, previousFileEntityList);
                // find entities that depend on previous file entities
                findDependsOnPreviousFileEntity(previousFileEntityList, dependsOnPreviousFileEntityList);
                // remove previous file entities and their children from entity repository
                for (Entity entity : previousFileEntityList) {
                    removePreviousEntity(entity);
                }
            }
            if (!currentFilePathList.isEmpty()) {
                this.inputSrcPath = getProjectPath(currentSnapshotFilePathList.get(0));
                this.includeDirs = new String[0];
                buildIncludeDirection(true);
                this.excludePaths = new ArrayList<>();
                getTestPath(this.excludePaths, this.includeDirs);
                parseAllFiles();
                // find current file entities
                findFileEntityByFileNameFromEntityRepository(currentSnapshotFilePathList, currentFileEntityList);
                // change current file path to previous path
                updateNewFileEntityPath(currentFileEntityList);
                // delete current packages and add current file entities to previous packages
                for (Entity entity : currentFileEntityList) {
                    removeCurrentParentEntity(entity);
                }
            }
            // identify dependencies again
            Collection<Entity> entityCollection = new ArrayList<>();
            entityCollection.addAll(dependsOnPreviousFileEntityList);
            entityCollection.addAll(currentFileEntityList);
            if (!entityCollection.isEmpty()) {
                // reset the scope is true so that could identify its dependencies again
                for (Entity entity : entityCollection) {
                    entity.setInScope(true);
                }
                // identify
                resolveBindings(false, entityCollection);
            }
            System.gc();
            System.runFinalization();
        }
    }

    private void markAllEntitiesScope() {
        this.entityRepo.getFileEntities().forEach(entity -> {
            Entity file = entity.getAncestorOfType(FileEntity.class);
            try {
                if (!file.getQualifiedName().startsWith(this.inputSrcPath)) {
                    entity.setInScope(false);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void findFileEntityByFileNameFromEntityRepository(List<String> filePathList, List<Entity> fileEntityList) {
        Collection<Entity> fileEntityCollection = this.entityRepo.getFileEntities();
        for (Entity fileEntity : fileEntityCollection) {
            Entity file = fileEntity.getAncestorOfType(FileEntity.class);
            if (filePathList.contains(file.getQualifiedName())) {
                fileEntityList.add(file);
            }
        }
    }

    private void findDependsOnPreviousFileEntity(List<Entity> previousFileEntityList, List<Entity> dependsOnPreviousFileEntityList) {
        List<Entity> previousEntityList = new ArrayList<>();
        getAllEntity(previousFileEntityList, previousEntityList);
        Collection<Entity> fileEntityCollection = this.entityRepo.getFileEntities();
        for (Entity fileEntity : fileEntityCollection) {
            Entity file = fileEntity.getAncestorOfType(FileEntity.class);
            if (isDependsOnPreviousEntity(file, previousEntityList)) {
                dependsOnPreviousFileEntityList.add(file);
            }
        }
    }

    private void getAllEntity(Collection<Entity> entityList, List<Entity> allEntityList) {
        for (Entity entity : entityList) {
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

    private void updateNewFileEntityPath(List<Entity> fileEntityList) {
        for (Entity fileEntity : fileEntityList) {
            String newFilePath = calculateFilePathFromSnapshot(fileEntity.getQualifiedName(), this.projectPath, true);
            this.entityRepo.updateEntityPath(fileEntity, newFilePath);
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

    public void resolveBindings(boolean callAsImpl, Collection<Entity> entityCollection) {
        this.potentialExternalDependencies = inferer.resolveAllBindings(callAsImpl, entityCollection, this);
        if (getExternalDependencies().size() > 0) {
            System.out.println("There are " + getExternalDependencies().size() + " items are potential external dependencies.");
        }
    }

    private void parseAllFiles() {
        System.out.println("Start parsing files...");
        FileTraversal fileTransversal = new FileTraversal(file -> {
            String fileFullPath = file.getAbsolutePath();
            fileFullPath = FileUtil.uniqFilePath(fileFullPath);
            if (fileFullPath.startsWith(this.inputSrcPath)) {
                parseFile(fileFullPath);
            }
        });
        fileTransversal.extensionFilter(this.fileSuffixes());
        fileTransversal.setExcludePaths(this.excludePaths);
        fileTransversal.travers(this.inputSrcPath);
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
        if (this.includePaths == null) {
            this.includePaths = buildIncludePath();
        }
        return includePaths;
    }

    private List<String> buildIncludePath() {
        this.includePaths = new ArrayList<>();
        for (String path : this.includeDirs) {
            if (FileUtils.fileExists(path)) {
                path = FileUtil.uniqFilePath(path);
                if (!this.includePaths.contains(path))
                    this.includePaths.add(path);
            }
            path = this.inputSrcPath + File.separator + path;
            if (FileUtils.fileExists(path)) {
                path = FileUtil.uniqFilePath(path);
                if (!this.includePaths.contains(path))
                    this.includePaths.add(path);
            }
        }
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
            List<String> additionalIncludePaths = includePathCollector.getFolders(this.inputSrcPath);
            additionalIncludePaths.addAll(Arrays.asList(this.includeDirs));
            this.includeDirs = additionalIncludePaths.toArray(new String[]{});
        }
    }
}
