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

package depends.entity;

import depends.entity.repo.EntityRepo;
import depends.extractor.java.JavaBuiltInType;
import depends.extractor.java.PathConverter;
import depends.relations.Inferer;
import depends.relations.Relation;
import multilang.depends.util.file.TemporaryFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.ref.WeakReference;
import java.util.*;

/**
 * ContainerEntity for example file, class, method, etc. they could contain
 * vars, functions, ecpressions, type parameters, etc.
 */
public abstract class ContainerEntity extends DecoratedEntity {
	private static final Logger logger = LoggerFactory.getLogger(ContainerEntity.class);

	private ArrayList<VarEntity> vars;
	private ArrayList<FunctionEntity> functions;
	WeakReference<HashMap<Object, Expression>> expressionWeakReference;
	private ArrayList<Expression> expressionList;
	private int expressionCount = 0;
	private Collection<GenericName> mixins;
	private Collection<ContainerEntity> resolvedMixins;

	private ArrayList<VarEntity> vars() {
		if (vars==null)
			vars = new ArrayList<>();
		return this.vars;
	}
	
	private Collection<GenericName> mixins() {
		if (mixins==null)
			mixins = new ArrayList<>();
		return this.mixins;
	}

	private ArrayList<FunctionEntity> functions() {
		if (functions==null)
			functions = new ArrayList<>();
		return this.functions;
	}
	
	public ContainerEntity() {
	}

	public ContainerEntity(GenericName rawName, Entity parent, Integer id) {
		super(rawName, parent, id);
	}

	public void addVar(VarEntity var) {
		if (logger.isDebugEnabled()) {
			logger.debug("var found: " + var.getRawName() + ":" + var.getRawType());
		}
		this.vars().add(var);
	}

	public ArrayList<VarEntity> getVars() {
		if (vars==null)
			return new ArrayList<>();
		return this.vars();
	}

	public void addFunction(FunctionEntity functionEntity) {
		this.functions().add(functionEntity);
	}

	public ArrayList<FunctionEntity> getFunctions() {
		if (functions==null)
			return new ArrayList<>();
		return this.functions;
	}

	public HashMap<Object, Expression> expressions() {
		if (expressionWeakReference==null)
			expressionWeakReference= new WeakReference<HashMap<Object, Expression>>(new HashMap<>());
		HashMap<Object, Expression> r = expressionWeakReference.get();
		if (r==null) return new HashMap<>();
		return r;
	}

	public void addExpression(Object key, Expression expression) {
		expressions().put(key, expression);
		expressionList().add(expression);
		expressionCount = expressionList.size();
	}

	public boolean containsExpression(Object key) {
		return 	expressions().containsKey(key);
	}
	/**
	 * For all data in the class, infer their types. Should be override in
	 * sub-classes
	 */
	public void inferLocalLevelEntities(Inferer inferer) {
		super.inferLocalLevelEntities(inferer);
		for (VarEntity var : this.vars()) {
			if (var.getParent()!=this) {
				var.inferLocalLevelEntities(inferer);
			}
		}
		for (FunctionEntity func : this.getFunctions()) {
			if (func.getParent()!=this) {
				func.inferLocalLevelEntities(inferer);
			}
		}
		for (AliasEntity alias : this.alias()) {
			if (alias.getParent()!=this) {
				alias.inferLocalLevelEntities(inferer);
			}
		}
		if (inferer.isEagerExpressionResolve()) {
			reloadExpression(inferer.getRepo());
			resolveExpressions(inferer);
			cacheExpressions();
		}
		resolvedMixins = identiferToContainerEntity(inferer, getMixins());
	}

	private Collection<GenericName> getMixins() {
		if (mixins==null)
			return new ArrayList<>();
		return mixins;
	}

	private Collection<ContainerEntity> identiferToContainerEntity(Inferer inferer, Collection<GenericName> identifiers) {
		if (identifiers.size()==0) return null;
		ArrayList<ContainerEntity> r = new ArrayList<>();
		for (GenericName identifier : identifiers) {
			Entity entity = inferer.resolveName(this, identifier, true);
			if (entity == null) {
				continue;
			}
			if (entity instanceof ContainerEntity)
				r.add((ContainerEntity) entity);
		}
		return r;
	}

