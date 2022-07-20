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

package depends.relations;

import depends.entity.*;
import depends.entity.repo.BuiltInType;
import depends.entity.repo.EntityRepo;
import depends.extractor.AbstractLangProcessor;
import depends.extractor.UnsolvedBindings;
import depends.extractor.java.JavaImportLookupStrategy;
import depends.extractor.java.JavaProcessor;
import depends.importtypes.Import;

import java.util.*;

public class Inferer {
    static final public TypeEntity buildInType = new TypeEntity(GenericName.build("built-in"), null, -1);
    static final public TypeEntity genericParameterType = new TypeEntity(GenericName.build("T"), null, -3);
    private final BuiltInType buildInTypeManager;
    private final ImportLookupStrategy importLookupStrategy;
    private final Set<UnsolvedBindings> unsolvedSymbols;
    private final EntityRepo repo;

    private final boolean eagerExpressionResolve;
    private boolean isCollectUnsolvedBindings = false;
    private boolean isDuckTypingDeduce = true;

    public Inferer(EntityRepo repo, ImportLookupStrategy importLookupStrategy, BuiltInType buildInTypeManager, boolean eagerExpressionResolve) {
        this.repo = repo;
        this.importLookupStrategy = importLookupStrategy;
        this.buildInTypeManager = buildInTypeManager;
        unsolvedSymbols = new HashSet<>();
        this.eagerExpressionResolve = eagerExpressionResolve;
    }

    /**
     * Resolve all bindings
     * - Firstly, we resolve all types from there names.
     * - Secondly, we resolve all expressions (expression will use type infomation of previous step
     */
    public Set<UnsolvedBindings> resolveAllBindings(boolean callAsImpl, Collection<Entity> entityCollection, AbstractLangProcessor langProcessor) {
        resolveTypes(entityCollection);
        if (langProcessor instanceof JavaProcessor) {
            new MyRelationCounter(entityCollection, this, repo, callAsImpl, langProcessor).computeRelations();
        } else {
            new RelationCounter(entityCollection, this, repo, callAsImpl, langProcessor).computeRelations();
        }
        System.out.println("Dependency done...");
        return unsolvedSymbols;
    }

    public Set<UnsolvedBindings> resolveAllBindings() {
        return resolveAllBindings(false, new ArrayList<>(), null);
    }

