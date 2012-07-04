#!/bin/bash


function get_pc_version {
	    # pathway commons uses a release date instead of a version
	    curl -s http://www.pathwaycommons.org/pc-snapshot/ | grep "current-release" | awk '{print $6}' > ${VERSIONS}/pathwaycommons.txt
}


function download_pc_data {
	    echo "[Downloading current Pathway Commons data]"
	    URL="http://www.pathwaycommons.org/pc-snapshot/current-release/gsea/by_source/"
	    curl ${URL}/nci-nature-entrez-gene-id.gmt.zip -o ${PATHWAYCOMMONS}/nci-nature-entrez-gene-id.gmt.zip -s  -w "Pathway Commons : HTTP code - %{http_code}\tDownload time:%{time_total} millisec\tFile size:%{size_download} Bytes\n"
	    get_pc_version
}



#this function will get the date of the file if there is no other way to get a version from it
# argument 1 - name of the file
# argument 2 - datasource to add it to
function get_webfile_version {
	echo "$2" >> ${VERSIONS}/${2}.txt
	echo "$1" >> ${VERSIONS}/${2}.txt
	curl $1 -I | grep "Last-Modified" >> ${VERSIONS}/${2}.txt
	echo "========" >> ${VERSIONS}/${2}.txt
}

#download NCI from PID
function download_nci_data {
	echo "[Downloading current NCI data]"
	URL="ftp://ftp1.nci.nih.gov/pub/PID/BioPAX_Level_3/NCI-Nature_Curated.bp3.owl.gz"
	curl ${URL} -o ${NCI}/NCI-Nature_Curated.bp3.owl.gz -s  -w "NCI : HTTP code - %{http_code}\tDownload time:%{time_total} millisec\tFile size:%{size_download} Bytes\n"
	get_webfile_version $URL "NCI_Nature"	
}

#get the 25 NetPath pathways they have on their website.  
# If new pathways are added we have no way of knowing and script will not get them.
function download_netpath_data {
	echo "[Downloading current NetPath data]"
	URL="http://www.netpath.org/data/biopax/"
	for Num in {1..25}; do
		get_webfile_version ${URL}/NetPath_${Num}.owl "NetPath"
		curl ${URL}/NetPath_${Num}.owl -o ${NETPATH}/NetPath_${Num}.owl -s  -w "NetPath_${Num}.owl : HTTP code - %{http_code}\tDownload time:%{time_total} millisec\tFile size:%{size_download} Bytes\n"
	done
}

function download_reactome_data {
	echo "[Downloading current Reactome data]"
	URL="http://www.reactome.org/download/current/"
	curl ${URL}/biopax3.zip -o ${REACTOME}/biopax3.zip -s  -w "Reactome : HTTP code - %{http_code}\tDownload time:%{time_total} millisec\tFile size:%{size_download} Bytes\n"
	get_webfile_version ${URL}/biopax3.zip "Reactome"
}

#argument 1 - species, either human or mouse
#argument 2 - directory to put the file
function download_biocyc_data {
	echo "[Downloading current BioCyc data - for species $1]"
	URL="http://bioinformatics.ai.sri.com/ecocyc/dist/flatfiles-52983746/"
	echo "${URL}/${1}.tar.gz" >> ${VERSIONS}/${1}cyc.txt
	curl ${URL}/${1}.tar.gz -u biocyc-flatfiles:data-20541 -I | grep "Last-Modified" >> ${VERSIONS}/${1}cyc.txt
	curl ${URL}/${1}.tar.gz -o ${2}/${1}.tar.gz -u biocyc-flatfiles:data-20541 -s  -w "Biocyc : HTTP code - %{http_code}\tDownload time:%{time_total} millisec\tFile size:%{size_download} Bytes\n"
}

# Go human data comes directly from ebi as they are the primary curators of human GO annotations
function download_GOhuman_data {
	echo "[Downloading current Go Human EBI data]"
	URL="ftp://ftp.ebi.ac.uk/pub/databases/GO/goa/HUMAN/"
	curl ${URL}/gene_association.goa_human.gz -o ${GOSRC}/gene_association.goa_human.gz -s  -w "GO (Human GAF) : HTTP code - %{http_code}\tDownload time:%{time_total} millisec\tFile size:%{size_download} Bytes\n"
	get_webfile_version ${URL}/gene_association.goa_human.gz "GO_Human"

	#get the obo file from the gene ontology website
	echo "[Downloading current GO OBO file]"
	URL="ftp://ftp.geneontology.org/pub/go/ontology/obo_format_1_2/"
	curl ${URL}/gene_ontology.1_2.obo -o ${GOSRC}/gene_ontology.1_2.obo -s  -w "GO (obo) : HTTP code - %{http_code}\tDownload time:%{time_total} millisec\tFile size:%{size_download} Bytes\n"
	get_webfile_version ${URL}/gene_ontology.1_2.obo "GO_OBO_FILE"


}