	/**
	 * Resolve all expression's type
	 * 
	 * @param inferer
	 */
	public void resolveExpressions(Inferer inferer) {

		if(inferer.getBuildInTypeManager() instanceof JavaBuiltInType){
			resolveJavaExpressions(inferer);
		}else{
			resolveOtherExpressions(inferer);
		}
	}

	private void resolveOtherExpressions(Inferer inferer){

		if (this instanceof FunctionEntity) {
			((FunctionEntity)this).linkReturnToLastExpression();
		}

		if (expressionList==null) return;
		if(expressionList.size()>10000) return;

		Entity fileEntity = this.getAncestorOfType(FileEntity.class);

		for (Expression expression : expressionList) {
			// 1. if expression's type existed, break;
			if (expression.getType() != null)
				continue;
			if (expression.isDot()) { // wait for previous
				continue;
			}
			if (expression.getRawType() == null && expression.getIdentifier() == null)
				continue;

			// 2. if expression's rawType existed, directly infer type by rawType
			// if expression's rawType does not existed, infer type based on identifiers
			if (expression.getRawType() != null) {
				expression.setType(inferer.inferTypeFromName(this, expression.getRawType()), null, inferer);
				if (expression.getType() != null) {
					continue;
				}
			}
			if (expression.getIdentifier() != null) {

				//Usingdeclaration
				if(fileEntity != null && ((FileEntity)fileEntity).UsingReflection().containsKey(expression.getIdentifier().getName())) {
					expression.setIdentifier(GenericName.build(((FileEntity)fileEntity).UsingReflection().get(expression.getIdentifier().getName())));
				}

//				if (this.getAncestorOfType(FileEntity.class).getRawName().contains("/examples/usersession/server.py") &&
//						expression.getIdentifier().contains("config")) {
//					System.out.print("dd");
//				}


				Entity entity = inferer.resolveName(this, expression.getIdentifier(), true);
				if(expression.isCall() && entity instanceof TypeEntity) {
					for(Entity func : ((TypeEntity) entity).getFunctions()) {
						if(func.getRawName().getName().equals(entity.getRawName().getName())) {
							entity = func;
							break;
						}
					}
				}
				String composedName = expression.getIdentifier().toString();
				Expression theExpr = expression;
				if (entity==null) {
					while(theExpr.getParent()!=null && theExpr.getParent().isDot()) {
						theExpr = theExpr.getParent();
						if (theExpr.getIdentifier()==null) break;
						composedName = composedName + "." + theExpr.getIdentifier().toString();
						entity = inferer.resolveName(this, GenericName.build(composedName), true);
						if (entity!=null)
							break;
					}
				}
				if (entity != null) {
					expression.setType(entity.getType(), entity, inferer);
					continue;
				}
				if (expression.isCall()) {
					List<Entity> funcs = this.lookupFunctionInVisibleScope(expression.getIdentifier());

					//修正连接到错误的类内调用
					if(funcs == null){
						if(this.getQualifiedName().contains(".") && this instanceof FunctionEntity && !expression.getIdentifier().contains(".")){
							GenericName trueName;
							String abosoluteclassName = this.getQualifiedName().substring(0, this.getQualifiedName().lastIndexOf("."));
							if(abosoluteclassName.contains(".")){
								String className = abosoluteclassName.substring(abosoluteclassName.lastIndexOf('.') + 1);
								trueName = new GenericName(className + "." + expression.getIdentifier());
							}else{
								trueName = new GenericName(abosoluteclassName + "." + expression.getIdentifier());
							}
							if(this.getParent() instanceof ContainerEntity){
								funcs = ((ContainerEntity)this.getParent()).lookupFunctionInVisibleScope(trueName);
							}
						}
					}
					if (funcs != null) {
						for (Entity func:funcs) {
							expression.setType(func.getType(), func, inferer);
						}
					}
				} else {

					Entity varEntity = this.lookupVarInVisibleScope(expression.getIdentifier());
					if (varEntity != null) {
						expression.setType(varEntity.getType(), varEntity, inferer);
					}
				}
			}
		}
	}

