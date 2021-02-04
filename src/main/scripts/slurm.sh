#!/bin/bash
# This script is intended to wrap common sh command beahviour upon SLURM srun.
# Especially, the kill or ctrl-c will correctly cancel the SLURM job using scancel.

abort() {
  echo "Abort SLURM process: "$qname
  scancel -n $qname
  echo "SLURM process aborted."
}
trap "abort" INT
trap "abort" TERM 

cmd=$1
input=${@:2}
cwd=`pwd`

qname="_"$$
export pid=$cwd/node.PID

# Read SBATCH options anywhere in input files
export SBATCH_OPT=""
SBATCH_OPT_in=`grep "SBATCH " * | sed 's/.*SBATCH //' | tr '\n' ' ' | tr -d '\r'`
  echo "parse SBATCH "$SBATCH_OPT_in >> out.txt
if [ ! "$SBATCH_OPT_in""zz" = "zz" ] ; then
  export SBATCH_OPT=$SBATCH_OPT_in
fi
echo "SBATCH: "$SBATCH_OPT

srun -J $qname $SBATCH_OPT --export=ALL --chdir=$cwd $cmd $input >> out.txt &
mid=$!

sleep 1

if [ `grep "Unable to allocate resources" out.txt | wc -w` != 0 ] ; then
  exit -1
fi

wait $mid
