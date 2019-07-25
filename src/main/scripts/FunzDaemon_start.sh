#!/bin/bash

MAIN=org.funz.calculator.Calculator

LIB=`find lib -name "funz-core-*.jar"`:`find lib -name "funz-calculator-*.jar"`:lib/commons-io-2.4.jar:lib/commons-exec-1.1.jar:lib/commons-lang-2.6.jar:lib/ftpserver-core-1.1.1.jar:lib/ftplet-api-1.1.1.jar:lib/mina-core-2.0.16.jar:lib/sigar-1.6.6.jar:lib/slf4j-api-1.5.2.jar:lib/slf4j-log4j12-1.5.2.jar

CALCULATOR=file:calculator.xml

if [ -e calculator-`hostname`.xml ]
then
CALCULATOR=file:calculator-`hostname`.xml
fi

FUNZ_HOME=$HOME/.Funz
if [ ! -d $FUNZ_HOME ]
then
mkdir $FUNZ_HOME
fi
if [ ! -d $FUNZ_HOME/log ]
then
mkdir $FUNZ_HOME/log
fi

HOSTNAME=`hostname`

for n in `seq 1 $1`
do
        nohup java -Dapp.home=. -classpath $LIB $MAIN $CALCULATOR > $FUNZ_HOME/log/funzd.$HOSTNAME.$n.out &
        PID=$!
        echo $PID > $FUNZ_HOME/log/funzd.$HOSTNAME.$n.pid
done
