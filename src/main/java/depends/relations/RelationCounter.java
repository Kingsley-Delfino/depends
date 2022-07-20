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

import java.util.*;
import java.util.stream.Collectors;

public class RelationCounter {

	private Collection<Entity> entities;
	private Inferer inferer;
	private EntityRepo repo;
	private boolean callAsImpl;
	private AbstractLangProcessor langProcessor;

	public RelationCounter(Collection<Entity> iterator, Inferer inferer, EntityRepo repo, boolean callAsImpl, AbstractLangProcessor langProcessor) {
		this.entities = iterator;
		this.inferer = inferer;
		this.repo = repo;
		this.callAsImpl = callAsImpl;
		this.langProcessor = langProcessor;
	}
	
	public void computeRelations() {
		int index = 0;
		int allEntitiesNum = entities.size();
		for(Entity entity : entities){
			computeRelationOf(entity);
			index++;
			try{
				System.out.print("\rNumber Of Binding-resolved files:[" + index + "/" + allEntitiesNum + "]");
				Thread.sleep(20);
			}catch(InterruptedException e){}
		}
		System.out.println();
	}

	private void computeRelationOf(Entity entity) {
		if (!entity.inScope())
			return;
		if (entity instanceof FileEntity) {
			computeImports((FileEntity)entity);
			computeMacroExpansions((FileEntity)entity);
		}
		else if (entity instanceof FunctionEntity) {
			computeFunctionRelations((FunctionEntity)entity);
		}
		else if (entity instanceof TypeEntity) {
			computeTypeRelations((TypeEntity)entity);
		}
		if (entity instanceof ContainerEntity) {
			computeContainerRelations((ContainerEntity)entity);
		}
		entity.getChildren().forEach(child->computeRelationOf(child));
	}

	

	private void computeContainerRelations(ContainerEntity entity) {
		for (VarEntity var:entity.getVars()) {
			if (var.getType()!=null) {
				if(var.getType() instanceof EmptyTypeEntity && var.getActualReferTo() != null) {
					entity.addRelation(buildRelation(entity,DependencyType.CONTAIN,var.getActualReferTo(),var.getLocation()));
				} else {
					entity.addRelation(buildRelation(entity,DependencyType.CONTAIN,var.getType(),var.getLocation()));
				}
			}
			for (Entity type:var.getResolvedTypeParameters()) {
				var.addRelation(buildRelation(var, DependencyType.PARAMETER,type,type.getLocation()));
			}
		}
		for(AliasEntity alias : entity.getAlias()) {
			entity.addRelation(buildRelation(entity,DependencyType.USE,alias.getReferToEntity(),alias.getLocation()));
		}
		for (Entity type:entity.getResolvedAnnotations()) {
			entity.addRelation(buildRelation(entity,DependencyType.ANNOTATION,type));
		}
		for (Entity type:entity.getResolvedTypeParameters()) {
			entity.addRelation(buildRelation(entity,DependencyType.USE,type));
		}
		for (ContainerEntity mixin:entity.getResolvedMixins()) {
			entity.addRelation(buildRelation(entity,DependencyType.MIXIN,mixin));
		}

		entity.reloadExpression(repo);
		if (!inferer.isEagerExpressionResolve())
		{
			entity.resolveExpressions(inferer);
		}
		for (Expression expression:entity.expressionList()){
			if (expression.isStatement()) {
				continue;
			}
			Entity referredEntity = expression.getReferredEntity();
			addRelationFromExpression(entity, expression, referredEntity);
		}
		entity.clearExpressions();
	}

