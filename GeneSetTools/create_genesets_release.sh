#!/bin/bash

function get_pc_version {
	    # pathway commons uses a release date instead of a version
	    curl -s http://www.pathwaycommons.org/pc-snapshot/ | grep "current-release" | awk '{print $6}' > ${VERSIONS}/pathwaycommons.txt
}

function download_pc_data {
	    echo "[Downloading current Pathway Commons data]"
	    URL="http://www.pathwaycommons.org/pc-snapshot/current-release/gsea/by_source/"
	    curl ${URL}/nci-nature-entrez-gene-id.gmt.zip -o ${SOURCE}/pathwaycommons/nci-nature-entrez-gene-id.gmt.zip
	    get_pc_version

}

#this function will get the date of the file if there is no other way to get a version from it
# argument 1 - name of the file
# argument 2 - datasource to add it to
function get_webfile_version {
	echo "$1" >> ${VERSIONS}/${2}.txt
	curl $1 -i | grep "Last-Modified" >> ${VERSIONS}/${2}.txt
}

#get the 25 NetPath pathways they have on their website.  
# If new pathways are added we have no way of knowing and script will not get them.
function download_netpath_data {
	echo "[Downloading current NetPath data]"
	URL="http://www.netpath.org/data/biopax/"
	for Num in {1..25}; do
		get_webfile_version ${URL}/NetPath_${Num}.owl "NetPath"
		curl ${URL}/NetPath_${Num}.owl -o ${NETPATH}/NetPath_${Num}.owl
	done
}

function download_reactome_data {
	echo "[Downloading current Reactome data]"
	URL="http://www.reactome.org/download/current/"
	curl ${URL}/biopax3.zip -o ${REACTOME}/biopax3.zip
	get_webfile_version ${URL}/biopax3.zip "Reactome"
}

function download_humancyc_data {
	echo "[Downloading current HumanCyc data]"
	URL="http://bioinformatics.ai.sri.com/ecocyc/dist/flatfiles-52983746/"
	curl ${URL}/human.tar.gz -o ${HUMANCYC}/human.tar.gz -u biocyc-flatfiles:data-20541
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
	./validate.sh "file:${CURRENTDIR}/$1" --output=${CURRENTDIR}/${1}_validationresults_initial.html --autofix 2>> biopax_process.err
	#create an auto-fix biopax file to use to create the gmt file
    ./validate.sh "file:${CURRENTDIR}/$1" --output=${CURRENTDIR}/${1}_updated_v1.owl --auto-fix --return-biopax 2>> biopax_process.err
    
    cd ${CURRENTDIR}
    #create gmt file from the given, autofixed biopax file
    #make sure the the id searching for doesn't have any spaces for the file name
    #long -D option turns off logging when using paxtools
    cat "$1" >> biopax_output.txt
    java -Xmx2G -Dorg.apache.commons.logging.Log=org.apache.commons.logging.impl.NoOpLog -jar ${TOOLDIR}/GenesetTools.jar toGSEA --biopax ${1}_updated_v1.owl --outfile ${1}_${2//[[:space:]]}.gmt --id "$2" --speciescheck FALSE --source "$3" 2>> biopax_process.err 1>> biopax_output.txt
}

#source all configuration parameters we need (contains paths to output directories and the like)

#create a new directory for this release (the directory name will be the date that it was built)
dir_name=`date '+%B_%d_%Y'`
TOOLDIR=/Users/risserlin/SourceCode/GeneSetTools
VALIDATORDIR=/Users/risserlin/SourceCode/GeneSetTools/biopax-validator-2.0.0beta4
WORKINGDIR=`pwd`
CUR_RELEASE=${WORKINGDIR}/${dir_name}
SOURCE=${CUR_RELEASE}/SRC
GMT=${CUR_RELEASE}/GMT
VERSIONS=${CUR_RELEASE}/version
mkdir ${CUR_RELEASE}
mkdir ${SOURCE}
mkdir ${GMT}
mkdir ${VERSIONS}


#There are three different types of files that we have to deal with during the download process:
# 1. biopax - need to validated, autofixed, converted to gmt files, convert identifiers to other desirable identifiers
# 2. gmt files  - convert the gmt to the EM desired format, need to convert identifiers to other desirable identifiers
# 3. GAF (Gene ontology flat file format) - create gmt files, convert identifiers to other desirable identifiers

#download NCI from pathway commons - will replace with direct download once we get them working.
mkdir -p ${SOURCE}/pathwaycommons
download_pc_data

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
#merge all the gmt into one NetPath GMT
cat *Entrezgene.gmt > NetPath_Entrezgene.gmt
cp NetPath_Entrezgene.gmt ${GMT}


#download humancyc
HUMANCYC=${SOURCE}/Humancyc
mkdir ${HUMANCYC}
download_humancyc_data
cd ${HUMANCYC}
#unzip and untar human.tar.gz file
tar -xvzf human.tar.gz *.owl
cd 15.1/data
for file in *.owl; do
	process_biopax $file "UniProt" "HumanCyc"
done

#download Reactome biopax data
REACTOME=${SOURCE}/Reactome
mkdir ${REACTOME}
download_reactome_data
cd ${REACTOME}
unzip biopax3.zip
cd biopax3
mv mv Homo\ sapiens.owl Homosapiens.owl
for file in *sqpiens.owl; do
	process_biopax $file "UniProt" "Reactome"
done
