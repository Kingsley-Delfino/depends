package depends.extractor.cpp;
import static org.junit.Assert.fail;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;

import depends.deptypes.DependencyType;

public class AliasTest extends CppParserTest{
    @Before
    public void setUp() {
    	super.init();
    }
	
	@Test
	public void test_genericTypes() throws IOException {
	    String src = "./src/test/resources/cpp-code-examples/Alias.cpp";
	    CppFileParser parser = createParser(src);
        parser.parse();
        inferer.resolveAllBindings();
        this.assertContainsRelation(repo.getEntity("bar"), DependencyType.CALL, "F.foo");
	}
	
	@Test
	public void test_refer_to_alias_type_should_work() throws IOException {
	    String src = "./src/test/resources/cpp-code-examples/AliasType.cpp";
	    CppFileParser parser = createParser(src);
        parser.parse();
        inferer.resolveAllBindings();
        this.assertContainsRelation(repo.getEntity("C"), DependencyType.INHERIT, "A");
	}
	
	@Test
	public void test_multi_declares_should_only_count_actual_referred() {
		fail("to be implemented");
	}
	
	@Test
	public void test_header_files_not_contains_include_should_be_resolved() {
		/*非规范的include形式，和include顺序有关，例如
		 A header file contains 
		     class T
		 A.hpp // the file use T, but not include T, because it always use after previous header file  
		     typedef T1 T;
		 * */
		fail("to be implemented");
	}

}
