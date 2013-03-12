
export PATH=/System/Library/Frameworks/JavaVM.framework/Versions/1.6.0/Commands:$PATH

export PATH=/Network/Servers/server1.baderlab.med.utoronto.ca/Volumes/RAID/Users/risserlin/bin:$PATH

export JAVA_HOME=/System/Library/Frameworks/JavaVM.framework/Versions/1.6.0/Home

BUILDLOG=`date '+%B_%d_%Y'`_build.log
ABSPATH=/Network/Servers/server1.baderlab.med.utoronto.ca/Volumes/RAID/Users/risserlin/AutomaticGeneSetCreation/GeneSetTools

cd ${ABSPATH}

#./create_genesets_release.sh >  ${BUILDLOG} 2>&1
${ABSPATH}/create_genesets_release.sh > ${ABSPATH}/${BUILDLOG} 2>&1

LOGFILE=`cat ${ABSPATH}/${BUILDLOG}`

./send_mail.py   "Geneset file build log"  "${LOGFILE}"   noreply@baderlab.org ruth.isserlin@utoronto.ca   server1.baderlab.med.utoronto.ca
