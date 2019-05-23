# Geneset Build Docker instance

## Setup
 * create a VM (with ~ 200 GB of data)
 * install docker on new VM
 ```
 sudo apt-get update
 sudo apt-get install \
   apt-transport-https \
   ca-certificates \
   curl \
   gnupg2 \
   software-properties-common
 curl -fsSL https://download.docker.com/linux/debian/gpg | sudo apt-key add -
 sudo apt-key fingerprint 0EBFCD88
 sudo add-apt-repository \
   "deb [arch=amd64] https://download.docker.com/linux/debian \
   $(lsb_release -cs) \
    stable"
 sudo apt-get update
 sudo apt-get install docker-ce docker-ce-cli containerd.io
 ```
 * enable sudo access for current user.
 ```
 apt-get install sudo
 usermod -aG sudo geneset-cron
 ```
 * check out EnrichmentMap_monthlyGenesetBuild from the Baderlab github (https://github.com/BaderLab/EnrichmentMap_monthlyGenesetBuild.git)
 * Change into the Docker_instance directory
 * Build docker
   ```
   sudo docker build -t geneset_build/geneset_build .
   ```
 * Run docker
   ```
   sudo docker run -dit  -v /home/geneset-cron/geneset_build_drive:/home/geneset_build_data --name geneset_build geneset_build/geneset_build  
   ```
