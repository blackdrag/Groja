import org.codehaus.groovy.control.CompilationFailedException
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.tools.GroovyClass
import spock.lang.Specification

class JointCompilerTest extends Specification {

    def classNames
    def groovyCompilationUnit

    def setup() {
        classNames = []
        groovyCompilationUnit = new GroovyJavaJointCompiler2()
        def op = new CompilationUnit.GroovyClassOperation() {
            @Override
            void call(GroovyClass gclass) throws CompilationFailedException {
                classNames << gclass.name
            }
        }
        groovyCompilationUnit.addPhaseOperation(op)
    }

    def "compiles only groovy and present classes"(List fileNames, List sources, result, caughtException) {
        expect:
        fileNames.eachWithIndex { def entry, i -> groovyCompilationUnit.addSource(entry, sources[i]) }
        try {
            groovyCompilationUnit.compile()
        } catch (e) {
            assert e.getClass() == caughtException
        }
        result == classNames

        where:
        fileNames                    || sources                                          || result       || caughtException
        ["Test.groovy", "Test.java"] || ["interface Test{}", "interface Test{}"]         || []           || MultipleCompilationErrorsException
        ["Test.groovy", "J1.java"]   || ["interface Test{}", "interface J1{}"]           || ["Test"]     || null
        ["Test.groovy"]              || ["interface Test{}"]                             || ["Test"]     || null
        ["J1.java"]                  || ["class J1 implements Test{}"]                   || []           || MultipleCompilationErrorsException
        ["G1.groovy"]                || ["class G1 extends J2{}"]                        || []           || MultipleCompilationErrorsException
        ["G1.groovy", "G2.groovy"]   || ["interface G1{}", "interface G2{}"]             || ["G1", "G2"] || null
        ["G1.groovy", "MyEnum.java",
         "EnumAnnotation.java"]      || ["@EnumAnnotation(MyEnum.FOO) class G1{}",
                                         "enum MyEnum{FOO}",
                                         "@interface EnumAnnotation{ MyEnum value(); }"] || ["G1"]       || null
    }
}