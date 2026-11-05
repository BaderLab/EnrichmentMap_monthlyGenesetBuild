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
   
 * Set up cron job to run the build once a month. - the cronjob will launch the docker, build the geneset files, copy them onto the download server and closer the docker instance.    
   * The cronjob is associated with the root user. 
   * log into the root user
   * launch the cron configuration file
   ```
   cronjob -e
   ```
   * Add the following lines to the crontab file:
   ```
   MAILTO=rr.weinberger@gmail.com
   5 1 1 * * sudo docker run -v /home/geneset-cron/geneset_build_drive:/mnt/build -v /home/geneset-cron/em_genesets_code_drive:/home/geneset-cron geneset_build/geneset_build /home/geneset-cron/EnrichmentMap_monthlyGenesetBuild/GeneSetTools/create_genesets_cronjob.sh  
   ```
 * To run the docker independentally you can run the following commnad
    ```
   sudo docker run -dit  -v /home/geneset-cron/geneset_build_drive:/mnt/build -v /home/geneset-cron/em_genesets_code_drive:/home/geneset-cron --name geneset_build geneset_build/geneset_build /bin/bash 
   ```

## Updating GenesetTools.jar
The container does **not** compile the Java code when it starts (it used to, and
when that failed the build carried on with the old jar without saying so). The
monthly build uses whatever `GeneSetTools/GenesetTools.jar` is on the code drive.

Whenever the Java code in `GeneSetTools/src` changes, rebuild the jar as a
separate step:
```
sudo docker run --rm -v /home/geneset-cron/em_genesets_code_drive:/home/geneset-cron geneset_build/geneset_build /home/geneset-cron/EnrichmentMap_monthlyGenesetBuild/GeneSetTools/build_jar.sh
```
`build_jar.sh` stops with `ERROR: GenesetTools.jar did not compile` and keeps the
previous jar if the build fails. Run the tests (`GeneSetTools/tests/run_tests.sh`)
before deploying a new jar.
