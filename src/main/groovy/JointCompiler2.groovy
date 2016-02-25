import com.github.javaparser.JavaParser
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.ImportDeclaration
import com.github.javaparser.ast.PackageDeclaration
import com.github.javaparser.ast.TypeParameter
import com.github.javaparser.ast.body.AnnotationMemberDeclaration
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.ConstructorDeclaration
import com.github.javaparser.ast.body.EnumConstantDeclaration
import com.github.javaparser.ast.body.EnumDeclaration
import com.github.javaparser.ast.body.FieldDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.body.MultiTypeParameter
import com.github.javaparser.ast.body.Parameter
import com.github.javaparser.ast.body.TypeDeclaration
import com.github.javaparser.ast.expr.ArrayCreationExpr
import com.github.javaparser.ast.expr.ArrayInitializerExpr
import com.github.javaparser.ast.expr.BooleanLiteralExpr
import com.github.javaparser.ast.expr.CharLiteralExpr
import com.github.javaparser.ast.expr.ClassExpr
import com.github.javaparser.ast.expr.DoubleLiteralExpr
import com.github.javaparser.ast.expr.IntegerLiteralExpr
import com.github.javaparser.ast.expr.IntegerLiteralMinValueExpr
import com.github.javaparser.ast.expr.LongLiteralExpr
import com.github.javaparser.ast.expr.LongLiteralMinValueExpr
import com.github.javaparser.ast.expr.MarkerAnnotationExpr
import com.github.javaparser.ast.expr.MemberValuePair
import com.github.javaparser.ast.expr.NormalAnnotationExpr
import com.github.javaparser.ast.expr.NullLiteralExpr
import com.github.javaparser.ast.expr.QualifiedNameExpr
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr
import com.github.javaparser.ast.expr.StringLiteralExpr
import com.github.javaparser.ast.expr.SuperExpr
import com.github.javaparser.ast.expr.ThisExpr
import com.github.javaparser.ast.expr.TypeExpr
import com.github.javaparser.ast.expr.UnaryExpr
import com.github.javaparser.ast.stmt.TypeDeclarationStmt
import com.github.javaparser.ast.type.ClassOrInterfaceType
import com.github.javaparser.ast.body.AnnotationDeclaration
import com.github.javaparser.ast.type.PrimitiveType
import com.github.javaparser.ast.type.ReferenceType
import com.github.javaparser.ast.type.VoidType
import com.github.javaparser.ast.type.WildcardType
import com.github.javaparser.ast.visitor.GenericVisitorAdapter
import org.codehaus.groovy.GroovyBugError
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.CompileUnit
import org.codehaus.groovy.ast.ConstructorNode
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.ast.GenericsType
import org.codehaus.groovy.ast.InnerClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.ModuleNode
import org.codehaus.groovy.ast.PackageNode
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.ReturnStatement
import org.codehaus.groovy.classgen.EnumVisitor
import org.codehaus.groovy.classgen.Verifier
import org.codehaus.groovy.control.AnnotationConstantsVisitor
import org.codehaus.groovy.control.CompilationFailedException
import org.codehaus.groovy.control.CompilationUnit as GCU
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.ErrorCollector
import org.codehaus.groovy.control.Phases
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.ast.Parameter as GroovyParameter
import org.codehaus.groovy.transform.ASTTransformationCollectorCodeVisitor

import java.lang.reflect.Modifier


class AstConverter2 extends GenericVisitorAdapter {

    List visit(List<Node> ln, boolean nullForEmpty=true, arg) {
        if (ln.isEmpty()) return null;
        def ret = ln.collect{
            visit( it, arg)
        }
        return ret
    }

    @Override
    def visit(AnnotationDeclaration n, arg) {
        ClassNode cn = makeClass(n.name, n.modifiers, ClassHelper.OBJECT_TYPE, ClassNode.EMPTY_ARRAY, arg)
        cn.modifiers |= 8192 |  512
        def copy = changeEnvForInnerClass(cn, arg)
        super.visit(n, copy)
    }

    def visit(x, arg) {
        assert x == null
    }

