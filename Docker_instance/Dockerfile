FROM adoptopenjdk/openjdk8:latest
#FROM ubuntu:18.04

# Install requirement (wget)
RUN apt-get update && apt-get install -y wget

#install git
RUN apt-get update && \
    apt-get upgrade -y && \
    apt-get install -y git

ARG HOME=/home/geneset_cron
ENV HOMEDIR=$HOME

#create a home directory to store the needed file
RUN mkdir $HOMEDIR
WORKDIR $HOMEDIR

#check out the geneset  codebase
RUN git clone https://github.com/BaderLab/EnrichmentMap_monthlyGenesetBuild.git

#install maven
RUN apt-get update -y && apt-get install maven -y

# install required programs
RUN apt-get update \
    && apt-get upgrade -y \
    && apt-get install -y --no-install-recommends \
    python-pip \
    python2.7 \
    python2.7-dev \
    python-pip \
    ssh \
    vim \
    curl \
    zip \
    unzip \
    make \
    gcc \
    libxml2-utils \
    tar \
    && apt-get autoremove \
    && apt-get clean

#install ant
ENV ANT_VERSION 1.10.5
RUN cd && \
    wget -q http://www.us.apache.org/dist//ant/binaries/apache-ant-${ANT_VERSION}-bin.tar.gz && \
    tar -xzf apache-ant-${ANT_VERSION}-bin.tar.gz && \
    mv apache-ant-${ANT_VERSION} /opt/ant && \
    rm apache-ant-${ANT_VERSION}-bin.tar.gz
ENV ANT_HOME /opt/ant
ENV PATH ${PATH}:/opt/ant/bin

#compile the geneset tools jar
WORKDIR $HOMEDIR/EnrichmentMap_monthlyGenesetBuild/GeneSetTools
RUN ant -f GeneSetTools.xml jars

#install the required perl libraries
#add perl modules needed
RUN apt-get install -y cpanminus

#install need modules
RUN cpanm CPAN::Meta \
 LWP::Simple \
 XML::Parser \
 XML::Simple \
 Data::Dumper \
 Text::CSV \ 
 Getopt::Long 
 
