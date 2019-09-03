#!/bin/bash

MAIN=org.funz.calculator.Calculator

LIB=`find lib -name "funz-core-*.jar"`:`find lib -name "funz-calculator-*.jar"`:`find lib -name "commons-io-2.4.jar"`:`find lib -name "commons-exec-*.jar"`:`find lib -name "commons-lang-*.jar"`:`find lib -name "ftpserver-core-*.jar"`:`find lib -name "ftplet-api-*.jar"`:`find lib -name "mina-core-*.jar"`:`find lib -name "sigar-*.jar"`:`find lib -name "slf4j-api-*.jar"`:`find lib -name "slf4j-log4j12-*.jar"`

CALCULATOR=file:calculator.xml

if [ -e calculator-`hostname`.xml ]
then
CALCULATOR=file:calculator-`hostname`.xml
fi

java -Dapp.home=. -classpath $LIB $MAIN $CALCULATOR