    private ClassNode makeClass(String name, int modifiers, ClassNode superclass, ClassNode[] interfaces, arg) {
        ClassNode cn
        if (arg.enclosingClass) {
            cn = new InnerClassNode(arg.enclosingClass, name, modifiers, superclass, interfaces, null)
        } else {
            cn = new ClassNode(name, modifiers, superclass, interfaces, null)
        }
        cn.module = arg.su.ast
        arg.cu.addClass(cn)
        return cn
    }

    def changeEnvForInnerClass(ClassNode cn, arg) {
        def copy = arg.clone()
        copy.currentClassNode = cn
        if (arg.currentClassNode) {
            copy.enclosingClassNode = arg.currentClassNode
        }
        return copy
    }

    @Override
    def visit(ClassOrInterfaceDeclaration n, arg) {
        int modifiers = n.modifiers
        ClassNode superclass
        ClassNode[] interfaces
        if (n.interface) {
            if (n.implements) superclass = visit(n.implements,arg)
            if (n.extends) interfaces = visit(n.extends[0],arg)
            modifiers = modifiers | 512 // set interface modifier
        } else {
            if (n.extends) superclass = visit(n.extends[0],arg)
            def ii = visit(n.implements,arg)
            if (n.implements) interfaces = ii
        }
        if (superclass==null) superclass = ClassHelper.OBJECT_TYPE
        if (interfaces==null) interfaces = ClassNode.EMPTY_ARRAY
        ClassNode cn = makeClass(n.name, modifiers, superclass, interfaces, arg)
        cn.genericsTypes = visit(n.typeParameters, arg)

        def copy = changeEnvForInnerClass(cn, arg)
        n.members.each { visit it, copy }
    }

    @Override
    def visit(ClassOrInterfaceType n, arg) {
        ClassNode cn = ClassHelper.make(n.name)
        visit(n.annotations, arg).each {cn.addAnnotation(it)}
        cn.genericsTypes = visit(n.typeArgs, arg)
        if (arg.setPlaceHolder) {
            cn.genericsPlaceHolder = n.typeArgs.empty
            cn.genericsTypes = visit(n.typeArgs, arg)
        }
        return cn
    }

    @Override
    def visit(ConstructorDeclaration n, arg) {
        ConstructorNode constructor = new ConstructorNode(n.modifiers, visit(n.parameters,arg) as GroovyParameter[], visit(n.exceptions, arg) as ClassNode[])
        arg.currentClassNode.addConstructor constructor
        constructor.genericsTypes = visit(n.typeParameters, arg)
        return constructor
    }

    @Override
    def visit(FieldDeclaration n, arg) {
        FieldNode fn = new FieldNode(n.name, n.modifiers, visit(n.type, arg), arg.currentClassNode, null)
        arg.currentClassNode.addField fn
    }

    @Override
    def visit(MethodDeclaration n, arg) {
        MethodNode mn = new MethodNode(n.name, n.modifiers, visit(n.type, arg), visit(n.parameters, arg) as GroovyParameter[], visit(n.exceptions, arg) as ClassNode[])
        arg.currentClassNode.addMethod mn
    }
    // TODO inner classes

    @Override
    def visit(EnumDeclaration n, arg) {
        int modifiers = n.modifiers
        ClassNode superclass = ClassHelper.Enum_Type
        ClassNode[] interfaces
        def ii = visit(n.implements,arg)
        if (n.implements) {
            interfaces = ii
        } else {
            interfaces= ClassNode.EMPTY_ARRAY
        }

        ClassNode cn = makeClass(n.name, modifiers, superclass, interfaces, arg)
        cn.modifiers |= 16384 | Modifier.FINAL
        def copy = changeEnvForInnerClass(cn, arg)
        n.members.each { visit it, copy }
        n.entries.each { visit it, copy }
        return cn
    }

    @Override
    def visit(ImportDeclaration n, arg) {
        ModuleNode module = su.ast
        if (n.isStatic()) {
            if (n.asterisk) {
                module.addImport n.name
            } else {
                module.addImport ClassHelper.make(n.name), null
            }
        } else {
            if (n.asterisk) {
                module.addImport ClassHelper.make(n.name)
            } else {
                def (node, name) = splitLast(n.name)
                module.addImport splitName.node, splitName.name
            }
        }
    }

