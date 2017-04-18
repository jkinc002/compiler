package visitor;
import syntaxtree.*;
import java.util.*;
import java.util.Stack;
import java.util.HashMap;
import java.util.Vector;

public class TypecheckVisitor<R extends String,A> extends GJDepthFirst<R,A> {
	private class VarOrMethod {
		String type;
		Vector<String> params;
		boolean isMethod;

		VarOrMethod(String t, Vector<String> p, boolean isMeth){
			type = t;
			params = p;
			isMethod = isMeth;
		}
	}
     Stack<HashMap<String,String>> symbolTable = null;
	 HashMap<String,String> inheritanceMap= null;
     HashMap<String,HashMap<String,VarOrMethod>> fieldMap = null;

	public VarOrMethod newVar(String t){
		return new VarOrMethod(t, new Vector<String>(), false);
	}
	public VarOrMethod newMethod(String t, Vector<String> p){
		return new VarOrMethod(t, p, true);
	}

	 public void printMap(Map<String,String> m){
		for(Map.Entry<String,String> entry : m.entrySet()) {
			System.out.println("Item: " + entry.getKey() + " Type: " + entry.getValue());
			}
			System.out.println(m.size());
	 }
	 public void printMethodParams(Vector<String> v){
		for(String s : v){
			System.out.println("\t\t" + s + ", ");
		}
	 }
	 public void printFieldMap()
	 {
	 	for (Map.Entry<String,HashMap<String,VarOrMethod>> classEntry : fieldMap.entrySet())
		{
			System.out.println("Class " + classEntry.getKey() + ": ");
			for (Map.Entry<String,VarOrMethod> fieldEntry: classEntry.getValue().entrySet())
			{
				VarOrMethod varmeth = fieldEntry.getValue();
				System.out.println("\t name: " + fieldEntry.getKey() + ", type: " + varmeth.type + ", ismethod: " + varmeth.isMethod);
				printMethodParams(varmeth.params);
			}
		}
	 }
	
	 private String getTypeString(Type type){
		if(type.f0.which == 0){
			return "Integer[]";
		}
		else if(type.f0.which == 1){
			return "Boolean";
		}
		else if(type.f0.which == 2){
			return "Integer";
		}
		else{
			Identifier identNode = (Identifier)type.f0.choice;
			return identNode.f0.tokenImage;
		}
	}

	private void addVarDeclarations (Map<String,String> m, NodeListOptional nodeList){
		for (int j = 0; j < nodeList.size(); ++j)
		{
			VarDeclaration varNode = (VarDeclaration)nodeList.elementAt(j);
			if(m.containsKey(varNode.f1.f0.tokenImage)){
				System.err.println("ERROR: duplicate variables declared in same scope");
			}
			m.put(varNode.f1.f0.tokenImage, getTypeString(varNode.f0));
		}
	}
	private void addMethodDeclarations (Map<String,String> m, NodeListOptional nodeList){
		for (int j = 0; j < nodeList.size(); ++j)
		{
			MethodDeclaration varNode = (MethodDeclaration)nodeList.elementAt(j);
			if(m.containsKey(varNode.f2.f0.tokenImage)){
				System.err.println("ERROR: duplicate methods declared in same scope");
			}
			m.put(varNode.f2.f0.tokenImage, "method");
		}
	}

