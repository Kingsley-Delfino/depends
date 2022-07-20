package depends.extractor.java;

import depends.entity.Entity;
import depends.entity.MultiDeclareEntities;

import java.util.HashSet;
import java.util.List;

public class PathConverter {

    public static Entity solveWrongEntityInSameNameByType(Entity fromEntity, Class clas){

        String preName = fromEntity.getRawName().getName();

        while(true){
            if(fromEntity == null) return null;

            Entity possibleEntity = fromEntity.getByName(preName, new HashSet<>());
            if(possibleEntity != null) {
                if(possibleEntity.getClass() == clas) return possibleEntity;
                MultiDeclareEntities multiDeclareEntities = possibleEntity.getMultiDeclare();
                if(multiDeclareEntities != null){
                    List<Entity> multi = multiDeclareEntities.getEntities();
                    for(Entity entity : multi){
                        if(entity.getClass() == clas){
                            return entity;
                        }
                    }
                }
            }
            fromEntity = fromEntity.getParent();
        }
    }

}
