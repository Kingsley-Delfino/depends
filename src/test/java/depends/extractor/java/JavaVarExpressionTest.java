package depends.extractor.java;

import depends.deptypes.DependencyType;
import depends.entity.Entity;
import depends.entity.VarEntity;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

public class JavaVarExpressionTest extends JavaParserTest{
    @Before
    public void setUp() {
        super.init();
    }

    @Test
    public void test_var_expression() throws IOException {
//        String src = "./src/test/resources/java-code-examples/VarExpressionTest.java";
//        JavaFileParser parser = createParser(src);
//        parser.parse();
//        this.inferer.resolveAllBindings();
//
//        VarEntity fielda = entityRepo.getEntity(0).findFields().get(0);
//        this.assertContainsRelation(fielda, DependencyType.CALL, "ComplexExpressionTest.foo");
//        this.assertContainsRelation(fielda, DependencyType.USE, "ComplexExpressionTest");
//
//        VarEntity fieldb = entityRepo.getEntity(0).findFields().get(1);
//        this.assertContainsRelation(fieldb, DependencyType.CREATE, "ComplexExpressionTest");
//        this.assertContainsRelation(fieldb, DependencyType.CALL, "ComplexExpressionTest");
//
//        VarEntity fieldc = entityRepo.getEntity(0).findFields().get(2);
//        this.assertContainsRelation(fieldc, DependencyType.CALL, "B.func");
//
//        VarEntity fieldd = entityRepo.getEntity(0).findFields().get(3);
//        this.assertContainsRelation(fieldd, DependencyType.USE, "ComplexExpressionTest.var");
//        this.assertContainsRelation(fieldd, DependencyType.USE, "ComplexExpressionTest");
//
//        Entity classB = entityRepo.getEntity("B");
//        this.assertContainNoRelation(classB);
    }
}

