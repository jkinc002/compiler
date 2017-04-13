package visitor;
import syntaxtree.*;
import java.util.*;
import java.util.Stack;
import java.util.HashMap;
import java.util.Vector;

public class TypecheckVisitor<R,A> extends GJDepthFirst<R,A> {
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

	private void addDeclarations (Map<String,String> m, NodeListOptional nodeList){
		for (int j = 0; j < nodeList.size(); ++j)
		{
			VarDeclaration varNode = (VarDeclaration)nodeList.elementAt(j);
			m.put(varNode.f1.f0.tokenImage, getTypeString(varNode.f0));
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
			if (classMap.containsKey(tokenImage))
			{
				System.err.println("Type error");	
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
			printFieldMap();
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
		Map<String, String> scope = new HashMap<String,String>();
		scope.put(n.f11.f0.tokenImage, "String[]");
		addDeclarations(scope, n.f14);
		//printMap(scope);


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
		return _ret;
	}
}

