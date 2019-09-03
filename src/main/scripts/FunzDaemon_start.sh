#!/bin/bash

MAIN=org.funz.calculator.Calculator

LIB=`find lib -name "funz-core-*.jar"`:`find lib -name "funz-calculator-*.jar"`:`find lib -name "commons-io-2.4.jar"`:`find lib -name "commons-exec-*.jar"`:`find lib -name "commons-lang-*.jar"`:`find lib -name "ftpserver-core-*.jar"`:`find lib -name "ftplet-api-*.jar"`:`find lib -name "mina-core-*.jar"`:`find lib -name "sigar-*.jar"`:`find lib -name "slf4j-api-*.jar"`:`find lib -name "slf4j-log4j12-*.jar"`

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
