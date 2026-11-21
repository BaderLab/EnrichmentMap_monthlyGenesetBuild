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

## Files the build needs that aren't in the repo
A fresh clone can't run the build on its own: these files have to be put in
place on the build server by hand. Paths are relative to
`EnrichmentMap_monthlyGenesetBuild/GeneSetTools/` on the code drive
(`/home/geneset-cron/em_genesets_code_drive/` on the host).

| File (on the build server) | Used for | Why it isn't in the repo | How to get it |
|---|---|---|---|
| `staticSrcFiles/biocyc/human.tar.gz` | HumanCyc pathways (static copy, Aug 2025, v29.0) | Proprietary (BioCyc academic licence) | BioCyc subscription (SRI). U of T no longer has one (March 2026), so keep the existing copy |
| `staticSrcFiles/Drugbank/drugbank.xml.zip` and its version `.txt` | DrugBank drug targets (static copy) | Proprietary (DrugBank academic licence) | DrugBank academic download (suspended in 2026 while DrugBank changes its model) |
| `staticSrcFiles/NetPath/NetPath_modified/*.owl` | NetPath pathways (static copy, netpath.org down since Feb 2024) | Not checked in yet. Check whether they can be added | Copy from the build server. The version note `staticSrcFiles/NetPath/NetPath.txt` *is* in the repo |
| `staticSrcFiles/msigdb_path/*.gmt`, `msigdb_hall/*.gmt`, `mirs/*.gmt`, `tf/*.gmt` | MSigDB C2, Hallmark, miR and TF gene sets (2026.1) | The 2023 files were deleted on Sept 15 2026 but the 2026.1 files weren't added. Check the MSigDB licence before adding them | Download from gsea-msigdb.org (Entrez gene versions). Update the `.txt` version notes next to them |
| `biopax-validator-5.0.0-SNAPSHOT/` | Validating/auto-fixing BioPAX (HumanCyc, NetPath) | Third-party (BioPAX project), modified by a former Bader lab developer to work with this pipeline | Copy from the build server |
| `GenesetTools.jar` | All the Java steps | Build output | `GeneSetTools/build_jar.sh` (see above) |
| `slack_webhook` and `geneset_tracking/slack_webhook` | Slack status messages | Secret: Slack automatically revokes webhooks that are pushed to GitHub | Slack app settings for the lab workspace |
| `biocyc_login`, `drugbank_login` | `user:password` for the BioCyc/DrugBank downloads (only used if those downloads are turned back on) | Secret | Account owners. These and `slack_webhook` are listed in `.gitignore` |

The build doesn't check for most of these up front. If one is missing, that
source's files are missing or empty, and the release isn't published (the
AllPathways check at the end of `create_genesets_release.sh`).
