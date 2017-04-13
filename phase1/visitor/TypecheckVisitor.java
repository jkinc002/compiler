package visitor;
import syntaxtree.*;
import java.util.*;
import java.util.Stack;
import java.util.HashMap;

public class TypecheckVisitor<R,A> extends GJDepthFirst<R,A> {
     Stack<HashMap<String,String>> symbolTable = null;
	 HashMap<String,String> inheritanceMap= null;
     HashMap<String,HashMap<String,String>> fieldMap = null;

	 public void printMap(Map<String,String> m){
		for(Map.Entry<String,String> entry : m.entrySet()) {
			System.out.println("Item: " + entry.getKey() + " Type: " + entry.getValue());
			}
			System.out.println(m.size());
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
   		fieldMap = new HashMap<String,HashMap<String,String>>();
	   // FIXME: make sure parent exists
	   // FIXME: make sure parent does not ultimately inherit the child (inheritance loop)
	   inheritanceMap.put("Object", "Object");

       R _ret = null;
	   HashMap<String,String> classMap = new HashMap<String,String>();

	   // Our main class
	   classMap.put(n.f0.f1.f0.tokenImage,"Class");
	   inheritanceMap.put(n.f0.f1.f0.tokenImage, "Object");
	   //put main class in field map
	   fieldMap.put(n.f0.f1.f0.tokenImage,new HashMap<String,String>());
	   //build field map
	   for(int i = 0; i < n.f1.size(); ++i){
		 HashMap<String,String> currFields = new HashMap<String,String>();
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
			    currFields.put(varNode.f1.f0.tokenImage, getTypeString(varNode.f0));
			}
			//Loop adds method declarations to field map
		    for (int k = 0; k < methFields.size(); ++k)
			{
				MethodDeclaration methNode = (MethodDeclaration)methFields.elementAt(k);
				currFields.put(methNode.f2.f0.tokenImage,getTypeString(methNode.f1));
			}
			printMap(currFields);
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
		printMap(scope);


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

