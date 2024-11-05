#concatenate all the translation summary logs and place in main directory
#argument 1 - directory to comile from
#argument 2 - identifier this directory contains.
function mergesummaries {
	go_files=$(ls ${1}/${GO}/*summary.log 2> /dev/null | wc -l)
	path_files=$(ls ${1}/${PATHWAYS}/*summary.log 2> /dev/null | wc -l)
	mir_files=$(ls ${1}/${MIR}/*summary.log 2> /dev/null | wc -l)
	tf_files=$(ls ${1}/${TF}/*summary.log 2> /dev/null | wc -l)
	dis_files=$(ls ${1}/${DISEASE}/*summary.log 2> /dev/null | wc -l)

	if [ $go_files != 0 ] ; then
		cat ${1}/${GO}/*summary.log > temp.log   
	fi
	if [ $path_files != 0 ] ; then
		cat temp.log ${1}/${PATHWAYS}/*summary.log >> temp1.log
	     mv temp1.log temp.log	
	fi
        if [ $mir_files != 0 ] ; then
		cat temp.log ${1}/${MIR}/*summary.log >> temp1.log   
	     mv temp1.log temp.log	
	fi
	if [ $tf_files != 0 ] ; then
		cat temp.log ${1}/${TF}/*summary.log >> temp1.log   
	     mv temp1.log temp.log	
	fi
	if [ $dis_files != 0 ] ; then
		cat temp.log ${1}/${DISEASE}/*summary.log >> temp1.log   
	     mv temp1.log temp.log	
	fi

	mv temp.log ${1}/${2}_translation_summary.log 

	#cat ${1}/${GO}/*summary.log ${1}/${PATHWAYS}/*summary.log ${1}/${MIR}/*summary.log ${1}/${TF}/*summary.log ${1}/${DISEASE}/*summary.log > ${1}/${2}_translation_summary.log   

}

#summarize the number of genesets in one file
#argument 1 - directory to comile from
function getstats {
      echo 'Gene Set Statistics for Release ' ${dir_name} > ${1}/Summary_GeneSet_Counts_${2}.txt
        cd ${1}/${GO}
        files=$(ls *.gmt 2> /dev/null | wc -l)
        if [ $files != 0 ] ; then

                echo 'GO with IEA Stats:' >> ${1}/Summary_GeneSet_Counts_${2}.txt
                wc -l *bp_with* >> ${1}/Summary_GeneSet_Counts_${2}.txt
                wc -l *mf_with* >> ${1}/Summary_GeneSet_Counts_${2}.txt
                wc -l *cc_with* >> ${1}/Summary_GeneSet_Counts_${2}.txt
                echo 'Total GO with IEA:' >> ${1}/Summary_GeneSet_Counts_${2}.txt
                wc -l *ALL_with* >> ${1}/Summary_GeneSet_Counts_${2}.txt
                echo '------------------'  >> ${1}/Summary_GeneSet_Counts_${2}.txt
                echo 'GO no IEA Stats:' >> ${1}/Summary_GeneSet_Counts_${2}.txt
                wc -l *bp_no* >> ${1}/Summary_GeneSet_Counts_${2}.txt
                wc -l *mf_no* >> ${1}/Summary_GeneSet_Counts_${2}.txt
                wc -l *cc_no* >> ${1}/Summary_GeneSet_Counts_${2}.txt
                echo 'Total GO no IEA:' >> ${1}/Summary_GeneSet_Counts_${2}.txt
                wc -l *ALL_no* >> ${1}/Summary_GeneSet_Counts_${2}.txt
                echo '------------------'  >> ${1}/Summary_GeneSet_Counts_${2}.txt
        fi

        cd ${1}/${PATHWAYS}
        files=$(ls *.gmt 2> /dev/null | wc -l)
        if [ $files != 0 ] ; then

                echo 'Pathway Stats:' >> ${1}/Summary_GeneSet_Counts_${2}.txt
                wc -l *.gmt >> ${1}/Summary_GeneSet_Counts_${2}.txt
                echo '------------------'  >> ${1}/Summary_GeneSet_Counts_${2}.txt
        fi

	 cd ${1}/${MIR}
        files=$(ls *.gmt 2> /dev/null | wc -l)
        if [ $files != 0 ] ; then
                echo 'miR Stats:' >> ${1}/Summary_GeneSet_Counts_${2}.txt
                wc -l *.gmt >> ${1}/Summary_GeneSet_Counts_${2}.txt
                echo '------------------'  >> ${1}/Summary_GeneSet_Counts_${2}.txt
        fi

        cd ${1}/${TF}
        files=$(ls *.gmt 2> /dev/null | wc -l)
        if [ $files != 0 ] ; then

                echo 'Transcription Factor Stats:' >> ${1}/Summary_GeneSet_Counts_${2}.txt
                wc -l *.gmt >> ${1}/Summary_GeneSet_Counts_${2}.txt
                echo '------------------'  >> ${1}/Summary_GeneSet_Counts_${2}.txt
        fi

        cd ${1}/${DISEASE}
        files=$(ls *.gmt 2> /dev/null | wc -l)
        if [ $files != 0 ] ; then

                echo 'Disease Phenotypes Stats:' >> ${1}/Summary_GeneSet_Counts_${2}.txt
                wc -l *.gmt >> ${1}/Summary_GeneSet_Counts_${2}.txt
        fi

        cd ${1}/${DRUGS}
        files=$(ls *.gmt 2> /dev/null | wc -l)
        if [ $files != 0 ] ; then

                echo 'Drug Targets Stats:' >> ${1}/Summary_GeneSet_Counts_${2}.txt
                wc -l *.gmt >> ${1}/Summary_GeneSet_Counts_${2}.txt
        fi

        mv ${1}/Summary_GeneSet_Counts_${2}.txt ${OUTPUTDIR}

	#Summarize all the log files

	${TOOLDIR}/GetAllStats.sh ${2} ${1}/Build_summary_stats_${2}.log ${3}
	mv ${1}/Build_summary_stats_${2}.log ${OUTPUTDIR}
}

dir_name="November_04_2024"
TOOLDIR=`pwd`
VALIDATORDIR=${TOOLDIR}/biopax-validator-5.0.0-SNAPSHOT
STATICDIR=${TOOLDIR}/staticSrcFiles
WORKINGDIR=`pwd`
CUR_RELEASE=${WORKINGDIR}/${dir_name}

#directory names
OUTPUTDIR=${CUR_RELEASE}/Human
UNIPROT=${OUTPUTDIR}/UniProt
EG=${OUTPUTDIR}/entrezgene
SYMBOL=${OUTPUTDIR}/symbol
ENSEMBL=${OUTPUTDIR}/ensembl
SOURCE=${CUR_RELEASE}/SRC
VERSIONS=${CUR_RELEASE}/version

#under each identifier type there are three different types of interactions
GO=GO
PATHWAYS=Pathways
MIR=miRs
TF=TranscriptionFactors
DISEASE=DiseasePhenotypes
DRUGS=DrugTargets
MISC=Misc

NOIEA=no_GO_iea
WITHIEA=with_GO_iea



#create all the different distributions
cd ${EG}/${PATHWAYS}
#create two different Pathway summary files - one with PFOCR and one without
for f in `ls --ignore=*PFOCR* | grep "\.gmt$" `; do   
	cat "$f" >> ../Human_AllPathways_noPFOCR_${dir_name}_entrezgene.gmt; 
done

cat *.gmt > ../Human_AllPathways_withPFOCR_${dir_name}_entrezgene.gmt

cd ${EG}/${GO}
cat ../Human_AllPathways_withPFOCR_${dir_name}_entrezgene.gmt Human_GOALL_${WITHIEA}_${dir_name}_entrezgene.gmt > ../Human_GO_AllPathways_withPFOCR_${WITHIEA}_${dir_name}_entrezgene.gmt
cat ../Human_AllPathways_withPFOCR_${dir_name}_entrezgene.gmt Human_GOALL_${NOIEA}_${dir_name}_entrezgene.gmt > ../Human_GO_AllPathways_withPFOCR_${NOIEA}_${dir_name}_entrezgene.gmt

#create summaries with noPFOCR
cat ../Human_AllPathways_noPFOCR_${dir_name}_entrezgene.gmt Human_GOALL_${WITHIEA}_${dir_name}_entrezgene.gmt > ../Human_GO_AllPathways_noPFOCR_${WITHIEA}_${dir_name}_entrezgene.gmt
cat ../Human_AllPathways_noPFOCR_${dir_name}_entrezgene.gmt Human_GOALL_${NOIEA}_${dir_name}_entrezgene.gmt > ../Human_GO_AllPathways_noPFOCR_${NOIEA}_${dir_name}_entrezgene.gmt

#create two new all pathways files with GOBP included
cat ../Human_AllPathways_withPFOCR_${dir_name}_entrezgene.gmt Human_GO_bp_${WITHIEA}_entrezgene.gmt > ../Human_GOBP_AllPathways_withPFOCR_${WITHIEA}_${dir_name}_entrezgene.gmt
cat ../Human_AllPathways_withPFOCR_${dir_name}_entrezgene.gmt Human_GO_bp_${NOIEA}_entrezgene.gmt > ../Human_GOBP_AllPathways_withPFOCR_${NOIEA}_${dir_name}_entrezgene.gmt

#create summaries with noPFOCR
cat ../Human_AllPathways_noPFOCR_${dir_name}_entrezgene.gmt Human_GO_bp_${WITHIEA}_entrezgene.gmt > ../Human_GOBP_AllPathways_noPFOCR_${WITHIEA}_${dir_name}_entrezgene.gmt
cat ../Human_AllPathways_noPFOCR_${dir_name}_entrezgene.gmt Human_GO_bp_${NOIEA}_entrezgene.gmt > ../Human_GOBP_AllPathways_noPFOCR_${NOIEA}_${dir_name}_entrezgene.gmt

#merge all the summaries
mergesummaries ${EG} entrezgene

#create all the different distributions
cd ${ENSEMBL}/${PATHWAYS}
#create two different Pathway summary files - one with PFOCR and one without
for f in `ls --ignore=*PFOCR* | grep "\.gmt$" `; do   
	cat "$f" >> ../Human_AllPathways_noPFOCR_${dir_name}_ensembl.gmt; 
done

cat *.gmt > ../Human_AllPathways_withPFOCR_${dir_name}_ensembl.gmt

cd ${ENSEMBL}/${GO}
cat ../Human_AllPathways_withPFOCR_${dir_name}_ensembl.gmt Human_GOALL_${WITHIEA}_${dir_name}_ensembl.gmt > ../Human_GO_AllPathways_withPFOCR_${WITHIEA}_${dir_name}_ensembl.gmt
cat ../Human_AllPathways_withPFOCR_${dir_name}_ensembl.gmt Human_GOALL_${NOIEA}_${dir_name}_ensembl.gmt > ../Human_GO_AllPathways_withPFOCR_${NOIEA}_${dir_name}_ensembl.gmt

#create summaries with noPFOCR
cat ../Human_AllPathways_noPFOCR_${dir_name}_ensembl.gmt Human_GOALL_${WITHIEA}_${dir_name}_ensembl.gmt > ../Human_GO_AllPathways_noPFOCR_${WITHIEA}_${dir_name}_ensembl.gmt
cat ../Human_AllPathways_noPFOCR_${dir_name}_ensembl.gmt Human_GOALL_${NOIEA}_${dir_name}_ensembl.gmt > ../Human_GO_AllPathways_noPFOCR_${NOIEA}_${dir_name}_ensembl.gmt

#create two new all pathways files with GOBP included
cat ../Human_AllPathways_withPFOCR_${dir_name}_ensembl.gmt Human_GO_bp_${WITHIEA}_ensembl.gmt > ../Human_GOBP_AllPathways_withPFOCR_${WITHIEA}_${dir_name}_ensembl.gmt
cat ../Human_AllPathways_withPFOCR_${dir_name}_ensembl.gmt Human_GO_bp_${NOIEA}_ensembl.gmt > ../Human_GOBP_AllPathways_withPFOCR_${NOIEA}_${dir_name}_ensembl.gmt

#create summaries with noPFOCR
cat ../Human_AllPathways_noPFOCR_${dir_name}_ensembl.gmt Human_GO_bp_${WITHIEA}_ensembl.gmt > ../Human_GOBP_AllPathways_noPFOCR_${WITHIEA}_${dir_name}_ensembl.gmt
cat ../Human_AllPathways_noPFOCR_${dir_name}_ensembl.gmt Human_GO_bp_${NOIEA}_ensembl.gmt > ../Human_GOBP_AllPathways_noPFOCR_${NOIEA}_${dir_name}_ensembl.gmt

#merge all the summaries
mergesummaries ${ENSEMBL} ensembl

cd ${SYMBOL}/${PATHWAYS}
#create two different Pathway summary files - one with PFOCR and one without
for f in `ls --ignore=*PFOCR* | grep "\.gmt$" `; do   
	cat "$f" >> ../Human_AllPathways_noPFOCR_${dir_name}_symbol.gmt; 
done

cat *.gmt > ../Human_AllPathways_withPFOCR_${dir_name}_symbol.gmt

cd ${SYMBOL}/${GO}
cat ../Human_AllPathways_withPFOCR_${dir_name}_symbol.gmt Human_GOALL_${WITHIEA}_${dir_name}_symbol.gmt > ../Human_GO_AllPathways_withPFOCR_${WITHIEA}_${dir_name}_symbol.gmt
cat ../Human_AllPathways_withPFOCR_${dir_name}_symbol.gmt Human_GOALL_${NOIEA}_${dir_name}_symbol.gmt > ../Human_GO_AllPathways_withPFOCR_${NOIEA}_${dir_name}_symbol.gmt

#create summaries with noPFOCR
cat ../Human_AllPathways_noPFOCR_${dir_name}_symbol.gmt Human_GOALL_${WITHIEA}_${dir_name}_symbol.gmt > ../Human_GO_AllPathways_noPFOCR_${WITHIEA}_${dir_name}_symbol.gmt
cat ../Human_AllPathways_noPFOCR_${dir_name}_symbol.gmt Human_GOALL_${NOIEA}_${dir_name}_symbol.gmt > ../Human_GO_AllPathways_noPFOCR_${NOIEA}_${dir_name}_symbol.gmt

#create two new all pathways files with GOBP included
cat ../Human_AllPathways_withPFOCR_${dir_name}_symbol.gmt Human_GO_bp_${WITHIEA}_symbol.gmt > ../Human_GOBP_AllPathways_withPFOCR_${WITHIEA}_${dir_name}_symbol.gmt
cat ../Human_AllPathways_withPFOCR_${dir_name}_symbol.gmt Human_GO_bp_${NOIEA}_symbol.gmt > ../Human_GOBP_AllPathways_withPFOCR_${NOIEA}_${dir_name}_symbol.gmt

#create two new all pathways files with GOBP included no PFOCR
cat ../Human_AllPathways_noPFOCR_${dir_name}_symbol.gmt Human_GO_bp_${WITHIEA}_symbol.gmt > ../Human_GOBP_AllPathways_noPFOCR_${WITHIEA}_${dir_name}_symbol.gmt
cat ../Human_AllPathways_noPFOCR_${dir_name}_symbol.gmt Human_GO_bp_${NOIEA}_symbol.gmt > ../Human_GOBP_AllPathways_noPFOCR_${NOIEA}_${dir_name}_symbol.gmt


#merge all the summaries
mergesummaries ${SYMBOL} symbol

cd ${UNIPROT}/${PATHWAYS}
#create two different Pathway summary files - one with PFOCR and one without
for f in `ls --ignore=*PFOCR* | grep "\.gmt$" `; do   
	cat "$f" >> ../Human_AllPathways_noPFOCR_${dir_name}_UniProt.gmt; 
done

cat *.gmt > ../Human_AllPathways_withPFOCR_${dir_name}_UniProt.gmt
cd ${UNIPROT}/${GO}
cat ../Human_AllPathways_withPFOCR_${dir_name}_UniProt.gmt Human_GOALL_${WITHIEA}_${dir_name}_UniProt.gmt > ../Human_GO_AllPathways_withPFOCR_${WITHIEA}_${dir_name}_UniProt.gmt
cat ../Human_AllPathways_withPFOCR_${dir_name}_UniProt.gmt Human_GOALL_${NOIEA}_${dir_name}_UniProt.gmt > ../Human_GO_AllPathways_withPFOCR_${NOIEA}_${dir_name}_UniProt.gmt

cat ../Human_AllPathways_noPFOCR_${dir_name}_UniProt.gmt Human_GOALL_${WITHIEA}_${dir_name}_UniProt.gmt > ../Human_GO_AllPathways_noPFOCR_${WITHIEA}_${dir_name}_UniProt.gmt
cat ../Human_AllPathways_noPFOCR_${dir_name}_UniProt.gmt Human_GOALL_${NOIEA}_${dir_name}_UniProt.gmt > ../Human_GO_AllPathways_noPFOCR_${NOIEA}_${dir_name}_UniProt.gmt

#create two new all pathways files with GOBP included
cat ../Human_AllPathways_withPFOCR_${dir_name}_UniProt.gmt Human_GO_bp_${WITHIEA}_UniProt.gmt > ../Human_GOBP_AllPathways_withPFOCR_${WITHIEA}_${dir_name}_UniProt.gmt
cat ../Human_AllPathways_withPFOCR_${dir_name}_UniProt.gmt Human_GO_bp_${NOIEA}_UniProt.gmt > ../Human_GOBP_AllPathways_withPFOCR_${NOIEA}_${dir_name}_UniProt.gmt

cat ../Human_AllPathways_noPFOCR_${dir_name}_UniProt.gmt Human_GO_bp_${WITHIEA}_UniProt.gmt > ../Human_GOBP_AllPathways_noPFOCR_${WITHIEA}_${dir_name}_UniProt.gmt
cat ../Human_AllPathways_noPFOCR_${dir_name}_UniProt.gmt Human_GO_bp_${NOIEA}_UniProt.gmt > ../Human_GOBP_AllPathways_noPFOCR_${NOIEA}_${dir_name}_UniProt.gmt

#merge all the summaries
mergesummaries ${UNIPROT} UniProt

#create the stats summary
getstats ${EG} "entrezgene" ${SOURCE}
getstats ${UNIPROT} "UniProt" ${SOURCE}
getstats ${SYMBOL} "symbol" ${SOURCE}
getstats ${ENSEMBL} "ensembl" ${SOURCE}

#the assumption is that this is just a re-do and fix.  copy the updated summaries to production.
cp -R ${CUR_RELEASE}/Human /mnt/build/EM_Genesets/$dir_name/
