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

# argument 1 - gmt file name
# argument 2 - taxonomy id
# argument 3 - id found in gmt file
function translate_gmt_UniProt {


	if [[ $3 == "UniProt" ]] ; then
		
		java -Xmx2G -jar ${TOOLDIR}/GenesetTools.jar translate --gmt $1 --organism $2 --oldID $3 --newID "symbol" 2>> translate_process.err 1>> translate_output.txt 
		java -Xmx2G -jar ${TOOLDIR}/GenesetTools.jar translate --gmt $1 --organism $2 --oldID $3 --newID "entrezgene" 2>> translate_process.err 1>> translate_output.txt 
		java -Xmx2G -jar ${TOOLDIR}/GenesetTools.jar translate --gmt $1 --organism $2 --oldID $3 --newID "ensembl" 2>> translate_process.err 1>> translate_output.txt 
	fi

	if [[ $3 == "entrezgene" ]] ; then
	
		java -Xmx2G -jar ${TOOLDIR}/GenesetTools.jar translate --gmt $1 --organism $2 --oldID $3 --newID "symbol" 2>> translate_process.err 1>> translate_output.txt 
		java -Xmx2G -jar ${TOOLDIR}/GenesetTools.jar translate --gmt $1 --organism $2 --oldID $3 --newID "UniProt" 2>> translate_process.err 1>> translate_output.txt 
	
		java -Xmx2G -jar ${TOOLDIR}/GenesetTools.jar translate --gmt $1 --organism $2 --oldID $3 --newID "ensembl" 2>> translate_process.err 1>> translate_output.txt 
	fi


	if [[ $3 == "symbol" ]] ; then
	
		java -Xmx2G -jar ${TOOLDIR}/GenesetTools.jar translate --gmt $1 --organism $2 --oldID $3 --newID "UniProt" 2>> translate_process.err 1>> translate_output.txt 
		java -Xmx2G -jar ${TOOLDIR}/GenesetTools.jar translate --gmt $1 --organism $2 --oldID $3 --newID "entrezgene" 2>> translate_process.err 1>> translate_output.txt 
		java -Xmx2G -jar ${TOOLDIR}/GenesetTools.jar translate --gmt $1 --organism $2 --oldID $3 --newID "ensembl" 2>> translate_process.err 1>> translate_output.txt 
	fi

	if [[ $3 == "MGI" ]] ; then
	
		java -Xmx2G -jar ${TOOLDIR}/GenesetTools.jar translate --gmt $1 --organism $2 --oldID $3 --newID "symbol" 2>> translate_process.err 1>> translate_output.txt 
		java -Xmx2G -jar ${TOOLDIR}/GenesetTools.jar translate --gmt $1 --organism $2 --oldID $3 --newID "UniProt" 2>> translate_process.err 1>> translate_output.txt 
		java -Xmx2G -jar ${TOOLDIR}/GenesetTools.jar translate --gmt $1 --organism $2 --oldID $3 --newID "entrezgene" 2>> translate_process.err 1>> translate_output.txt 
	fi

	if [[ $3 == "RGD" ]] ; then
	
		java -Xmx2G -jar ${TOOLDIR}/GenesetTools.jar translate --gmt $1 --organism $2 --oldID $3 --newID "symbol" 2>> translate_process.err 1>> translate_output.txt 
		java -Xmx2G -jar ${TOOLDIR}/GenesetTools.jar translate --gmt $1 --organism $2 --oldID $3 --newID "UniProt" 2>> translate_process.err 1>> translate_output.txt 
		java -Xmx2G -jar ${TOOLDIR}/GenesetTools.jar translate --gmt $1 --organism $2 --oldID $3 --newID "entrezgene" 2>> translate_process.err 1>> translate_output.txt 
	fi
}

