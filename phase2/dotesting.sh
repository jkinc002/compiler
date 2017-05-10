#!/bin/bash
pwd
if javac ./hw1/TypecheckVisitor.java  && javac ./hw1/Typecheck.java; then
	rm hw1/*.class
	rm -f hw1.tgz
	tar -czvf hw1.tgz hw1
	cd tests/Phase1Tester
	./run SelfTestCases ../../hw1.tgz
else
	echo "Compilation failed"
fi

