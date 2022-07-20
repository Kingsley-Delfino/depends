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

import depends.deptypes.DependencyType;
import depends.entity.*;
import depends.entity.repo.EntityRepo;
import depends.extractor.AbstractLangProcessor;
import depends.extractor.MultiDeclareResolve;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class MyRelationCounter {

    private final Collection<Entity> entities;
    private final Inferer inferer;
    private final EntityRepo repo;
    private final boolean callAsImpl;
    private final AbstractLangProcessor langProcessor;

    public MyRelationCounter(Collection<Entity> iterator, Inferer inferer, EntityRepo repo, boolean callAsImpl, AbstractLangProcessor langProcessor) {
        this.entities = iterator;
        this.inferer = inferer;
        this.repo = repo;
        this.callAsImpl = callAsImpl;
        this.langProcessor = langProcessor;
    }

    public void computeRelations() {
        int index = 0;
        int allEntitiesNum = entities.size();
        for (Entity entity : entities) {
            computeRelationOf(entity);
            index++;
            try {
                System.out.print("\rNumber Of Binding-resolved files:[" + index + "/" + allEntitiesNum + "]");
                Thread.sleep(20);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        System.out.println();
    }

    private void computeRelationOf(Entity entity) {
        if (!entity.inScope())
            return;
        entity.clearRelations();
        if (entity instanceof FileEntity) {
            computeImports((FileEntity) entity);
        } else if (entity instanceof FunctionEntity) {
            computeFunctionRelations((FunctionEntity) entity);
        } else if (entity instanceof TypeEntity) {
            computeTypeRelations((TypeEntity) entity);
        }
        if (entity instanceof ContainerEntity) {
            computeContainerRelations((ContainerEntity) entity);
        }
        entity.getChildren().forEach(this::computeRelationOf);
    }


    private void computeContainerRelations(ContainerEntity entity) {
        for (VarEntity var : entity.getVars()) {
            if (var.getType() != null)
                buildAndAddRelation(entity, DependencyType.CONTAIN, var.getType(), var.getLocation());
            for (Entity type : var.getResolvedTypeParameters()) {
                buildAndAddRelation(var, DependencyType.PARAMETER, type, type.getLocation());
            }
        }
        for (Entity type : entity.getResolvedAnnotations()) {
            buildAndAddRelation(entity, DependencyType.ANNOTATION, type);
        }
        for (Entity type : entity.getResolvedTypeParameters()) {
            buildAndAddRelation(entity, DependencyType.USE, type);
        }
        for (ContainerEntity mixin : entity.getResolvedMixins()) {
            buildAndAddRelation(entity, DependencyType.MIXIN, mixin);
        }

        entity.reloadExpression(repo);
        if (!inferer.isEagerExpressionResolve()) {
            entity.resolveExpressions(inferer);
        }
        for (Expression expression : entity.expressionList()) {
            if (expression.isStatement()) {
                continue;
            }
            Entity referredEntity = expression.getReferredEntity();
            addRelationFromExpression(entity, expression, referredEntity);
        }
        entity.clearExpressions();
    }

    private void addRelationFromExpression(ContainerEntity entity, Expression expression, Entity referredEntity) {

        if (referredEntity == null) {
            return;
        }
        if (referredEntity.getId() < 0) {
            return;
        }
        if (referredEntity instanceof MultiDeclareEntities) {
            List<Entity> selectedMulti = MultiDeclareResolve.selectMostRelative((MultiDeclareEntities) referredEntity, entity);
            for (Entity e : selectedMulti) {
                addRelationFromExpression(entity, expression, e);
            }
            return;
        }

        boolean matched = false;
        if (expression.isCall()) {
            /* if it is a FunctionEntityProto, add Relation to all Impl Entities*/
            if (callAsImpl && referredEntity instanceof FunctionEntityProto) {
                if (entity.getAncestorOfType(FileEntity.class).getId().equals(referredEntity.getAncestorOfType(FileEntity.class).getId())) {
                    buildAndAddRelation(entity, DependencyType.CALL, referredEntity, expression.getLocation());
                } else {
                    Entity multiDeclare = repo.getEntity(referredEntity.getQualifiedName());
                    if (multiDeclare instanceof MultiDeclareEntities) {
                        MultiDeclareEntities m = (MultiDeclareEntities) multiDeclare;
                        List<Entity> resolvedMulti = MultiDeclareResolve.selectMostRelative(m, entity);
                        List<Entity> entities = resolvedMulti.stream().filter(item -> (item instanceof FunctionEntityImpl))
                                .collect(Collectors.toList());
                        for (Entity e : entities) {
                            buildAndAddRelation(entity, DependencyType.IMPLLINK, e, expression.getLocation(), expression);
                            matched = true;
                        }
                    }
                }
            }
            buildAndAddRelation(entity, DependencyType.CALL, referredEntity, expression.getLocation());
            matched = true;
        }
        if (expression.isCreate()) {
            buildAndAddRelation(entity, DependencyType.CREATE, referredEntity, expression.getLocation());
            matched = true;
        }
        if (expression.isThrow()) {
            buildAndAddRelation(entity, DependencyType.THROW, referredEntity, expression.getLocation());
            matched = true;
        }
        if (expression.isCast()) {
            buildAndAddRelation(entity, DependencyType.CAST, referredEntity, expression.getLocation());
            matched = true;
        }
        if (!matched) {
            if (callAsImpl && repo.getEntity(referredEntity.getQualifiedName()) instanceof MultiDeclareEntities &&
                    (referredEntity instanceof VarEntity || referredEntity instanceof FunctionEntity)) {
                if (entity.getAncestorOfType(FileEntity.class).getId().equals(referredEntity.getAncestorOfType(FileEntity.class).getId())) {
                    buildAndAddRelation(entity, DependencyType.USE, referredEntity, expression.getLocation());
                } else {
                    MultiDeclareEntities m = (MultiDeclareEntities) (repo.getEntity(referredEntity.getQualifiedName()));
                    for (Entity e : MultiDeclareResolve.selectMostRelative(m, entity)) {
                        if (e == referredEntity) {
                            buildAndAddRelation(entity, DependencyType.USE, e, expression.getLocation(), expression);
                        } else {
                            buildAndAddRelation(entity, DependencyType.IMPLLINK, e, expression.getLocation(), expression);
                        }
                        matched = true;
                    }
                }
            } else {
                buildAndAddRelation(entity, DependencyType.USE, referredEntity, expression.getLocation(), expression);
            }
        }
    }

    private void buildAndAddRelation(Entity from, String type, Entity referredEntity) {
        buildAndAddRelation(from, type, referredEntity, from.getLocation());
    }

    private void buildAndAddRelation(Entity from, String type, Entity referredEntity, Expression expression) {
        buildAndAddRelation(from, type, referredEntity, from.getLocation(), expression);
    }

    private void buildAndAddRelation(Entity from, String type, Entity referredEntity, Location location) {
        if (referredEntity instanceof AliasEntity) {
            if (from.getAncestorOfType(FileEntity.class).equals(referredEntity.getAncestorOfType(FileEntity.class))) {
                AliasEntity alias = ((AliasEntity) referredEntity);
                if (alias.deepResolve() != null) {
                    referredEntity = alias.deepResolve();
                }
            }
        }
        if (this.langProcessor == null) {
            from.addRelation(new Relation(type, referredEntity, location));
            return;
        }
        if (referredEntity instanceof MultiDeclareEntities) {
            if (((MultiDeclareEntities) referredEntity).getEntities().get(0) instanceof FunctionEntity && type.equals("Import")) {
                List<Entity> resolvedMulti = MultiDeclareResolve.selectMostRelative((MultiDeclareEntities) referredEntity, from);
                for (Entity e : resolvedMulti) {
                    from.addRelation(new Relation(type, e, location));
                }
                return;
            } else {
                List<Entity> resolvedMulti = MultiDeclareResolve.selectMostRelative((MultiDeclareEntities) referredEntity, from);
                from.addRelation(new Relation(type, resolvedMulti.get(0), location));
                return;
            }

        } else if (referredEntity != null && referredEntity.getMultiDeclare() != null) {
            List<Entity> resolvedMulti = MultiDeclareResolve.selectMostRelative(referredEntity.getMultiDeclare(), from);
            if (!resolvedMulti.contains(referredEntity)) {
                from.addRelation(new Relation(type, resolvedMulti.get(0), resolvedMulti.get(0).getLocation()));
            } else {
                from.addRelation(new Relation(type, referredEntity, location));
            }
            return;
        }
        from.addRelation(new Relation(langProcessor.getRelationMapping(type), referredEntity, location));
    }

    private void buildAndAddRelation(Entity from, String type, Entity referredEntity, Location location, Expression expression) {
        if (referredEntity instanceof AliasEntity) {
            if (from.getAncestorOfType(FileEntity.class).equals(referredEntity.getAncestorOfType(FileEntity.class))) {
                AliasEntity alias = ((AliasEntity) referredEntity);
                if (alias.deepResolve() != null) {
                    referredEntity = alias.deepResolve();
                }
            }
        }
        if (this.langProcessor == null) {
            ((ContainerEntity) from).addRelation(expression, new Relation(type, referredEntity, location));
            return;
        }
        if (referredEntity instanceof MultiDeclareEntities) {
            if (((MultiDeclareEntities) referredEntity).getEntities().get(0) instanceof FunctionEntity && type.equals("Import")) {
                List<Entity> resolvedMulti = MultiDeclareResolve.selectMostRelative((MultiDeclareEntities) referredEntity, from);
                for (Entity e : resolvedMulti) {
                    ((ContainerEntity) from).addRelation(expression, new Relation(type, e, location));
                }
                return;
            } else {
                List<Entity> resolvedMulti = MultiDeclareResolve.selectMostRelative((MultiDeclareEntities) referredEntity, from);
                ((ContainerEntity) from).addRelation(expression, new Relation(type, resolvedMulti.get(0), location));
                return;
            }
        } else if (referredEntity != null && referredEntity.getMultiDeclare() != null) {
            List<Entity> resolvedMulti = MultiDeclareResolve.selectMostRelative(referredEntity.getMultiDeclare(), from);
            if (!resolvedMulti.contains(referredEntity)) {
                ((ContainerEntity) from).addRelation(expression, new Relation(type, resolvedMulti.get(0), location));
            } else {
                ((ContainerEntity) from).addRelation(expression, new Relation(type, referredEntity, location));
            }
            return;
        }
        ((ContainerEntity) from).addRelation(expression, new Relation(langProcessor.getRelationMapping(type), referredEntity, location));
    }

    private void computeTypeRelations(TypeEntity type) {
        for (TypeEntity superType : type.getInheritedTypes()) {
            buildAndAddRelation(type, DependencyType.INHERIT, superType);
        }
        for (TypeEntity interfaceType : type.getImplementedTypes()) {
            buildAndAddRelation(type, DependencyType.IMPLEMENT, interfaceType);
        }
    }

    private void computeFunctionRelations(FunctionEntity func) {
        for (Entity returnType : func.getReturnTypes()) {
            buildAndAddRelation(func, DependencyType.RETURN, returnType.getActualReferTo());
        }
        for (VarEntity parameter : func.getParameters()) {
            if (parameter.getType() != null)
                buildAndAddRelation(func, DependencyType.PARAMETER, parameter.getActualReferTo());
        }
        for (Entity throwType : func.getThrowTypes()) {
            buildAndAddRelation(func, DependencyType.THROW, throwType);
        }
        for (Entity type : func.getResolvedTypeParameters()) {
            buildAndAddRelation(func, DependencyType.PARAMETER, type);
        }
        if (func instanceof FunctionEntityImpl) {
            FunctionEntityImpl funcImpl = (FunctionEntityImpl) func;
            if (funcImpl.getImplemented() != null) {
                buildAndAddRelation(func, DependencyType.IMPLEMENT, funcImpl.getImplemented());
            }
        }
    }

    private void computeImports(FileEntity file) {
        Collection<Entity> imports = file.getImportedRelationEntities();
        if (imports == null) return;
        for (Entity imported : imports) {
            if (imported instanceof FileEntity) {
                if (((FileEntity) imported).isInProjectScope())
                    buildAndAddRelation(file, DependencyType.IMPORT, imported);
            } else {
                buildAndAddRelation(file, DependencyType.IMPORT, imported);
            }
        }
    }

}
