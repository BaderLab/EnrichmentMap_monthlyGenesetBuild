#!/bin/bash

function get_pc_version {
	    # pathway commons uses a release date instead of a version
	    curl -s http://www.pathwaycommons.org/pc-snapshot/ | grep "current-release" | awk '{print $6}' > ${VERSIONS}/pathwaycommons.txt
}


function download_pc_data {
	    echo "[Downloading current Pathway Commons data]"
	    URL="http://www.pathwaycommons.org/pc-snapshot/current-release/gsea/by_source/"
	    curl ${URL}/nci-nature-entrez-gene-id.gmt.zip -o ${PATHWAYCOMMONS}/nci-nature-entrez-gene-id.gmt.zip
	    get_pc_version
}



#this function will get the date of the file if there is no other way to get a version from it
# argument 1 - name of the file
# argument 2 - datasource to add it to
function get_webfile_version {
	echo "$1" >> ${VERSIONS}/${2}.txt
	curl $1 -I | grep "Last-Modified" >> ${VERSIONS}/${2}.txt
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
	echo "${URL}/human.tar.gz" >> ${VERSIONS}/Humancyc.txt
	curl ${URL}/human.tar.gz -u biocyc-flatfiles:data-20541 -I | grep "Last-Modified" >> ${VERSIONS}/Humancyc.txt
	curl ${URL}/human.tar.gz -o ${HUMANCYC}/human.tar.gz -u biocyc-flatfiles:data-20541
}

