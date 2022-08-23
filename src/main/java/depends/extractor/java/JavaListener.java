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

package depends.extractor.java;

import depends.entity.*;
import depends.entity.repo.EntityRepo;
import depends.extractor.java.JavaParser.*;
import depends.extractor.java.context.*;
import depends.importtypes.ExactMatchImport;
import depends.relations.Inferer;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class JavaListener extends JavaParserBaseListener {
	private final JavaHandlerContext context;
	private final AnnotationProcessor annotationProcessor;
	private final ExpressionUsage expressionUsage;
	private final EntityRepo entityRepo;

	public JavaListener(String fileFullPath, EntityRepo entityRepo,Inferer inferer) {
		this.context = new JavaHandlerContext(entityRepo,inferer);
		this.entityRepo = entityRepo;
		annotationProcessor = new AnnotationProcessor();
		expressionUsage = new ExpressionUsage(context,entityRepo);
		context.startFile(fileFullPath);
	}

	////////////////////////
	// Package
	@Override
	public void enterPackageDeclaration(PackageDeclarationContext ctx) {
		context.foundNewPackage(QualitiedNameContextHelper.getName(ctx.qualifiedName()));
		super.enterPackageDeclaration(ctx);
	}


	////////////////////////
	// Import
	@Override
	public void enterImportDeclaration(ImportDeclarationContext ctx) {
		context.foundNewImport(new ExactMatchImport(ctx.qualifiedName().getText()));
		super.enterImportDeclaration(ctx);
	}

	///////////////////////
	// Class or Interface
	// classDeclaration | enumDeclaration | interfaceDeclaration |
	/////////////////////// annotationTypeDeclaration
	@Override
	public void enterClassDeclaration(ClassDeclarationContext ctx) {
		if (ctx.IDENTIFIER()==null) return;
		TypeEntity type = context.foundNewType(GenericName.build(ctx.IDENTIFIER().getText()), ctx.getStart().getLine(), ctx.getStop().getLine());
		// implements
		if (ctx.typeList() != null) {
			for (int i = 0; i < ctx.typeList().typeType().size(); i++) {
				context.foundImplements(GenericName.build(ClassTypeContextHelper.getClassName(ctx.typeList().typeType().get(i))));
			}
		}
		// extends relation
		if (ctx.typeType() != null) {
			context.foundExtends(GenericName.build(ClassTypeContextHelper.getClassName(ctx.typeType())));
		}

		if (ctx.typeParameters() != null) {
			foundTypeParametersUse(ctx.typeParameters());
		}
		annotationProcessor.processAnnotationModifier(ctx, TypeDeclarationContext.class ,"classOrInterfaceModifier.annotation",context.lastContainer());
		if (ctx.classBody().LBRACE() != null) {
			type.setStartLine(ctx.classBody().LBRACE().getSymbol().getLine());
		}
		if (ctx.classBody().RBRACE() != null) {
			type.setEndLine(ctx.classBody().RBRACE().getSymbol().getLine());
		}
		processTypeEntity(type, ctx);
		super.enterClassDeclaration(ctx);
	}

	@Override
	public void exitClassDeclaration(ClassDeclarationContext ctx) {
		exitLastEntity();
		super.exitClassDeclaration(ctx);
	}

	@Override
	public void enterEnumDeclaration(EnumDeclarationContext ctx) {
		TypeEntity type = context.foundNewType(GenericName.build(ctx.IDENTIFIER().getText()), ctx.getStart().getLine(), ctx.getStop().getLine());
		annotationProcessor.processAnnotationModifier(ctx, TypeDeclarationContext.class ,"classOrInterfaceModifier.annotation",context.lastContainer());
		if (ctx.LBRACE() != null) {
			type.setStartLine(ctx.LBRACE().getSymbol().getLine());
		}
		if (ctx.RBRACE() != null) {
			type.setEndLine(ctx.RBRACE().getSymbol().getLine());
		}
		processTypeEntity(type, ctx);
		type.setEnum(true);
		super.enterEnumDeclaration(ctx);
	}

	@Override
	public void enterAnnotationTypeDeclaration(AnnotationTypeDeclarationContext ctx) {
		TypeEntity type = context.foundNewType(GenericName.build(ctx.IDENTIFIER().getText()), ctx.getStart().getLine(), ctx.getStop().getLine());
		annotationProcessor.processAnnotationModifier(ctx, TypeDeclarationContext.class ,"classOrInterfaceModifier.annotation",context.lastContainer());
		if (ctx.annotationTypeBody().LBRACE() != null) {
			type.setStartLine(ctx.annotationTypeBody().LBRACE().getSymbol().getLine());
		}
		if (ctx.annotationTypeBody().RBRACE() != null) {
			type.setEndLine(ctx.annotationTypeBody().RBRACE().getSymbol().getLine());
		}
		processTypeEntity(type, ctx);
		super.enterAnnotationTypeDeclaration(ctx);
	}
	
	@Override
	public void exitEnumDeclaration(EnumDeclarationContext ctx) {
		exitLastEntity();
		super.exitEnumDeclaration(ctx);
	}

	/**
	 * interfaceDeclaration : INTERFACE IDENTIFIER typeParameters? (EXTENDS
	 * typeList)? interfaceBody ;
	 */
	@Override
	public void enterInterfaceDeclaration(InterfaceDeclarationContext ctx) {
		TypeEntity type = context.foundNewType(GenericName.build(ctx.IDENTIFIER().getText()), ctx.getStart().getLine(), ctx.getStop().getLine());
		// type parameters
		if (ctx.typeParameters() != null) {
			foundTypeParametersUse(ctx.typeParameters());
		}
		// extends relation
		if (ctx.typeList() != null) {
			for (int i = 0; i < ctx.typeList().typeType().size(); i++) {
				context.foundExtends(ClassTypeContextHelper.getClassName(ctx.typeList().typeType().get(i)));
			}
		}
		annotationProcessor.processAnnotationModifier(ctx, TypeDeclarationContext.class ,"classOrInterfaceModifier.annotation",context.lastContainer());
		if (ctx.interfaceBody().LBRACE() != null) {
			type.setStartLine(ctx.interfaceBody().LBRACE().getSymbol().getLine());
		}
		if (ctx.interfaceBody().RBRACE() != null) {
			type.setEndLine(ctx.interfaceBody().RBRACE().getSymbol().getLine());
		}
		processTypeEntity(type, ctx);
		type.setInterface(true);
		super.enterInterfaceDeclaration(ctx);
	}

	@Override
	public void exitInterfaceDeclaration(InterfaceDeclarationContext ctx) {
		exitLastEntity();
		super.exitInterfaceDeclaration(ctx);
	}



	@Override
	public void exitAnnotationTypeDeclaration(AnnotationTypeDeclarationContext ctx) {
		exitLastEntity();
		super.exitAnnotationTypeDeclaration(ctx);
	}

	/////////////////////////
	// Method
	@Override
	public void enterMethodDeclaration(MethodDeclarationContext ctx) {
		List<String> throwedType = QualitiedNameContextHelper.getNames(ctx.qualifiedNameList());
		String methodName = ctx.IDENTIFIER().getText();
		String returnedType = ClassTypeContextHelper.getClassName(ctx.typeTypeOrVoid());
		FunctionEntity method = context.foundMethodDeclarator(methodName, returnedType, throwedType,ctx.getStart().getLine());
		new FormalParameterListContextHelper(ctx.formalParameters(), method, entityRepo);
		if (ctx.typeParameters() != null) {
			List<GenericName> parameters = TypeParameterContextHelper.getTypeParameters(ctx.typeParameters());
			method.addTypeParameter(parameters);
		}
		annotationProcessor.processAnnotationModifier(ctx, ClassBodyDeclarationContext.class,"modifier.classOrInterfaceModifier.annotation",context.lastContainer());
		super.enterMethodDeclaration(ctx);
		
		BlockContext block = ctx.methodBody().block();
		if(block != null) {
//			method.setStartLine(block.start.getLine());
			method.setEndLine(block.stop.getLine());
		} else {
//			method.setStartLine(ctx.start.getLine());
			method.setEndLine(ctx.stop.getLine());
		}
		processFunctionEntity(method, ctx);
	}

	@Override
	public void exitMethodDeclaration(MethodDeclarationContext ctx) {
		exitLastEntity();
		super.exitMethodDeclaration(ctx);
	}

	private void exitLastEntity() {
		context.exitLastedEntity();
	}

//	interfaceMethodDeclaration
//    : interfaceMethodModifier* (typeTypeOrVoid | typeParameters annotation* typeTypeOrVoid)
//      IDENTIFIER formalParameters ('[' ']')* (THROWS qualifiedNameList)? methodBody

	@Override
	public void enterInterfaceMethodDeclaration(InterfaceMethodDeclarationContext ctx) {
		List<String> throwedType = QualitiedNameContextHelper.getNames(ctx.qualifiedNameList());
		FunctionEntity method = context.foundMethodDeclarator(ctx.IDENTIFIER().getText(),
				ClassTypeContextHelper.getClassName(ctx.typeTypeOrVoid()), throwedType,ctx.getStart().getLine());
		new FormalParameterListContextHelper(ctx.formalParameters(), method, entityRepo);
		if (ctx.typeParameters() != null) {
			foundTypeParametersUse(ctx.typeParameters());
		}
		annotationProcessor.processAnnotationModifier(ctx, InterfaceBodyDeclarationContext.class,"modifier.classOrInterfaceModifier.annotation",context.lastContainer());
		super.enterInterfaceMethodDeclaration(ctx);
		
		BlockContext block = ctx.methodBody().block();
		if(block != null) {
//			method.setStartLine(block.start.getLine());
			method.setEndLine(block.stop.getLine());
		} else {
//			method.setStartLine(ctx.start.getLine());
			method.setEndLine(ctx.stop.getLine());
			// 接口的抽象方法，默认使用abstract
			method.setAbstract(true);
		}
		processFunctionEntity(method, ctx);
	}

	@Override
	public void exitInterfaceMethodDeclaration(InterfaceMethodDeclarationContext ctx) {
		exitLastEntity();
		super.exitInterfaceMethodDeclaration(ctx);
	}

	@Override
	public void enterConstructorDeclaration(ConstructorDeclarationContext ctx) {
		List<String> throwedType = QualitiedNameContextHelper.getNames(ctx.qualifiedNameList());
		FunctionEntity method = context.foundMethodDeclarator(ctx.IDENTIFIER().getText(), ctx.IDENTIFIER().getText(),
				throwedType,ctx.getStart().getLine());
		new FormalParameterListContextHelper(ctx.formalParameters(), method, entityRepo);
		method.addReturnType(context.currentType());
		annotationProcessor.processAnnotationModifier(ctx, ClassBodyDeclarationContext.class,"modifier.classOrInterfaceModifier.annotation",context.lastContainer());
		super.enterConstructorDeclaration(ctx);
		
		BlockContext block = ctx.block();
		if(block != null) {
//			method.setStartLine(block.start.getLine());
			method.setEndLine(block.stop.getLine());
		} else {
//			method.setStartLine(ctx.start.getLine());
			method.setEndLine(ctx.stop.getLine());
		}
		processFunctionEntity(method, ctx);
	}

	@Override
	public void exitConstructorDeclaration(ConstructorDeclarationContext ctx) {
		exitLastEntity();
		super.exitConstructorDeclaration(ctx);
	}

	/////////////////////////////////////////////////////////
	// Field
	@Override
	public void enterFieldDeclaration(FieldDeclarationContext ctx) {
		List<String> varNames = VariableDeclaratorsContextHelper.getVariables(ctx.variableDeclarators());
		String type = ClassTypeContextHelper.getClassName(ctx.typeType());
		List<GenericName> typeArguments = ClassTypeContextHelper.getTypeArguments(ctx.typeType());
		List<VarEntity> vars = context.foundVarDefinitions(varNames, type,typeArguments,ctx.getStart().getLine(), true);
		for(VarEntity var : vars) {
			if(ctx.getStop() != null) {
				var.setEndLine(ctx.getStop().getLine());
			} else {
				var.setEndLine(ctx.getStart().getLine());
			}
			processVariableEntity(var, ctx);
		}
		annotationProcessor.processAnnotationModifier(ctx, ClassBodyDeclarationContext.class,"modifier.classOrInterfaceModifier.annotation",vars);
		super.enterFieldDeclaration(ctx);
	}

	@Override
	public void exitFieldDeclaration(FieldDeclarationContext ctx) {
		exitLastEntity();
		super.exitFieldDeclaration(ctx);
	}

	@Override
	public void enterConstDeclaration(ConstDeclarationContext ctx) {
		List<GenericName> typeArguments = ClassTypeContextHelper.getTypeArguments(ctx.typeType());
		List<VarEntity> vars = context.foundVarDefinitions(VariableDeclaratorsContextHelper.getVariables(ctx.constantDeclarator()),
				ClassTypeContextHelper.getClassName(ctx.typeType()),typeArguments, ctx.getStart().getLine(), false);
		for(VarEntity var : vars) {
			if(ctx.getStop() != null) {
				var.setEndLine(ctx.getStop().getLine());
			} else {
				var.setEndLine(ctx.getStart().getLine());
			}
			processVariableEntity(var, ctx);
		}
		annotationProcessor.processAnnotationModifier(ctx, InterfaceBodyDeclarationContext.class,"modifier.classOrInterfaceModifier.annotation",vars);
		super.enterConstDeclaration(ctx);
	}

	@Override
	public void enterEnumConstant(EnumConstantContext ctx) {
		if (ctx.IDENTIFIER() != null) {
			VarEntity var = context.foundEnumConstDefinition(ctx.IDENTIFIER().getText(),ctx.getStart().getLine());
			if(ctx.getStop() != null) {
				var.setEndLine(ctx.getStop().getLine());
			} else {
				var.setEndLine(ctx.getStart().getLine());
			}
		}
		super.enterEnumConstant(ctx);
	}

	@Override
	public void enterAnnotationMethodRest(AnnotationMethodRestContext ctx) {
		context.foundMethodDeclarator(ctx.IDENTIFIER().getText(), ClassTypeContextHelper.getClassName(ctx.typeType()),
				new ArrayList<>(),ctx.getStart().getLine());
		super.enterAnnotationMethodRest(ctx);
	}

	@Override
	public void exitAnnotationMethodRest(AnnotationMethodRestContext ctx) {
		exitLastEntity();
		super.exitAnnotationMethodRest(ctx);
	}

	@Override
	public void enterAnnotationConstantRest(AnnotationConstantRestContext ctx) {
		// TODO: no variable type defined in annotation const？
		List<VarEntity> vars = context.foundVarDefinitions(VariableDeclaratorsContextHelper.getVariables(ctx.variableDeclarators()), "", new ArrayList<>(), ctx.getStart().getLine(), false);
		for(VarEntity var : vars) {
			if(ctx.getStop() != null) {
				var.setEndLine(ctx.getStop().getLine());
			} else {
				var.setEndLine(ctx.getStart().getLine());
			}
		}
		super.enterAnnotationConstantRest(ctx);
	}

	///////////////////////////////////////////
	// variables
	// TODO: all modifier have not processed yet.
	@Override
	public void enterLocalVariableDeclaration(LocalVariableDeclarationContext ctx) {
		List<GenericName> typeArguments = ClassTypeContextHelper.getTypeArguments(ctx.typeType());
		List<VarEntity> vars = context.foundVarDefinitions(VariableDeclaratorsContextHelper.getVariables((ctx.variableDeclarators())),
				ClassTypeContextHelper.getClassName(ctx.typeType()), typeArguments, ctx.getStart().getLine(), false);
		for(VarEntity var : vars) {
			if(ctx.getStop() != null) {
				var.setEndLine(ctx.getStop().getLine());
			} else {
				var.setEndLine(ctx.getStart().getLine());
			}
		}
		super.enterLocalVariableDeclaration(ctx);
	}

	// for循环中的变量，如for(Node node : nodes)中的node
	public void enterEnhancedForControl(EnhancedForControlContext ctx) {
		List<GenericName> typeArguments = ClassTypeContextHelper.getTypeArguments(ctx.typeType());
		List<VarEntity> vars = context.foundVarDefinitions(VariableDeclaratorsContextHelper.getVariable((ctx.variableDeclaratorId())),
				ClassTypeContextHelper.getClassName(ctx.typeType()), typeArguments, ctx.getStart().getLine(), false);
		for(VarEntity var : vars) {
			if(ctx.getStop() != null) {
				var.setEndLine(ctx.getStop().getLine());
			} else {
				var.setEndLine(ctx.getStart().getLine());
			}
		}
		super.enterEnhancedForControl(ctx);
	}

//	resource
//    : variableModifier* classOrInterfaceType variableDeclaratorId '=' expression
//    ;
	@Override
	public void enterResource(ResourceContext ctx) {
		List<GenericName> typeArguments = ClassTypeContextHelper.getTypeArguments(ctx.classOrInterfaceType());
		VarEntity var = context.foundVarDefinition(ctx.variableDeclaratorId().IDENTIFIER().getText(),
				GenericName.build(IdentifierContextHelper.getName(ctx.classOrInterfaceType().IDENTIFIER())), typeArguments,ctx.getStart().getLine());
		if(ctx.getStop() != null) {
			var.setEndLine(ctx.getStop().getLine());
		} else {
			var.setEndLine(ctx.getStart().getLine());
		}
		super.enterResource(ctx);
	}

	@Override
	public void enterExpression(ExpressionContext ctx) {
		Expression expr = expressionUsage.foundExpression(ctx);
		expr.setStartLine(ctx.getStart().getLine());
		super.enterExpression(ctx);
	}

	/////////////////////////////////////////////
	// Block
	@Override
	public void enterBlock(BlockContext ctx) {
		// TODO support block in java
		ParserRuleContext parent = ctx.getParent();
		if(parent instanceof ClassBodyDeclarationContext) {
			ParseTree child0 = parent.getChild(0);
			if(child0.toString().equals("static")) {
				BlockEntity blockEntity = context.foundNewBlock(new GenericName("staticblock" + ctx.getStart().getLine()),true);
				blockEntity.setStartLine(ctx.getStart().getLine());
				blockEntity.setEndLine(ctx.getStop().getLine());
			}
		}
		super.enterBlock(ctx);
	}

	@Override
	public void exitBlock(BlockContext ctx) {
		// TODO support block in java
		super.exitBlock(ctx);
	}

	/* type parameters <T> <T1,T2>, <> treat as USE */
	private void foundTypeParametersUse(TypeParametersContext typeParameters) {
		for (int i = 0; i < typeParameters.typeParameter().size(); i++) {
			TypeParameterContext typeParam = typeParameters.typeParameter(i);
			if (typeParam.typeBound() != null) {
				for (int j = 0; j < typeParam.typeBound().typeType().size(); j++) {
					context.foundTypeParametes(GenericName.build(ClassTypeContextHelper.getClassName(typeParam.typeBound().typeType(j))));
				}
			}
			context.currentType().addTypeParameter(GenericName.build(typeParam.IDENTIFIER().getText()));
		}
	}

	public void done() {
		context.done();
	}

	private void processTypeEntity(TypeEntity type, RuleContext ctx) {
		Set<String> rootClassSet = new HashSet<>();
		rootClassSet.add("TypeDeclarationContext");
		rootClassSet.add("LocalTypeDeclarationContext");
		rootClassSet.add("InterfaceBodyDeclarationContext");
		rootClassSet.add("ClassBodyDeclarationContext");
		while (true) {
			if (ctx == null)
				break;
			if (rootClassSet.contains(ctx.getClass().getSimpleName()))
				break;
			ctx = ctx.parent;
		}
		if (ctx != null) {
			List<ClassOrInterfaceModifierContext> modifierContextList = new ArrayList<>();
			switch (ctx.getClass().getSimpleName()) {
				case "TypeDeclarationContext":
					modifierContextList = ((TypeDeclarationContext) ctx).classOrInterfaceModifier();
					break;
				case "LocalTypeDeclarationContext":
					modifierContextList = ((LocalTypeDeclarationContext) ctx).classOrInterfaceModifier();
					break;
				case "InterfaceBodyDeclarationContext":
					for (ModifierContext modifierContext : ((InterfaceBodyDeclarationContext) ctx).modifier()) {
						if (modifierContext.classOrInterfaceModifier() != null) {
							modifierContextList.add(modifierContext.classOrInterfaceModifier());
						}
					}
					// 接口中的内部类（包括普通类、接口、枚举类）都默认为public、static
					type.setAccessModifier("public");
					type.setStatic(true);
					break;
				case "ClassBodyDeclarationContext":
					for (ModifierContext modifierContext : ((ClassBodyDeclarationContext) ctx).modifier()) {
						if (modifierContext.classOrInterfaceModifier() != null) {
							modifierContextList.add(modifierContext.classOrInterfaceModifier());
						}
					}
					break;
			}
			for (ClassOrInterfaceModifierContext modifierContext : modifierContextList) {
				if (modifierContext == null) {
					System.out.println("here");
				}
				String modifier = modifierContext.getText();
				if (modifier.equals("public") || modifier.equals("private") || modifier.equals("protected")) {
					type.setAccessModifier(modifier);
				} else {
					switch (modifier) {
						case "abstract":
							type.setAbstract(true);
							break;
						case "static":
							type.setStatic(true);
							break;
						case "final":
							type.setFinal(true);
							break;
					}
				}
			}
		}
	}

	private void processFunctionEntity(FunctionEntity function, RuleContext ctx) {
		Set<String> rootClassSet = new HashSet<>();
		rootClassSet.add("InterfaceBodyDeclarationContext");
		rootClassSet.add("ClassBodyDeclarationContext");
		while (true) {
			if (ctx == null)
				break;
			if (rootClassSet.contains(ctx.getClass().getSimpleName()))
				break;
			ctx = ctx.parent;
		}
		if (ctx != null) {
			List<ClassOrInterfaceModifierContext> modifierContextList = new ArrayList<>();
			switch (ctx.getClass().getSimpleName()) {
				case "InterfaceBodyDeclarationContext":
					for (ModifierContext modifierContext : ((InterfaceBodyDeclarationContext) ctx).modifier()) {
						if (modifierContext.classOrInterfaceModifier() != null) {
							modifierContextList.add(modifierContext.classOrInterfaceModifier());
						}
					}
					// 接口中除了私有实例方法和私有类方法，其他方法均默认使用public修饰，即使是default方法，该方法为有方法体的实例方法，而不是表示该方法的访问权限为default
					// 若为私有方法，则当前的public会被private覆盖掉，若为default方法，则不需要改变，因为并不对default进行处理
					function.setAccessModifier("public");
					break;
				case "ClassBodyDeclarationContext":
					for (ModifierContext modifierContext : ((ClassBodyDeclarationContext) ctx).modifier()) {
						if (modifierContext.classOrInterfaceModifier() != null) {
							modifierContextList.add(modifierContext.classOrInterfaceModifier());
						}
					}
					break;
			}
			for (ClassOrInterfaceModifierContext modifierContext : modifierContextList) {
				if (modifierContext == null) {
					System.out.println("here");
				}
				String modifier = modifierContext.getText();
				if (modifier.equals("public") || modifier.equals("private") || modifier.equals("protected")) {
					function.setAccessModifier(modifier);
				} else {
					switch (modifier) {
						case "abstract":
							function.setAbstract(true);
							break;
						case "static":
							function.setStatic(true);
							break;
						case "final":
							function.setFinal(true);
							break;
					}
				}
			}
		}
	}

	private void processVariableEntity(VarEntity variable, RuleContext ctx) {
		Set<String> rootClassSet = new HashSet<>();
		rootClassSet.add("InterfaceBodyDeclarationContext");
		rootClassSet.add("ClassBodyDeclarationContext");
		while (true) {
			if (ctx == null)
				break;
			if (rootClassSet.contains(ctx.getClass().getSimpleName()))
				break;
			ctx = ctx.parent;
		}
		if (ctx != null) {
			List<ClassOrInterfaceModifierContext> modifierContextList = new ArrayList<>();
			switch (ctx.getClass().getSimpleName()) {
				case "InterfaceBodyDeclarationContext":
					for (ModifierContext modifierContext : ((InterfaceBodyDeclarationContext) ctx).modifier()) {
						if (modifierContext.classOrInterfaceModifier() != null) {
							modifierContextList.add(modifierContext.classOrInterfaceModifier());
						}
					}
					// 接口中的变量只能为静态成员变量，默认public、static、final
					variable.setAccessModifier("public");
					variable.setStatic(true);
					variable.setFinal(true);
					break;
				case "ClassBodyDeclarationContext":
					for (ModifierContext modifierContext : ((ClassBodyDeclarationContext) ctx).modifier()) {
						if (modifierContext.classOrInterfaceModifier() != null) {
							modifierContextList.add(modifierContext.classOrInterfaceModifier());
						}
					}
					break;
			}
			for (ClassOrInterfaceModifierContext modifierContext : modifierContextList) {
				if (modifierContext == null) {
					System.out.println("here");
				}
				String modifier = modifierContext.getText();
				if (modifier.equals("public") || modifier.equals("private") || modifier.equals("protected")) {
					variable.setAccessModifier(modifier);
				} else {
					switch (modifier) {
						case "static":
							variable.setStatic(true);
							break;
						case "final":
							variable.setFinal(true);
							break;
					}
				}
			}
		}
	}
}
