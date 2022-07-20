package depends.entity;

import depends.relations.Inferer;

import java.util.HashSet;

public class FunctionEntityImpl extends FunctionEntity {
	Entity implementedFunction = null;
	public FunctionEntityImpl() {
		super();
	}
    public FunctionEntityImpl(GenericName simpleName, Entity parent, Integer id, GenericName returnType) {
		super(simpleName,parent,id,returnType);
	}
	@Override
	public void inferLocalLevelEntities(Inferer inferer) {
		super.inferLocalLevelEntities(inferer);
		implementedFunction = inferer.lookupTypeInImported((FileEntity)(getAncestorOfType(FileEntity.class)),this.getQualifiedName());
		if(implementedFunction != null){
				solveReloadFunction();
		}
	}
	public Entity getImplemented() {
		return implementedFunction;
	}

	private void solveReloadFunction(){
		String implSignature = this.getArgSignature();
		if(implementedFunction instanceof FunctionEntityProto &&
				(((FunctionEntity) implementedFunction).getArgSignature().equals(this.getArgSignature())))	{
			return ;
		}
		if(implementedFunction.getMultiDeclare() != null){
			for(Entity entity : implementedFunction.getMultiDeclare().getEntities()){
				if(entity instanceof FunctionEntityProto){
					implementedFunction = entity;
					if(((FunctionEntity) entity).getArgSignature().equals(implSignature)){
						implementedFunction =  entity;
						return ;
					}
				}
			}
		}
		//System.out.println("Can not find true method!");
		return ;
	}

	@Override
	public Entity getByName(String name, HashSet<Entity> searched) {
		Entity entity = super.getByName(name, searched);
		if (entity!=null)
			return entity;
		Entity funcProto = getImplemented();
		if(funcProto != null && funcProto instanceof FunctionEntityProto) {
			Entity realParent = funcProto.getParent();
			if(realParent != null && realParent.getClass() == TypeEntity.class) {
				if (searched.contains(realParent)) return null;
				entity = realParent.getByName(name, searched);
				if (entity!=null) {
					if(entity instanceof FunctionEntity) {
						Entity realType = entity.getParent();
						if(realType.getClass() == TypeEntity.class && entity.getRawName().getName().equals(realType.getRawName().getName())) {
							entity = realType;
						}
					}
					return entity;
				}
			}
		}
		return null;
	}

    
}
