import visitor.*;
import syntaxtree.*;
import java.util.*;

public class TranslationVisitor extends GJDepthFirst < VisitorReturn, VisitorReturn > {

    private class Pair {
        Boolean first;
        String second;

        Pair(Boolean b, String s) {
            first = b;
            second = s;
        }
    }

    private class Printer {
        int scope;
        Printer() {
            scope = 0;
        }

        void increaseScope()
        {
            scope += 1;
        }

        void decreaseScope()
        {
            scope -= 1;
        }

        void printScope()
        {
            for (int i = 0; i < scope; ++i)
            {
                System.out.print("  ");
            }
        }

        void println(String s)
        {
            printScope();
            
            System.out.println(s);
        }

        void print(String s)
        {
            System.out.print(s);
        }


    }

    int labelCount = 0;
    private String newLabel(String s)
    {
        labelCount++;
        return "_l." + s + Integer.toString(labelCount);
    }

    int tmpCount = 0;
    private String newTmp(String s)
    {
        tmpCount++;
        return "t." + s + "." + Integer.toString(tmpCount);
    }

    private void printvTables()
    {
        for (Map.Entry < String, HashMap<String, VTableEntry> > vTableMapEntry: vTable.entrySet()) {
            String className = vTableMapEntry.getKey();
            HashMap<String, VTableEntry> classVTable = vTableMapEntry.getValue();
            Vector<String> methods = new Vector<String>(classVTable.size());
            for (Map.Entry < String, VTableEntry > classVTableEntry: classVTable.entrySet()) {
                String methodName = classVTableEntry.getKey();
                Integer index = classVTableEntry.getValue().offset;
                methods.add(index, methodName);
            }

            printer.println("const vmt_" + className);
            printer.increaseScope();
            
            for (String methodName : methods)
            {
                //FIXME: sub classes must print parent name for non-overridden methods
                printer.println(":" + classVTable.get(methodName).definingClass + "." + methodName);
            }

            printer.decreaseScope();
            printer.println("");
            
        }
    }
    
    private class Scope {
        Map < String, String > fields; //maps identifier to type
        Map < String, Method > methods; //maps identifier to return type and param types

        private class Method {

            Vector < String > paramTypes;
            String retType;

            Method(String retType, Vector < String > paramTypes) {
                this.retType = retType;
                this.paramTypes = paramTypes;
            }
        }
        Scope() {
            fields = new HashMap < String, String > ();
            methods = new HashMap < String, Method > ();
        }
        Scope(Scope copy) {
            fields = new HashMap < String, String > (copy.fields);
            methods = new HashMap < String, Method > (copy.methods);
        }

        public void addMethod(String id, String retType, Vector < String > paramTypes) {
            if (methods.containsKey(id)) {
                System.out.println("Type error");
                System.exit(1);
            }

            methods.put(id, new Method(retType, paramTypes));
        }

        public void addField(String id, String type) {
            if (fields.containsKey(id)) {
                System.out.println("Type error");
                System.exit(1);
            }

            fields.put(id, type);
        }
    }

    Stack < Scope > symbolTable = null;
    HashMap < String,
    String > inheritanceMap = null;
    HashMap < String,
    Scope > fieldMap = null;
    HashMap < String,
    HashMap < String,
    Integer >> classRecord = null; //maps class-->field-->offset
    HashMap < String,
    HashMap < String,
    VTableEntry >> vTable = null; //maps class-->method-->offest
    String currentWorkingClass = null;
    Printer printer = new Printer();
    int count = 0;

    Vector < String > currentParams = null;

    private Boolean areChildAndParent(String childType, String parentType) {
        if (childType == parentType) return true;

        if (!inheritanceMap.containsKey(childType) || !inheritanceMap.containsKey(parentType)) {
            return false;
        }

        String currClass = childType;
        while (!currClass.equals("Object")) {
            if (currClass.equals(parentType)) {
                return true;
            }
            currClass = inheritanceMap.get(currClass);
        }
        return false;
    }

    private String getTypeString(Type type) {
        if (type.f0.which == 0) {
            return "Integer[]";
        } else if (type.f0.which == 1) {
            return "Boolean";
        } else if (type.f0.which == 2) {
            return "Integer";
        } else {
            Identifier identNode = (Identifier) type.f0.choice;
            return identNode.f0.tokenImage;
        }
    }

