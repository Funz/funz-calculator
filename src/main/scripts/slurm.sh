#!/bin/bash
# This script is intended to wrap common sh command beahviour upon SGE qrsh.
# Especially, the kill or ctrl-c will correctly cancel the SGE job using qdel.

trap "abort" INT
trap "abort" TERM

abort() {
  echo "Abort queued process..."
  scancel $qname
  echo "Queued process aborted."
}

cmd=$1
input=${@:2}
cwd=`pwd`

qname="_"$$
export pid=$cwd/node.PID

export RUN_OPT="-p std --mem=60G --cpus-per-task 1"
RUN_OPT_in=`grep "RUN_OPT=" * | sed 's/.*RUN_OPT=//' | tr -d '\n' | tr -d '\r'`
  echo "parse RUN_OPT_in "$RUN_OPT_in >> out.txt
if [ ! "$RUN_OPT_in""zz" = "zz" ] ; then
  export RUN_OPT=$RUN_OPT_in
fi
echo "RUN_OPT: <"$RUN_OPT">"

srun -J $qname $RUN_OPT --export=ALL --chdir=$cwd $cmd $input >> out.txt &
mid=$!

sleep 1

if [ `grep "Unable to allocate resources" out.txt | wc -w` != 0 ] ; then
  exit -1
fi

#lastq=`squeue | grep $qname | tail -1`
#qid=`echo ${lastq/ /} | tr -s " " | cut -d" " -f3`

wait $mid