	private void addRelationFromExpression(ContainerEntity entity, Expression expression, Entity referredEntity) {
		
		if (referredEntity==null) {
			return;
		}
		if (referredEntity.getId()<0){
			return;
		}
		if (referredEntity instanceof MultiDeclareEntities) {
			for (Entity e:((MultiDeclareEntities)referredEntity).getEntities()) {
				addRelationFromExpression(entity,expression,e);
			}
			return;
		}
		
		boolean matched = false;
		if (expression.isCall()) {
			//修正关系到FunctionProto
			if(referredEntity.getMultiDeclare() != null && !isInHeaderFile(referredEntity) &&
				referredEntity.getAncestorOfType(FileEntity.class) != entity.getAncestorOfType(FileEntity.class)){
				for(Entity entity1 : referredEntity.getMultiDeclare().getEntities()){
					if(entity1.getAncestorOfType(FileEntity.class).getId().equals(entity.getAncestorOfType(FileEntity.class).getId())){
						referredEntity = entity1;
						break;
					}
					if(isInHeaderFile(entity1)){
						referredEntity = entity1;
					}
				}
			}
			/* if it is a FunctionEntityProto, add Relation to all Impl Entities*/
			if (callAsImpl && referredEntity instanceof FunctionEntityProto) {
				if (entity.getAncestorOfType(FileEntity.class).getId().equals(referredEntity.getAncestorOfType(FileEntity.class).getId())){
					entity.addRelation(buildRelation(entity,DependencyType.CALL,referredEntity,expression.getLocation()));
				}else {
					Entity multiDeclare = repo.getEntity(referredEntity.getQualifiedName());
					if (multiDeclare instanceof MultiDeclareEntities) {
						MultiDeclareEntities m = (MultiDeclareEntities) multiDeclare;
						List<Entity> entities = m.getEntities().stream().filter(item -> (item instanceof FunctionEntityImpl && !isInHeaderFile(item)))
								.collect(Collectors.toList());
						for (Entity e : entities) {
							entity.addRelation(expression, buildRelation(entity, DependencyType.IMPLLINK, e, expression.getLocation()));
							matched = true;
						}
					}
				}
			}

			//修正连接到错误的类内调用
			if(!isInHeaderFile(referredEntity)){
				if(!(entity.getAncestorOfType(FileEntity.class).getId().equals(referredEntity.getAncestorOfType(FileEntity.class).getId()))){
					Entity parent = entity.getParent();
					if(parent != null){
						for(Entity entity1: parent.getChildren()){
							if(entity1.getQualifiedName().contains(referredEntity.getQualifiedName())){
								referredEntity = entity1;
							}
						}
					}
				}
			}
			entity.addRelation(buildRelation(entity,DependencyType.CALL,referredEntity,expression.getLocation()));
			matched = true;

		}
		if (expression.isCreate()) {
			entity.addRelation(buildRelation(entity,DependencyType.CREATE,referredEntity,expression.getLocation()));
			matched = true;
		}
		if (expression.isThrow()) {
			entity.addRelation(buildRelation(entity,DependencyType.THROW,referredEntity,expression.getLocation()));
			matched = true;
		}
		if (expression.isCast()) { 
			entity.addRelation(buildRelation(entity,DependencyType.CAST,referredEntity,expression.getLocation()));
			matched = true;
		}
		if (!matched)  {
			if (callAsImpl && repo.getEntity(referredEntity.getQualifiedName()) instanceof MultiDeclareEntities &&
					(referredEntity instanceof VarEntity ||referredEntity instanceof FunctionEntity)) {
				if (entity.getAncestorOfType(FileEntity.class).getId().equals(referredEntity.getAncestorOfType(FileEntity.class).getId())){
					entity.addRelation(buildRelation(entity,DependencyType.USE,referredEntity,expression.getLocation()));
					matched = true;
				}else {
					MultiDeclareEntities m = (MultiDeclareEntities) (repo.getEntity(referredEntity.getQualifiedName()));
					for (Entity e : m.getEntities()) {
						if (e == referredEntity) {
							entity.addRelation(expression, buildRelation(entity, DependencyType.USE, e, expression.getLocation()));
						} else {
							entity.addRelation(expression, buildRelation(entity, DependencyType.IMPLLINK, e, expression.getLocation()));
						}
						matched = true;
					}
				}
			}
			if (!matched) {
				if(referredEntity instanceof FunctionEntity) {
					entity.addRelation(expression,buildRelation(entity,DependencyType.CALL,referredEntity,expression.getLocation()));
				} else {
					entity.addRelation(expression,buildRelation(entity,DependencyType.USE,referredEntity,expression.getLocation()));
				}
			}
		}
	}

	private Relation buildRelation(Entity from, String type, Entity referredEntity) {
		return buildRelation(from,type,referredEntity,from.getLocation());
	}