     public R visit(Goal n, A argu){
	   symbolTable = new Stack<HashMap<String,String>>();
	   inheritanceMap = new HashMap<String,String>();
   		fieldMap = new HashMap<String,HashMap<String,VarOrMethod>>();
	   // FIXME: make sure parent exists
	   // FIXME: make sure parent does not ultimately inherit the child (inheritance loop)
	   inheritanceMap.put("Object", "Object");

       R _ret = null;
	   HashMap<String,String> classMap = new HashMap<String,String>();

	   // Our main class
	   classMap.put(n.f0.f1.f0.tokenImage,"Class");
	   inheritanceMap.put(n.f0.f1.f0.tokenImage, "Object");
	   //put main class in field map
	   fieldMap.put(n.f0.f1.f0.tokenImage,new HashMap<String,VarOrMethod>());
	   //build field map
	   for(int i = 0; i < n.f1.size(); ++i){
		 HashMap<String,VarOrMethod> currFields = new HashMap<String,VarOrMethod>();
	     TypeDeclaration temp = (TypeDeclaration) n.f1.elementAt(i);
	   		String tokenImage;
			String parent;
			NodeListOptional varFields;
		    NodeListOptional methFields;
			if(temp.f0.which == 0){
	     		ClassDeclaration castedNode = (ClassDeclaration)temp.f0.choice;
	   	 		tokenImage = castedNode.f1.f0.tokenImage;
				parent = "Object";
				varFields = castedNode.f3;
				methFields = castedNode.f4;	
	   		}
	   		else {
	    		ClassExtendsDeclaration castedNode = (ClassExtendsDeclaration)temp.f0.choice;
	   	 		tokenImage = castedNode.f1.f0.tokenImage;
				parent = castedNode.f3.f0.tokenImage;
				varFields = castedNode.f5;
				methFields = castedNode.f6;	
			}
			//check for duplicate class declarations
			if (classMap.containsKey(tokenImage))
			{
				System.err.println("ERROR: Duplicate classes declared");	
				System.exit(1);
			}
			classMap.put(tokenImage,"Class");
			inheritanceMap.put(tokenImage,parent);
			//Loop adds var declarations to field map
			for (int j = 0; j < varFields.size(); ++j)
			{
				VarDeclaration varNode = (VarDeclaration)varFields.elementAt(j);
			    currFields.put(varNode.f1.f0.tokenImage, newVar(getTypeString(varNode.f0)));
			}
			//Loop adds method declarations to field map
		    for (int k = 0; k < methFields.size(); ++k)
			{
				MethodDeclaration methNode = (MethodDeclaration)methFields.elementAt(k);
				//check for duplicate method declarations
				if(currFields.containsKey(methNode.f2.f0.tokenImage)){
					System.err.println("ERROR: duplicate methods declared");
					System.exit(1);
				}
				String methodType = getTypeString(methNode.f1);
				Vector<String> methodParams = new Vector<String>();
				if(methNode.f4.present()){
				    FormalParameterList paramListNode = (FormalParameterList)methNode.f4.node;
					methodParams.add(getTypeString(paramListNode.f0.f0));
					
					for(int l = 0; l < paramListNode.f1.size(); ++l){
						FormalParameterRest paramNode = (FormalParameterRest)paramListNode.f1.elementAt(l);
						methodParams.add(getTypeString(paramNode.f1.f0));
					}
					
				}
				currFields.put(methNode.f2.f0.tokenImage,newMethod(methodType, methodParams));
			}
			//printMap(currFields);
			fieldMap.put(tokenImage,currFields);
	   }
	   symbolTable.push(classMap);
	   n.f0.accept(this, argu);
	   n.f1.accept(this, argu);
	   n.f2.accept(this, argu);
       return _ret;
     }   
 /**
    * f0 -> "class"
    * f1 -> Identifier()
    * f2 -> "{"
    * f3 -> "public"
    * f4 -> "static"
    * f5 -> "void"
    * f6 -> "main"
    * f7 -> "("
    * f8 -> "String"
    * f9 -> "["
    * f10 -> "]"
    * f11 -> Identifier()
    * f12 -> ")"
    * f13 -> "{"
    * f14 -> ( VarDeclaration() )*
    * f15 -> ( Statement() )*
    * f16 -> "}"
    * f17 -> "}"
    */

	public R visit(MainClass n, A argu){
		HashMap<String, String> scope = new HashMap<String,String>();
		scope.put(n.f11.f0.tokenImage, "String[]");
		addVarDeclarations(scope, n.f14);
	    symbolTable.push(scope);	

		R _ret=null;
		n.f0.accept(this, argu);
		n.f1.accept(this, argu);
		n.f2.accept(this, argu);
		n.f3.accept(this, argu);
		n.f4.accept(this, argu);
		n.f5.accept(this, argu);
		n.f6.accept(this, argu);
		n.f7.accept(this, argu);
		n.f8.accept(this, argu);
		n.f9.accept(this, argu);
		n.f10.accept(this, argu);
		n.f11.accept(this, argu);
		n.f12.accept(this, argu);
		n.f13.accept(this, argu);
		n.f14.accept(this, argu);
		n.f15.accept(this, argu);
		n.f16.accept(this, argu);
		n.f17.accept(this, argu);

		symbolTable.pop();
		return _ret;
	}

