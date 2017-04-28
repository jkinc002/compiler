import visitor.*;
import syntaxtree.*;
import java.util.*;
import java.util.Stack;
import java.util.HashMap;
import java.util.Vector;

public class TypecheckVisitor extends GJDepthFirst<String, String> {

	private class Pair {
		Boolean first;
		String second;

		Pair(Boolean b, String s){
			first = b;
			second = s;
		}
	}

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
	
 

     private class Scope {
         Map<String, String> fields; //maps identifier to type
	 Map<String, Method> methods; //maps identifier to return type and param types
     	
	private class Method {

		Vector<String> paramTypes;
		String retType;

		Method (String retType, Vector<String> paramTypes)
		{
			this.retType = retType;
			this.paramTypes = paramTypes;
		}
	}
	Scope()
	{
		fields = new HashMap<String, String>();
		methods = new HashMap<String, Method>();
	}
	Scope(Scope copy){
		fields = new HashMap<String,String>(copy.fields);
		methods = new HashMap<String,Method>(copy.methods);
	}

        public void addMethod(String id, String retType, Vector<String> paramTypes)
	{
		if(methods.containsKey(id)){
			System.err.println("ERROR: duplicate methods declared");
			System.exit(1);
		}

		methods.put(id, new Method(retType, paramTypes));	
	}

	public void addField(String id, String type)
	{
	    if(fields.containsKey(id)){
		System.err.println("ERROR: duplicate fields declared");
		System.exit(1);
	    }

            fields.put(id, type);
	}
     }

    	Stack<Scope> symbolTable = null;
	HashMap<String,String> inheritanceMap= null;
     	HashMap<String,Scope> fieldMap = null;
	String currentWorkingClass = null;

	 Vector<String> currentParams = null;

