#!/bin/bash

echo "Checking local dependencies"
if [ $(dpkg-query -W -f='${Status}' autossh 2>/dev/null | grep -c "ok installed") -eq 0 ];
then
  echo "  'autossh' missing. Install:"
  sudo apt install -y autossh;
fi
if [ $(dpkg-query -W -f='${Status}' socat 2>/dev/null | grep -c "ok installed") -eq 0 ];
then
  echo "  'socat' missing. Install:"
  sudo apt install -y socat;
fi
if [ $(dpkg-query -W -f='${Status}' awscli 2>/dev/null | grep -c "ok installed") -eq 0 ];
then
  echo "  'awscli' missing. Install:"
  sudo apt install -y awscli;
fi


imageid="ami-0bdf93799014acdc4" # Ubuntu 18 @ Frankfurt
user="ubuntu"
instance_type="c5d.4xlarge" # 16 CPU
region="eu-central-1" # Frankfurt
size="8"

packages=""
deps=""
configure=""

tunnel_port="2222"
udp_port="19001"
tcp_port="19010"
offset_ports="0"

usage() {
cat <<EOFusage
usage : $0 [-h]
           AWS EC2 parameters:
           [-i <ami-xxxxx> (ami-04169656fea786776)] [-u <username> (ubuntu)] [-t <instance type> (t1.micro)] [-r <AWS region> (us-east-1)] [-s <storage size in Gb> (8)]
           EC2 instance parameters:
           [-a <some-apt-packages> ()] [-d <some files to upload in ~/.> ()] [-c <more configure instructions> ()]
           Funz network parameters:
           [-o <ports_offset> (0)] [-p <listening UDP port> (19001)]
EOFusage
}
while getopts  "i:u:t:r:s:a:d:c:o:p:h:" flag; do
 case $flag in
    i) imageid=$OPTARG;;
    u) user=$OPTARG;;
    t) instance_type=$OPTARG;;
    r) region=$OPTARG;;
    s) size=$OPTARG;;
    a) packages=$OPTARG;;
    d) deps=$OPTARG;;
    c) configure=$OPTARG;;
    o) offset_ports=$OPTARG;;
    p) udp_port=$OPTARG;;
    h) usage
       exit 0;;
 esac
done
shift $(( $OPTIND-1 ))

tunnel_port="$(($tunnel_port + $offset_ports))"
tcp_port="$(($tcp_port + $offset_ports))"

echo "Instanciating server"
echo "  image:         "$imageid
echo "  user:          "$user
echo "  instance type: "$instance_type
echo "  region:        "$region
echo "  size:          "$size
echo "  ports (offset  "$offset_ports"):"
echo "    tunnel:      "$tunnel_port
echo "    tcp:         "$tcp_port
echo "    udp:         "$udp_port
echo "  packages:      "$packages
echo "  configure:     "$configure
echo "  dependencies:  "$deps

wait_seconds="5"
myip=`dig +short myip.opendns.com @resolver1.opendns.com`
echo "  local IP:      "$myip
FUNZ_PATH="$( cd "$(dirname "$0")" ; pwd -P )"

stamp=`date +"%Y%m%d_%H%M%S""__"$RANDOM`
echo "  stamp:         "$stamp
    
teardown() {
    if [ ! -z $id ]; then
        echo "Terminate instance..."
        volid=`aws ec2 describe-volumes --region $region | grep "\"VolumeId\|"$id"" | sed -n -e '/InstanceId/,$p' | grep VolumeId | cut -d: -f2 | tr -d '"' | tr -d ' ' | head -1 2>&1`
        aws ec2 terminate-instances --instance-ids $id --region $region
        while true; do
            state=`aws ec2 describe-instances --region $region | grep "\"Name\|"$id"" | sed -n -e '/InstanceId/,$p' | grep Name | cut -d: -f2 | tr -d '"' | tr -d ' ' | head -1 2>&1`
            if [[ $state == "terminated" ]]; then
                break
            else
                echo "                    ... $state"
                sleep 1
            fi
        done
        echo "                   ... terminated"
    
        echo "Delete volume..."
        aws ec2 delete-volume --region $region --volume-id $volid
        echo "             ... deleted"
    fi
    
    echo "Cleanup tmp objects"
    if [ $secgrp = "tmp-"$stamp ]; then
        aws ec2 delete-security-group --region $region --group-name $secgrp
    fi
    if [ $key = $tmpkey.pem ]; then
        aws ec2 delete-key-pair --region $region --key-name $tmpkey
    fi
    rm -f /tmp/.*-$stamp.*

    echo "Destroy connections"
    while read line
    do
      kill -9 $line
    done < /tmp/.$stamp.pid
}
trap teardown INT


key=`ls *.pem 2> /dev/null | head -1`
if [ -z "$key" ]; then
    echo "Create key pair"
    tmpkey="tmp-"$stamp
    key=/tmp/.$tmpkey.pem
    aws ec2 create-key-pair --key-name $tmpkey --query 'KeyMaterial' --region $region --output text > $key
    chmod 400 $key
fi
key_name=`echo $key | cut -d. -f2`