function download_biocyc_data {
	echo "[Downloading current BioCyc data - for species $1]"
	curl -X POST -H 'Content-type: plication/json' --data '{"text":"'"[Downloading current BioCyc data - for species $1"'"}' `cat ${TOOLDIR}/slack_webhook`
	#URL="http://bioinformatics.ai.sri.com/ecocyc/dist/flatfiles-52983746/"
	#URL="https://bioinformatics.ai.sri.com/ecocyc/dist/flatfiles-52983746/"

	#We are using old data - from May 2021.  Try the new version - not the public data
	#January 2024
	URL="https://brg-files.ai.sri.com/subscription/dist/flatfiles-52983746"

	#Feb 2023 - changed the url back to this one! (been down for 3 months.)
	#URL="https://brg-files.ai.sri.com/public/dist"

	echo "${URL}/tier1-tier2-biopax.tar.gz" >> ${VERSIONS}/${1}cyc.txt
	curl ${URL}/tier1-tier2-biopax.tar.gz -u biocyc-flatfiles:data-20541 -I | grep "Last-Modified" >> ${VERSIONS}/${1}cyc.txt
	echo "curl ${URL}/tier1-tier2-biopax.tar.gz -o ${2}/${1}.tar.gz "  
	curl ${URL}/tier1-tier2-biopax.tar.gz -o ${2}/${1}.tar.gz -u biocyc-flatfiles:data-20541

	# unfortunately the latest biopax file seems to be missing all the genes
	# associated with the pathways.  - wait on hearing back from them before
	# implementing.
	#November 2023
        #the above url is the public distribution that is two years old.  We want the latest one.  U of T has an institutional license and the below link should work.  Unfortunately the flatfile directory name changes with each realease.  Get the directory name
	#get the index page of the subscription and find the line that starts with flatfiles
	#FLATFILE_DIR=`curl -s https://brg-files.ai.sri.com/subscription/dist/ | grep flatfiles | awk -F"[><]" ' {for(i=1;i<NF+1;i++) {tmp=match($i, /^flatfiles/); if(tmp){ print $i}}}'`
	#URL="https://brg-files.ai.sri.com/subscription/dist/${FLATFILE_DIR}"
	#echo "${URL}"
	#echo "${URL}tier1-tier2-biopax.tar.gz" >> ${VERSIONS}/${1}cyc.txt
	#curl ${URL}tier1-tier2-biopax.tar.gz  -I | grep "Last-Modified" >> ${VERSIONS}/${1}cyc.txt
	#echo "curl ${URL}tier1-tier2-biopax.tar.gz -o ${2}/${1}.tar.gz "  
	#curl ${URL}tier1-tier2-biopax.tar.gz -o ${2}/${1}.tar.gz   
}


