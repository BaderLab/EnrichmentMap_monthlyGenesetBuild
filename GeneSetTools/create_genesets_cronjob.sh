
#export PATH=/usr/lib/jvm/java-6-oracle/bin:$PATH

#export JAVA_HOME=/usr/lib/jvm/java-6-oracle/

#for required perl libraries for drugbank perl parser
eval $(perl -I$HOMEDIR/perl5/lib/perl5 -Mlocal::lib)
export PERL5LIB=${PERL5LIB}:~/lib/perl5:~/lib/perl5/lib64/perl5:~/lib/perl5/lib:~/lib/perl5/lib/i386-linux-thread-multi/:~/lib/perl5/lib/perl5/site_perl

BUILDLOG=`date '+%B_%d_%Y'`_build.log
ABSPATH=$HOMEDIR/EnrichmentMap_monthlyGenesetBuild/GeneSetTools

cd ${ABSPATH}

#./create_genesets_release.sh >  ${BUILDLOG} 2>&1
${ABSPATH}/create_genesets_release.sh > ${ABSPATH}/${BUILDLOG} 2>&1

LOGFILE=`cat ${ABSPATH}/${BUILDLOG}`

#./send_mail.py   "Geneset file build log"  "${LOGFILE}"   auto.geneset.build@gmail.com ruth.isserlin@utoronto.ca smtp.gmail.com geneset-cron

#sending log file via gmail no longer works - send log file through slack.
#curl -X POST -H 'Content-type: plication/json' --data '{"text":"'"${LOGFILE}"'"}' `cat ${ABSPATH}/slack_webhook`
curl -X POST -H 'Content-type: plication/json' --data '{"text":"'"Finished Geneset build - see log file for details"'"}' `cat ${ABSPATH}/slack_webhook`