echo "Create sec group"
secgrp="tmp-"$stamp
aws ec2 create-security-group --group-name $secgrp --region $region --description "tmp sec group" > /tmp/.deploy-$stamp.log
aws ec2 authorize-security-group-ingress --group-name $secgrp --region $region --protocol all --cidr $myip/0 >> /tmp/.deploy-$stamp.log


echo "Starting instance..."
id=`aws ec2 run-instances --image-id $imageid --count 1 --instance-type $instance_type --region $region --key-name $key_name --security-groups $secgrp --block-device-mappings 'DeviceName=/dev/sda1,Ebs={VolumeSize='$size'}' | grep InstanceId | cut -d: -f2 | cut -d, -f1 | tr -d \" | tr -d ' ' 2>&1`
echo "                 ... "$id


echo "Waiting for IP..."
while true; do
    sleep $wait_seconds
    ip=`aws ec2 describe-instances --region $region | grep "PublicIpAddress\|"$id"" | sed -n -e '/InstanceId/,$p' | grep -E -o "([0-9]{1,3}[\.]){3}[0-9]{1,3}" | head -1`
    if [ ! -z "$ip" ]; then
        break
    else
        echo "              ..."
        sleep $wait_seconds
    fi
done
echo "              ... "$ip

i="-o ServerAliveInterval=10 -o StrictHostKeyChecking=no -i $key $user@$ip"
echo "|              Connect using:"
echo "|              ssh "$i


echo "Checking instance state..."
while true; do
    sleep $wait_seconds
    un=`ssh $i "uname -a"`
    if [ ! -z "$un" ]; then
        break
    else
        echo "              ..."
        sleep $wait_seconds
    fi
done
echo "              ... "$un


echo "Configure instance:"

echo "  kill running apt"
ssh $i "sudo mv /usr/bin/unattended-upgrade /usr/bin/unattended-upgrade.sav; sudo mv /usr/lib/apt/apt.systemd.daily /usr/lib/apt/apt.systemd.daily.sav" > /tmp/.packages0-$stamp.log 2>&1
ssh $i "sudo systemctl stop apt-daily.service; sudo systemctl kill --kill-who=all apt-daily.service; sudo killall apt-daily.service; while ! (systemctl list-units --all apt-daily.service | fgrep -q dead); do echo \"...\"; sleep 1; done;" >> /tmp/.packages0-$stamp.log 2>&1
ssh $i "sudo systemctl stop apt-daily.timer;sudo systemctl kill --kill-who=all apt-daily.timer; sudo killall apt-daily.timer; while ! (systemctl list-units --all apt-daily.timer | fgrep -q dead); do echo \"...\"; sleep 1; done;" >> /tmp/.packages0-$stamp.log 2>&1

echo "  install packages"
ssh $i "sudo apt update; sleep 10; sudo apt update; sleep 10; sudo apt -y install socat openjdk-8-jre-headless $packages" > /tmp/.packages-$stamp.log 2>&1
echo "  copy dependencies"
if [ ! -z "$deps" ]; then
    scp -i $key -oStrictHostKeyChecking=no -r $deps $user@$ip:. > /tmp/.dependencies-$stamp.log 2>&1
fi
echo "  configure"
if [ ! -z "$configure" ]; then
    ssh $i "$configure" > /tmp/.configure-$stamp.log 2>&1
fi


echo "Deploy Funz"
scp -i $key -oStrictHostKeyChecking=no -r $FUNZ_PATH/. $user@$ip:. > /tmp/.deploy-$stamp.log 2>&1


echo "Configure Funz:"

echo "  calculator.xml"
ssh $i "sed -e \"s/<CALCULATOR /<CALCULATOR port='$tcp_port' /g\" calculator.xml > .calculator.xml; mv .calculator.xml calculator.xml"
ssh $i "sed -e \"1,/>/s/>/><HOST name='127.0.0.1' port='$udp_port'\/>/\" calculator.xml > .calculator.xml; mv .calculator.xml calculator.xml"

echo "  remote/UDP:$udp_port -> remote/TCP:$tunnel_port -[SSH]-> local/TCP:$tunnel_port -> local/UDP:$udp_port"
autossh -M 0 -N -R $tunnel_port:localhost:$tunnel_port $i > /tmp/.tunUDP-$stamp.log 2>&1 &
echo "$!" >> /tmp/.$stamp.pid
autossh $i "socat -u udp4-recvfrom:$udp_port,reuseaddr,fork TCP:localhost:$tunnel_port" > /tmp/.UDP2TCP-$stamp.log 2>&1 &
echo "$!" >> /tmp/.$stamp.pid
socat -u tcp4-listen:$tunnel_port,reuseaddr,fork UDP:localhost:$udp_port > /tmp/.TCP2UDP-$stamp.log 2>&1 &
echo "$!" >> /tmp/.$stamp.pid


echo "  local/TCP:$tcp_port -[SSH]-> remote/TCP:$tcp_port"
autossh -M 0 -g -N -L $tcp_port:localhost:$tcp_port $i > /tmp/.tunUDP-$stamp.log 2>&1 &
echo "$!" >> /tmp/.$stamp.pid


echo "Starting Funz daemon..."
autossh $i -t "./FunzDaemon.sh" 
teardown
  
