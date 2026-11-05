

BUILDLOG=`date '+%B_%Y'`_geneset_summary.log
ABSPATH=/home/geneset-cron/em_genesets_code_drive/EnrichmentMap_monthlyGenesetBuild/GeneSetTools/geneset_tracking/

cd ${ABSPATH}

#./create_genesets_release.sh >  ${BUILDLOG} 2>&1
#create_summaries.sh exits 1 if any species' report failed (its README is then left unchanged)
if ${ABSPATH}/create_summaries.sh > ${ABSPATH}/${BUILDLOG} 2>&1; then
	MSG="Finished Geneset Stats build - see summary at http://download.baderlab.org/EM_Genesets/current_release/Human/README.html"
else
	MSG="Geneset Stats build FAILED for at least one species - see ${BUILDLOG}"
fi


#sending log file via gmail no longer works - send log file through slack.
#curl -X POST -H 'Content-type: plication/json' --data '{"text":"'"${LOGFILE}"'"}' `cat ${ABSPATH}/slack_webhook`
curl -X POST -H 'Content-type: application/json' --data '{"text":"'"${MSG}"'"}' `cat ${ABSPATH}/slack_webhook`