#this function will validate, autofix and create gmt files from biopax files.
# argument 1 - biopax file name
# argument 2 - identifier to extract from file
# argument 3 - database source
#argument 4 - taxid
function process_biopax {
	#validate the biopax file
	#need to change to the validator directory in order to run the validator
	CURRENTDIR=`pwd`
	cd ${VALIDATORDIR}
	#latest version of the validator will generate the output to a .modified.owl file.
	sh validate.sh "file:${CURRENTDIR}/$1"  --auto-fix  --profile=notstrict 2>> biopax_process.err

	#create an auto-fix biopax file to use to create the gmt file
	#./validate.sh "file:${CURRENTDIR}/$1" --output=${CURRENTDIR}/${1}_updated_v1.owl --auto-fix --return-biopax 2>> biopax_process.err

	#move the auto-corrected owl file back to original directory
	mv ${1}.modified.owl ${CURRENTDIR} 

	cd ${CURRENTDIR}
	#create gmt file from the given, autofixed biopax file
	#make sure the the id searching for doesn't have any spaces for the file name
	#long -D option turns off logging when using paxtool
	java -Xmx2G -Dorg.apache.commons.logging.Log=org.apache.commons.logging.impl.NoOpLog -jar ${TOOLDIR}/GenesetTools.jar toGSEA --biopax ${1}.modified.owl --outfile ${1}_${2//[[:space:]]}.gmt --id "$2" --speciescheck --source "$3" --species "$4" 2>> biopax_process.err 1>> biopax_output.txt

	#ticket #209
        #get rid of forward slashes in the resutls gmt file (occurs in NCI, Reactome, and Humancyc) and causes
	#GSEA to produce error when creating a details file.
	sed 's/\// /g' ${1}_${2//[[:space:]]}.gmt > temp.txt
	mv temp.txt ${1}_${2//[[:space:]]}.gmt
}

# argument 1 - source to copy
#argument 2 - species
#argument 3 - division to put data into
function copy2release {

	files=$(ls *gene.gmt 2> /dev/null | wc -l)
	if [ $files != 0 ] ; then
		cat *gene.gmt > ${2}_${1}_entrezgene.gmt
		cp ${2}_${1}_entrezgene.gmt ${EG}/${3}/${2}_${1}_${dir_name}_Entrezgene.gmt
	fi
	#concatenate all the translation summaries
	
	files=$(ls *gene_summary.log 2> /dev/null | wc -l)
	if [ $files != 0 ] ; then
	#if [ -e *gene_summary.log ] ; then
		cat *gene_summary.log > ${2}_${1}_entrezgene_translation_summary.log
		cp ${2}_${1}_entrezgene_translation_summary.log ${EG}/${3}/${2}_${1}_Entrezgene_translation_summary.log
	fi
	
	#copy over ensembl files and summaries
	files=$(ls *ensembl.gmt 2> /dev/null | wc -l)
	if [ $files != 0 ] ; then
		cat *ensembl.gmt > ${2}_${1}_ensembl.gmt
		cp ${2}_${1}_ensembl.gmt ${ENSEMBL}/${3}/${2}_${1}_${dir_name}_ensembl.gmt
	fi
	#concatenate all the translation summaries
	
	files=$(ls *ensembl_summary.log 2> /dev/null | wc -l)
	if [ $files != 0 ] ; then
	#if [ -e *gene_summary.log ] ; then
		cat *ensembl_summary.log > ${2}_${1}_ensembl_translation_summary.log
		cp ${2}_${1}_ensembl_translation_summary.log ${ENSEMBL}/${3}/${2}_${1}_ensembl_translation_summary.log
	fi

	
	files=$(ls *UniProt.gmt 2> /dev/null | wc -l)
	if [ $files != 0 ] ; then
		cat *UniProt.gmt > ${2}_${1}_UniProt.gmt
		cp ${2}_${1}_UniProt.gmt ${UNIPROT}/${3}/${2}_${1}_${dir_name}_UniProt.gmt
	fi

	#concatenate all the translation summaries
	files=$(ls *UniProt_summary.log 2> /dev/null | wc -l)
	if [ $files != 0 ] ; then
	#if [ -e *UniProt_summary.log ] ; then
		cat *UniProt_summary.log > ${2}_${1}_UniProt_translation_summary.log
		cp ${2}_${1}_UniProt_translation_summary.log ${UNIPROT}/${3}/${2}_${1}_UniProt_translation_summary.log
	fi
	
	files=$(ls *symbol.gmt 2> /dev/null | wc -l)
	if [ $files != 0 ] ; then
		cat *symbol.gmt > ${2}_${1}_symbol.gmt
		cp ${2}_${1}_symbol.gmt ${SYMBOL}/${3}/${2}_${1}_${dir_name}_symbol.gmt
	fi

	#concatenate all the translation summaries
	files=$(ls *symbol_summary.log 2> /dev/null | wc -l)
	if [ $files != 0 ] ; then
	#if [ -e *symbol_summary.log ] ; then
		cat *symbol_summary.log > ${2}_${1}_symbol_translation_summary.log
		cp ${2}_${1}_symbol_translation_summary.log ${SYMBOL}/${3}/${2}_${1}_symbol_translation_summary.log
	fi

}


dir_name="January_04_2025"
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

#download humancyc data
#HUMANCYC=${SOURCE}/Humancyc
#mkdir ${HUMANCYC}
#
#issue - novemeber 20,2018 -can't download new data without new subscription
#retrying download after U of T sponsored subscriptions.
#download_biocyc_data "human" ${HUMANCYC}
#cd ${HUMANCYC}

#unzip and untar human.tar.gz file
#tar --wildcards -xvzf human.tar.gz humancyc/*level3.owl
#the release number keeps changing - need a way to change into the right directory without knowing what the new number is
# instead of specifying the name of the directory put *.  This will break if
# they change the data directory structure though.
#mv humancyc/*level3.owl ./

#for file in *.owl; do
#	process_biopax $file "UniProt" "HumanCyc" "9606"
#done
#for file in *.gmt; do
#	translate_gmt_UniProt $file "9606" "UniProt"
#done
#copy2release HumanCyc Human ${PATHWAYS}


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

#copy the files over the webserver
#mkdir /mnt/build/EM_Genesets/$dir_name
cp -R ${CUR_RELEASE}/Human /mnt/build/EM_Genesets/$dir_name/

#create a symbolic link to the latest download indicating it as current_release
#rm /mnt/build/EM_Genesets/current_release
#cd /mnt/build/EM_Genesets
#ln -sf $dir_name/ current_release