function download_GOhuman_data {
	echo "[Downloading current Go Human EBI data]"
	URL="ftp://ftp.ebi.ac.uk/pub/databases/GO/goa/HUMAN/"
	curl ${URL}/gene_association.goa_human.gz -o ${GO}/gene_association.goa_human.gz
	get_webfile_version ${URL}/gene_association.goa_human.gz "GO_Human"
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
    #long -D option turns off logging when using paxtool
    java -Xmx2G -Dorg.apache.commons.logging.Log=org.apache.commons.logging.impl.NoOpLog -jar ${TOOLDIR}/GenesetTools.jar toGSEA --biopax ${1}_updated_v1.owl --outfile ${1}_${2//[[:space:]]}.gmt --id "$2" --speciescheck FALSE --source "$3" 2>> biopax_process.err 1>> biopax_output.txt
}



# argument 1 - gmt file name
# argument 2 - Source Name
function process_gmt {
	#add the gmt source to the front of every geneset name
	sed 's/^${2}\?//g' $1 > temp.gmt
	mv temp.gmt $1 
}

# argument 1 - gmt file name
# argument 2 - taxonomy id
# argument 3 - id found in gmt file
function translate_gmt {
	java -Xmx2G -jar ${TOOLDIR}/GenesetTools.jar translate --gmt $1 --organism $2 --oldID $3 2>> translate_process.err 1>> translate_output.txt 
}

#exclude Inferred by electronic annotations
# argument 1 - gaf file name
# argument 2 - taxonomy id
# argument 3 - branch of go (all, bp, mf or cc)
function process_gaf_noiea {
	java -Xmx2G -jar ${TOOLDIR}/GenesetTools.jar createGo --organism $2 --branch $3 --infile $1 --outfile ${1}_{$3}_noiea.gmt --exclude 2>> noiea_process.err 1>> noiea_output.txt 
}

# argument 1 - gaf file name
# argument 2 - taxonomy id
# argument 3 - branch of go (all, bp, mf or cc)
function process_gaf {
	java -Xmx2G -jar ${TOOLDIR}/GenesetTools.jar createGo --organism $2 --branch $3 --infile $1 --outfile ${1}_${3}_withiea.gmt  2>> withiea_process.err 1>> withiea_output.txt 
}

# argument 1 - source to copy
function copy2release {
	cat *gene.gmt > ${1}_Entrezgene.gmt
	cp ${1}_Entrezgene.gmt ${EG}/${1}_Entrezgene.gmt

	cat *_uniprot.gmt > ${1}_uniprot.gmt
	cp ${1}_uniprot.gmt ${UNIPROT}/${1}_uniprot.gmt

	cat *_symbol.gmt > ${1}_symbol.gmt
	cp ${1}_symbol.gmt ${SYMBOL}/${1}_symbol.gmt

}


#source all configuration parameters we need (contains paths to output directories and the like)

#create a new directory for this release (the directory name will be the date that it was built)
dir_name=`date '+%B_%d_%Y'`
TOOLDIR=/Users/risserlin/SourceCode/GeneSetTools
VALIDATORDIR=/Users/risserlin/SourceCode/GeneSetTools/biopax-validator-2.0.0beta4
STATICDIR=/Users/risserlin/SourceCode/GeneSetTools/staticSrcFiles
WORKINGDIR=`pwd`
CUR_RELEASE=${WORKINGDIR}/${dir_name}
OUTPUTDIR=${CUR_RELEASE}/Human
UNIPROT=${OUTPUTDIR}/Uniprot
EG=${OUTPUTDIR}/Entrezgene
SYMBOL=${OUTPUTDIR}/symbol
SOURCE=${CUR_RELEASE}/SRC
GMT=${CUR_RELEASE}/GMT
VERSIONS=${CUR_RELEASE}/version

mkdir ${CUR_RELEASE}
mkdir ${SOURCE}
mkdir ${GMT}
mkdir ${VERSIONS}
mkdir -p ${UNIPROT}
mkdir -p ${EG}
mkdir -p ${SYMBOL}

#There are three different types of files that we have to deal with during the download process:
# 1. biopax - need to validated, autofixed, converted to gmt files, convert identifiers to other desirable identifiers
# 2. gmt files  - convert the gmt to the EM desired format, need to convert identifiers to other desirable identifiers
# 3. GAF (Gene ontology flat file format) - create gmt files, convert identifiers to other desirable identifiers

#download NCI from pathway commons - will replace with direct download once we get them working.
PATHWAYCOMMONS=${SOURCE}/pathwaycommons
mkdir -p ${PATHWAYCOMMONS}
download_pc_data
cd ${PATHWAYCOMMONS}
unzip *.zip
#modify gmt file so the gmt conforms to our standard with name and description
for file in *.gmt; do
	awk 'BEGIN{FS="\t"} {$2 = $1; for (i=1; i<=NF; i++) printf("%s\t", $i); printf("\n");}' $file > temp.txt
	awk 'BEGIN{FS="\t"} {sub(/^/,"PC_NCI?")};1' temp.txt > $file
	translate_gmt $file "9606" "entrezgene"
done 
copy2release NCINature


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
copy2release NetPath

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
for file in *.gmt; do
	translate_gmt $file "9606" "UniProt"
done
copy2release HumanCyc


#download Reactome biopax data
REACTOME=${SOURCE}/Reactome
mkdir ${REACTOME}
download_reactome_data
cd ${REACTOME}
unzip biopax3.zip *sapiens.owl
mv Homo\ sapiens.owl Homosapiens.owl
for file in *sapiens.owl; do
	process_biopax $file "UniProt" "Reactome"
done
for file in *.gmt; do
	translate_gmt $file "9606" "UniProt"
done
copy2release Reactome



#process the static sources - currently KEGG and IOB
cd ${STATICDIR}
for dir in `ls -d`; do
	#for each of directories in the static directory, create a directory in the src directory	
	if [[ $dir -eq "IOB" ]]; then
		cd $dir
		mkdir ${SOURCE}/$dir
		cp *.owl ${SOURCE}/$dir
		cd ${SOURCE}/$dir
		for file in *.owl; do
			process_biopax $file "Entrez gene" "IOB"
		done
		for file in *.gmt; do
			translate_gmt $file "9606" "Entrezgene"
		done

		copy2release IOB
		
		cd ${STATICDIR}
	fi

	if [[ $dir -eq "KEGG" ]]; then
		cd $dir
		mkdir ${SOURCE}/$dir
		cp *.gmt ${SOURCE}/$dir
		cd ${SOURCE}/$dir
		for file in *.gmt; do
			translate_gmt $file "9606" "symbol"
		done
		copy2release KEGG
		cd ${STATICDIR}
	fi
done

#process GO
GO=${SOURCE}/GO
mkdir ${GO}
download_GOhuman_data
cd ${GO}
gunzip *.gz
for file in *.goa*; do
	process_gaf $file "9606" "all"
	process_gaf_noiea  $file "9606" "all"
	process_gaf $file "9606" "bp"
	process_gaf_noiea  $file "9606" "bp"
	process_gaf $file "9606" "mf"
	process_gaf_noiea  $file "9606" "mf"
	process_gaf $file "9606" "cc"
	process_gaf_noiea  $file "9606" "cc"
done
for file in *.gmt; do
	translate_gmt $file "9606" "UniProt"
done

cp *Entrezgene.gmt ${EG}/
cp *uniprot.gmt ${UNIPROT}/
cp *symbol.gmt ${SYMBOL}/