	private Relation buildRelation(Entity from, String type, Entity referredEntity,Location location) {
		if (referredEntity instanceof AliasEntity) {
			if (from.getAncestorOfType(FileEntity.class).equals(referredEntity.getAncestorOfType(FileEntity.class))) {
				AliasEntity alias = ((AliasEntity) referredEntity);
				if (alias.deepResolve()!=null) {
					referredEntity = alias.deepResolve();
				}
			}
		}
		if (this.langProcessor==null)
			return new Relation(type,referredEntity,location);
		return new Relation(langProcessor.getRelationMapping(type),referredEntity,location);
	}

	private void computeTypeRelations(TypeEntity type) {
		for (TypeEntity superType:type.getInheritedTypes()) {
			type.addRelation(buildRelation(type,DependencyType.INHERIT,superType));
		}
		for (TypeEntity interfaceType:type.getImplementedTypes()) {
			type.addRelation(buildRelation(type,DependencyType.IMPLEMENT,interfaceType));
		}
	}

	private void computeFunctionRelations(FunctionEntity func) {
		for (Entity returnType:func.getReturnTypes()) {
			func.addRelation(buildRelation(func,DependencyType.RETURN,returnType.getActualReferTo()));
		}
		for (VarEntity parameter:func.getParameters()) {
			if (parameter.getType()!=null) 
				func.addRelation(buildRelation(func,DependencyType.PARAMETER,parameter.getActualReferTo()));
		}
		for (Entity throwType:func.getThrowTypes()) {
			func.addRelation(buildRelation(func,DependencyType.THROW,throwType));
		}
		for (Entity type:func.getResolvedTypeParameters()) {
			func.addRelation(buildRelation(func,DependencyType.PARAMETER,type));
		}
		if (func instanceof FunctionEntityImpl) {
			FunctionEntityImpl funcImpl = (FunctionEntityImpl)func;
			if(funcImpl.getImplemented()!=null) {
				func.addRelation(buildRelation(func,DependencyType.IMPLEMENT,funcImpl.getImplemented()));
			}
		}
	}

	private void computeImports(FileEntity file) {
		Collection<Entity> imports = file.getImportedRelationEntities();
		if (imports==null) return;
		for (Entity imported:imports) {
			if (imported instanceof FileEntity)
			{
				if (((FileEntity)imported).isInProjectScope())
					file.addRelation(buildRelation(file,DependencyType.IMPORT,imported));
			}else if (imported instanceof TypeEntity) {
				//do nothing
			} else {
				file.addRelation(buildRelation(file,DependencyType.IMPORT,imported));
			}
		}

		Collection<Entity> importedfuncntion = file.getImportedFunctions();
		for(Entity func : importedfuncntion) {
			if(func instanceof FunctionEntity) {
				file.addRelation(buildRelation(file,DependencyType.CALL,func));
			} else if(func instanceof MultiDeclareEntities) {
				for(Entity multi : ((MultiDeclareEntities) func).getEntities()) {
					if(multi instanceof FunctionEntity && imports.contains(multi.getAncestorOfType(FileEntity.class))) {
						file.addRelation(buildRelation(file,DependencyType.CALL,func));
					}
				}
			}
		}

		Collection<Entity> importedType = file.getImportedTypes();
		for(Entity type : importedType) {
			if(type.getClass() == TypeEntity.class) {
				file.addRelation(buildRelation(file,DependencyType.USE,type));
			}
		}
	}

	private void computeMacroExpansions(FileEntity file) {
		Collection<Entity> expansions = file.getMacroExpansions();
		if(expansions == null) return;
		for(Entity macro : expansions) {
			if(macro instanceof MultiDeclareEntities){
				macro = ((MultiDeclareEntities) macro).getEntities().get(0);
			}
			if(macro instanceof VarEntity) {
				file.addRelation(buildRelation(file,DependencyType.USE,macro));
			}else if(macro instanceof FunctionEntity) {
				file.addRelation(buildRelation(file,DependencyType.CALL,macro));
			}
		}
	}

	private Boolean isInHeaderFile(Entity entity){
		return (entity.getAncestorOfType(FileEntity.class).getQualifiedName().contains(".h") ||
				entity.getAncestorOfType(FileEntity.class).getQualifiedName().contains(".hpp") ||
				entity.getAncestorOfType(FileEntity.class).getQualifiedName().contains(".hxx"));
	}


}
