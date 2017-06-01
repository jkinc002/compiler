
import java.io.InputStreamReader;
import java.io.FileInputStream;
import syntaxtree.*;

class J2V {
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

    // Do the type checking
	TypecheckVisitor mgj = new TypecheckVisitor();
	mgj.visit(g,null);

    // Actuall translate the code
	TranslationVisitor translationVisitor= new TranslationVisitor();
	translationVisitor.visit(g,null);


	
	return;
  }

  
}
