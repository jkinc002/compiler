#!/bin/bash
pwd

rm hw2/*.class
cp hw2/* ./
#./TypecheckVisitor.java && javac ./VisitorReturn.java && javac ./TranslationVisitor.java  && 
if javac ./J2V.java; then
	rm *.class
	rm -f hw2.tgz
	tar -czvf hw2.tgz hw2
	cd tests/Phase2Tester
	./run SelfTestCases ../../hw2.tgz
else
	echo "Compilation failed"
fi