    private String getTypeOfField(String ident) {

        for (int i = symbolTable.size() - 1; i >= 0; --i) {
            if (symbolTable.get(i).fields.containsKey(ident)) {
                return symbolTable.get(i).fields.get(ident);
            }
        }
        return "";

    }

    private boolean isMethodOfClass(String methodName, String className) {
        if (fieldMap.get(className).methods.containsKey(methodName)) {
            return true;
        }
        return false;
    }

    private Pair isMethodOfInheritance(String classType, String methodName, Vector < String > args) {
        String currClass = classType;
        while (!currClass.equals("Object")) {
            if (isMethodOfClass(methodName, currClass)) {
                Vector < String > paramTypes = fieldMap.get(currClass).methods.get(methodName).paramTypes;
                if (paramTypes.size() != args.size()) {
                    //ERROR: same method but different params
                    System.out.println("Type error");
                    System.exit(1);
                }
                for (int i = 0; i < paramTypes.size(); ++i) {
                    if (!areChildAndParent(args.get(i), paramTypes.get(i))) {
                        //ERROR: same method but different params
                        System.out.println("Type error");
                        System.exit(1);
                    }
                }

                return new Pair(true, fieldMap.get(currClass).methods.get(methodName).retType);
            } else {
                currClass = inheritanceMap.get(currClass);
            }
        }
        return new Pair(false, "");
    }
    private void addVarDeclarations(Scope m, NodeListOptional nodeList) {
        for (int j = 0; j < nodeList.size(); ++j) {
            VarDeclaration varNode = (VarDeclaration) nodeList.elementAt(j);
            if (m.fields.containsKey(varNode.f1.f0.tokenImage)) {
                System.out.println("Type error");
                System.exit(1);
            }
            m.addField(varNode.f1.f0.tokenImage, getTypeString(varNode.f0));
        }
    }

