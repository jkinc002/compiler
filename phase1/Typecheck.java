import java.io.InputStreamReader;
import java.io.FileInputStream;
import syntaxtree.*;
import visitor.*;

class Typecheck {
  public static void main (String[] args)
  {
	MiniJavaParser mjp = new MiniJavaParser(System.in);
	Goal g = null;
	try{
		g = mjp.Goal();
	}
	catch(ParseException e){
	  e.printStackTrace();
	}
	if(g == null) return;

	TypecheckVisitor<Boolean,Object> mgj = new TypecheckVisitor<Boolean,Object>();
	
	mgj.visit(g,null);
	
	return;
  }

  
}
