# Build with:
#
# docker build -t nlesc/xenon-slurm:14.11.9 .
#
# Run with:
#
# docker run -d --name=xenon-slurm nlesc/xenon-slurm
#
# Test with:
#
# ssh xenon@<ip of xenon-slurm container>
#

FROM nlesc/xenon-phusion-base
MAINTAINER Stefan Verhoeven "s.verhoeven@esciencecenter.nl"

ENV SLURM_VERSION 14.11.9

ADD build.sh /tmp/build.sh
RUN sh /tmp/build.sh # && rm /tmp/build.sh

ADD munge.key /etc/munge/munge.key
ADD service /etc/service
ADD slurm.conf /etc/slurm/slurm.conf

CMD ["/sbin/my_init"]