    private String compareMethodSigs(Map < String, Scope.Method > childMethods, Map < String, Scope.Method > parentMethods) {
        for (Map.Entry < String, Scope.Method > childMethodEntry: childMethods.entrySet()) {
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

    private void addFieldAndMethodDeclarations(String className) {
        //Add the fields of className and its super classes to the scope

        Scope childScope = new Scope();
        String currClass = className;
        while (currClass != "Object") {
            Scope parentScope = new Scope(fieldMap.get(currClass));
            parentScope.fields.putAll(childScope.fields);
            String overloadedMethod = compareMethodSigs(childScope.methods, parentScope.methods);
            if (overloadedMethod != null) {
                System.out.println("Type error");
            }
            parentScope.methods.putAll(childScope.methods);
            childScope = parentScope;
            currClass = inheritanceMap.get(currClass);
        }
        symbolTable.peek().fields.putAll(childScope.fields);
        symbolTable.peek().methods.putAll(childScope.methods);
    }

    class VTableEntry {
        String definingClass;
        int offset;
        
        public VTableEntry(String definingClass, int offset)
        {
            this.definingClass = definingClass;
            this.offset = offset;
        }
    }

    public VisitorReturn visit(Goal n, VisitorReturn argu) {
        symbolTable = new Stack < Scope > ();
        inheritanceMap = new HashMap < String, String > ();
        fieldMap = new HashMap < String, Scope > ();
        currentParams = new Vector < String > ();
        inheritanceMap.put("Object", "Object");
        classRecord = new HashMap<String, HashMap<String, Integer>>();
        vTable = new HashMap<String, HashMap<String, VTableEntry>>();

       
        VisitorReturn _ret = new VisitorReturn();
        Scope classMap = new Scope();

        // Our main class
        classMap.addField(n.f0.f1.f0.tokenImage, "Class");
        inheritanceMap.put(n.f0.f1.f0.tokenImage, "Object");
        //put main class in field map
        fieldMap.put(n.f0.f1.f0.tokenImage, new Scope()); //TODO deal with recusive main call
        //build field map
        for (int i = 0; i < n.f1.size(); ++i) {
            Scope varMethodMap = new Scope();
            HashMap < String, Integer > fieldOffsetMap = new HashMap < String, Integer > ();
            TypeDeclaration temp = (TypeDeclaration) n.f1.elementAt(i);
            String tokenImage;
            String parent;
            NodeListOptional varFields;
            NodeListOptional methFields;
            Boolean isSubclass = false;
            if (temp.f0.which == 0) {
                ClassDeclaration castedNode = (ClassDeclaration) temp.f0.choice;
                tokenImage = castedNode.f1.f0.tokenImage;
                parent = "Object";
                varFields = castedNode.f3;
                methFields = castedNode.f4;
            } else {
                ClassExtendsDeclaration castedNode = (ClassExtendsDeclaration) temp.f0.choice;
                tokenImage = castedNode.f1.f0.tokenImage;
                parent = castedNode.f3.f0.tokenImage;
                varFields = castedNode.f5;
                methFields = castedNode.f6;
                isSubclass = true;
            }

            classMap.fields.put(tokenImage, "Class");
            inheritanceMap.put(tokenImage, parent);
            //Loop adds var declarations to field map
            int parentSize = 0;
            if (isSubclass) {
                parentSize = classRecord.get(parent).get("_totalnumfields");
            }

            for (int j = 0; j < varFields.size(); ++j) {
                VarDeclaration varNode = (VarDeclaration) varFields.elementAt(j);
                varMethodMap.addField(varNode.f1.f0.tokenImage, getTypeString(varNode.f0));
                //populate fieldOffsetMap depending on if subclass or not

                fieldOffsetMap.put(varNode.f1.f0.tokenImage, (parentSize + j + 1) * 4);
            }
            fieldOffsetMap.put("_totalnumfields", varFields.size() + parentSize);
            classRecord.put(tokenImage, fieldOffsetMap);


            //Loop adds method declarations to field map
            HashMap < String, VTableEntry > methodOffsetMap = new HashMap < String, VTableEntry > ();
            if (isSubclass) {
                methodOffsetMap.putAll(vTable.get(parent));
            }
            for (int k = 0; k < methFields.size(); ++k) {
                MethodDeclaration methNode = (MethodDeclaration) methFields.elementAt(k);

                String methodType = getTypeString(methNode.f1);
                Vector < String > methodParams = new Vector < String > ();
                if (methNode.f4.present()) {
                    FormalParameterList paramListNode = (FormalParameterList) methNode.f4.node;
                    methodParams.add(getTypeString(paramListNode.f0.f0));

                    for (int l = 0; l < paramListNode.f1.size(); ++l) {
                        FormalParameterRest paramNode = (FormalParameterRest) paramListNode.f1.elementAt(l);
                        methodParams.add(getTypeString(paramNode.f1.f0));
                    }

                }

                String methodName = methNode.f2.f0.tokenImage;
                varMethodMap.addMethod(methodName, methodType, methodParams);
                VTableEntry e = methodOffsetMap.get(methodName);
                if (e == null)
                {
                    methodOffsetMap.put(methodName, new VTableEntry(tokenImage, methodOffsetMap.size() * 4));
                }
                else
                {
                    methodOffsetMap.put(methodName, new VTableEntry(tokenImage, e.offset));
                }
            }
            vTable.put(tokenImage, methodOffsetMap);
            fieldMap.put(tokenImage, varMethodMap);
        }
        symbolTable.push(classMap);

        printvTables();

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

    public VisitorReturn visit(MainClass n, VisitorReturn argu) {
        Scope scope = new Scope();
        symbolTable.push(scope);
        scope.addMethod(n.f11.f0.tokenImage, "String[]", null);
        addVarDeclarations(scope, n.f14);

        printer.println("func Main()");
        printer.increaseScope();

       
        VisitorReturn _ret = new VisitorReturn();
 
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

        printer.decreaseScope();
        printer.println("");
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
    public VisitorReturn visit(ClassDeclaration n, VisitorReturn argu) {
        currentWorkingClass = n.f1.f0.tokenImage;
        Scope scope = new Scope();
        symbolTable.push(scope);
        addFieldAndMethodDeclarations(n.f1.f0.tokenImage);

       
        VisitorReturn _ret = new VisitorReturn();
 
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
    public VisitorReturn visit(ClassExtendsDeclaration n, VisitorReturn argu) {
        currentWorkingClass = n.f1.f0.tokenImage;
        Scope scope = new Scope();
        symbolTable.push(scope);
        addFieldAndMethodDeclarations(n.f1.f0.tokenImage);

       
        VisitorReturn _ret = new VisitorReturn();
 
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
    public VisitorReturn visit(VarDeclaration n, VisitorReturn argu) {
        VisitorReturn _ret = new VisitorReturn();
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
    public VisitorReturn visit(MethodDeclaration n, VisitorReturn argu) {
        Scope scope = new Scope();
        symbolTable.push(scope);
        //GOAL: check for duplicate parameter names
        String params;
        params = "(this";
        if (n.f4.present()) {
            FormalParameterList paramListNode = (FormalParameterList) n.f4.node;
            scope.addField(paramListNode.f0.f1.f0.tokenImage, getTypeString(paramListNode.f0.f0));
            params = params + ' ' + paramListNode.f0.f1.f0.tokenImage;

            for (int l = 0; l < paramListNode.f1.size(); ++l) {
                FormalParameterRest paramNode = (FormalParameterRest) paramListNode.f1.elementAt(l);
                scope.addField(paramNode.f1.f1.f0.tokenImage, getTypeString(paramNode.f1.f0));
                params = params + ' ' + paramNode.f1.f1.f0.tokenImage;
            }

        }
        params = params + ')';
        //AddVar checks for duplicate declarations in the same scope
        addVarDeclarations(scope, n.f7);

        printer.println("func " + currentWorkingClass + '.' + n.f2.f0.tokenImage + params);
        printer.increaseScope();

        VisitorReturn _ret = new VisitorReturn();
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
        printer.decreaseScope();
        printer.println("");
        return _ret;
    }

    /**
     * f0 -> FormalParameter()
     * f1 -> ( FormalParameterRest() )*
     */
    public VisitorReturn visit(FormalParameterList n, VisitorReturn argu) {
       
        VisitorReturn _ret = new VisitorReturn();
 
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        return _ret;
    }

    /**
     * f0 -> Type()
     * f1 -> Identifier()
     */
    public VisitorReturn visit(FormalParameter n, VisitorReturn argu) {
       
        VisitorReturn _ret = new VisitorReturn();
 
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        return _ret;
    }

    /**
     * f0 -> ","
     * f1 -> FormalParameter()
     */
    public VisitorReturn visit(FormalParameterRest n, VisitorReturn argu) {
       
        VisitorReturn _ret = new VisitorReturn();
 
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
    public VisitorReturn visit(Type n, VisitorReturn argu) {
        n.f0.accept(this, argu);
        VisitorReturn _ret = new VisitorReturn(getTypeString(n));
        return _ret;
    }

    /**
     * f0 -> "int"
     * f1 -> "["
     * f2 -> "]"
     */
    public VisitorReturn visit(ArrayType n, VisitorReturn argu) {
        
        VisitorReturn _ret = new VisitorReturn();
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        return _ret;
    }

    /**
     * f0 -> "boolean"
     */
    public VisitorReturn visit(BooleanType n, VisitorReturn argu) {
       
        VisitorReturn _ret = new VisitorReturn();
 
        n.f0.accept(this, argu);
        return _ret;
    }

    /**
     * f0 -> "int"
     */
    public VisitorReturn visit(IntegerType n, VisitorReturn argu) {
       
        VisitorReturn _ret = new VisitorReturn();
 
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
    public VisitorReturn visit(Statement n, VisitorReturn argu) {
       
        VisitorReturn _ret = new VisitorReturn();
 
        n.f0.accept(this, argu);
        return _ret;
    }

    /**
     * f0 -> "{"
     * f1 -> ( Statement() )*
     * f2 -> "}"
     */
    public VisitorReturn visit(Block n, VisitorReturn argu) {
        
        VisitorReturn _ret = new VisitorReturn();
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
    public VisitorReturn visit(AssignmentStatement n, VisitorReturn argu) {
       
        VisitorReturn _ret = new VisitorReturn();
 
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
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
    public VisitorReturn visit(ArrayAssignmentStatement n, VisitorReturn argu) {
        //GOAL: Identifier must be of type "INTEGER[]"
        //	 : Expression inside [] must be of type "INTEGER"
        //	 : Expression on RHS must be of type "INTEGER"
        VisitorReturn _ret = new VisitorReturn();
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
    public VisitorReturn visit(IfStatement n, VisitorReturn argu) {
        //GOAL:	expression is of type "Boolean"
        //	 :	Any Statement errors should resolve on their own, not here
        VisitorReturn _ret = new VisitorReturn();
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
    public VisitorReturn visit(WhileStatement n, VisitorReturn argu) {
        //GOAL:	expression is of type "Boolean"
        //	 :	Any Statement errors should resolve on their own, not here
        VisitorReturn _ret = new VisitorReturn();
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        n.f3.accept(this, argu);
        n.f4.accept(this, argu);
        return _ret;
    }

    /**
     * f0 -> "System.out.println
     * f1 -> "("
     * f2 -> Expression()
     * f3 -> ")"
     * f4 -> ";"
     */
    public VisitorReturn visit(PrintStatement n, VisitorReturn argu) {
        //GOAL:	expression must be of type Integer
        VisitorReturn _ret = new VisitorReturn();
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
    public VisitorReturn visit(Expression n, VisitorReturn argu) {
        VisitorReturn _ret = n.f0.accept(this, argu);
        return _ret;
    }

    private String visitBinExpr(Node n0, Node n1, Node n2, VisitorReturn argu, String operator) {
        String retTmp = newTmp("");
        String lhs = n0.accept(this, argu).getTmp();
        n1.accept(this, argu);
        String rhs = n2.accept(this, argu).getTmp();
        printer.println(retTmp + " = " +  operator + '(' + lhs + ' ' + rhs + ')');

        return retTmp;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "&&"
     * f2 -> PrimaryExpression()
     */
    public VisitorReturn visit(AndExpression n, VisitorReturn argu) {
        //handled
        VisitorReturn _ret = new VisitorReturn("Boolean");
        _ret.addTmp(visitBinExpr(n.f0, n.f1, n.f2, argu, "And"));

        return _ret;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "<"
     * f2 -> PrimaryExpression()
     */
    public VisitorReturn visit(CompareExpression n, VisitorReturn argu) {
        //handled
        VisitorReturn _ret = new VisitorReturn("Boolean");
        _ret.addTmp(visitBinExpr(n.f0, n.f1, n.f2, argu, "LtS"));
        return _ret;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "+"
     * f2 -> PrimaryExpression()
     */
    public VisitorReturn visit(PlusExpression n, VisitorReturn argu) {
        //handled
        VisitorReturn _ret = new VisitorReturn("Integer");
        _ret.addTmp(visitBinExpr(n.f0, n.f1, n.f2, argu, "Add"));
        return _ret;
 
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "-"
     * f2 -> PrimaryExpression()
     */
    public VisitorReturn visit(MinusExpression n, VisitorReturn argu) {
        //handled
        VisitorReturn _ret = new VisitorReturn("Integer");
        _ret.addTmp(visitBinExpr(n.f0, n.f1, n.f2, argu, "Sub"));
        return _ret;

    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "*"
     * f2 -> PrimaryExpression()
     */
    public VisitorReturn visit(TimesExpression n, VisitorReturn argu) {
        //handled
        VisitorReturn _ret = new VisitorReturn("Integer");
        _ret.addTmp(visitBinExpr(n.f0, n.f1, n.f2, argu, "MulS"));
        return _ret;

    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "["
     * f2 -> PrimaryExpression()
     * f3 -> "]"
     */
    public VisitorReturn visit(ArrayLookup n, VisitorReturn argu) {
        //handle
        VisitorReturn _ret = new VisitorReturn("Integer");
        String goodLookup = newLabel("goodLookup");
        String badLookup = newLabel("badLookup");
        String lookupTmp = newTmp("ArrayLookup");
        String arrayStart =  n.f0.accept(this, argu).getTmp();
        n.f1.accept(this, argu);
        String indexValue = n.f2.accept(this, argu).getTmp();
        n.f3.accept(this, argu);

        //Arracy accuracy check
        String lessThanTmp = newTmp("lessThan");
        String greaterThanTmp = newTmp("greaterThan");
        String maxsizetmp = newTmp("maxSize");
        printer.println(maxsizetmp + " = [" + arrayStart + ']');
        printer.println(greaterThanTmp + " = LtS(" + indexValue + " " + maxsizetmp + ")");
        printer.println("if0 " + greaterThanTmp + "goto :" + badLookup);
        printer.println(lessThanTmp + " = LtS(" + indexValue + " 0)");
        printer.println("if " + lessThanTmp + "goto :" + badLookup);
        printer.println("goto " + goodLookup);

        //print errors
        printer.println(badLookup + ":");
        printer.println("Error(\"Array index out of bounds\")");

        //-actual array lookup
        printer.println(goodLookup + ":");
        printer.println(lookupTmp + " = MulS(" + indexValue + " 4)");
        printer.println(lookupTmp + " = Add(" + lookupTmp + " 4)");
        printer.println(lookupTmp + " = [" + arrayStart + "+" + lookupTmp + "]");
        
        return _ret;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "."
     * f2 -> "length"
     */
    public VisitorReturn visit(ArrayLength n, VisitorReturn argu) {
        //handled
        VisitorReturn _ret = new VisitorReturn("Integer");
        String temp1 = n.f0.accept(this, argu).getTmp();
        String temp2 = newTmp("length");
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        printer.print(temp2 + " = [" + temp1 + ']'); 
        _ret.addTmp(temp1);
        return _ret;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "."
     * f2 -> Identifier()
     * f3 -> "("
     * f4 -> ( ExpressionList() )?
     * f5 -> ")"
     */
    public VisitorReturn visit(MessageSend n, VisitorReturn argu) {
        //handled
        VisitorReturn primExpr = n.f0.accept(this, argu);
        String petmp = primExpr.getTmp();
        String classType = primExpr.getType();
        n.f1.accept(this, argu);
        String identName = n.f2.accept(this, argu).getType();
        
        n.f3.accept(this, argu);
        currentParams.clear();

        String functmp = newTmp("msgsendfunc");
        String rettmp = newTmp("msgsendret");
        VisitorReturn paramsList = n.f4.accept(this, argu); // print expression list
        Integer offset = vTable.get(primExpr.getType()).get(identName).offset;
        printer.println(functmp + " = " + "[" + petmp + "]");
        printer.println(functmp + " = " + "[" + functmp + "+" + Integer.toString(offset) + "]");
        printer.printScope();
        printer.print(rettmp + " = call " + functmp + "(" + petmp);
        if (paramsList != null)
        {
            for(String tmp : paramsList.tmps)
            {
                printer.print(" " + tmp);
            }
        }
        printer.print(")\n");

        
        //make vector of args
        Pair result = isMethodOfInheritance(classType, identName, currentParams);

        VisitorReturn _ret = new VisitorReturn(result.second);
        _ret.addTmp(rettmp);
            
        n.f5.accept(this, argu);
        return _ret;
    }

    /**
     * f0 -> Expression()
     * f1 -> ( ExpressionRest() )*
     */
    public VisitorReturn visit(ExpressionList n, VisitorReturn argu) {
        //handled
        VisitorReturn _ret = n.f0.accept(this, argu);
        currentParams.add(_ret.getType());
        n.f1.accept(this, _ret);
        return _ret;
    }

    /**
     * f0 -> ","
     * f1 -> Expression()
     */
    public VisitorReturn visit(ExpressionRest n, VisitorReturn argu) {
        //handled
        n.f0.accept(this, argu);
        VisitorReturn _ret = n.f1.accept(this, argu);
        argu.addTmp(_ret.getTmp());
        currentParams.add(_ret.getType());
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
    public VisitorReturn visit(PrimaryExpression n, VisitorReturn argu) {
        //handled
        VisitorReturn _ret = n.f0.accept(this, argu);

        if (n.f0.which == 3) { //is identifier node
            _ret.setType(getTypeOfField(_ret.getType()));
        }

        return _ret;
    }

    /**
     * f0 -> <INTEGER_LITERAL>
     */
    public VisitorReturn visit(IntegerLiteral n, VisitorReturn argu) {
        //handled
        n.f0.accept(this, argu);
        VisitorReturn _ret = new VisitorReturn("Integer", n.f0.tokenImage);
        return _ret;
    }

    /**
     * f0 -> "true"
     */
    public VisitorReturn visit(TrueLiteral n, VisitorReturn argu) {
        //handled
        n.f0.accept(this, argu);
        VisitorReturn _ret = new VisitorReturn("Boolean", "1");
        return _ret;
    }

    /**
     * f0 -> "false"
     */
    public VisitorReturn visit(FalseLiteral n, VisitorReturn argu) {
        //handled
        n.f0.accept(this, argu);
        VisitorReturn _ret = new VisitorReturn("Boolean", "0");
        return _ret;
    }

    /**
     * f0 -> <IDENTIFIER>
     */
    public VisitorReturn visit(Identifier n, VisitorReturn argu) {
        //handled
        VisitorReturn _ret = new VisitorReturn(n.f0.tokenImage, n.f0.tokenImage);
        n.f0.accept(this, argu);
        return _ret;
    }

    /**
     * f0 -> "this"
     */
    public VisitorReturn visit(ThisExpression n, VisitorReturn argu) {
        //handled maybe
        n.f0.accept(this, argu);
        VisitorReturn _ret = new VisitorReturn(currentWorkingClass, "this");
        return _ret;
    }

    /**
     * f0 -> "new"
     * f1 -> "int"
     * f2 -> "["
     * f3 -> Expression()
     * f4 -> "]"
     */
    public VisitorReturn visit(ArrayAllocationExpression n, VisitorReturn argu) {
        //handled
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        String arraySize = n.f3.accept(this, argu).getTmp();
        n.f4.accept(this, argu);
        VisitorReturn _ret = new VisitorReturn("Integer[]");
        String temp1 = newTmp("ArrayAlloc");
        printer.println(temp1 + " = " + "MulS(" + arraySize + " 4)");
        printer.println(temp1 + " = " + "Add(" + temp1 + " 4)");
        printer.println(temp1 + " = HeapAllocZ(" + temp1 + ")");
        printer.println('[' + temp1 + "] = " + arraySize);
        _ret.addTmp(temp1);
        return _ret;
    }

    /**
     * f0 -> "new"
     * f1 -> Identifier()
     * f2 -> "("
     * f3 -> ")"
     */
    public VisitorReturn visit(AllocationExpression n, VisitorReturn argu) {
        //handled
        String tmp = newTmp("alloc");
        String label = newLabel("allocgood");
        String className = n.f1.f0.tokenImage;
        int numFields = classRecord.get(className).get("_totalnumfields");
        
        printer.println(tmp + " = HeapAllocZ(" + Integer.toString((numFields+1) * 4) + ")");
        printer.println("[" + tmp + "] = :vmt_" + className);
        printer.println("if " + tmp + " goto :" + label);
        printer.println("Error(\"null pointer\")");
        printer.println(label + ":");
        
        n.f0.accept(this, argu);
        VisitorReturn _ret = n.f1.accept(this, argu);
        _ret.tmps.clear();
        _ret.addTmp(tmp);
        n.f2.accept(this, argu);
        n.f3.accept(this, argu);
        return _ret;
    }

    /**
     * f0 -> "!"
     * f1 -> Expression()
     */
    public VisitorReturn visit(NotExpression n, VisitorReturn argu) {
        //handled
        VisitorReturn _ret = new VisitorReturn("Boolean");
        n.f0.accept(this, argu);
        String temp1 = n.f1.accept(this, argu).getTmp();
        String temp2 = newTmp("NotExpr");
        printer.print(temp2 + " = Not(" + temp1 + ')');
        _ret.addTmp(temp1);
        return _ret;
    }

    /**
     * f0 -> "("
     * f1 -> Expression()
     * f2 -> ")"
     */
    public VisitorReturn visit(BracketExpression n, VisitorReturn argu) {
        //handled
        n.f0.accept(this, argu);
        VisitorReturn _ret = n.f1.accept(this, argu);
        n.f2.accept(this, argu);
        return _ret;
    }
}