 	/** 
    * f0 -> "class"
    * f1 -> Identifier()
    * f2 -> "{"
    * f3 -> ( VarDeclaration() )*
    * f4 -> ( MethodDeclaration() )*
    * f5 -> "}"
    */
	public R visit(ClassDeclaration n, A argu) {
		HashMap<String, String> scope = new HashMap<String,String>();
		addVarDeclarations(scope, n.f3);
		addMethodDeclarations(scope, n.f4);
		//printMap(scope);
		symbolTable.push(scope);

      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      n.f3.accept(this, argu);
      n.f4.accept(this, argu);
      n.f5.accept(this, argu);

		symbolTable.pop();

      return _ret;
   }   
   /**
    * f0 -> "class"
    * f1 -> Identifier()
    * f2 -> "extends"
    * f3 -> Identifier()
    * f4 -> "{"
    * f5 -> ( VarDeclaration() )*
    * f6 -> ( MethodDeclaration() )*
    * f7 -> "}"
    */
   public R visit(ClassExtendsDeclaration n, A argu) {
		HashMap<String, String> scope = new HashMap<String,String>();
		addVarDeclarations(scope, n.f5);
		addMethodDeclarations(scope, n.f6);
		//printMap(scope);
		symbolTable.push(scope);

      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      n.f3.accept(this, argu);
      n.f4.accept(this, argu);
      n.f5.accept(this, argu);
      n.f6.accept(this, argu);
      n.f7.accept(this, argu);

		symbolTable.pop();

      return _ret;
   }

