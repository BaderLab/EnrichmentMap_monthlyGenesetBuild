#!/bin/bash
#
# Build GenesetTools.jar from the Java code in src/.
#
# Run this whenever the Java code changes - the monthly build uses whatever
# GenesetTools.jar is in this directory and no longer rebuilds it on start up.
# Inside the build container (which has ant):
#
#   sudo docker run --rm -v /home/geneset-cron/em_genesets_code_drive:/home/geneset-cron \
#       geneset_build/geneset_build \
#       /home/geneset-cron/EnrichmentMap_monthlyGenesetBuild/GeneSetTools/build_jar.sh
#
# Stops with an error (and leaves the old jar alone) if the build fails.

cd "$(dirname "$0")" || exit 1

if ! command -v ant > /dev/null ; then
	echo "ERROR: ant is not installed - run this inside the geneset_build container"
	exit 1
fi

#build into a temporary jar so a failed build can't replace a working one
if [ -e GenesetTools.jar ]; then
	cp GenesetTools.jar GenesetTools.jar.previous
fi

if ! ant -f GeneSetTools.xml clean jars ; then
	echo "ERROR: GenesetTools.jar did not compile"
	if [ -e GenesetTools.jar.previous ]; then
		mv GenesetTools.jar.previous GenesetTools.jar
		echo "the previous GenesetTools.jar has been kept"
	fi
	exit 1
fi

if [ ! -s GenesetTools.jar ]; then
	echo "ERROR: ant finished but GenesetTools.jar is missing or empty"
	exit 1
fi

rm -f GenesetTools.jar.previous
echo "Built $(pwd)/GenesetTools.jar"