    private splitLast(String nameWithDot) {
        def index = nameWithDot.lastIndexOf('.')
        return [ClassHelper.make(nameWithDot[0,index]), nameWithDot[index+1,-1]]
    }

    @Override
    def visit(CompilationUnit n, Object arg) {
        CompileUnit cu = arg.cu
        n.package?.visit(this,arg)
        n.imports?.each {this.visit(it,arg)}
        n.types?.each {this.visit(it,arg)}
    }

    @Override
    def visit(PackageDeclaration n, Object arg) {
        ModuleNode mn = arg.su.ast
        def pn = new PackageNode(n.name)
        pn.annotations = visit(n.annotations)
    }

    @Override
    def visit(TypeParameter n, Object arg) {
        ClassNode cn = ClassHelper.makeWithoutCaching(n.name)
        cn.annotations = visit(n.annotations, arg)
        cn.genericsTypes = visit(n.typeBound)
        return cn
    }

    @Override
    Object visit(EnumConstantDeclaration n, Object arg) {
        boolean hasClassBody = n.classBody
        ClassNode enumBaseClass = arg.currentClassNode
        ClassNode type = enumBaseClass
        enumBaseClass.addField(n.name, Modifier.FINAL, enumBaseClass, null)
        return type;
    }

    @Override
    Object visit(AnnotationMemberDeclaration n, Object arg) {
        ClassNode cn = arg.currentClassNode
        // TODO: Add exception here please
        MethodNode mn = cn.addMethod(n.name, n.modifiers, visit(n.type, arg), org.codehaus.groovy.ast.Parameter.EMPTY_ARRAY, ClassNode.EMPTY_ARRAY, null)
        if (n.defaultValue) {
            mn.annotationDefault = visit(n.defaultValue, arg)
            mn.code = new ReturnStatement(visit(n.defaultValue, arg))
        }
    }

    @Override
    Object visit(Parameter n, Object arg) {
        new org.codehaus.groovy.ast.Parameter(n.id.name, makeArray(visit(n.type),n.id.arrayCount))
    }

    private makeArray(ClassNode cn, int dim) {
        dim.times { cn = cn.makeArray() }
        return cn
    }

    @Override
    Object visit(MultiTypeParameter n, Object arg) {
        println n
        return null
    }

    @Override
    Object visit(PrimitiveType n, Object arg) {
        return ClassHelper.make(n.type.name())
    }

    @Override
    Object visit(ReferenceType n, Object arg) {
        makeArray(visit(n.type, arg), n.arrayCount)
    }

    @Override
    Object visit(VoidType n, Object arg) {
        ClassHelper.VOID_TYPE
    }

    @Override
    Object visit(WildcardType n, Object arg) {
        GenericsType gt = new GenericsType(placeHolder: true)
        gt.upperBounds = visit(n.super, arg)
        gt.lowerBound = visit(n.extends, arg)
        return gt
    }

    @Override
    Object visit(ArrayCreationExpr n, Object arg) {
        println n
        return null
    }

    @Override
    Object visit(ArrayInitializerExpr n, Object arg) {
        println n
        return null
    }

    @Override
    Object visit(ClassExpr n, Object arg) {
        return new ClassExpression(visit(n.type,arg))
    }

    @Override
    Object visit(StringLiteralExpr n, Object arg) {
        new ConstantExpression(n.value)
    }

    @Override
    Object visit(IntegerLiteralExpr n, Object arg) {
        new ConstantExpression(Integer.parseInt(n.value))
    }

    @Override
    Object visit(LongLiteralExpr n, Object arg) {
        new ConstantExpression(Long.parseLong(n.value))
    }

    @Override
    Object visit(IntegerLiteralMinValueExpr n, Object arg) {
        new ConstantExpression(Integer.MIN_VALUE)
    }

    @Override
    Object visit(LongLiteralMinValueExpr n, Object arg) {
        new ConstantExpression(Long.MIN_VALUE)
    }

    @Override
    Object visit(CharLiteralExpr n, Object arg) {
        new ConstantExpression(n.value)
    }

    @Override
    Object visit(BooleanLiteralExpr n, Object arg) {
        new ConstantExpression(n.value)
    }

