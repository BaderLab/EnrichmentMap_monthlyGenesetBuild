#!/bin/bash

#compile geneset tools jar
cd $HOMEDIR/EnrichmentMap_monthlyGenesetBuild/GeneSetTools
ant -f GeneSetTools.xml jars

exec "$@"
