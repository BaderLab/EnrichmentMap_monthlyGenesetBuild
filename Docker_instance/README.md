# Geneset Build Docker instance

## Setup
 * create a VM (with ~ 200 GB of data)
 * install docker on new VM
 * enable sudo access for current user.
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