    private void resolveTypes(Collection<Entity> entityCollection) {
        int index = 0;
        int allFilesNum = entityCollection.size();
        for (Entity entity : entityCollection) {
            entity.inferEntities(this);
            index++;
            try {
                System.out.print("\rNumber Of Type-resolved files:[" + index + "/" + allFilesNum + "]");
                Thread.sleep(20);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        System.out.println();
    }

    /**
     * For types start with the prefix, it will be treated as built-in type
     * For example, java.io.* in Java, or __ in C/C++
     */
    public boolean isBuiltInTypePrefix(String prefix) {
        return buildInTypeManager.isBuiltInTypePrefix(prefix);
    }

    /**
     * Different languages have different strategy on how to compute the imported types
     * and the imported files.
     * For example, in C/C++, both imported types (using namespace, using <type>) and imported files exists.
     * while in java, only 'import class/function, or import wildcard class.* package.* exists.
     */
    public Collection<Entity> getImportedRelationEntities(List<Import> importedNames) {
        return importLookupStrategy.getImportedRelationEntities(importedNames, repo);
    }

    public Collection<Entity> getImportedTypes(List<Import> importedNames, FileEntity fileEntity) {
        HashSet<UnsolvedBindings> unsolved = new HashSet<>();
        Collection<Entity> result;
        if (importLookupStrategy instanceof JavaImportLookupStrategy) {
            result = ((JavaImportLookupStrategy) importLookupStrategy).getImportedTypes(importedNames, repo, unsolved, fileEntity);
        } else {
            result = importLookupStrategy.getImportedTypes(importedNames, repo, unsolved);
        }
        for (UnsolvedBindings item : unsolved) {
            item.setFromEntity(fileEntity);
            addUnsolvedBinding(item);
        }
        return result;
    }

    private void addUnsolvedBinding(UnsolvedBindings item) {
        if (!isCollectUnsolvedBindings) return;
        this.unsolvedSymbols.add(item);
    }

    public Collection<Entity> getImportedFiles(List<Import> importedNames) {
        return importLookupStrategy.getImportedFiles(importedNames, repo);
    }

    /**
     * By given raw name, to infer the type of the name
     * for example
     * (It is just a wrapper of resolve name)
     * if it is a class, the class is the type
     * if it is a function, the return type is the type
     * if it is a variable, type of variable is the type
     */
    public TypeEntity inferTypeFromName(Entity fromEntity, GenericName rawName) {
        Entity data = resolveName(fromEntity, rawName, true);
        if (data == null)
            return null;
        return data.getType();
    }

    /**
     * By given raw name, to infer the entity of the name
     */
    public Entity resolveName(Entity fromEntity, GenericName rawName, boolean searchImport) {
        if (rawName == null) return null;
        Entity entity = resolveNameInternal(fromEntity, rawName, searchImport);
        if (entity == null) {
            if (!this.buildInTypeManager.isBuiltInType(rawName.getName())) {
                addUnsolvedBinding(new UnsolvedBindings(rawName.getName(), fromEntity));
            }
        }
        return entity;
    }

    private Entity resolveNameInternal(Entity fromEntity, GenericName rawName, boolean searchImport) {
        if (rawName == null || rawName.getName() == null)
            return null;
        if (buildInTypeManager.isBuiltInType(rawName.getName())) {
            return buildInType;
        }
        if (buildInTypeManager.isBuiltInTypePrefix(rawName.getName())) {
            return buildInType;
        }
        // qualified name will first try global name directly
        if (rawName.startsWith(".")) {
            rawName = rawName.substring(1);
            if (repo.getEntity(rawName) != null)
                return repo.getEntity(rawName);
        }
        Entity entity;
        int indexCount = 0;
        String name = rawName.getName();
        if (fromEntity == null) return null;
        do {
            entity = lookupEntity(fromEntity, name, searchImport);
            if (entity != null) {
                if (indexCount == 0) {
                    return entity;
                }
                break;
            }
            if (importLookupStrategy.supportGlobalNameLookup()) {
                if (repo.getEntity(name) != null) {
                    entity = repo.getEntity(name);
                    break;
                }
            }

            indexCount++;
            if (name.contains("."))
                name = name.substring(0, name.lastIndexOf('.'));
            else
                break;
        } while (true);
        if (entity == null) {
            return null;
        }
        String[] names = rawName.getName().split("\\.");
        if (names.length == 0)
            return null;
        if (names.length == 1) {
            return entity;
        }

        if (entity instanceof AliasEntity) {
            names[names.length - indexCount - 1] = ((AliasEntity) entity).getOriginName().getName();
            StringBuilder stringBuilder = new StringBuilder();
            for (int i = 0; i < names.length; i++) {
                stringBuilder.append(i == 0 ? names[i] : '.' + names[i]);
            }
            Entity inferEntity = resolveName(fromEntity, GenericName.build(stringBuilder.toString()), true);
            if (inferEntity != null) return inferEntity;
        }
        // then find the subsequent symbols
        return findEntitySince(entity, names, names.length - indexCount);
    }

    private Entity lookupEntity(Entity fromEntity, String name, boolean searchImport) {
        if (name.equals("this") || name.equals("class")) {
            return (TypeEntity) (fromEntity.getAncestorOfType(TypeEntity.class));
        } else if (name.equals("super")) {
            TypeEntity parent = (TypeEntity) (fromEntity.getAncestorOfType(TypeEntity.class));
            if (parent != null) {
                TypeEntity parentType = parent.getInheritedType();
                if (parentType != null)
                    return parentType;
            }
        }
        Entity inferData = findEntityUnderSamePackage(fromEntity, name);
        if (inferData != null) {
            return inferData;
        }
        if (searchImport)
            inferData = lookupTypeInImported((FileEntity) (fromEntity.getAncestorOfType(FileEntity.class)), name);
        return inferData;
    }

    /**
     * To lookup entity in case of a.b.c from a;
     */
    private Entity findEntitySince(Entity precendenceEntity, String[] names, int nameIndex) {
        if (nameIndex >= names.length) {
            return precendenceEntity;
        }
        if (nameIndex < 0) {
            //System.err.println("error");
            return null;
        }
        //If it is not an entity with types (not a type, var, function), fall back to itself
        if (precendenceEntity.getType() == null)
            return precendenceEntity;

        for (Entity child : precendenceEntity.getType().getChildren()) {
            if (child.getRawName().getName().equals(names[nameIndex])) {
                return findEntitySince(child, names, nameIndex + 1);
            }
            //In C++,an enumararion can be used directly
            if (child instanceof TypeEntity && ((TypeEntity) child).isEnum()) {
                for (Entity enumeration : child.getChildren()) {
                    if (enumeration.getRawName().getName().equals(names[nameIndex])) {
                        return findEntitySince(enumeration, names, nameIndex + 1);
                    }
                }
            }
        }
        return null;
    }

    public Entity lookupTypeInImported(FileEntity fileEntity, String name) {
        if (fileEntity == null) {
            return null;
        }
        return importLookupStrategy.lookupImportedType(name, fileEntity, repo, this);
    }


    /**
     * In Java/C++ etc, the same package names should take priority of resolving.
     * the entity lookup is implemented recursively.
     */
    private Entity findEntityUnderSamePackage(Entity fromEntity, String name) {
        do {
            Entity entity = fromEntity.getByName(name, new HashSet<>());
            if (entity != null) return entity;
            if (fromEntity instanceof PackageEntity && fromEntity.getMultiDeclare() != null) {
                entity = findEntityInMultiPackages(fromEntity.getMultiDeclare(), name);
                if (entity != null) {
                    return entity;
                }
            }
            fromEntity = fromEntity.getParent();
        } while (fromEntity != null);
        return null;
    }

    private Entity findEntityInMultiPackages(MultiDeclareEntities multiDeclareEntities, String name) {
        Entity res;
        for (Entity entity : multiDeclareEntities.getEntities()) {
            res = entity.getByName(name, new HashSet<>());
            if (res != null) return res;
        }
        return null;
    }

    /**
     * Deduce type based on function calls
     * If the function call is a subset of a type, then the type could be a candidate of the var's type
     */
    public List<TypeEntity> calculateCandidateTypes(List<FunctionCall> functionCalls) {
        if (buildInTypeManager.isBuildInTypeMethods(functionCalls)) {
            return new ArrayList<>();
        }
        if (!isDuckTypingDeduce)
            return new ArrayList<>();
        return searchTypesInRepo(functionCalls);
    }

    private List<TypeEntity> searchTypesInRepo(List<FunctionCall> functionCalls) {
        List<TypeEntity> types = new ArrayList<>();
        Iterator<Entity> iterator = repo.sortedFileIterator();
        while (iterator.hasNext()) {
            Entity f = iterator.next();
            if (f instanceof FileEntity) {
                for (TypeEntity type : ((FileEntity) f).getDeclaredTypes()) {
                    FunctionMatcher functionMatcher = new FunctionMatcher(type.getFunctions());
                    if (functionMatcher.containsAll(functionCalls)) {
                        types.add(type);
                    }
                }
            }
        }
        return types;
    }

    public boolean isEagerExpressionResolve() {
        return eagerExpressionResolve;
    }

    public void setCollectUnsolvedBindings(boolean isCollectUnsolvedBindings) {
        this.isCollectUnsolvedBindings = isCollectUnsolvedBindings;
    }

    public EntityRepo getRepo() {
        return repo;
    }

    public void setDuckTypingDeduce(boolean isDuckTypingDeduce) {
        this.isDuckTypingDeduce = isDuckTypingDeduce;
    }

    public BuiltInType getBuildInTypeManager() {
        return buildInTypeManager;
    }

    public Collection<Entity> getMacroExpansions(List<String> macroNames) {
        ArrayList<Entity> result = new ArrayList<>();
        for (String macroName : macroNames) {
            Entity macro = repo.getEntity(macroName);
            if (macro == null) continue;
            result.add(macro);
        }
        return result;
    }
}