   /**
    * f0 -> Type()
    * f1 -> Identifier()
    * f2 -> ";"
    */
   public R visit(VarDeclaration n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> "public"
    * f1 -> Type()
    * f2 -> Identifier()
    * f3 -> "("
    * f4 -> ( FormalParameterList() )?
    * f5 -> ")"
    * f6 -> "{"
    * f7 -> ( VarDeclaration() )*
    * f8 -> ( Statement() )*
    * f9 -> "return"
    * f10 -> Expression()
    * f11 -> ";"
    * f12 -> "}"
    */
   public R visit(MethodDeclaration n, A argu) {
		HashMap<String, String> scope = new HashMap<String,String>();
		addVarDeclarations(scope, n.f7);

		if(n.f4.present()){
			FormalParameterList paramListNode = (FormalParameterList)n.f4.node;
			scope.put(paramListNode.f0.f1.f0.tokenImage, getTypeString(paramListNode.f0.f0));
			
			for(int l = 0; l < paramListNode.f1.size(); ++l){
				FormalParameterRest paramNode = (FormalParameterRest)paramListNode.f1.elementAt(l);
				scope.put(paramNode.f1.f1.f0.tokenImage, getTypeString(paramNode.f1.f0));
			}
			
		}

		symbolTable.push(scope);

      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      n.f3.accept(this, argu);
      n.f4.accept(this, argu);
      n.f5.accept(this, argu);
      n.f6.accept(this, argu);
      n.f7.accept(this, argu);
      n.f8.accept(this, argu);
      n.f9.accept(this, argu);
      n.f10.accept(this, argu);
      n.f11.accept(this, argu);
      n.f12.accept(this, argu);

		symbolTable.pop();
      return _ret;
   }

   /**
    * f0 -> FormalParameter()
    * f1 -> ( FormalParameterRest() )*
    */
   public R visit(FormalParameterList n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> Type()
    * f1 -> Identifier()
    */
   public R visit(FormalParameter n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> ","
    * f1 -> FormalParameter()
    */
   public R visit(FormalParameterRest n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> ArrayType()
    *       | BooleanType()
    *       | IntegerType()
    *       | Identifier()
    */
   public R visit(Type n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> "int"
    * f1 -> "["
    * f2 -> "]"
    */
   public R visit(ArrayType n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> "boolean"
    */
   public R visit(BooleanType n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> "int"
    */
   public R visit(IntegerType n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> Block()
    *       | AssignmentStatement()
    *       | ArrayAssignmentStatement()
    *       | IfStatement()
    *       | WhileStatement()
    *       | PrintStatement()
    */
   public R visit(Statement n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> "{"
    * f1 -> ( Statement() )*
    * f2 -> "}"
    */
   public R visit(Block n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> Identifier()
    * f1 -> "="
    * f2 -> Expression()
    * f3 -> ";"
    */
   public R visit(AssignmentStatement n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      R rhsType = n.f2.accept(this, argu);
      n.f3.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> Identifier()
    * f1 -> "["
    * f2 -> Expression()
    * f3 -> "]"
    * f4 -> "="
    * f5 -> Expression()
    * f6 -> ";"
    */
   public R visit(ArrayAssignmentStatement n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      n.f3.accept(this, argu);
      n.f4.accept(this, argu);
      n.f5.accept(this, argu);
      n.f6.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> "if"
    * f1 -> "("
    * f2 -> Expression()
    * f3 -> ")"
    * f4 -> Statement()
    * f5 -> "else"
    * f6 -> Statement()
    */
   public R visit(IfStatement n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      n.f3.accept(this, argu);
      n.f4.accept(this, argu);
      n.f5.accept(this, argu);
      n.f6.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> "while"
    * f1 -> "("
    * f2 -> Expression()
    * f3 -> ")"
    * f4 -> Statement()
    */
   public R visit(WhileStatement n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      n.f3.accept(this, argu);
      n.f4.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> "System.out.println"
    * f1 -> "("
    * f2 -> Expression()
    * f3 -> ")"
    * f4 -> ";"
    */
   public R visit(PrintStatement n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      n.f3.accept(this, argu);
      n.f4.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> AndExpression()
    *       | CompareExpression()
    *       | PlusExpression()
    *       | MinusExpression()
    *       | TimesExpression()
    *       | ArrayLookup()
    *       | ArrayLength()
    *       | MessageSend()
    *       | PrimaryExpression()
    */
   public R visit(Expression n, A argu) {
      R _ret=null;
      _ret = n.f0.accept(this, argu);
      return _ret;
   }

   private void typeCheckBinExpr(Node n0,Node n1,Node n2, A argu, R t, String errmsg)
   {
       R lhsType = n0.accept(this, argu);
	   n1.accept(this, argu);
       R rhsType = n2.accept(this, argu);

	   if (!lhsType.equals(t) || !rhsType.equals(t))
	   {
	       System.err.println("ERROR: " + errmsg);
		   System.exit(1);
	   }
   }


   /**
    * f0 -> PrimaryExpression()
    * f1 -> "&&"
    * f2 -> PrimaryExpression()
    */
   public R visit(AndExpression n, A argu) {
	  typeCheckBinExpr(n.f0,n.f1,n.f2, argu, (R) "Boolean", "Expected boolean types for operator &&");
	  return (R)"Boolean";
   }

   /**
    * f0 -> PrimaryExpression()
    * f1 -> "<"
    * f2 -> PrimaryExpression()
    */
   public R visit(CompareExpression n, A argu) {
      typeCheckBinExpr(n.f0,n.f1,n.f2, argu, (R) "Integer", "Expected Integer types for operator <");
	  return (R)"Boolean";
   }

   /**
    * f0 -> PrimaryExpression()
    * f1 -> "+"
    * f2 -> PrimaryExpression()
    */
   public R visit(PlusExpression n, A argu) {
      typeCheckBinExpr(n.f0,n.f1,n.f2, argu, (R) "Integer", "Expected Integer types for operator +");
	  return (R)"Integer";
   }

   /**
    * f0 -> PrimaryExpression()
    * f1 -> "-"
    * f2 -> PrimaryExpression()
    */
   public R visit(MinusExpression n, A argu) {
      typeCheckBinExpr(n.f0,n.f1,n.f2, argu, (R) "Integer", "Expected Integer types for operator -");
	  return (R)"Integer";
   }

   /**
    * f0 -> PrimaryExpression()
    * f1 -> "*"
    * f2 -> PrimaryExpression()
    */
   public R visit(TimesExpression n, A argu) {
      typeCheckBinExpr(n.f0,n.f1,n.f2, argu, (R) "Integer", "Expected Integer types for operator *");
	  return (R)"Integer";

   }

   /**
    * f0 -> PrimaryExpression()
    * f1 -> "["
    * f2 -> PrimaryExpression()
    * f3 -> "]"
    */
   public R visit(ArrayLookup n, A argu) {
      R _ret=null;
      R lhsType = n.f0.accept(this, argu);
	  if(!lhsType.equals("Integer[]")){
		System.err.println("ERROR: expected type Integer[] in lhs of ArrayLookup");
		System.exit(1);
	  }
      n.f1.accept(this, argu);
      R rhsType = n.f2.accept(this, argu);
	  if(!rhsType.equals("Integer")){
		System.err.println("ERROR: expected type Integer in rhs of ArrayLookup");
		System.exit(1);
	  }
      n.f3.accept(this, argu);
      return (R)"Integer";
   }

   /**
    * f0 -> PrimaryExpression()
    * f1 -> "."
    * f2 -> "length"
    */
   public R visit(ArrayLength n, A argu) {
      R _ret=null;
      R lhsType = n.f0.accept(this, argu);
	  if(!lhsType.equals("Integer[]")){
		System.err.println("ERROR: expected type Integer[] in lhs of ArrayLength");
		System.exit(1);
	  }
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      return (R)"Integer";
   }

   /**
    * f0 -> PrimaryExpression()
    * f1 -> "."
    * f2 -> Identifier()
    * f3 -> "("
    * f4 -> ( ExpressionList() )?
    * f5 -> ")"
    */
   public R visit(MessageSend n, A argu) {
      R _ret=null;

      R classType = n.f0.accept(this, argu);

	  if(!fieldMap.containsKey(classType)){
		System.err.println("ERROR: primary expression is not a valid type in MessageSend statement");
		System.exit(1);
	  }

	  n.f1.accept(this, argu);

      R identType = n.f2.accept(this, argu);
	  String identName = n.f2.f0.tokenImage;
	  //VarOrMethod method = fieldMap(classType).(identType);
	  if(!fieldMap.get(classType).containsKey(identName) ||
	  	 !fieldMap.get(classType).get(identName).isMethod)
	  {
		System.err.format("ERROR: %s is not defined as a method in class %s", identName, classType);
		System.exit(1);
	  }
	  
	 // VarOrMethod method = fieldMap.get(classType).get(identName);

	
      n.f3.accept(this, argu);
      n.f4.accept(this, argu);
      n.f5.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> Expression()
    * f1 -> ( ExpressionRest() )*
    */
   public R visit(ExpressionList n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> ","
    * f1 -> Expression()
    */
   public R visit(ExpressionRest n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> IntegerLiteral()
    *       | TrueLiteral()
    *       | FalseLiteral()
    *       | Identifier()
    *       | ThisExpression()
    *       | ArrayAllocationExpression()
    *       | AllocationExpression()
    *       | NotExpression()
    *       | BracketExpression()
    */
   public R visit(PrimaryExpression n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> <INTEGER_LITERAL>
    */
   public R visit(IntegerLiteral n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> "true"
    */
   public R visit(TrueLiteral n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> "false"
    */
   public R visit(FalseLiteral n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> <IDENTIFIER>
    */
   public R visit(Identifier n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> "this"
    */
   public R visit(ThisExpression n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> "new"
    * f1 -> "int"
    * f2 -> "["
    * f3 -> Expression()
    * f4 -> "]"
    */
   public R visit(ArrayAllocationExpression n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      n.f3.accept(this, argu);
      n.f4.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> "new"
    * f1 -> Identifier()
    * f2 -> "("
    * f3 -> ")"
    */
   public R visit(AllocationExpression n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      n.f3.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> "!"
    * f1 -> Expression()
    */
   public R visit(NotExpression n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> "("
    * f1 -> Expression()
    * f2 -> ")"
    */
   public R visit(BracketExpression n, A argu) {
      R _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      return _ret;
   }
}




