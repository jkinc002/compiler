VaporVisitor: 
	javac -cp ".:vapor-parser.jar" VaporVisitor.java

v2vm: v2vm.java VaporVisitor
	javac -cp ".:vapor-parser.jar" v2vm.java 

run: v2vm 
	java -cp ".:vapor-parser.jar" v2vm < Factorial.compiler.out
