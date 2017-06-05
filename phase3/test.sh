#!/bin/bash
pwd

#rm hw3/*.class
#cp hw3/* ./
#./TypecheckVisitor.java && javac ./VisitorReturn.java && javac ./TranslationVisitor.java  && 
#if javac ./J2V.java; then
cp VaporMGenerator.java hw3
cp VaporVisitor.java hw3
cp V2VM.java hw3

rm *.class
rm -f hw3.tgz
tar -czvf hw3.tgz hw3
cd Phase3Tester
./run SelfTestCases ../hw3.tgz
#else
#	echo "Compilation failed"
#fi