    @Override
    Object visit(DoubleLiteralExpr n, Object arg) {
        n.value ? ConstantExpression.PRIM_TRUE : ConstantExpression.PRIM_FALSE
    }

    @Override
    Object visit(NullLiteralExpr n, Object arg) {
        ConstantExpression.NULL
    }

    @Override
    Object visit(QualifiedNameExpr n, Object arg) {
        return visit(n.qualifier,arg)+"."+n.name
    }

    @Override
    Object visit(ThisExpr n, Object arg) {
        return VariableExpression.THIS_EXPRESSION
    }

    @Override
    Object visit(SuperExpr n, Object arg) {
        return VariableExpression.SUPER_EXPRESSION
    }

    @Override
    Object visit(UnaryExpr n, Object arg) {
        println n
        return null
    }

    @Override
    Object visit(MarkerAnnotationExpr n, Object arg)  {
        def type = ClassHelper.makeWithoutCaching(visit(n.name,arg))
        return new AnnotationNode(type)
    }

    @Override
    Object visit(SingleMemberAnnotationExpr n, Object arg) {
        println n
        return null
    }

    @Override
    Object visit(NormalAnnotationExpr n, Object arg) {
        println n
        return null
    }

    @Override
    Object visit(MemberValuePair n, Object arg) {
        println n
        return null
    }

    @Override
    Object visit(TypeDeclarationStmt n, Object arg) {
        println n
        return null
    }

    @Override
    Object visit(TypeExpr n, Object arg) {
        return visit(n.type)
    }
}

class GroovyJavaJointCompiler2 extends GCU {
    GroovyJavaJointCompiler2() {
        addPhaseOperation(new GCU.SourceUnitOperation() {
            @Override
            void call(SourceUnit source) throws CompilationFailedException {
                if (!(source instanceof JavaSourceUnit)) return
                source.AST.classes.each { classNode ->
                    EnumVisitor ev = new EnumVisitor(GroovyJavaJointCompiler2.this, source);
                    ev.visitClass(classNode);
                    ASTTransformationCollectorCodeVisitor collector =
                            new ASTTransformationCollectorCodeVisitor(source, GroovyJavaJointCompiler2.this.getTransformLoader());
                    collector.visitClass(classNode);
                }
            }
        }, Phases.CONVERSION)
        addPhaseOperation(new GCU.SourceUnitOperation() {
            @Override
            void call(SourceUnit source) throws CompilationFailedException {
                if (!(source instanceof JavaSourceUnit)) return
                source.AST.classes.each { classNode ->
                    new Verifier() {
                        protected void addTimeStamp(ClassNode node) {}
                        protected void addInitialization(ClassNode node) {}
                        protected void addReturnIfNeeded(MethodNode node) {}
                        protected void addDefaultParameters(Verifier.DefaultArgsAction action, MethodNode method) {}
                    }.visitClass(classNode);
                    new AnnotationConstantsVisitor().visitClass(classNode)
                }
            }
        }, Phases.CLASS_GENERATION)
    }

    SourceUnit addSource(String name, String scriptText) {
        if (name.endsWith(".java")) {
            JavaSourceUnit jsu = new JavaSourceUnit(name, scriptText, configuration, classLoader, getErrorCollector())
            SourceUnit su = super.addSource(jsu)
            if (jsu!=su) return su;
            su.ast = new ModuleNode(su)
            this.ast.addModule(su.ast)
            return su
        } else {
            return super.addSource(name, scriptText)
        }
    }
}

class JavaSourceUnit extends SourceUnit {
    public JavaSourceUnit(String name, String source, CompilerConfiguration flags, GroovyClassLoader loader, ErrorCollector er) {
        super(name, source, flags, loader, er)
    }
    @Override
    void convert() throws CompilationFailedException {}
    @Override
    void parse() throws CompilationFailedException {
        if (this.phase > Phases.PARSING) {
            throw new GroovyBugError("parsing is already complete");
        }

        if (this.phase == Phases.INITIALIZATION) {
            nextPhase();
        }

        def javaParserCU = JavaParser.parse(this.getSource().getReader(), false)
        javaParserCU.accept(new AstConverter2(),[cu:this.ast.unit, su:this])
        nextPhase()
    }
}