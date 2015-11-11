#!/usr/bin/env bash

# deps
apt-get update
apt-get install -y build-essential bzip2 munge libmunge-dev python

# compile
curl -fsL http://www.schedmd.com/download/total/slurm-$SLURM_VERSION.tar.bz2 | tar xfj - -C /opt/
cd /opt/slurm-$SLURM_VERSION
./configure --prefix=/usr --sysconfdir=/etc/slurm
make -j 2
make install

# config
mkdir /var/run/munge
/usr/sbin/useradd -d /home/slurm -m -u 64030 -U --shell /bin/false slurm

# cleanup
apt-get remove -y build-essential libmunge-dev
apt-get clean
rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/* /opt/slurm-$SLURM_VERSION
