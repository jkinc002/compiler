VaporVisitor: 
	javac -cp ".:vapor-parser.jar" VaporVisitor.java

V2VM: V2VM.java VaporVisitor
	javac -cp ".:vapor-parser.jar" V2VM.java 

run: V2VM 
	java -cp ".:vapor-parser.jar" V2VM < LinkedList.vapor > LinkedList.compiler.out