	private void resolveJavaExpressions(Inferer inferer){
		if (this instanceof FunctionEntity) {
			((FunctionEntity)this).linkReturnToLastExpression();
		}

		if (expressionList==null) return;
		if(expressionList.size()>10000) return;

		List<Expression> unsolvedCalls = new ArrayList<>();

		for (Expression expression : expressionList) {


			// 1. if expression's type existed, break;
			if (expression.getType() != null)
				continue;
			if (expression.isDot()) { // wait for previous
				continue;
			}
			if (expression.getRawType() == null && expression.getIdentifier() == null)
				continue;

			// 2. if expression's rawType existed, directly infer type by rawType
			// if expression's rawType does not existed, infer type based on identifiers
			if (expression.getRawType() != null) {
				expression.setType(inferer.inferTypeFromName(this, expression.getRawType()), null, inferer);
				if (expression.getType() != null) {
					continue;
				}
			}
			if (expression.getIdentifier() != null) {

//				if (this.getAncestorOfType(FileEntity.class).getRawName().contains("/examples/usersession/server.py") &&
//						expression.getIdentifier().contains("config")) {
//					System.out.print("dd");
//				}


				Entity entity = inferer.resolveName(this, expression.getIdentifier(), true);

				String composedName = expression.getIdentifier().toString();
				Expression theExpr = expression;
				if (entity==null) {
					while(theExpr.getParent()!=null && theExpr.getParent().isDot()) {
						theExpr = theExpr.getParent();
						if (theExpr.getIdentifier()==null) break;
						composedName = composedName + "." + theExpr.getIdentifier().toString();
						entity = inferer.resolveName(this, GenericName.build(composedName), true);
						if (entity!=null)
							break;
					}
				}

				if(entity != null){
					if(expression.isCall() && entity.getClass() == VarEntity.class){
						entity = PathConverter.solveWrongEntityInSameNameByType(entity, FunctionEntity.class);
					}else if(!expression.isCall() && entity.getClass() == FunctionEntity.class){
						Entity preReferred = entity;
						entity = PathConverter.solveWrongEntityInSameNameByType(entity, VarEntity.class);
						if(entity == null){
							entity = PathConverter.solveWrongEntityInSameNameByType(preReferred, TypeEntity.class);
						}
					}
				}

				if (entity != null) {
					expression.setType(entity.getType(), entity, inferer);
					continue;
				}
				if (expression.isCall()) {
					List<Entity> funcs = this.lookupFunctionInVisibleScope(expression.getIdentifier());
					if (funcs != null) {
						for (Entity func:funcs) {
							expression.setType(func.getType(), func, inferer);
						}
					}
				} else {

					Entity varEntity = this.lookupVarInVisibleScope(expression.getIdentifier());
					if (varEntity != null) {
						expression.setType(varEntity.getType(), varEntity, inferer);
					}
				}
			}
		}
		//解决可能的重载CALL

		for(Expression expression : expressionList){
			if(expression.isCall()){
				Entity preReferred = expression.getReferredEntity();
				if(preReferred != null){
					if(preReferred.getMultiDeclare() != null && preReferred.getClass() == FunctionEntity.class){
						if((expression.isDot() && expression.getChildren().size() > 1) ||
								(!expression.isDot() && expression.getChildren().size() > 0)){
							unsolvedCalls.add(expression);
						}
					}
				}
			}
		}
		if(unsolvedCalls.size() > 0){
			for(Expression unsolvedCall : unsolvedCalls){
				Entity prereferred = unsolvedCall.getReferredEntity();
				int deduceId = unsolvedCall.getDeduceTypeBasedId();
				List<TypeEntity> trueTypes = new ArrayList<>();
				if(unsolvedCall.isDot()){
					for(Expression child : unsolvedCall.getChildren()){
						if(child.id != deduceId){
							trueTypes.add(child.getType());
						}
					}
				}else{
					for(Expression child : unsolvedCall.getChildren()){
						trueTypes.add(child.getType());
					}
				}
				//根据参数类型寻找重载方法中合适的方法（基本类型由于都是built-in类型所以无法区分）
				for(Entity entity : prereferred.getMultiDeclare().getEntities()){
					if(entity.getClass() == FunctionEntity.class){
						Collection<VarEntity> parameters = new ArrayList<>(((FunctionEntity) entity).getParameters());
						if(parameters.size() != trueTypes.size()) break;
						int numOfCorrectType = 0;
						//参数匹配
						for(TypeEntity trueType : trueTypes){
							Iterator<VarEntity> parametersIt = parameters.iterator();
							while(parametersIt.hasNext()){
								VarEntity parameter = parametersIt.next();
								if(trueType == parameter.getType()){
									numOfCorrectType++;
									parametersIt.remove();
									break;
								}
							}
							if(numOfCorrectType == trueTypes.size()) break;
						}
						if(numOfCorrectType == trueTypes.size()){
							unsolvedCall.setType(entity.getType(), entity, inferer);
						}
					}
				}
			}
		}
	}
	