#Mouse data is downloaded from the Gene ontology website and comes from MGI
function download_GOmouse_data {
	echo "[Downloading current Go Mouse MGI  data]"
	URL="http://cvsweb.geneontology.org/cgi-bin/cvsweb.cgi/go/gene-associations/gene_association.mgi.gz?rev=HEAD"
	curl $URL -o ${GOSRC}/gene_association.mgi.gz -s  -w "GO (mouse gaf) : HTTP code - %{http_code}\tDownload time:%{time_total} millisec\tFile size:%{size_download} Bytes\n"
	get_webfile_version ${URL} "GO_Mouse"
}

#this function will validate, autofix and create gmt files from biopax files.
# argument 1 - biopax file name
# argument 2 - identifier to extract from file
# argument 3 - database source
function process_biopax {
	#validate the biopax file
	#need to change to the validator directory in order to run the validator
	CURRENTDIR=`pwd`
	cd ${VALIDATORDIR}
	./validate.sh "file:${CURRENTDIR}/$1" --output=${CURRENTDIR}/${1}_validationresults_initial.xml --autofix  2>> biopax_process.err

	#create an auto-fix biopax file to use to create the gmt file
	./validate.sh "file:${CURRENTDIR}/$1" --output=${CURRENTDIR}/${1}_updated_v1.owl --auto-fix --return-biopax 2>> biopax_process.err

	cd ${CURRENTDIR}
	#create gmt file from the given, autofixed biopax file
	#make sure the the id searching for doesn't have any spaces for the file name
	#long -D option turns off logging when using paxtool
	java -Xmx2G -Dorg.apache.commons.logging.Log=org.apache.commons.logging.impl.NoOpLog -jar ${TOOLDIR}/GenesetTools.jar toGSEA --biopax ${1}_updated_v1.owl --outfile ${1}_${2//[[:space:]]}.gmt --id "$2" --speciescheck FALSE --source "$3" 2>> biopax_process.err 1>> biopax_output.txt

	#ticket #209
        #get rid of forward slashes in the resutls gmt file (occurs in NCI, Reactome, and Humancyc) and causes
	#GSEA to produce error when creating a details file.
	sed 's/\// /g' ${1}_${2//[[:space:]]}.gmt > temp.txt
	mv temp.txt ${1}_${2//[[:space:]]}.gmt
}

#this function create gmt files from biopax files without validation and auto-fix.
# argument 1 - biopax file name
# argument 2 - identifier to extract from file
# argument 3 - database source
function process_biopax_novalidation {

	#create gmt file from the given, autofixed biopax file
	#make sure the the id searching for doesn't have any spaces for the file name
	#long -D option turns off logging when using paxtool
	java -Xmx2G -Dorg.apache.commons.logging.Log=org.apache.commons.logging.impl.NoOpLog -jar ${TOOLDIR}/GenesetTools.jar toGSEA --biopax ${1} --outfile ${1}_${2//[[:space:]]}.gmt --id "$2" --speciescheck FALSE --source "$3" 2>> biopax_process.err 1>> biopax_output.txt

	#ticket #209
        #get rid of forward slashes in the resutls gmt file (occurs in NCI, Reactome, and Humancyc) and causes
	#GSEA to produce error when creating a details file.
	sed 's/\// /g' ${1}_${2//[[:space:]]}.gmt > temp.txt
	mv temp.txt ${1}_${2//[[:space:]]}.gmt

}



# argument 1 - gmt file name
# argument 2 - Source Name
# argument 3 - if 1 replace second column with value of first column 
function process_gmt {
	
        #ticket #202 there are issues with some of the gmt files have \r instaed of \n as end of line
        # make sure we replace all \r with \n
	cat $1 | tr '\r' '\n' > temp.txt
	mv temp.txt $1        
	
	#replace the second column with the first column if the third argument is 1
	if [[ $3 == 1 ]] ; then
		awk 'BEGIN{FS="\t"} {$2 = $1; for (i=1; i<=NF; i++) printf("%s\t", $i); printf("\n");}' $1 > temp.txt
		mv temp.txt $1
	fi
	
	#add the gmt source to the front of every geneset name
        #change of naming format.  Geneset name % Geneset source % Geneset ID	
	awk -v name="${2}" 'BEGIN{FS="\t"} {sub(/^/,$1"%"name"%")};1' $1 > temp.txt
	#sed 's/^${2}\|//g' $1 > temp.gmt
	mv temp.txt $1 
}

# argument 1 - gmt file name
# argument 2 - taxonomy id
# argument 3 - id found in gmt file
function translate_gmt {
	java -Xmx2G -jar ${TOOLDIR}/GenesetTools.jar translate --gmt $1 --organism $2 --oldID $3 2>> translate_process.err 1>> translate_output.txt 
}

# argument 1 - gmt file name
# argument 2 - taxonomy id
# argument 3 - taxonomy name
function convert_gmt {
	java -Xmx2G -jar ${TOOLDIR}/GenesetTools.jar convertGeneSets --gmt $1 --homology ${TOOLDIR}/testFiles/homologene.data --newtaxid $2 --outfile ${3}_${1//[[:Human:]]} 2>> convert_process.err 1>> convert_output.txt 
}

#exclude Inferred by electronic annotations
# argument 1 - gaf file name
# argument 2 - taxonomy id
# argument 3 - branch of go (all, bp, mf or cc)
# argument 4 - obo file name
function process_gaf_noiea {
	java -Xmx2G -jar ${TOOLDIR}/GenesetTools.jar createGo --organism $2 --branch $3 --gaffile $1 --obofile $4 --exclude 2>> ${NOIEA}_process.err 1>> ${NOIEA}_output.txt 
}

# argument 1 - gaf file name
# argument 2 - taxonomy id
# argument 3 - branch of go (all, bp, mf or cc)
# argument 4 - obo file name
function process_gaf {
	java -Xmx2G -jar ${TOOLDIR}/GenesetTools.jar createGo --organism $2 --branch $3 --gaffile $1 --obofile $4  2>> ${WITHIEA}_process.err 1>> ${WITHIEA}_output.txt 
}

# argument 1 - source to copy
#argument 2 - species
#argument 3 - division to put data into
function copy2release {
	cat *gene.gmt > ${2}_${1}_Entrezgene.gmt
	cp ${2}_${1}_Entrezgene.gmt ${EG}/${3}/${2}_${1}_Entrezgene.gmt
	#concatenate all the translation summaries
	
	files=$(ls *gene_summary.log 2> /dev/null | wc -l)
	if [ $files != 0 ] ; then
	#if [ -e *gene_summary.log ] ; then
		cat *gene_summary.log > ${2}_${1}_Entrezgene_translation_summary.log
		cp ${2}_${1}_Entrezgene_translation_summary.log ${EG}/${3}/${2}_${1}_Entrezgene_translation_summary.log
	fi

	cat *UniProt.gmt > ${2}_${1}_UniProt.gmt
	cp ${2}_${1}_UniProt.gmt ${UNIPROT}/${3}/${2}_${1}_UniProt.gmt
	#concatenate all the translation summaries
	files=$(ls *UniProt_summary.log 2> /dev/null | wc -l)
	if [ $files != 0 ] ; then
	#if [ -e *UniProt_summary.log ] ; then
		cat *UniProt_summary.log > ${2}_${1}_UniProt_translation_summary.log
		cp ${2}_${1}_UniProt_translation_summary.log ${UNIPROT}/${3}/${2}_${1}_UniProt_translation_summary.log
	fi
	
	cat *symbol.gmt > ${2}_${1}_symbol.gmt
	cp ${2}_${1}_symbol.gmt ${SYMBOL}/${3}/${2}_${1}_symbol.gmt
	#concatenate all the translation summaries
	files=$(ls *symbol_summary.log 2> /dev/null | wc -l)
	if [ $files != 0 ] ; then
	#if [ -e *symbol_summary.log ] ; then
		cat *symbol_summary.log > ${2}_${1}_symbol_translation_summary.log
		cp ${2}_${1}_symbol_translation_summary.log ${SYMBOL}/${3}/${2}_${1}_symbol_translation_summary.log
	fi

}

# argument 1 - directory where you want the divisions created
function createDivisionDirs {
	cd ${1}
	mkdir ${GO}
	mkdir ${PATHWAYS}
	mkdir ${MIR}
	mkdir ${TF}
	mkdir ${DISEASE}
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
		cat temp.log ${1}/${PATHWAYS}/*summary.log > temp.log   
	fi
        if [ $mir_files != 0 ] ; then
		cat temp.log ${1}/${MIR}/*summary.log > temp.log   
	fi
	if [ $tf_files != 0 ] ; then
		cat temp.log ${1}/${TF}/*summary.log > temp.log   
	fi
	if [ $dis_files != 0 ] ; then
		cat temp.log ${1}/${DISEASE}/*summary.log > temp.log   
	fi

	mv temp.log ${1}/${2}_translation_summary.log 

	#cat ${1}/${GO}/*summary.log ${1}/${PATHWAYS}/*summary.log ${1}/${MIR}/*summary.log ${1}/${TF}/*summary.log ${1}/${DISEASE}/*summary.log > ${1}/${2}_translation_summary.log   

}

#summarize the number of genesets in one file
#argument 1 - directory to comile from
function getstats {
      echo 'Gene Set Statistics for Release ' ${dir_name} > ${1}/Summary_GeneSet_Counts.txt
        cd ${1}/${GO}
        files=$(ls *.gmt 2> /dev/null | wc -l)
        if [ $files != 0 ] ; then

                echo 'GO with IEA Stats:' >> ${1}/Summary_Geneset_Counts.txt
                wc -l *bp_with* >> ${1}/Summary_Geneset_Counts.txt
                wc -l *mf_with* >> ${1}/Summary_Geneset_Counts.txt
                wc -l *cc_with* >> ${1}/Summary_Geneset_Counts.txt
                echo 'Total GO with IEA:' >> ${1}/Summary_Geneset_Counts.txt
                wc -l *ALL_with* >> ${1}/Summary_Geneset_Counts.txt
                echo '------------------'  >> ${1}/Summary_Geneset_Counts.txt
                echo 'GO no IEA Stats:' >> ${1}/Summary_Geneset_Counts.txt
                wc -l *bp_no* >> ${1}/Summary_Geneset_Counts.txt
                wc -l *mf_no* >> ${1}/Summary_Geneset_Counts.txt
                wc -l *cc_no* >> ${1}/Summary_Geneset_Counts.txt
                echo 'Total GO no IEA:' >> ${1}/Summary_Geneset_Counts.txt
                wc -l *ALL_no* >> ${1}/Summary_Geneset_Counts.txt
                echo '------------------'  >> ${1}/Summary_Geneset_Counts.txt
        fi

        cd ${1}/${PATHWAYS}
        files=$(ls *.gmt 2> /dev/null | wc -l)
        if [ $files != 0 ] ; then

                echo 'Pathway Stats:' >> ${1}/Summary_Geneset_Counts.txt
                wc -l *.gmt >> ${1}/Summary_Geneset_Counts.txt
                echo '------------------'  >> ${1}/Summary_Geneset_Counts.txt
        fi

	 cd ${1}/${MIR}
        files=$(ls *.gmt 2> /dev/null | wc -l)
        if [ $files != 0 ] ; then
                echo 'miR Stats:' >> ${1}/Summary_Geneset_Counts.txt
                wc -l *.gmt >> ${1}/Summary_Geneset_Counts.txt
                echo '------------------'  >> ${1}/Summary_Geneset_Counts.txt
        fi

        cd ${1}/${TF}
        files=$(ls *.gmt 2> /dev/null | wc -l)
        if [ $files != 0 ] ; then

                echo 'Transcription Factor Stats:' >> ${1}/Summary_Geneset_Counts.txt
                wc -l *.gmt >> ${1}/Summary_Geneset_Counts.txt
                echo '------------------'  >> ${1}/Summary_Geneset_Counts.txt
        fi

        cd ${1}/${DISEASE}
        files=$(ls *.gmt 2> /dev/null | wc -l)
        if [ $files != 0 ] ; then

                echo 'Disease Phenotypes Stats:' >> ${1}/Summary_Geneset_Counts.txt
                wc -l *.gmt >> ${1}/Summary_Geneset_Counts.txt
        fi

        mv ${1}/Summary_Geneset_Counts.txt ${OUTPUTDIR}


}

#source all configuration parameters we need (contains paths to output directories and the like)

#make sure we are using Java 6
export JAVA_HOME="/System/Library/Frameworks/JavaVM.framework/Versions/1.6/Home"

#create a new directory for this release (the directory name will be the date that it was built)
dir_name=`date '+%B_%d_%Y'`
TOOLDIR=`pwd`
VALIDATORDIR=${TOOLDIR}/biopax-validator-2.0.0beta4
STATICDIR=${TOOLDIR}/staticSrcFiles
WORKINGDIR=`pwd`
CUR_RELEASE=${WORKINGDIR}/${dir_name}


##########################################################
# Create Human Genesets.
##########################################################

OUTPUTDIR=${CUR_RELEASE}/Human
UNIPROT=${OUTPUTDIR}/UniProt
EG=${OUTPUTDIR}/Entrezgene
SYMBOL=${OUTPUTDIR}/symbol
SOURCE=${CUR_RELEASE}/SRC
VERSIONS=${CUR_RELEASE}/version

#under each identifier type there are three different types of interactions
GO=GO
PATHWAYS=Pathways
MIR=miRs
TF=TranscriptionFactors
DISEASE=DiseasePhenotypes

NOIEA=no_GO_iea
WITHIEA=with_GO_iea

mkdir ${CUR_RELEASE}
mkdir ${SOURCE}
mkdir ${VERSIONS}
mkdir -p ${UNIPROT}
mkdir -p ${EG}
mkdir -p ${SYMBOL}

createDivisionDirs ${UNIPROT}
createDivisionDirs ${EG}
createDivisionDirs ${SYMBOL}


#There are three different types of files that we have to deal with during the download process:
# 1. biopax - need to validated, autofixed, converted to gmt files, convert identifiers to other desirable identifiers
# 2. gmt files  - convert the gmt to the EM desired format, need to convert identifiers to other desirable identifiers
# 3. GAF (Gene ontology flat file format) - create gmt files, convert identifiers to other desirable identifiers

#download NCI from pathway commons - will replace with direct download once we get them working.
#PATHWAYCOMMONS=${SOURCE}/pathwaycommons
#mkdir -p ${PATHWAYCOMMONS}
#download_pc_data
#cd ${PATHWAYCOMMONS}
#unzip *.zip
#mv nci-nature-entrez-gene-id.gmt nci-nature-entrezgene.gmt
#modify gmt file so the gmt conforms to our standard with name and description
#for file in *.gmt; do
#	process_gmt $file "PC_NCI" 1
#	translate_gmt $file "9606" "entrezgene"
#done 
#copy2release PC_NCI_Nature Human ${PATHWAYS}

#download NCI from NCI database.
NCI=${SOURCE}/NCI
mkdir -p ${NCI}
download_nci_data
cd ${NCI}
gunzip *.gz
#modify gmt file so the gmt conforms to our standard with name and description
for file in *.owl; do
	process_biopax_novalidation $file "UniProt" "NCI_Nature"
done
for file in *.gmt; do
	translate_gmt $file "9606" "UniProt"
done 
copy2release NCI_Nature Human ${PATHWAYS}

#Download all the biopax sources
# Steps to follow with biopax files:
# 1. validate (store validation results for troubleshooting files) using biopax validator
# 2. autofix biopax file using biopax validator
# 3. convert to gmt file using GenesetTools
# 4. convert to other identifiers using GenesetTools

#download NetPath biopax data
NETPATH=${SOURCE}/NetPath
mkdir ${NETPATH}
download_netpath_data

cd ${NETPATH}
#process each file in the NetPath directory.
for file in *.owl; do
	process_biopax $file "Entrez gene" "NetPath"
done
for file in *.gmt; do
	translate_gmt $file "9606" "entrezgene"
done
#merge all the gmt into one NetPath GMT
copy2release NetPath Human ${PATHWAYS}

#download humancyc
HUMANCYC=${SOURCE}/Humancyc
mkdir ${HUMANCYC}
download_biocyc_data "human" ${HUMANCYC}
cd ${HUMANCYC}
#unzip and untar human.tar.gz file
tar -xvzf human.tar.gz *level3.owl
#the release number keeps changing - need a way to change into the right directory without knowing what the new number is
# instead of specifying the name of the directory put *.  This will break if
# they change the data directory structure though.
cd */data
for file in *.owl; do
	process_biopax $file "UniProt" "HumanCyc"
done
for file in *.gmt; do
	translate_gmt $file "9606" "UniProt"
done
copy2release HumanCyc Human ${PATHWAYS}


#download Reactome biopax data
REACTOME=${SOURCE}/Reactome
mkdir ${REACTOME}
download_reactome_data
cd ${REACTOME}
unzip biopax3.zip *sapiens.owl
mv Homo\ sapiens.owl Homosapiens.owl

#for some reason the validated and fixed Reactome file hangs.
for file in *sapiens.owl; do
	process_biopax_novalidation $file "UniProt" "Reactome"
done
for file in *.gmt; do
	translate_gmt $file "9606" "UniProt"
done
copy2release Reactome Human ${PATHWAYS}



#process the static sources - currently KEGG and IOB
cd ${STATICDIR}
for dir in `ls`; do
	#for each of directories in the static directory, create a directory in the src directory	
	if [[ $dir == "IOB" ]] ; then
		cd $dir
		mkdir ${SOURCE}/$dir
		cp *.owl ${SOURCE}/$dir
		cp *.txt ${VERSIONS}
		cd ${SOURCE}/$dir
		for file in *.owl; do
			process_biopax_novalidation $file "Entrez gene" "IOB"
		done
		for file in *.gmt; do
			translate_gmt $file "9606" "Entrezgene"
		done

		copy2release IOB Human ${PATHWAYS}
		
		cd ${STATICDIR}
	fi

	if [[ $dir == "KEGG" ]] ; then
		cd $dir
		mkdir ${SOURCE}/$dir
		cp *.gmt ${SOURCE}/$dir
		cp *.txt ${VERSIONS}
		cd ${SOURCE}/$dir
		for file in *.gmt; do
			translate_gmt $file "9606" "symbol"
		done
		copy2release KEGG Human ${PATHWAYS}
		cd ${STATICDIR}
	fi
	if [[ $dir == "msigdb_path" ]] ; then
		cd $dir
		mkdir ${SOURCE}/$dir
		cp *.gmt ${SOURCE}/$dir
		cp *.txt ${VERSIONS}
		cd ${SOURCE}/$dir
		for file in *.gmt; do
			process_gmt $file "MSigdb_C2" 1
			translate_gmt $file "9606" "Entrezgene"
		done
		copy2release MSigdb Human ${PATHWAYS}
		cd ${STATICDIR}
	fi
	if [[ $dir == "DiseasePhenotypes" ]] ; then
		cd $dir
		mkdir ${SOURCE}/$dir
		cp *entrezgene.gmt ${SOURCE}/$dir
		cp *version.txt ${VERSIONS}
		cd ${SOURCE}/$dir
		for file in *.gmt; do
			translate_gmt $file "9606" "Entrezgene"
		done
		copy2release DiseasePhenotypes Human ${DISEASE}
		cd ${STATICDIR}
	fi
	if [[ $dir == "mirs" ]] ; then
		cd $dir
		mkdir ${SOURCE}/$dir
		cp *.gmt ${SOURCE}/$dir
		cp *.txt ${VERSIONS}
		cd ${SOURCE}/$dir
		for file in *.gmt; do
			process_gmt $file "MSigdb_C3" 1
			translate_gmt $file "9606" "Entrezgene"
		done
		copy2release MSigdb Human ${MIR}
		cd ${STATICDIR}
	fi
	if [[ $dir == "tf" ]] ; then
		cd $dir
		mkdir ${SOURCE}/$dir
		cp *.gmt ${SOURCE}/$dir
		cp *.txt ${VERSIONS}
		cd ${SOURCE}/$dir
		for file in *.gmt; do
			process_gmt $file "MSigdb_C3" 1
			translate_gmt $file "9606" "Entrezgene"
		done
		copy2release MSigdb Human ${TF}
		cd ${STATICDIR}
	fi
done

#process GO
GOSRC=${SOURCE}/GO
mkdir ${GOSRC}
download_GOhuman_data
GOOBO=${GOSRC}/gene_ontology.1_2.obo
cd ${GOSRC}
gunzip *.gz
for file in *.goa*; do
	process_gaf $file "9606" "bp" ${GOOBO}
	process_gaf_noiea  $file "9606" "bp" ${GOOBO}
	process_gaf $file "9606" "mf" ${GOOBO}
	process_gaf_noiea  $file "9606" "mf" ${GOOBO}
	process_gaf $file "9606" "cc" ${GOOBO}
	process_gaf_noiea  $file "9606" "cc" ${GOOBO}
done
for file in *.gmt; do
	translate_gmt $file "9606" "UniProt"
done

#create  the compilation of all branches
cat *_${WITHIEA}*entrezgene.gmt > Human_GOALL_${WITHIEA}_entrezgene.gmt
cat *_${WITHIEA}*UniProt.gmt > Human_GOALL_${WITHIEA}_UniProt.gmt
cat *_${WITHIEA}*symbol.gmt > Human_GOALL_${WITHIEA}_symbol.gmt

cat *_${NOIEA}*entrezgene.gmt > Human_GOALL_${NOIEA}_entrezgene.gmt
cat *_${NOIEA}*UniProt.gmt > Human_GOALL_${NOIEA}_UniProt.gmt
cat *_${NOIEA}*symbol.gmt > Human_GOALL_${NOIEA}_symbol.gmt

cp *entrezgene.gmt ${EG}/${GO}
#create report of translations
cat *gene_summary.log > ${EG}/${GO}/Human_GO_entrezgene_translation_summary.log

cp *UniProt.gmt ${UNIPROT}/${GO}
#create report of translations
cat *UniProt_summary.log > ${UNIPROT}/${GO}/Human_GO_UniProt_translation_summary.log

cp *symbol.gmt ${SYMBOL}/${GO}
#create report of translations
cat *UniProt_summary.log > ${UNIPROT}/${GO}/Human_GO_UniProt_translation_summary.log

#compile all the different versions
cd ${VERSIONS}
cat *.txt > ${OUTPUTDIR}/${dir_name}_versions.txt


#create all the different distributions
cd ${EG}/${PATHWAYS}
cat *.gmt > ../Human_AllPathways_entrezgene.gmt
cd ${EG}/${GO}
cat ../Human_AllPathways_entrezgene.gmt Human_GOALL_${WITHIEA}_entrezgene.gmt > ../Human_GO_AllPathways_${WITHIEA}_entrezgene.gmt
cat ../Human_AllPathways_entrezgene.gmt Human_GOALL_${NOIEA}_entrezgene.gmt > ../Human_GO_AllPathways_${NOIEA}_entrezgene.gmt
#merge all the summaries
mergesummaries ${EG} entrezgene

cd ${SYMBOL}/${PATHWAYS}
cat *.gmt > ../Human_AllPathways_symbol.gmt
cd ${SYMBOL}/${GO}
cat ../Human_AllPathways_symbol.gmt Human_GOALL_${WITHIEA}_symbol.gmt > ../Human_GO_AllPathways_${WITHIEA}_symbol.gmt
cat ../Human_AllPathways_symbol.gmt Human_GOALL_${NOIEA}_symbol.gmt > ../Human_GO_AllPathways_${NOIEA}_symbol.gmt
#merge all the summaries
mergesummaries ${SYMBOL} symbol

cd ${UNIPROT}/${PATHWAYS}
cat *.gmt > ../Human_AllPathways_UniProt.gmt
cd ${UNIPROT}/${GO}
cat ../Human_AllPathways_UniProt.gmt Human_GOALL_${WITHIEA}_UniProt.gmt > ../Human_GO_AllPathways_${WITHIEA}_UniProt.gmt
cat ../Human_AllPathways_UniProt.gmt Human_GOALL_${NOIEA}_UniProt.gmt > ../Human_GO_AllPathways_${NOIEA}_UniProt.gmt
#merge all the summaries
mergesummaries ${UNIPROT} UniProt

#create the stats summary
getstats ${EG}

#################################################################
# Create Mouse Genesets.
##########################################################
#get the directory where the pathways to be converted are
HUMANGMTS=${EG}
HUMANVERSIONS=${VERSIONS}

OUTPUTDIR=${CUR_RELEASE}/Mouse
UNIPROT=${OUTPUTDIR}/UniProt
EG=${OUTPUTDIR}/Entrezgene
SYMBOL=${OUTPUTDIR}/symbol
MOUSESOURCE=${CUR_RELEASE}/SRC_Mouse
VERSIONS=${CUR_RELEASE}/version_mouse

mkdir ${MOUSESOURCE}
mkdir ${VERSIONS}
mkdir -p ${UNIPROT}
mkdir -p ${EG}
mkdir -p ${SYMBOL}

createDivisionDirs ${UNIPROT}
createDivisionDirs ${EG}
createDivisionDirs ${SYMBOL}


#Direct source for mouse come from GO, Mousecyc, Reactome, Kegg

#download Mousecyc --get from human instead, there is nothing in the Mousecyc
#MOUSECYC=${MOUSESOURCE}/Mousecyc
#mkdir ${MOUSECYC}
#download_biocyc_data "mouse" ${MOUSECYC}
#cd ${MOUSECYC}
#unzip and untar mouse biopax level 3 file
#tar -xvzf mouse.tar.gz *level3.owl
#cd 1.36/data
#for file in *.owl; do
#	process_biopax $file "UniProt" "MouseCyc"
#done
#for file in *.gmt; do
#	translate_gmt $file "10090" "UniProt"
#done
#copy2release MouseCyc Mouse ${PATHWAYS}


#download Reactome biopax data
REACTOME=${MOUSESOURCE}/Reactome
mkdir ${REACTOME}
#copy reactome file from human src directory
cp ${SOURCE}/Reactome/*.zip ${REACTOME}/
#copy reactome version into mouse_versions directory.
cp ${HUMANVERSIONS}/Reactome.txt ${VERSIONS}
cd ${REACTOME}
unzip biopax3.zip *musculus.owl
mv Mus\ musculus.owl Musmusculus.owl

#for some reason the validated and fixed Reactome file hangs.
for file in *.owl; do
	process_biopax_novalidation $file "UniProt" "Reactome"
done
for file in *.gmt; do
	translate_gmt $file "10090" "UniProt"
done
copy2release Reactome Mouse ${PATHWAYS}


#process GO
GOSRC=${MOUSESOURCE}/GO
mkdir ${GOSRC}
download_GOmouse_data
cd ${GOSRC}
gunzip *.gz
for file in *.mgi*; do
	process_gaf $file "10090" "bp" ${GOOBO}
	process_gaf_noiea  $file "10090" "bp" ${GOOBO}
	process_gaf $file "10090" "mf" ${GOOBO}
	process_gaf_noiea  $file "10090" "mf" ${GOOBO}
	process_gaf $file "10090" "cc" ${GOOBO}
	process_gaf_noiea  $file "10090" "cc" ${GOOBO}
done
for file in *.gmt; do
	translate_gmt $file "10090" "MGI"
done

#create  the compilation of all branches
cat *_${WITHIEA}*entrezgene.gmt > Mouse_GOALL_${WITHIEA}_entrezgene.gmt
cat *_${WITHIEA}*UniProt.gmt > Mouse_GOALL_${WITHIEA}_UniProt.gmt
cat *_${WITHIEA}*symbol.gmt > Mouse_GOALL_${WITHIEA}_symbol.gmt

cat *_${NOIEA}*entrezgene.gmt > Mouse_GOALL_${NOIEA}_entrezgene.gmt
cat *_${NOIEA}*UniProt.gmt > Mouse_GOALL_${NOIEA}_UniProt.gmt
cat *_${NOIEA}*symbol.gmt > Mouse_GOALL_${NOIEA}_symbol.gmt

cp *entrezgene.gmt ${EG}/${GO}
#create report of translations
cat *gene_summary.log > ${EG}/${GO}/Mouse_GO_entrezgene_translation_summary.log

cp *UniProt.gmt ${UNIPROT}/${GO}
#create report of translations
cat *UniProt_summary.log > ${UNIPROT}/${GO}/Mouse_GO_UniProt_translation_summary.log

cp *symbol.gmt ${SYMBOL}/${GO}
#create report of translations
cat *UniProt_summary.log > ${UNIPROT}/${GO}/Mouse_GO_UniProt_translation_summary.log


#create a directory will all the gmt from human we want to convert to mouse
CONV=${MOUSESOURCE}/ToBeConverted
mkdir ${CONV}

#copy the following files to mouse directory to be converted from human to mouse
cd ${CONV}
cp ${HUMANGMTS}/${PATHWAYS}/*NetPath*.gmt ./
cp ${HUMANGMTS}/${PATHWAYS}/*IOB*.gmt ./
cp ${HUMANGMTS}/${PATHWAYS}/*MSig*.gmt ./
cp ${HUMANGMTS}/${PATHWAYS}/*NCI*.gmt ./
cp ${HUMANGMTS}/${PATHWAYS}/*HumanCyc*.gmt ./
cp ${HUMANGMTS}/${PATHWAYS}/*KEGG*.gmt ./

#copy all version into mouse_versions directory.
cp ${HUMANVERSIONS}/NetPath.txt ${VERSIONS}
cp ${HUMANVERSIONS}/IOB.txt ${VERSIONS}
cp ${HUMANVERSIONS}/msigdb_path.txt ${VERSIONS}
cp ${HUMANVERSIONS}/NCI_Nature.txt ${VERSIONS}
cp ${HUMANVERSIONS}/humancyc.txt ${VERSIONS}
cp ${HUMANVERSIONS}/KEGG.txt ${VERSIONS}

#cp ${HUMANGMTS}/${MIRS}/*.gmt ./
#cp ${HUMANGMTS}/${TF}/*.gmt ./

#go through each of the gmt file and convert to mouse entrez genes
for file in Human*.gmt ; do
	convert_gmt $file "10090" "Mouse"
done

#got through all the newly created Mouse gmt file and translate them from eg to uniprot and symbol
for file in Mouse*.gmt ; do
	translate_gmt $file "10090" "entrezgene"
done

#copy all the pathway file - can't use the copy function because there are multiple pathway datasets in this set. 
cp Mouse*_Entrezgene.gmt ${EG}/${PATHWAYS}/
cp Mouse*_UniProt.gmt ${UNIPROT}/${PATHWAYS}/
cp Mouse*_symbol.gmt ${SYMBOL}/${PATHWAYS}/

#concatenate all the translation summaries	
files=$(ls *10090_conversion.log 2> /dev/null | wc -l)
if [ $files != 0 ] ; then
	cat *10090_conversion.log > Mouse_translatedPathways_Entrezgene_translation_summary.log
	cp Mouse_translatedPathways_Entrezgene_translation_summary.log ${EG}/${PATHWAYS}/Mouse_translatedPathways_Entrezgene_translation_summary.log
fi
	
files=$(ls *UniProt_summary.log 2> /dev/null | wc -l)
if [ $files != 0 ] ; then
	cat *UniProt_summary.log > Mouse_translatedPathways_UniProt_translation_summary.log
	cp Mouse_translatedPathways_UniProt_translation_summary.log ${UNIPROT}/${PATHWAYS}/Mouse_translatedPathways_UniProt_translation_summary.log
fi
		
files=$(ls *symbol_summary.log 2> /dev/null | wc -l)
if [ $files != 0 ] ; then
	cat *symbol_summary.log > Mouse_translatedPathways_symbol_translation_summary.log
	cp $Mouse_translatedPathways_symbol_translation_summary.log ${SYMBOL}/${PATHWAYS}/Mouse_translatedPathways_symbol_translation_summary.log
fi


#compile all the different versions
cd ${VERSIONS}
cat *.txt > ${OUTPUTDIR}/${dir_name}_versions.txt

#create all the different distributions
cd ${EG}/${PATHWAYS}
cat *.gmt > ../Mouse_AllPathways_entrezgene.gmt
cd ${EG}/${GO}
cat ../Mouse_AllPathways_entrezgene.gmt Mouse_GOALL_${WITHIEA}_entrezgene.gmt > ../Mouse_GO_AllPathways_${WITHIEA}_entrezgene.gmt
cat ../Mouse_AllPathways_entrezgene.gmt Mouse_GOALL_${NOIEA}_entrezgene.gmt > ../Mouse_GO_AllPathways_${NOIEA}_entrezgene.gmt
#merge all the summaries
mergesummaries ${EG} entrezgene

cd ${SYMBOL}/${PATHWAYS}
cat *.gmt > ../Mouse_AllPathways_symbol.gmt
cd ${SYMBOL}/${GO}
cat ../Mouse_AllPathways_symbol.gmt Mouse_GOALL_${WITHIEA}_symbol.gmt > ../Mouse_GO_AllPathways_${WITHIEA}_symbol.gmt
cat ../Mouse_AllPathways_symbol.gmt Mouse_GOALL_${NOIEA}_symbol.gmt > ../Mouse_GO_AllPathways_${NOIEA}_symbol.gmt
#merge all the summaries
mergesummaries ${SYMBOL} symbol

cd ${UNIPROT}/${PATHWAYS}
cat *.gmt > ../Mouse_AllPathways_UniProt.gmt
cd ${UNIPROT}/${GO}
cat ../Mouse_AllPathways_UniProt.gmt Mouse_GOALL_${WITHIEA}_UniProt.gmt > ../Mouse_GO_AllPathways_${WITHIEA}_UniProt.gmt
cat ../Mouse_AllPathways_UniProt.gmt Mouse_GOALL_${NOIEA}_UniProt.gmt > ../Mouse_GO_AllPathways_${NOIEA}_UniProt.gmt
#merge all the summaries
mergesummaries ${UNIPROT} UniProt

getstats ${EG}

#copy the files over the webserver
mkdir /Volumes/RAID/WebServer/Hosting/download.baderlab.org/EM_Genesets/$dir_name
cp -R ${CUR_RELEASE}/Human /Volumes/RAID/WebServer/Hosting/download.baderlab.org/EM_Genesets/$dir_name/
cp -R ${CUR_RELEASE}/Mouse /Volumes/RAID/WebServer/Hosting/download.baderlab.org/EM_Genesets/$dir_name/

#create a symbolic link to the latest download indicating it as current_release
rm /Volumes/RAID/WebServer/Hosting/download.baderlab.org/EM_Genesets/current_release
ln -sf /Volumes/RAID/WebServer/Hosting/download.baderlab.org/EM_Genesets/$dir_name/ /Volumes/RAID/WebServer/Hosting/download.baderlab.org/EM_Genesets/current_release

#rm -rf ${CUR_RELEASE}