	private Boolean areChildAndParent(String childType, String parentType){
		if(childType == parentType) return true;
		
		if (!inheritanceMap.containsKey(childType) || inheritanceMap.containsKey(parentType)) 
		{
			return false;
		}
 
		String currClass = childType;
		while(!currClass.equals("Object")){
			if(currClass.equals(parentType)){
				return true;
			}
			currClass = inheritanceMap.get(currClass);
		}
		return false;
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

	private String getTypeOfField(String ident){
		
		for(int i = symbolTable.size() - 1;i >= 0; --i){
			if(symbolTable.get(i).fields.containsKey(ident)){
				return symbolTable.get(i).fields.get(ident);
			}
		}
		return "";
		
	}

	private boolean isMethodOfClass(String methodName, String className)
	{
		if(fieldMap.get(className).methods.containsKey(methodName)){
			return true;
		}
		return false;
	}

	private Pair isMethodOfInheritance(String classType, String methodName, Vector<String> args)
	{
		String currClass = classType;
		while(!currClass.equals("Object")){
			if(isMethodOfClass(methodName, currClass)){
				Vector<String> paramTypes = fieldMap.get(currClass).methods.get(methodName).paramTypes;
				if(paramTypes.size() != args.size()){
					//ERROR: same method but different params
					System.err.format("Method %s exists in class %s but uses different parameters%n", methodName, currClass);
					System.exit(1);
				}
				for(int i = 0; i < paramTypes.size(); ++i){
					if(!areChildAndParent(args.get(i), paramTypes.get(i))){
						//ERROR: same method but different params
						System.err.format("Method %s exists in class %s but uses different parameters%n", methodName, currClass);
						System.exit(1);
					}
				}
				
				return new Pair(true,fieldMap.get(currClass).methods.get(methodName).retType);
			}
			else{
				currClass = inheritanceMap.get(currClass);
			}
		}
		return new Pair(false,"");
	}

	private void addVarDeclarations (Scope m, NodeListOptional nodeList){
		for (int j = 0; j < nodeList.size(); ++j)
		{
			VarDeclaration varNode = (VarDeclaration)nodeList.elementAt(j);
			if(m.fields.containsKey(varNode.f1.f0.tokenImage)){
				System.err.println("ERROR: duplicate variables declared in same scope");
				System.exit(1);
			}
			m.addField(varNode.f1.f0.tokenImage, getTypeString(varNode.f0));
		}
	}

	private String compareMethodSigs(Map<String, Scope.Method> childMethods, Map<String, Scope.Method> parentMethods)
	{
		for (Map.Entry<String, Scope.Method> childMethodEntry : childMethods.entrySet())
		{
			String methodName = childMethodEntry.getKey();
			Scope.Method childMethod = childMethodEntry.getValue();
			Scope.Method parentMethod = parentMethods.get(methodName);
			if (parentMethod != null) {
				if (childMethod.retType != parentMethod.retType) {
					return methodName;
				}
				if (childMethod.paramTypes.size() != parentMethod.paramTypes.size()) {
					return methodName;
				}
				for (int i = 0; i < childMethod.paramTypes.size(); i++) {
					if (childMethod.paramTypes.get(i) != parentMethod.paramTypes.get(i)) {
						return methodName;
					}
				}	
			}
		} 
		return null;
	}
	
	private void addFieldAndMethodDeclarations (String className){
		//Add the fields of className and its super classes to the scope

		Scope childScope = new Scope();
		String currClass = className;
		while (currClass != "Object")
		{
		    Scope parentScope = new Scope(fieldMap.get(currClass));
		    parentScope.fields.putAll(childScope.fields);
		    String overloadedMethod = compareMethodSigs(childScope.methods, parentScope.methods);
	            if (overloadedMethod != null)
                    {
                        System.err.format("%s cannot overload parent %s's method %s", className, currClass, overloadedMethod); 
                    }
		    parentScope.methods.putAll(childScope.methods);
		    childScope = parentScope;
		    currClass = inheritanceMap.get(currClass);
		}
		symbolTable.peek().fields.putAll(childScope.fields);
		symbolTable.peek().methods.putAll(childScope.methods);
	}	

     public String visit(Goal n, String argu){
	   symbolTable = new Stack<Scope>();
	   inheritanceMap = new HashMap<String,String>();
   	   fieldMap = new HashMap<String,Scope>();
	   // FIXME: make sure parent exists
	   // FIXME: make sure parent does not ultimately inherit the child (inheritance loop)
	   inheritanceMap.put("Object", "Object");

           String _ret = null;
	   Scope classMap = new Scope();

	   // Our main class
	   classMap.addField(n.f0.f1.f0.tokenImage,"Class");
	   inheritanceMap.put(n.f0.f1.f0.tokenImage, "Object");
	   //put main class in field map
	   fieldMap.put(n.f0.f1.f0.tokenImage,new Scope()); //TODO deal with recusive main call
	   //build field map
	   for(int i = 0; i < n.f1.size(); ++i){
		 Scope varMethodMap = new Scope();
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
			if (classMap.fields.containsKey(tokenImage))
			{
				System.err.println("ERROR: Duplicate classes declared");	
				System.exit(1);
			}
			classMap.fields.put(tokenImage,"Class");
			inheritanceMap.put(tokenImage,parent);
			//Loop adds var declarations to field map
			for (int j = 0; j < varFields.size(); ++j)
			{
				VarDeclaration varNode = (VarDeclaration)varFields.elementAt(j);
			    varMethodMap.addField(varNode.f1.f0.tokenImage, getTypeString(varNode.f0));
			}
			//Loop adds method declarations to field map
		    for (int k = 0; k < methFields.size(); ++k)
			{
				MethodDeclaration methNode = (MethodDeclaration)methFields.elementAt(k);
				//check for duplicate method declarations
				if(varMethodMap.methods.containsKey(methNode.f2.f0.tokenImage)){
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
				varMethodMap.addMethod(methNode.f2.f0.tokenImage, methodType, methodParams);
			}
			//printMap(varMethodMap);
			fieldMap.put(tokenImage,varMethodMap);
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

	public String visit(MainClass n, String argu){
		Scope scope = new Scope();
	    	symbolTable.push(scope);	
		scope.addMethod(n.f11.f0.tokenImage, "String[]", null);
		addVarDeclarations(scope, n.f14);

		String _ret=null;
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
	public String visit(ClassDeclaration n, String argu) {
		currentWorkingClass = n.f1.f0.tokenImage;
		Scope scope = new Scope();
		symbolTable.push(scope);
		addFieldAndMethodDeclarations(n.f1.f0.tokenImage);
		//printMap(scope);

      String _ret=null;
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
   public String visit(ClassExtendsDeclaration n, String argu) {
		currentWorkingClass = n.f1.f0.tokenImage;
		Scope scope = new Scope();
		symbolTable.push(scope);
		addFieldAndMethodDeclarations(n.f1.f0.tokenImage);

      String _ret=null;
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
	//DONE HERE
   public String visit(VarDeclaration n, String argu) {
      String _ret=null;
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
   public String visit(MethodDeclaration n, String argu) {
	Scope scope = new Scope();
	symbolTable.push(scope);

	//GOAL: check for duplicate parameter names
	if(n.f4.present()){
		FormalParameterList paramListNode = (FormalParameterList)n.f4.node;
		scope.addField(paramListNode.f0.f1.f0.tokenImage, getTypeString(paramListNode.f0.f0));
		
		for(int l = 0; l < paramListNode.f1.size(); ++l){
			FormalParameterRest paramNode = (FormalParameterRest)paramListNode.f1.elementAt(l);
			scope.addField(paramNode.f1.f1.f0.tokenImage, getTypeString(paramNode.f1.f0));
		}
		
	}
	//AddVar checks for duplicate declarations in the same scope
	addVarDeclarations(scope, n.f7);


      String _ret=null;
      n.f0.accept(this, argu);
      String methodType = n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      n.f3.accept(this, argu);
      n.f4.accept(this, argu);
      n.f5.accept(this, argu);
      n.f6.accept(this, argu);
      n.f7.accept(this, argu);
      n.f8.accept(this, argu);
      n.f9.accept(this, argu);
      String retType = n.f10.accept(this, argu);
	if(!methodType.equals(retType)){
		//return type did not match method type
		System.err.format("ERROR: Method return type found %s, expected %s%n",retType,methodType);
		System.exit(1);
	}
      n.f11.accept(this, argu);
      n.f12.accept(this, argu);

		symbolTable.pop();
      return _ret;
   }

   /**
    * f0 -> FormalParameter()
    * f1 -> ( FormalParameterRest() )*
    */
   public String visit(FormalParameterList n, String argu) {
      String _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> Type()
    * f1 -> Identifier()
    */
   public String visit(FormalParameter n, String argu) {
      String _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> ","
    * f1 -> FormalParameter()
    */
   public String visit(FormalParameterRest n, String argu) {
      String _ret=null;
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
   public String visit(Type n, String argu) {
      String _ret=null;
      n.f0.accept(this, argu);
      _ret = getTypeString(n);
      return _ret;
   }

   /**
    * f0 -> "int"
    * f1 -> "["
    * f2 -> "]"
    */
   public String visit(ArrayType n, String argu) {
      String _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> "boolean"
    */
   public String visit(BooleanType n, String argu) {
      String _ret=null;
      n.f0.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> "int"
    */
   public String visit(IntegerType n, String argu) {
      String _ret=null;
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
   public String visit(Statement n, String argu) {
      String _ret=null;
      n.f0.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> "{"
    * f1 -> ( Statement() )*
    * f2 -> "}"
    */
   public String visit(Block n, String argu) {
      String _ret=null;
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
   public String visit(AssignmentStatement n, String argu) {
      String _ret=null;
      String lhsType = getTypeOfField(n.f0.accept(this, argu));
	  if(lhsType.equals("")){
	  	System.out.format("ERROR: Identifier %s not previously declared in a reachable scope%n", n.f0.f0.tokenImage);
		System.exit(1);
	  }
      n.f1.accept(this, argu);
      String rhsType = n.f2.accept(this, argu);
	//FIXME: handle inheritance here
	  if(!areChildAndParent(rhsType,lhsType)){
	  	System.out.format("ERROR: Assignment of incompatible types: Expected %s, got %s%n", lhsType, rhsType);
		System.exit(1);
		}
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
   public String visit(ArrayAssignmentStatement n, String argu) {
   //GOAL: Identifier must be of type "INTEGER[]"
   //	 : Expression inside [] must be of type "INTEGER"
   //	 : Expression on RHS must be of type "INTEGER"
      String _ret=null;
      String identType = getTypeOfField(n.f0.accept(this, argu));
	  if(!identType.equals("Integer[]")){
		//ERROR: Identifier was not of type INTEGER[]
		System.out.format("Identifier %n is not of type INTEGER_ARRAY%n", n.f0.f0.tokenImage);
		System.exit(1);
	  }
      n.f1.accept(this, argu);
      String expression1 = n.f2.accept(this, argu);
	  if(!expression1.equals("Integer")){
		//ERROR: expression1 was not of type Integer
		System.out.format("Array index must be of type INT%n");
		System.exit(1);
	  }
      n.f3.accept(this, argu);
      n.f4.accept(this, argu);
      String expression2 = n.f5.accept(this, argu);
	  if(!expression2.equals("Integer")){
		//ERROR: expression2 was not of type Integer
		System.out.format("Assignement of Array at index must be of type INT%n");
		System.exit(1);
	  }
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
   public String visit(IfStatement n, String argu) {
   //GOAL:	expression is of type "Boolean"
   //	 :	Any Statement errors should resolve on their own, not here
      String _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      String expressionType = n.f2.accept(this, argu);
	  if(!expressionType.equals("Boolean")){
	  	//ERROR: expression is not of type "Boolean"
		System.out.println("If condition is not of type Boolean");
		System.exit(1);
	  }
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
   public String visit(WhileStatement n, String argu) {
   //GOAL:	expression is of type "Boolean"
   //	 :	Any Statement errors should resolve on their own, not here
      String _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      String expressionType = n.f2.accept(this, argu);
	  if(!expressionType.equals("Boolean")){
	  	//ERROR: expression is not of type "Boolean"
		System.out.println("If condition is not of type Boolean");
		System.exit(1);
	  }
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
   public String visit(PrintStatement n, String argu) {
   //GOAL:	expression must be of type Integer
      String _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      String expressionType = n.f2.accept(this, argu);
	  if(!expressionType.equals("Integer")){
		//ERROR: expression is not of type "Integer"
		System.out.println("If condition is not of type Integer");
		System.exit(1);
	  }
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
   public String visit(Expression n, String argu) {
      String _ret=null;
      _ret = n.f0.accept(this, argu);
      return _ret;
   }

   private void typeCheckBinExpr(Node n0,Node n1,Node n2, String argu, String t, String errmsg)
   {
       String lhsType = n0.accept(this, argu);
	   n1.accept(this, argu);
       String rhsType = n2.accept(this, argu);

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
   public String visit(AndExpression n, String argu) {
	  typeCheckBinExpr(n.f0,n.f1,n.f2, argu,  "Boolean", "Expected boolean types for operator &&");
	  return "Boolean";
   }

   /**
    * f0 -> PrimaryExpression()
    * f1 -> "<"
    * f2 -> PrimaryExpression()
    */
   public String visit(CompareExpression n, String argu) {
      typeCheckBinExpr(n.f0,n.f1,n.f2, argu,  "Integer", "Expected Integer types for operator <");
	  return "Boolean";
   }

   /**
    * f0 -> PrimaryExpression()
    * f1 -> "+"
    * f2 -> PrimaryExpression()
    */
   public String visit(PlusExpression n, String argu) {
      typeCheckBinExpr(n.f0,n.f1,n.f2, argu,  "Integer", "Expected Integer types for operator +");
	  return "Integer";
   }

   /**
    * f0 -> PrimaryExpression()
    * f1 -> "-"
    * f2 -> PrimaryExpression()
    */
   public String visit(MinusExpression n, String argu) {
      typeCheckBinExpr(n.f0,n.f1,n.f2, argu,  "Integer", "Expected Integer types for operator -");
	  return "Integer";
   }

   /**
    * f0 -> PrimaryExpression()
    * f1 -> "*"
    * f2 -> PrimaryExpression()
    */
   public String visit(TimesExpression n, String argu) {
      typeCheckBinExpr(n.f0,n.f1,n.f2, argu,  "Integer", "Expected Integer types for operator *");
	  return "Integer";

   }

   /**
    * f0 -> PrimaryExpression()
    * f1 -> "["
    * f2 -> PrimaryExpression()
    * f3 -> "]"
    */
   public String visit(ArrayLookup n, String argu) {
      String _ret=null;
      String lhsType = n.f0.accept(this, argu);
	  if(!lhsType.equals("Integer[]")){
		System.err.println("ERROR: expected type Integer[] in lhs of ArrayLookup");
		System.exit(1);
	  }
      n.f1.accept(this, argu);
      String rhsType = n.f2.accept(this, argu);
	  if(!rhsType.equals("Integer")){
		System.err.println("ERROR: expected type Integer in rhs of ArrayLookup");
		System.exit(1);
	  }
      n.f3.accept(this, argu);
      return "Integer";
   }

   /**
    * f0 -> PrimaryExpression()
    * f1 -> "."
    * f2 -> "length"
    */
   public String visit(ArrayLength n, String argu) {
      String _ret=null;
      String lhsType = n.f0.accept(this, argu);
	  if(!lhsType.equals("Integer[]")){
		System.err.println("ERROR: expected type Integer[] in lhs of ArrayLength");
		System.exit(1);
	  }
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      return "Integer";
   }

   /**
    * f0 -> PrimaryExpression()
    * f1 -> "."
    * f2 -> Identifier()
    * f3 -> "("
    * f4 -> ( ExpressionList() )?
    * f5 -> ")"
    */
   public String visit(MessageSend n, String argu) {
   //GOAL:	PrimaryExpression's type is of type Class
   //	 : 	PrimaryExpression's id is not overshadowed
   //	 :	Identifier is a method of PrimaryExpression's class type
      String _ret=null;

      String classType = n.f0.accept(this, argu);
	  //check if PrimaryExpression is not overshadowed with non-class type
	  if(!getTypeOfField(classType).equals("Class")){
		System.err.format("ERROR: Primary expression is not a class type%n");
	  }
	  //check if PrimaryExpression exists as a class
	  if(!fieldMap.containsKey(classType)){
		System.err.format("ERROR: primary expression is not a valid type in MessageSend statement");
		System.exit(1);
	  }
	  n.f1.accept(this, argu);

      String identName = n.f2.accept(this, argu);
	
      n.f3.accept(this, argu);
	  currentParams = new Vector<String>();
      n.f4.accept(this, argu);
	  //make vector of args
	  Pair result = isMethodOfInheritance(classType, identName, currentParams);
	  if(!result.first){
		//Method is not in class or parent classes
		System.err.format("ERROR: Method %s called from class %s is not a declared method of class %s or its parent classes%n", identName, classType, classType);
		System.exit(1);
	  }

	  _ret = result.second;
	  currentParams = null;

      n.f5.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> Expression()
    * f1 -> ( ExpressionRest() )*
    */
   public String visit(ExpressionList n, String argu) {
      String _ret=null;
      currentParams.add(n.f0.accept(this, argu));
	  n.f1.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> ","
    * f1 -> Expression()
    */
   public String visit(ExpressionRest n, String argu) {
      String _ret=null;
      n.f0.accept(this, argu);
	  currentParams.add(n.f1.accept(this, argu));
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
   public String visit(PrimaryExpression n, String argu) {
      String _ret=null;
      _ret = n.f0.accept(this, argu);

		if(n.f0.which == 3){ //is identifier node
			String ident = _ret;
			_ret = getTypeOfField(_ret);
			if(_ret.equals("")){
				System.out.format("Identifier %s not previously declared in a reachable scope%n", ident);
				System.exit(1);
			}
		}
		else{	//is another node
			
		}

      return _ret;
   }

   /**
    * f0 -> <INTEGER_LITERAL>
    */
   public String visit(IntegerLiteral n, String argu) {
      String _ret=null;
      n.f0.accept(this, argu);
	  _ret = "Integer";
      return _ret;
   }

   /**
    * f0 -> "true"
    */
   public String visit(TrueLiteral n, String argu) {
      String _ret=null;
      n.f0.accept(this, argu);
	  _ret = "Boolean";
      return _ret;
   }

   /**
    * f0 -> "false"
    */
   public String visit(FalseLiteral n, String argu) {
      String _ret=null;
      n.f0.accept(this, argu);
	  _ret = "Boolean";
      return _ret;
   }

   /**
    * f0 -> <IDENTIFIER>
    */
   public String visit(Identifier n, String argu) {
      String _ret=null;
      _ret = n.f0.tokenImage;
	  n.f0.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> "this"
    */
   public String visit(ThisExpression n, String argu) {
      String _ret=null;
	if(currentWorkingClass == null){
		System.err.format("ERROR: 'this' used in static context%n");
		System.exit(1);
	}
      n.f0.accept(this, argu);
	_ret = currentWorkingClass;
      return _ret;
   }

   /**
    * f0 -> "new"
    * f1 -> "int"
    * f2 -> "["
    * f3 -> Expression()
    * f4 -> "]"
    */
   public String visit(ArrayAllocationExpression n, String argu) {
      String _ret=null;
      n.f0.accept(this, argu);
      n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      String expressionType = n.f3.accept(this, argu);
	if(!expressionType.equals("Integer")){
		System.err.println("ERROR: Attempted array allocation with non-Integer size"); 
		System.exit(1);
	}
      n.f4.accept(this, argu);
	_ret = "Integer[]";
      return _ret;
   }

   /**
    * f0 -> "new"
    * f1 -> Identifier()
    * f2 -> "("
    * f3 -> ")"
    */
   public String visit(AllocationExpression n, String argu) {
   	//GOAL: check if Identifier is a created class
      String _ret=null;
      n.f0.accept(this, argu);
      _ret = n.f1.accept(this, argu);
		//checking identifier
		if(!getTypeOfField(_ret).equals("Class"))
		{
			System.err.format("Identifier %s is not of type Class%n", _ret);
			System.exit(1);
		}

      n.f2.accept(this, argu);
      n.f3.accept(this, argu);
      return _ret;
   }

   /**
    * f0 -> "!"
    * f1 -> Expression()
    */
   public String visit(NotExpression n, String argu) {
      String _ret=null;
      n.f0.accept(this, argu);
      String expressionType = n.f1.accept(this, argu);
	if(!expressionType.equals("Boolean")){
		System.err.format("ERROR: Not expression used on non-boolean expression%n");
		System.exit(1);
	}
	_ret = "Boolean";
      return _ret;
   }

   /**
    * f0 -> "("
    * f1 -> Expression()
    * f2 -> ")"
    */
   public String visit(BracketExpression n, String argu) {
      String _ret=null;
      n.f0.accept(this, argu);
      _ret = n.f1.accept(this, argu);
      n.f2.accept(this, argu);
      return _ret;
   }
}