	public void cacheChildExpressions() {
		cacheExpressions();
		for (Entity child:getChildren()) {
			if (child instanceof ContainerEntity) {
				((ContainerEntity)child).cacheChildExpressions();
			}
		}
	}


	public void cacheExpressions() {
		if (expressionWeakReference==null) return;
		if (expressionList==null) return;
		this.expressions().clear();
		this.expressionWeakReference.clear();
		cacheExpressionListToFile();
		this.expressionList.clear();
		this.expressionList=null;
		this.expressionList = new ArrayList<>();
	}

	public void clearExpressions() {
		if (expressionWeakReference==null) return;
		if (expressionList==null) return;
		this.expressions().clear();
		this.expressionWeakReference.clear();
		this.expressionList.clear();
		this.expressionList=null;
		this.expressionList = new ArrayList<>();
		this.expressionUseList = null;
	}
	
	private void cacheExpressionListToFile() {
		if (expressionCount ==0) return;
		try {
			FileOutputStream fileOut = new FileOutputStream(TemporaryFile.getInstance().exprPath(this.id));
			ObjectOutputStream out = new ObjectOutputStream(fileOut);
			out.writeObject(this.expressionList);
			out.close();
			fileOut.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@SuppressWarnings("unchecked")
	public void reloadExpression(EntityRepo repo) {
		if (expressionCount ==0) return;
		try
	      {
	         FileInputStream fileIn = new FileInputStream(TemporaryFile.getInstance().exprPath(this.id));
	         ObjectInputStream in = new ObjectInputStream(fileIn);
	         expressionList = (ArrayList<Expression>) in.readObject();
	         if (expressionList==null) expressionList = new ArrayList<>();
	         for (Expression expr:expressionList) {
	        	 expr.reload(repo,expressionList);
	         }
	         in.close();
	         fileIn.close();
	      }catch(IOException | ClassNotFoundException i)
	      {
	         return;
	      }
	}
	

	public List<Expression> expressionList() {
		if (expressionList==null) 
			expressionList = new ArrayList<>();
		return expressionList;
	}

	public boolean containsExpression() {
		return expressions().size() > 0;
	}
	
	/**
	 * The entry point of lookup functions. It will treat multi-declare entities and
	 * normal entity differently. - for multiDeclare entity, it means to lookup all
	 * entities - for normal entity, it means to lookup entities from current scope
	 * still root
	 * 
	 * @param functionName
	 * @return
	 */
	public List<Entity> lookupFunctionInVisibleScope(GenericName functionName) {
		List<Entity> functions = new ArrayList<>();
		if (this.getMultiDeclare() != null) {
			for (Entity fromEntity : this.getMultiDeclare().getEntities()) {
				Entity f = lookupFunctionBottomUpTillTopContainer(functionName, fromEntity);
				if (f != null) {
					functions.add(f);
					return functions;
				}
			}
		} else {
			ContainerEntity fromEntity = this;
			Entity f = lookupFunctionBottomUpTillTopContainer(functionName, fromEntity);
			if (f != null) {
				functions.add(f);
				return functions;
			}
		}
		return null;
	}

	/**
	 * lookup function bottom up till the most outside container
	 * 
	 * @param functionName
	 * @param fromEntity
	 * @return
	 */
	private Entity lookupFunctionBottomUpTillTopContainer(GenericName functionName, Entity fromEntity) {
		while (fromEntity != null) {
			if (fromEntity instanceof ContainerEntity) {
				FunctionEntity func = ((ContainerEntity) fromEntity).lookupFunctionLocally(functionName);
				if (func != null)
					return func;
			}
			for (Entity child:this.getChildren()) {
				if (child instanceof AliasEntity) {
					if (child.getRawName().equals(functionName))
						return child;
				}
			}
			fromEntity = (ContainerEntity) this.getAncestorOfType(ContainerEntity.class);
		}
		return null;
	}

	/**
	 * lookup function in local entity. It could be override such as the type entity
	 * (it should also lookup the inherit/implemented types
	 * 
	 * @param functionName
	 * @return
	 */
	public FunctionEntity lookupFunctionLocally(GenericName functionName) {
		for (FunctionEntity func : getFunctions()) {
			if (func.getRawName().equals(functionName))
				return func;
		}
		return null;
	}

	/**
	 * The entry point of lookup var. It will treat multi-declare entities and
	 * normal entity differently. - for multiDeclare entity, it means to lookup all
	 * entities - for normal entity, it means to lookup entities from current scope
	 * still root
	 * 
	 * @param varName
	 * @return
	 */
	public Entity lookupVarInVisibleScope(GenericName varName) {
		ContainerEntity fromEntity = this;
		return lookupVarBottomUpTillTopContainer(varName, fromEntity);
	}

	/**
	 * To found the var.
	 * 
	 * @param fromEntity
	 * @param varName
	 * @return
	 */
	private Entity lookupVarBottomUpTillTopContainer(GenericName varName, ContainerEntity fromEntity) {
		while (fromEntity != null) {
			if (fromEntity instanceof ContainerEntity) {
				VarEntity var = ((ContainerEntity) fromEntity).lookupVarLocally(varName);
				if (var != null)
					return var;
			}
			for (Entity child:this.getChildren()) {
				if (child instanceof AliasEntity) {
					if (child.getRawName().getName().equals(varName.getName()))
						return child;
				}
			}
			fromEntity = (ContainerEntity) this.getAncestorOfType(ContainerEntity.class);
		}
		return null;
	}

	public VarEntity lookupVarLocally(GenericName varName) {
		for (VarEntity var : getVars()) {
			if(var.getRawName() != null && varName != null) {
				if (var.getRawName().getName().equals(varName.getName())) {
					return var;
				}
			}
		}
		return null;
	}
	
	public VarEntity lookupVarLocally(String varName) {
		return this.lookupVarLocally(GenericName.build(varName));
	}

	public void addMixin(GenericName moduleName) {
		mixins().add(moduleName);
	}

	public Collection<ContainerEntity> getResolvedMixins() {
		if (resolvedMixins==null) return new ArrayList<>();
		return resolvedMixins;
	}

	HashMap<String,Set<Expression>> expressionUseList = null;
	public void addRelation(Expression expression, Relation relation) {
		String key = relation.getEntity().qualifiedName+relation.getType();
		if (this.expressionUseList==null)
			expressionUseList = new HashMap<>();
		if (expressionUseList.containsKey(key)){
			Set<Expression> expressions = expressionUseList.get(key);
			for (Expression expr:expressions){
				if (linkedExpr(expr,expression)) return;
			}
		}else{
			expressionUseList.put(key,new HashSet<>());
		}

		expressionUseList.get(key).add(expression);
		super.addRelation(relation);
	}

	private boolean linkedExpr(Expression a, Expression b) {
		Expression parent = a.getParent();
		while(parent!=null){
			if (parent==b) return true;
			parent = parent.getParent();
		}
		parent = b.getParent();
		while(parent!=null){
			if (parent==a) return true;
			parent = parent.getParent();
		}
		return  false;
	}

	private ArrayList<AliasEntity> alias;

	public void addAlias(AliasEntity aliasEntity) {
		this.alias().add(aliasEntity);
	}

	private ArrayList<AliasEntity> alias() {
		if (alias==null)
			alias = new ArrayList<>();
		return this.alias;
	}

	public ArrayList<AliasEntity> getAlias () {
		return alias();
	}
}
