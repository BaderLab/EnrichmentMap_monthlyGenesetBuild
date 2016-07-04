
export PATH=/System/Library/Frameworks/JavaVM.framework/Versions/1.6.0/Commands:$PATH

export PATH=/Users/risserlin/bin:$PATH

export JAVA_HOME=/System/Library/Frameworks/JavaVM.framework/Versions/1.6.0/Home

#for required perl libraries for drugbank perl parser
eval $(perl -I$HOME/perl5/lib/perl5 -Mlocal::lib)
export PERL5LIB=${PERL5LIB}:~/lib/perl5:~/lib/perl5/lib64/perl5:~/lib/perl5/lib:~/lib/perl5/lib/i386-linux-thread-multi/:~/lib/perl5/lib/perl5/site_perl

BUILDLOG=`date '+%B_%d_%Y'`_build.log
ABSPATH=/Users/risserlin/EnrichmentMap_monthlyGenesetBuild/GeneSetTools

cd ${ABSPATH}

#./create_genesets_release.sh >  ${BUILDLOG} 2>&1
${ABSPATH}/create_genesets_release.sh > ${ABSPATH}/${BUILDLOG} 2>&1

LOGFILE=`cat ${ABSPATH}/${BUILDLOG}`

./send_mail.py   "Geneset file build log"  "${LOGFILE}"   noreply@baderlab.org ruth.isserlin@utoronto.ca   192.168.81.32
