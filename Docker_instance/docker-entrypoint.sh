#!/bin/bash

#compile geneset tools jar
cd $HOMEDIR/EnrichmentMap_monthlyGenesetBuild/GeneSetTools
ant -f GeneSetTools.xml jars

#create crontab file
(crontab -l ; echo "0 0 1 * * $HOMEDIR/EnrichmentMap_monthlyGenesetBuild/GeneSetTools/create_genesets_cronjob.sh") | sort - | uniq - | crontab -

exec "$@"
