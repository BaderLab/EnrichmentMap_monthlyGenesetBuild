#!/bin/bash


function get_pc_version {
	    # pathway commons uses a release date instead of a version
	    curl -s http://www.pathwaycommons.org/pc-snapshot/ | grep "current-release" | awk '{print $6}' > ${VERSIONS}/pathwaycommons.txt
}


function download_pc_data {
	    echo "[Downloading current Pathway Commons data]"
	    URL="http://www.pathwaycommons.org/pc-snapshot/current-release/gsea/by_source/"
	    curl ${URL}/nci-nature-entrez-gene-id.gmt.zip -o ${PATHWAYCOMMONS}/nci-nature-entrez-gene-id.gmt.zip -s
	    get_pc_version
}

function download_panther_data {
	    echo "[Downloading current Panther Pathway data]"
            # temporarily  change this to get the 3.5 release as the latest release is broken.  Change back once we hear back from  them.
	    URL="ftp://ftp.pantherdb.org//pathway/current_release/"
	    #URL="ftp://ftp.pantherdb.org//pathway/3.5/"
	    curl ${URL}/BioPAX.tar.gz -o ${PANTHER}/BioPAX.tar.gz -s 
	    get_webfile_version ${URL}/BioPAX.tar.gz "Panther"
}

#this function will get the date of the file if there is no other way to get a version from it
# argument 1 - name of the file
# argument 2 - datasource to add it to
function get_webfile_version {
	echo "$2" >> ${VERSIONS}/${2}.txt
	echo "$1" >> ${VERSIONS}/${2}.txt
	curl -s $1 -I | grep "Last-Modified" >> ${VERSIONS}/${2}.txt
	echo "========" >> ${VERSIONS}/${2}.txt
}

#download NCI from PID
function download_nci_data {
	echo "[Downloading current NCI data]"
	URL="ftp://ftp1.nci.nih.gov/pub/PID/BioPAX_Level_3/NCI-Nature_Curated.bp3.owl.gz"
	wget ${URL}
	#curl ${URL} -o ${NCI}/NCI-Nature_Curated.bp3.owl.gz -s  -w "NCI : HTTP code - %{http_code};time:%{time_total} millisec;size:%{size_download} Bytes\n"
	get_webfile_version $URL "NCI_Nature"	
}

#get the 25 NetPath pathways they have on their website.  
# If new pathways are added we have no way of knowing and script will not get them.
function download_netpath_data {
	echo "[Downloading current NetPath data]"
	URL="http://www.netpath.org/data/biopax/"
	for Num in {1..25}; do
		get_webfile_version ${URL}/NetPath_${Num}.owl "NetPath"
		curl ${URL}/NetPath_${Num}.owl -o ${NETPATH}/NetPath_${Num}.owl -s 
	done
}

function download_reactome_data {
	echo "[Downloading current Reactome data]"
	URL="https://www.reactome.org/download/current/"
	curl ${URL}/biopax.zip -o ${REACTOME}/biopax.zip -s -L 
	get_webfile_version ${URL}/biopax.zip "Reactome"
}

function download_wikipathways_data {
	echo "[Downloading current WikiPathways data]"
	URL="http://data.wikipathways.org/current/gmt/"
	FILE=`echo "cat //html/body/div/table/tbody/tr/td/a" |  xmllint --html --shell ${URL} | grep -o -E ">(.*$1.gmt)<" | sed -E 's/(<|>)//g'`
	curl ${URL}/${FILE} -o ${WIKIPATHWAYS}/WikiPathways_${1}_Entrezgene.gmt -s -L 
	get_webfile_version ${URL}/${FILE} "WikiPathways"
}

#argument 1 - species, either human or mouse
#argument 2 - directory to put the file
function download_biocyc_data {
	echo "[Downloading current BioCyc data - for species $1]"
	#URL="http://bioinformatics.ai.sri.com/ecocyc/dist/flatfiles-52983746/"
	URL="http://brg-files.ai.sri.com/public/dist/"
	echo "${URL}/tier1-tier2-biopax.tar.gz" >> ${VERSIONS}/${1}cyc.txt
	curl ${URL}/tier1-tier2-biopax.tar.gz -u biocyc-flatfiles:data-20541 -I | grep "Last-Modified" >> ${VERSIONS}/${1}cyc.txt
	curl ${URL}/tier1-tier2-biopax.tar.gz -o ${2}/${1}.tar.gz -u biocyc-flatfiles:data-20541 -s 
}

# Go human data comes directly from ebi as they are the primary curators of human GO annotations
function download_GOhuman_data {
	echo "[Downloading current Go Human EBI data]"
	URL="ftp://ftp.ebi.ac.uk/pub/databases/GO/goa/HUMAN/"

	#June 2016 - looks like they changed the name of the go files !!!
	curl ${URL}/goa_human.gaf.gz -o ${GOSRC}/gene_association.goa_human.gz -s 
	get_webfile_version ${URL}/goa_human.gaf.gz "GO_Human"

	#get the obo file from the gene ontology website
	echo "[Downloading current GO OBO file]"
	URL="ftp://ftp.geneontology.org/pub/go/ontology/obo_format_1_2/"
	curl ${URL}/gene_ontology.1_2.obo -o ${GOSRC}/gene_ontology.1_2.obo -s 
	get_webfile_version ${URL}/gene_ontology.1_2.obo "GO_OBO_FILE"


}

#Mouse data is downloaded from the Gene ontology website and comes from MGI
function download_GOmouse_data {
	echo "[Downloading current Go Mouse MGI  data]"
	URL="http://cvsweb.geneontology.org/cgi-bin/cvsweb.cgi/go/gene-associations/gene_association.mgi.gz?rev=HEAD"
	curl $URL -o ${GOSRC}/gene_association.mgi.gz -s 
	get_webfile_version ${URL} "GO_Mouse"
}

#download the Human Phenotype data (obo file and annotation file)
function download_HPO_data {
	#file no longer exists.  Use latest version from June 16, 2013
	echo "[Downloading current Human Phenotype data]"
        #URL="http://compbio.charite.de/hudson/job/hpo.annotations.monthly/lastStableBuild/artifact/annotation/ALL_SOURCES_ALL_FREQUENCIES_genes_to_phenotype.txt"
	URL="http://compbio.charite.de/jenkins/job/hpo.annotations.monthly/lastStableBuild/artifact/annotation/ALL_SOURCES_ALL_FREQUENCIES_genes_to_phenotype.txt"
	curl ${URL} -o ${DISEASESRC}/genes_to_phenotype.txt -s 
	get_webfile_version ${URL} "Human_Phenotype"

	#get the obo file
	echo "[Downloading current Phenotype OBO file]"
	#URL="http://compbio.charite.de/svn/hpo/trunk/src/ontology/"
	#curl ${URL}/human-phenotype-ontology.obo -o ${DISEASESRC}/human-phenotype-ontology.obo -s  -w "HPO (obo) : HTTP code - %{http_code};time:%{time_total} millisec; size:%{size_download} Bytes\n"
        #URL="http://compbio.charite.de/jenkins/job/hpo/lastStableBuild/artifact/hp"
	URL="https://raw.githubusercontent.com/obophenotype/human-phenotype-ontology/master"

	curl ${URL}/hp.obo -o ${DISEASESRC}/human-phenotype-ontology.obo -s 
	get_webfile_version ${URL}/hp.obo "Human_phenotype_OBO_FILE"

}

#download Drugbank files
function download_drugbank_data {
	
	#July 2016 - drugbank changed how we can download data. 
	# needed to create an account and URL changed

	echo "[Downloading current Drugbank data]"
	URL="http://www.drugbank.ca/system/downloads/current"
        curl -Lf -o drugbank.xml.zip -u ruth.isserlin@gmail.com:emililab https://go.drugbank.com/releases/latest/downloads/all-full-database	
        #curl ${URL}/drugbank.xml.zip -o ${DRUGSSRC}/drugbank.xml.zip -s -w "Drugbank : HTTP code - %{http_code};time:%{time_total} millisec;size:%{size_download} Bytes\n"
	#get_webfile_version ${URL}/drugbank.xml.zip "DrugBank"

	#echo "[Downloading current Drugbank external identifiers info]"
	#curl ${URL}/target_links.csv.zip -o ${DRUGSSRC}/target_links.csv.zip -s -w "Drugbank(external links) : HTTP code - %{http_code};time:%{time_total} millisec;size:%{size_download} Bytes\n"
	#curl ${URL}/all_target_ids_all.csv.zip -o ${DRUGSSRC}/target_links.csv.zip -s -w "Drugbank(external links) : HTTP code - %{http_code};time:%{time_total} millisec;size:%{size_download} Bytes\n"
	#get_webfile_version ${URL}/all_target_ids_all.csv.zip "DrugBank_external_links"

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
	#latest version of the validator will generate the output to a .modified.owl file.
	./validate.sh "file:${CURRENTDIR}/$1"  --auto-fix  --profile=notstrict 2>> biopax_process.err

	#create an auto-fix biopax file to use to create the gmt file
	#./validate.sh "file:${CURRENTDIR}/$1" --output=${CURRENTDIR}/${1}_updated_v1.owl --auto-fix --return-biopax 2>> biopax_process.err

	#move the auto-corrected owl file back to original directory
	cp ${1}.modified.owl ${CURRENTDIR} 

	cd ${CURRENTDIR}
	#create gmt file from the given, autofixed biopax file
	#make sure the the id searching for doesn't have any spaces for the file name
	#long -D option turns off logging when using paxtool
	java -Xmx2G -Dorg.apache.commons.logging.Log=org.apache.commons.logging.impl.NoOpLog -jar ${TOOLDIR}/GenesetTools.jar toGSEA --biopax ${1}.modified.owl --outfile ${1}_${2//[[:space:]]}.gmt --id "$2" --speciescheck FALSE --source "$3" 2>> biopax_process.err 1>> biopax_output.txt

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

#tranlsate ids using synergizer
# argument 1 - gmt file name
# argument 2 - taxonomy id
# argument 3 - id found in gmt file
function translate_gmt {

		java -Xmx2G -jar ${TOOLDIR}/GenesetTools.jar translate_synergizer --gmt $1 --organism $2 --oldID $3 2>> translate_process.err 1>> translate_output.txt 

}

# argument 1 - gmt file name
# argument 2 - taxonomy id
# argument 3 - id found in gmt file
function translate_gmt_uniprot {

	if [[ $3 == "UniProt" ]] ; then
		
		java -Xmx2G -jar ${TOOLDIR}/GenesetTools.jar translate --gmt $1 --organism $2 --oldID $3 --newID "symbol" 2>> translate_process.err 1>> translate_output.txt 
		java -Xmx2G -jar ${TOOLDIR}/GenesetTools.jar translate --gmt $1 --organism $2 --oldID $3 --newID "entrezgene" 2>> translate_process.err 1>> translate_output.txt 
	fi

	if [[ $3 == "entrezgene" ]] ; then
	
		java -Xmx2G -jar ${TOOLDIR}/GenesetTools.jar translate --gmt $1 --organism $2 --oldID $3 --newID "symbol" 2>> translate_process.err 1>> translate_output.txt 
		java -Xmx2G -jar ${TOOLDIR}/GenesetTools.jar translate --gmt $1 --organism $2 --oldID $3 --newID "uniprot" 2>> translate_process.err 1>> translate_output.txt 
	
	fi

	if [[ $3 == "MGI" ]] ; then
		
		java -Xmx2G -jar ${TOOLDIR}/GenesetTools.jar translate --gmt $1 --organism $2 --oldID $3 --newID "symbol" 2>> translate_process.err 1>> translate_output.txt 
		java -Xmx2G -jar ${TOOLDIR}/GenesetTools.jar translate --gmt $1 --organism $2 --oldID $3 --newID "entrezgene" 2>> translate_process.err 1>> translate_output.txt 	
		java -Xmx2G -jar ${TOOLDIR}/GenesetTools.jar translate --gmt $1 --organism $2 --oldID $3 --newID "uniprot" 2>> translate_process.err 1>> translate_output.txt 
	fi

	if [[ $3 == "RGD" ]] ; then
		
		java -Xmx2G -jar ${TOOLDIR}/GenesetTools.jar translate --gmt $1 --organism $2 --oldID $3 --newID "symbol" 2>> translate_process.err 1>> translate_output.txt 
		java -Xmx2G -jar ${TOOLDIR}/GenesetTools.jar translate --gmt $1 --organism $2 --oldID $3 --newID "entrezgene" 2>> translate_process.err 1>> translate_output.txt 	
		java -Xmx2G -jar ${TOOLDIR}/GenesetTools.jar translate --gmt $1 --organism $2 --oldID $3 --newID "uniprot" 2>> translate_process.err 1>> translate_output.txt 
	fi

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

# argument 1 - hpo annot file name
# argument 2 - hpo obo file
# argument 3 - outputfile
function process_hpo {
	java -Xmx2G -jar ${TOOLDIR}/GenesetTools.jar createHPO --annotfile $1 --obofile $2 --outfile $3  2>> HPO_process.err 1>> HPO_output.txt 
}

# argument 1 - source to copy
#argument 2 - species
#argument 3 - division to put data into
function copy2release {
	cat *gene.gmt > ${2}_${1}_Entrezgene.gmt
	cp ${2}_${1}_Entrezgene.gmt ${EG}/${3}/${2}_${1}_${dir_name}_Entrezgene.gmt
	#concatenate all the translation summaries
	
	files=$(ls *gene_summary.log 2> /dev/null | wc -l)
	if [ $files != 0 ] ; then
	#if [ -e *gene_summary.log ] ; then
		cat *gene_summary.log > ${2}_${1}_Entrezgene_translation_summary.log
		cp ${2}_${1}_Entrezgene_translation_summary.log ${EG}/${3}/${2}_${1}_Entrezgene_translation_summary.log
	fi

	cat *UniProt.gmt > ${2}_${1}_UniProt.gmt
	cp ${2}_${1}_UniProt.gmt ${UNIPROT}/${3}/${2}_${1}_${dir_name}_UniProt.gmt
	#concatenate all the translation summaries
	files=$(ls *UniProt_summary.log 2> /dev/null | wc -l)
	if [ $files != 0 ] ; then
	#if [ -e *UniProt_summary.log ] ; then
		cat *UniProt_summary.log > ${2}_${1}_UniProt_translation_summary.log
		cp ${2}_${1}_UniProt_translation_summary.log ${UNIPROT}/${3}/${2}_${1}_UniProt_translation_summary.log
	fi
	
	cat *symbol.gmt > ${2}_${1}_symbol.gmt
	cp ${2}_${1}_symbol.gmt ${SYMBOL}/${3}/${2}_${1}_${dir_name}_symbol.gmt
	#concatenate all the translation summaries
	files=$(ls *symbol_summary.log 2> /dev/null | wc -l)
	if [ $files != 0 ] ; then
	#if [ -e *symbol_summary.log ] ; then
		cat *symbol_summary.log > ${2}_${1}_symbol_translation_summary.log
		cp ${2}_${1}_symbol_translation_summary.log ${SYMBOL}/${3}/${2}_${1}_symbol_translation_summary.log
	fi

}

# argument 1 - source to copy
#argument 2 - species
#argument 3 - division to put data into
function copy2release_nomerge {
	cp *ntrezgene.gmt ${EG}/${3}/
	#concatenate all the translation summaries
	
	files=$(ls *gene_summary.log 2> /dev/null | wc -l)
	if [ $files != 0 ] ; then
	#if [ -e *gene_summary.log ] ; then
		cat *gene_summary.log > ${2}_${1}_Entrezgene_translation_summary.log
		cp ${2}_${1}_Entrezgene_translation_summary.log ${EG}/${3}/${2}_${1}_Entrezgene_translation_summary.log
	fi

	cp *UniProt.gmt ${UNIPROT}/${3}/
	#concatenate all the translation summaries
	files=$(ls *UniProt_summary.log 2> /dev/null | wc -l)
	if [ $files != 0 ] ; then
	#if [ -e *UniProt_summary.log ] ; then
		cat *UniProt_summary.log > ${2}_${1}_UniProt_translation_summary.log
		cp ${2}_${1}_UniProt_translation_summary.log ${UNIPROT}/${3}/${2}_${1}_UniProt_translation_summary.log
	fi
	
	cp *_symbol.gmt ${SYMBOL}/${3}/
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
	mkdir ${DRUGS}
        mkdir ${MISC}
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

        cd ${1}/${DRUGS}
        files=$(ls *.gmt 2> /dev/null | wc -l)
        if [ $files != 0 ] ; then

                echo 'Drug Targets Stats:' >> ${1}/Summary_Geneset_Counts.txt
                wc -l *.gmt >> ${1}/Summary_Geneset_Counts.txt
        fi

        mv ${1}/Summary_Geneset_Counts.txt ${OUTPUTDIR}

        ${TOOLDIR}/GetAllStats.sh ${2} ${1}/Build_summary_stats_${2}.log ${3}
        mv ${1}/Build_summary_stats_${2}.log ${OUTPUTDIR}
}

#source all configuration parameters we need (contains paths to output directories and the like)

#make sure we are using Java 6
export JAVA_HOME="/System/Library/Frameworks/JavaVM.framework/Versions/1.6/Home"

LOCAL=/Users/risserlin

#make sure we are using our local perl libs
export PERL5LIB=${PERL5LIB}:${LOCAL}/lib/perl5:${LOCAL}/lib/perl5/lib64/perl5:${LOCAL}/lib/perl5/lib:${LOCAL}/lib/perl5/lib/i386-linux-thread-multi/:${LOCAL}/lib/perl5/lib/perl5/site_perl

#create a new directory for this release (the directory name will be the date that it was built)
dir_name=`date '+%B_%d_%Y'`
TOOLDIR=`pwd`
VALIDATORDIR=${TOOLDIR}/biopax-validator-3.0.0
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
DRUGS=DrugTargets
MISC=Misc

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

#download humancyc
HUMANCYC=${SOURCE}/Humancyc
mkdir ${HUMANCYC}

#issue - novemeber 20,2018 -can't download new data without new subscription
#download_biocyc_data "human" ${HUMANCYC}
cd ${HUMANCYC}

cp ${STATICDIR}/biocyc/human*.gz ./

#unzip and untar human.tar.gz file
tar --wildcards -xvzf human.tar.gz *level3.owl
#the release number keeps changing - need a way to change into the right directory without knowing what the new number is
# instead of specifying the name of the directory put *.  This will break if
# they change the data directory structure though.
cd humancyc/*/data
for file in *.owl; do
	process_biopax_novalidation $file "UniProt" "HumanCyc"
done
for file in *.gmt; do
	translate_gmt $file "9606" "UniProt"
done
copy2release HumanCyc Human ${PATHWAYS}



#download the WikiPathways gmt files
WIKIPATHWAYS=${SOURCE}/WikiPathways
mkdir ${WIKIPATHWAYS}
download_wikipathways_data Homo_sapiens

#modify the gmt file so the name is the first thing seen in the description column
cd ${WIKIPATHWAYS}
for file in *.gmt; do
	cat $file | awk -F\\t '{OFS=FS; split($1,b,"%");$2=b[1]; print $0}' > temp.gmt
        cp temp.gmt $file
	translate_gmt $file "9606" "entrezgene"
done
copy2release WikiPathways Human ${PATHWAYS}



#download NCI from NCI database.
NCI=${SOURCE}/NCI
mkdir -p ${NCI}
cd ${NCI}
#download_nci_data
cp ${STATICDIR}/NCI/*.gz ./
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

#download panther biopax data
PANTHER=${SOURCE}/Panther
mkdir ${PANTHER}
download_panther_data
#unzip and untar human.tar.gz file
cd ${PANTHER}
tar -xvzf BioPAX.tar.gz --strip=1 >/dev/null
#Go through each pathway file and grab the uniprots
for file in *.owl; do
	process_biopax_novalidation $file "UniProt" "Panther"
done
for file in *.gmt; do
	#because we are going through processes instead of pathways (because of biopax contents)
        #we get duplicated lines.  Get rid of all lines that don't have a Panther ID
	grep "%PANTHER Pathway%" $file > temp_file.gmt
	mv temp_file.gmt $file
	translate_gmt $file "9606" "UniProt"
done
copy2release Panther Human ${PATHWAYS}

#download NetPath biopax data
NETPATH=${SOURCE}/NetPath
mkdir ${NETPATH}
download_netpath_data

cd ${NETPATH}
#process each file in the NetPath directory.
for file in *.owl; do
	process_biopax_novalidation $file "Entrez gene" "NetPath"
done
for file in *.gmt; do
	translate_gmt $file "9606" "entrezgene"
done
#merge all the gmt into one NetPath GMT
copy2release NetPath Human ${PATHWAYS}



#download Reactome biopax data
REACTOME=${SOURCE}/Reactome
mkdir ${REACTOME}
download_reactome_data
cd ${REACTOME}
unzip biopax.zip *sapiens.owl
mv Homo\ sapiens.owl Homosapiens.owl

#for some reason the validated and fixed Reactome file hangs.
for file in *sapiens.owl; do
	process_biopax_novalidation $file "UniProt" "Reactome"
done
for file in *.gmt; do
	translate_gmt $file "9606" "UniProt"
done
copy2release Reactome Human ${PATHWAYS}





#download Human Phenotype data
DISEASESRC=${SOURCE}/DiseasePhenotypes
mkdir ${DISEASESRC}
download_HPO_data 
cd ${DISEASESRC}
#cp ${STATICDIR}/DiseasePhenotypes/gene_to_phenotype_june142013.txt ./

#parse the the phenotypes and obo file.
process_hpo genes_to_phenotype.txt human-phenotype-ontology.obo Human_DiseasePhenotypes_Entrezgene.gmt

#translate the new file into other identifiers
for file in *.gmt; do
	translate_gmt $file "9606" "entrezgene"
done
copy2release_nomerge DiseasePhenotypes Human ${DISEASE}

#parse drugbank data
DRUGSSRC=${SOURCE}/DrugBank
mkdir -p ${DRUGSSRC}
cd ${DRUGSSRC}
download_drugbank_data

# if drugbank goes down revert to using static file
#cp ${STATICDIR}/Drugbank/drugbank.xml.zip ./
#cp ${STATICDIR}/Drugbank/*.txt ${VERSIONS}

for file in *.zip; do
	unzip $file	
done 

#move the xml file to drugbank.xml
mv *.xml drugbank.xml

#process the drugbank file - all drugs
perl ${TOOLDIR}/scripts/parseDrugBankXml.pl -f drugbank.xml -o Human_DrugBank_all_symbol.gmt -i genename 2>>drugbankparse.err
#process the drugbank file - approved drugs
perl ${TOOLDIR}/scripts/parseDrugBankXml.pl -f drugbank.xml -o Human_DrugBank_approved_symbol.gmt -d "approved" -i genename  2>>drugbankparse.err
#process the drugbank file - illicit drugs
perl ${TOOLDIR}/scripts/parseDrugBankXml.pl -f drugbank.xml -o Human_DrugBank_illicit_symbol.gmt -d "illicit" -i genename  2>>drugbankparse.err
#process the drugbank file - experimental drugs
perl ${TOOLDIR}/scripts/parseDrugBankXml.pl -f drugbank.xml -o Human_DrugBank_experimental_symbol.gmt -d "experimental" -i genename  2>>drugbankparse.err
#process the drugbank file - nutraceutical drugs
perl ${TOOLDIR}/scripts/parseDrugBankXml.pl -f drugbank.xml -o Human_DrugBank_nutraceutical_symbol.gmt -d "nutraceutical" -i genename  2>>drugbankparse.err
#process the drugbank file - small molecule drugs
perl ${TOOLDIR}/scripts/parseDrugBankXml.pl -f drugbank.xml -o Human_DrugBank_smallmolecule_symbol.gmt -d "small molecule" -i genename  2>>drugbankparse.err

#uniprot computation
#process the drugbank file - all drugs
perl ${TOOLDIR}/scripts/parseDrugBankXml.pl -f drugbank.xml -o Human_DrugBank_all_uniprot.gmt -i uniprot 2>>drugbankparse.err
#process the drugbank file - approved drugs
perl ${TOOLDIR}/scripts/parseDrugBankXml.pl -f drugbank.xml -o Human_DrugBank_approved_uniprot.gmt -d "approved" -i uniprot  2>>drugbankparse.err
#process the drugbank file - illicit drugs
perl ${TOOLDIR}/scripts/parseDrugBankXml.pl -f drugbank.xml -o Human_DrugBank_illicit_uniprot.gmt -d "illicit" -i uniprot  2>>drugbankparse.err
#process the drugbank file - experimental drugs
perl ${TOOLDIR}/scripts/parseDrugBankXml.pl -f drugbank.xml -o Human_DrugBank_experimental_uniprot.gmt -d "experimental" -i uniprot  2>>drugbankparse.err
#process the drugbank file - nutraceutical drugs
perl ${TOOLDIR}/scripts/parseDrugBankXml.pl -f drugbank.xml -o Human_DrugBank_nutraceutical_uniprot.gmt -d "nutraceutical" -i uniprot  2>>drugbankparse.err
#process the drugbank file - small molecule drugs
perl ${TOOLDIR}/scripts/parseDrugBankXml.pl -f drugbank.xml -o Human_DrugBank_smallmolecule_uniprot.gmt -d "small molecule" -i uniprot  2>>drugbankparse.err

for file in *symbol.gmt; do
	translate_gmt $file "9606" "UniProt"
done
copy2release_nomerge DrugBank Human ${DRUGS}




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
			translate_gmt $file "9606" "entrezgene"
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
		copy2release KEGG Human ${MISC}
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
			translate_gmt $file "9606" "entrezgene"
		done
		copy2release MSigdb Human ${PATHWAYS}
		cd ${STATICDIR}
	fi
#	if [[ $dir == "DiseasePhenotypes" ]] ; then
#		cd $dir
#		mkdir ${SOURCE}/$dir
#		cp *entrezgene.gmt ${SOURCE}/$dir
#		cp *version.txt ${VERSIONS}
#		cd ${SOURCE}/$dir
#		for file in *.gmt; do
#			translate_gmt $file "9606" "entrezgene"
#		done
#		copy2release DiseasePhenotypes Human ${DISEASE}
#		cd ${STATICDIR}
#	fi
	if [[ $dir == "mirs" ]] ; then
		cd $dir
		mkdir ${SOURCE}/$dir
		cp *.gmt ${SOURCE}/$dir
		cp *.txt ${VERSIONS}
		cd ${SOURCE}/$dir
		for file in *.gmt; do
			process_gmt $file "MSigdb_C3" 1
			translate_gmt $file "9606" "entrezgene"
		done
		copy2release miRs_MSigdb Human ${MIR}
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
			translate_gmt $file "9606" "entrezgene"
		done
		copy2release TranscriptionFactors_MSigdb Human ${TF}
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
cat *_${WITHIEA}*entrezgene.gmt > Human_GOALL_${WITHIEA}_${dir_name}_entrezgene.gmt
cat *_${WITHIEA}*UniProt.gmt > Human_GOALL_${WITHIEA}_${dir_name}_UniProt.gmt
cat *_${WITHIEA}*symbol.gmt > Human_GOALL_${WITHIEA}_${dir_name}_symbol.gmt

cat *_${NOIEA}*entrezgene.gmt > Human_GOALL_${NOIEA}_${dir_name}_entrezgene.gmt
cat *_${NOIEA}*UniProt.gmt > Human_GOALL_${NOIEA}_${dir_name}_UniProt.gmt
cat *_${NOIEA}*symbol.gmt > Human_GOALL_${NOIEA}_${dir_name}_symbol.gmt

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
cat *.gmt > ../Human_AllPathways_${dir_name}_entrezgene.gmt
cd ${EG}/${GO}
cat ../Human_AllPathways_${dir_name}_entrezgene.gmt Human_GOALL_${WITHIEA}_${dir_name}_entrezgene.gmt > ../Human_GO_AllPathways_${WITHIEA}_${dir_name}_entrezgene.gmt
cat ../Human_AllPathways_${dir_name}_entrezgene.gmt Human_GOALL_${NOIEA}_${dir_name}_entrezgene.gmt > ../Human_GO_AllPathways_${NOIEA}_${dir_name}_entrezgene.gmt

#create two new all pathways files with GOBP included
cat ../Human_AllPathways_${dir_name}_entrezgene.gmt Human_GO_bp_${WITHIEA}_entrezgene.gmt > ../Human_GOBP_AllPathways_${WITHIEA}_${dir_name}_entrezgene.gmt
cat ../Human_AllPathways_${dir_name}_entrezgene.gmt Human_GO_bp_${NOIEA}_entrezgene.gmt > ../Human_GOBP_AllPathways_${NOIEA}_${dir_name}_entrezgene.gmt

#merge all the summaries
mergesummaries ${EG} entrezgene

cd ${SYMBOL}/${PATHWAYS}
cat *.gmt > ../Human_AllPathways_${dir_name}_symbol.gmt
cd ${SYMBOL}/${GO}
cat ../Human_AllPathways_${dir_name}_symbol.gmt Human_GOALL_${WITHIEA}_${dir_name}_symbol.gmt > ../Human_GO_AllPathways_${WITHIEA}_${dir_name}_symbol.gmt
cat ../Human_AllPathways_${dir_name}_symbol.gmt Human_GOALL_${NOIEA}_${dir_name}_symbol.gmt > ../Human_GO_AllPathways_${NOIEA}_${dir_name}_symbol.gmt

#create two new all pathways files with GOBP included
cat ../Human_AllPathways_${dir_name}_symbol.gmt Human_GO_bp_${WITHIEA}_symbol.gmt > ../Human_GOBP_AllPathways_${WITHIEA}_${dir_name}_symbol.gmt
cat ../Human_AllPathways_${dir_name}_symbol.gmt Human_GO_bp_${NOIEA}_symbol.gmt > ../Human_GOBP_AllPathways_${NOIEA}_${dir_name}_symbol.gmt


#merge all the summaries
mergesummaries ${SYMBOL} symbol

cd ${UNIPROT}/${PATHWAYS}
cat *.gmt > ../Human_AllPathways_${dir_name}_UniProt.gmt
cd ${UNIPROT}/${GO}
cat ../Human_AllPathways_${dir_name}_UniProt.gmt Human_GOALL_${WITHIEA}_${dir_name}_UniProt.gmt > ../Human_GO_AllPathways_${WITHIEA}_${dir_name}_UniProt.gmt
cat ../Human_AllPathways_${dir_name}_UniProt.gmt Human_GOALL_${NOIEA}_${dir_name}_UniProt.gmt > ../Human_GO_AllPathways_${NOIEA}_${dir_name}_UniProt.gmt

#create two new all pathways files with GOBP included
cat ../Human_AllPathways_${dir_name}_UniProt.gmt Human_GO_bp_${WITHIEA}_UniProt.gmt > ../Human_GOBP_AllPathways_${WITHIEA}_${dir_name}_UniProt.gmt
cat ../Human_AllPathways_${dir_name}_UniProt.gmt Human_GO_bp_${NOIEA}_UniProt.gmt > ../Human_GOBP_AllPathways_${NOIEA}_${dir_name}_UniProt.gmt


#merge all the summaries
mergesummaries ${UNIPROT} UniProt

#create the stats summary
getstats ${EG} "entrezgene" ${SOURCE}
getstats ${UNIPROT} "UniProt" ${SOURCE}
getstats ${SYMBOL} "symbol" ${SOURCE}

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

#
#July 4, 2016 - noticed there are significantly less pathways in the mouse
# reactome set than in the human set.  This wasn't always the situation. 
# Comparing file from June 2014 and there were pathways in this set that 
# were missing from the latest set. 
# take out direct download and convert the human file.
#
#download Reactome biopax data
#REACTOME=${MOUSESOURCE}/Reactome
#mkdir ${REACTOME}
#copy reactome file from human src directory
#cp ${SOURCE}/Reactome/*.zip ${REACTOME}/
#copy reactome version into mouse_versions directory.
#cp ${HUMANVERSIONS}/Reactome.txt ${VERSIONS}
#cd ${REACTOME}
#unzip biopax.zip *musculus.owl
#mv Mus\ musculus.owl Musmusculus.owl

#for some reason the validated and fixed Reactome file hangs.
#for file in *.owl; do
#	process_biopax_novalidation $file "UniProt" "Reactome"
#done
#for file in *.gmt; do
#	translate_gmt $file "10090" "UniProt"
#done
#copy2release Reactome Mouse ${PATHWAYS}

#download the WikiPathways gmt files
WIKIPATHWAYS=${MOUSESOURCE}/WikiPathways
mkdir ${WIKIPATHWAYS}
download_wikipathways_data Mus_musculus
cd ${WIKIPATHWAYS}
for file in *.gmt; do
	cat $file | awk -F\\t '{OFS=FS;split($1,b,"%");$2=b[1]; print $0}' > temp.gmt
	mv temp.gmt $file
	translate_gmt $file "10090" "entrezgene"
done
copy2release WikiPathways Mouse ${PATHWAYS}



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
cat *_${WITHIEA}*entrezgene.gmt > Mouse_GOALL_${WITHIEA}_${dir_name}_entrezgene.gmt
cat *_${WITHIEA}*UniProt.gmt > Mouse_GOALL_${WITHIEA}_${dir_name}_UniProt.gmt
cat *_${WITHIEA}*symbol.gmt > Mouse_GOALL_${WITHIEA}_${dir_name}_symbol.gmt

cat *_${NOIEA}*entrezgene.gmt > Mouse_GOALL_${NOIEA}_${dir_name}_entrezgene.gmt
cat *_${NOIEA}*UniProt.gmt > Mouse_GOALL_${NOIEA}_${dir_name}_UniProt.gmt
cat *_${NOIEA}*symbol.gmt > Mouse_GOALL_${NOIEA}_${dir_name}_symbol.gmt

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
#cp ${HUMANGMTS}/${PATHWAYS}/*KEGG*.gmt ./
cp ${HUMANGMTS}/${PATHWAYS}/*Panther*.gmt ./
cp ${HUMANGMTS}/${PATHWAYS}/*Reactome*.gmt ./

#copy the human drugs files
cp ${HUMANGMTS}/${DRUGS}/*DrugBank*.gmt ./

#copy all version into mouse_versions directory.
cp ${HUMANVERSIONS}/NetPath.txt ${VERSIONS}
cp ${HUMANVERSIONS}/IOB.txt ${VERSIONS}
cp ${HUMANVERSIONS}/msigdb_path.txt ${VERSIONS}
cp ${HUMANVERSIONS}/NCI_Nature.txt ${VERSIONS}
cp ${HUMANVERSIONS}/humancyc.txt ${VERSIONS}
cp ${HUMANVERSIONS}/KEGG.txt ${VERSIONS}
cp ${HUMANVERSIONS}/Panther.txt ${VERSIONS}

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

#mv all the drugbank files to a separate directory
mkdir ${DRUGS}
mv *DrugBank* ${DRUGS}

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

#copy the drugbank converted files
cd ${DRUGS}
#copy all the pathway file - can't use the copy function because there are multiple pathway datasets in this set. 
cp Mouse*_entrezgene.gmt ${EG}/${DRUGS}/
cp Mouse*_UniProt.gmt ${UNIPROT}/${DRUGS}/
cp Mouse*_symbol.gmt ${SYMBOL}/${DRUGS}/

#concatenate all the translation summaries	
files=$(ls *10090_conversion.log 2> /dev/null | wc -l)
if [ $files != 0 ] ; then
	cat *10090_conversion.log > Mouse_translatedDrugs_Entrezgene_translation_summary.log
	cp Mouse_translatedDrugs_Entrezgene_translation_summary.log ${EG}/${DRUGS}/Mouse_translatedDrugs_Entrezgene_translation_summary.log
fi
	
files=$(ls *UniProt_summary.log 2> /dev/null | wc -l)
if [ $files != 0 ] ; then
	cat *UniProt_summary.log > Mouse_translatedDrugs_UniProt_translation_summary.log
	cp Mouse_translatedDrugs_UniProt_translation_summary.log ${UNIPROT}/${DRUGS}/Mouse_translatedDrugs_UniProt_translation_summary.log
fi
		
files=$(ls *symbol_summary.log 2> /dev/null | wc -l)
if [ $files != 0 ] ; then
	cat *symbol_summary.log > Mouse_translatedDrugs_symbol_translation_summary.log
	cp Mouse_translatedDrugs_symbol_translation_summary.log ${SYMBOL}/${DRUGS}/Mouse_translatedDrugs_symbol_translation_summary.log
fi


#compile all the different versions
cd ${VERSIONS}
cat *.txt > ${OUTPUTDIR}/${dir_name}_versions.txt

#create all the different distributions
cd ${EG}/${PATHWAYS}
cat *.gmt > ../Mouse_AllPathways_${dir_name}_entrezgene.gmt
cd ${EG}/${GO}
cat ../Mouse_AllPathways_${dir_name}_entrezgene.gmt Mouse_GOALL_${WITHIEA}_${dir_name}_entrezgene.gmt > ../Mouse_GO_AllPathways_${WITHIEA}_${dir_name}_entrezgene.gmt
cat ../Mouse_AllPathways_${dir_name}_entrezgene.gmt Mouse_GOALL_${NOIEA}_${dir_name}_entrezgene.gmt > ../Mouse_GO_AllPathways_${NOIEA}_${dir_name}_entrezgene.gmt

#create two new all pathways files with GOBP included
cat ../Mouse_AllPathways_${dir_name}_entrezgene.gmt MOUSE_GO_bp_${WITHIEA}_entrezgene.gmt > ../Mouse_GOBP_AllPathways_${WITHIEA}_${dir_name}_entrezgene.gmt
cat ../Mouse_AllPathways_${dir_name}_entrezgene.gmt MOUSE_GO_bp_${NOIEA}_entrezgene.gmt > ../Mouse_GOBP_AllPathways_${NOIEA}_${dir_name}_entrezgene.gmt


#merge all the summaries
mergesummaries ${EG} entrezgene

cd ${SYMBOL}/${PATHWAYS}
cat *.gmt > ../Mouse_AllPathways_${dir_name}_symbol.gmt
cd ${SYMBOL}/${GO}
cat ../Mouse_AllPathways_${dir_name}_symbol.gmt Mouse_GOALL_${WITHIEA}_${dir_name}_symbol.gmt > ../Mouse_GO_AllPathways_${WITHIEA}_${dir_name}_symbol.gmt
cat ../Mouse_AllPathways_${dir_name}_symbol.gmt Mouse_GOALL_${NOIEA}_${dir_name}_symbol.gmt > ../Mouse_GO_AllPathways_${NOIEA}_${dir_name}_symbol.gmt

#create two new all pathways files with GOBP included
cat ../Mouse_AllPathways_${dir_name}_symbol.gmt MOUSE_GO_bp_${WITHIEA}_symbol.gmt > ../Mouse_GOBP_AllPathways_${WITHIEA}_${dir_name}_symbol.gmt
cat ../Mouse_AllPathways_${dir_name}_symbol.gmt MOUSE_GO_bp_${NOIEA}_symbol.gmt > ../Mouse_GOBP_AllPathways_${NOIEA}_${dir_name}_symbol.gmt


#merge all the summaries
mergesummaries ${SYMBOL} symbol

cd ${UNIPROT}/${PATHWAYS}
cat *.gmt > ../Mouse_AllPathways_${dir_name}_UniProt.gmt
cd ${UNIPROT}/${GO}
cat ../Mouse_AllPathways_${dir_name}_UniProt.gmt Mouse_GOALL_${WITHIEA}_${dir_name}_UniProt.gmt > ../Mouse_GO_AllPathways_${WITHIEA}_${dir_name}_UniProt.gmt
cat ../Mouse_AllPathways_${dir_name}_UniProt.gmt Mouse_GOALL_${NOIEA}_${dir_name}_UniProt.gmt > ../Mouse_GO_AllPathways_${NOIEA}_${dir_name}_UniProt.gmt

#create two new all pathways files with GOBP included
cat ../Mouse_AllPathways_${dir_name}_UniProt.gmt MOUSE_GO_bp_${WITHIEA}_UniProt.gmt > ../Mouse_GOBP_AllPathways_${WITHIEA}_${dir_name}_UniProt.gmt
cat ../Mouse_AllPathways_${dir_name}_UniProt.gmt MOUSE_GO_bp_${NOIEA}_UniProt.gmt > ../Mouse_GOBP_AllPathways_${NOIEA}_${dir_name}_UniProt.gmt


#merge all the summaries
mergesummaries ${UNIPROT} UniProt

#create the stats summary
getstats ${EG} "entrezgene" ${MOUSESOURCE}
getstats ${UNIPROT} "UniProt" ${MOUSESOURCE}
getstats ${SYMBOL} "symbol" ${MOUSESOURCE}

#Rat data is downloaded from the Gene ontology website and comes from RGD
function download_GORat_data {
	echo "[Downloading current Go Rat  data]"
	URL="http://cvsweb.geneontology.org/cgi-bin/cvsweb.cgi/go/gene-associations/gene_association.rgd.gz?rev=HEAD"
	curl $URL -o ${GOSRC}/gene_association.rgd.gz -s
	get_webfile_version ${URL} "GO_Rat"

	#get the obo file from the gene ontology website
	echo "[Downloading current GO OBO file]"
	URL="ftp://ftp.geneontology.org/pub/go/ontology/obo_format_1_2/"
	curl ${URL}/gene_ontology.1_2.obo -o ${GOSRC}/gene_ontology.1_2.obo -s
	get_webfile_version ${URL}/gene_ontology.1_2.obo "GO_OBO_FILE"

}


#################################################################
# Create Rat Genesets.
##########################################################
#use same gmts as mouse

OUTPUTDIR=${CUR_RELEASE}/Rat
UNIPROT=${OUTPUTDIR}/UniProt
EG=${OUTPUTDIR}/Entrezgene
SYMBOL=${OUTPUTDIR}/symbol
RatSOURCE=${CUR_RELEASE}/SRC_Rat
VERSIONS=${CUR_RELEASE}/version_Rat

mkdir ${RatSOURCE}
mkdir ${VERSIONS}
mkdir -p ${UNIPROT}
mkdir -p ${EG}
mkdir -p ${SYMBOL}

createDivisionDirs ${UNIPROT}
createDivisionDirs ${EG}
createDivisionDirs ${SYMBOL}


#Direct source for Rat come from GO, Ratcyc, Reactome, Kegg

#download Ratcyc --get from human instead, there is nothing in the Ratcyc
#RatCYC=${RatSOURCE}/Ratcyc
#mkdir ${RatCYC}
#download_biocyc_data "Rat" ${RatCYC}
#cd ${RatCYC}
#unzip and untar Rat biopax level 3 file
#tar -xvzf Rat.tar.gz *level3.owl
#cd 1.36/data
#for file in *.owl; do
#	process_biopax $file "UniProt" "RatCyc"
#done
#for file in *.gmt; do
#	translate_gmt $file "10090" "UniProt"
#done
#copy2release RatCyc Rat ${PATHWAYS}


#download Reactome biopax data
REACTOME=${RatSOURCE}/Reactome
mkdir ${REACTOME}
#copy reactome file from human src directory
cp ${SOURCE}/Reactome/*.zip ${REACTOME}/
#copy reactome version into Rat_versions directory.
cp ${HUMANVERSIONS}/Reactome.txt ${VERSIONS}
cd ${REACTOME}
unzip biopax.zip Rattus_norvegicus.owl

#for some reason the validated and fixed Reactome file hangs.
for file in *.owl; do
	process_biopax_novalidation $file "UniProt" "Reactome"
done
for file in *.gmt; do
	translate_gmt $file "10116" "UniProt"
done
copy2release Reactome Rat ${PATHWAYS}


#download the WikiPathways gmt files
WIKIPATHWAYS=${RATSOURCE}/WikiPathways
mkdir ${WIKIPATHWAYS}
download_wikipathways_data Rattus_norvegicus
cd ${WIKIPATHWAYS}
for file in *.gmt; do
	cat $file | awk -F\\t '{OFS=FS;split($1,b,"%");$2=b[1]; print $0}' > temp.gmt
	mv temp.gmt $file
	translate_gmt $file "10116" "entrezgene"
done
copy2release WikiPathways Rat ${PATHWAYS}



#process GO
GOSRC=${RatSOURCE}/GO
mkdir ${GOSRC}
download_GORat_data
cd ${GOSRC}
GOOBO=${GOSRC}/gene_ontology.1_2.obo
gunzip *.gz
for file in *.rgd*; do
	process_gaf $file "10116" "bp" ${GOOBO}
	process_gaf_noiea  $file "10116" "bp" ${GOOBO}
	process_gaf $file "10116" "mf" ${GOOBO}
	process_gaf_noiea  $file "10116" "mf" ${GOOBO}
	process_gaf $file "10116" "cc" ${GOOBO}
	process_gaf_noiea  $file "10116" "cc" ${GOOBO}
done
for file in *.gmt; do
	translate_gmt $file "10116" "RGD"
done

#create  the compilation of all branches
cat *_${WITHIEA}*entrezgene.gmt > Rat_GOALL_${WITHIEA}_${dir_name}_entrezgene.gmt
cat *_${WITHIEA}*UniProt.gmt > Rat_GOALL_${WITHIEA}_${dir_name}_UniProt.gmt
cat *_${WITHIEA}*symbol.gmt > Rat_GOALL_${WITHIEA}_${dir_name}_symbol.gmt

cat *_${NOIEA}*entrezgene.gmt > Rat_GOALL_${NOIEA}_${dir_name}_entrezgene.gmt
cat *_${NOIEA}*UniProt.gmt > Rat_GOALL_${NOIEA}_${dir_name}_UniProt.gmt
cat *_${NOIEA}*symbol.gmt > Rat_GOALL_${NOIEA}_${dir_name}_symbol.gmt

cp *entrezgene.gmt ${EG}/${GO}
#create report of translations
cat *gene_summary.log > ${EG}/${GO}/Rat_GO_entrezgene_translation_summary.log

cp *UniProt.gmt ${UNIPROT}/${GO}
#create report of translations
cat *UniProt_summary.log > ${UNIPROT}/${GO}/Rat_GO_UniProt_translation_summary.log

cp *symbol.gmt ${SYMBOL}/${GO}
#create report of translations
cat *UniProt_summary.log > ${UNIPROT}/${GO}/Rat_GO_UniProt_translation_summary.log


#create a directory will all the gmt from human we want to convert to Rat
CONV=${RatSOURCE}/ToBeConverted
mkdir ${CONV}

#copy the following files to Rat directory to be converted from human to Rat
cd ${CONV}
cp ${HUMANGMTS}/${PATHWAYS}/*NetPath*.gmt ./
cp ${HUMANGMTS}/${PATHWAYS}/*IOB*.gmt ./
cp ${HUMANGMTS}/${PATHWAYS}/*MSig*.gmt ./
cp ${HUMANGMTS}/${PATHWAYS}/*NCI*.gmt ./
cp ${HUMANGMTS}/${PATHWAYS}/*HumanCyc*.gmt ./
#cp ${HUMANGMTS}/${PATHWAYS}/*KEGG*.gmt ./
cp ${HUMANGMTS}/${PATHWAYS}/*Panther*.gmt ./

#copy the human drugs files
cp ${HUMANGMTS}/${DRUGS}/*DrugBank*.gmt ./

#copy all version into Rat_versions directory.
cp ${HUMANVERSIONS}/NetPath.txt ${VERSIONS}
cp ${HUMANVERSIONS}/IOB.txt ${VERSIONS}
cp ${HUMANVERSIONS}/msigdb_path.txt ${VERSIONS}
cp ${HUMANVERSIONS}/NCI_Nature.txt ${VERSIONS}
cp ${HUMANVERSIONS}/humancyc.txt ${VERSIONS}
cp ${HUMANVERSIONS}/KEGG.txt ${VERSIONS}
cp ${HUMANVERSIONS}/Panther.txt ${VERSIONS}

#go through each of the gmt file and convert to Rat entrez genes
for file in Human*.gmt ; do
	convert_gmt $file "10116" "Rat"
done

#got through all the newly created Rat gmt file and translate them from eg to uniprot and symbol
for file in Rat*.gmt ; do
	translate_gmt $file "10116" "entrezgene"
done

#mv all the drugbank files to a separate directory
mkdir ${DRUGS}
mv *DrugBank* ${DRUGS}

#copy all the pathway file - can't use the copy function because there are multiple pathway datasets in this set. 
cp Rat*_Entrezgene.gmt ${EG}/${PATHWAYS}/
cp Rat*_UniProt.gmt ${UNIPROT}/${PATHWAYS}/
cp Rat*_symbol.gmt ${SYMBOL}/${PATHWAYS}/

#concatenate all the translation summaries	
files=$(ls *10116_conversion.log 2> /dev/null | wc -l)
if [ $files != 0 ] ; then
	cat *10116_conversion.log > Rat_translatedPathways_Entrezgene_translation_summary.log
	cp Rat_translatedPathways_Entrezgene_translation_summary.log ${EG}/${PATHWAYS}/Rat_translatedPathways_Entrezgene_translation_summary.log
fi
	
files=$(ls *UniProt_summary.log 2> /dev/null | wc -l)
if [ $files != 0 ] ; then
	cat *UniProt_summary.log > Rat_translatedPathways_UniProt_translation_summary.log
	cp Rat_translatedPathways_UniProt_translation_summary.log ${UNIPROT}/${PATHWAYS}/Rat_translatedPathways_UniProt_translation_summary.log
fi
		
files=$(ls *symbol_summary.log 2> /dev/null | wc -l)
if [ $files != 0 ] ; then
	cat *symbol_summary.log > Rat_translatedPathways_symbol_translation_summary.log
	cp $Rat_translatedPathways_symbol_translation_summary.log ${SYMBOL}/${PATHWAYS}/Rat_translatedPathways_symbol_translation_summary.log
fi

#compile all the different versions
cd ${VERSIONS}
cat *.txt > ${OUTPUTDIR}/${dir_name}_versions.txt

#create all the different distributions
cd ${EG}/${PATHWAYS}
cat *.gmt > ../Rat_AllPathways_${dir_name}_entrezgene.gmt
cd ${EG}/${GO}
cat ../Rat_AllPathways_${dir_name}_entrezgene.gmt Rat_GOALL_${WITHIEA}_${dir_name}_entrezgene.gmt > ../Rat_GO_AllPathways_${WITHIEA}_${dir_name}_entrezgene.gmt
cat ../Rat_AllPathways_${dir_name}_entrezgene.gmt Rat_GOALL_${NOIEA}_${dir_name}_entrezgene.gmt > ../Rat_GO_AllPathways_${NOIEA}_${dir_name}_entrezgene.gmt

#create two new all pathways files with GOBP included
cat ../Rat_AllPathways_${dir_name}_entrezgene.gmt RAT_GO_bp_${WITHIEA}_entrezgene.gmt > ../Rat_GOBP_AllPathways_${WITHIEA}_${dir_name}_entrezgene.gmt
cat ../Rat_AllPathways_${dir_name}_entrezgene.gmt RAT_GO_bp_${NOIEA}_entrezgene.gmt > ../Rat_GOBP_AllPathways_${NOIEA}_${dir_name}_entrezgene.gmt


#merge all the summaries
mergesummaries ${EG} entrezgene

cd ${SYMBOL}/${PATHWAYS}
cat *.gmt > ../Rat_AllPathways_${dir_name}_symbol.gmt
cd ${SYMBOL}/${GO}
cat ../Rat_AllPathways_${dir_name}_symbol.gmt Rat_GOALL_${WITHIEA}_${dir_name}_symbol.gmt > ../Rat_GO_AllPathways_${WITHIEA}_${dir_name}_symbol.gmt
cat ../Rat_AllPathways_${dir_name}_symbol.gmt Rat_GOALL_${NOIEA}_${dir_name}_symbol.gmt > ../Rat_GO_AllPathways_${NOIEA}_${dir_name}_symbol.gmt

#create two new all pathways files with GOBP included
cat ../Rat_AllPathways_${dir_name}_symbol.gmt RAT_GO_bp_${WITHIEA}_symbol.gmt > ../Rat_GOBP_AllPathways_${WITHIEA}_${dir_name}_symbol.gmt
cat ../Rat_AllPathways_${dir_name}_symbol.gmt RAT_GO_bp_${NOIEA}_symbol.gmt > ../Rat_GOBP_AllPathways_${NOIEA}_${dir_name}_symbol.gmt


#merge all the summaries
mergesummaries ${SYMBOL} symbol

cd ${UNIPROT}/${PATHWAYS}
cat *.gmt > ../Rat_AllPathways_${dir_name}_UniProt.gmt
cd ${UNIPROT}/${GO}
cat ../Rat_AllPathways_${dir_name}_UniProt.gmt Rat_GOALL_${WITHIEA}_${dir_name}_UniProt.gmt > ../Rat_GO_AllPathways_${WITHIEA}_${dir_name}_UniProt.gmt
cat ../Rat_AllPathways_${dir_name}_UniProt.gmt Rat_GOALL_${NOIEA}_${dir_name}_UniProt.gmt > ../Rat_GO_AllPathways_${NOIEA}_${dir_name}_UniProt.gmt

#create two new all pathways files with GOBP included
cat ../Rat_AllPathways_${dir_name}_UniProt.gmt RAT_GO_bp_${WITHIEA}_UniProt.gmt > ../Rat_GOBP_AllPathways_${WITHIEA}_${dir_name}_UniProt.gmt
cat ../Rat_AllPathways_${dir_name}_UniProt.gmt RAT_GO_bp_${NOIEA}_UniProt.gmt > ../Rat_GOBP_AllPathways_${NOIEA}_${dir_name}_UniProt.gmt

#merge all the summaries
mergesummaries ${UNIPROT} UniProt

getstats ${EG} "entrezgene" ${RatSOURCE}
getstats ${UNIPROT} "UniProt" ${RatSOURCE}
getstats ${SYMBOL} "symbol" ${RatSOURCE}

#copy the files over the webserver
mkdir /mnt/build/EM_Genesets/$dir_name
cp -R ${CUR_RELEASE}/Human /mnt/build/EM_Genesets/$dir_name/
cp -R ${CUR_RELEASE}/Mouse /mnt/build/EM_Genesets/$dir_name/
cp -R ${CUR_RELEASE}/Rat /mnt/build/EM_Genesets/$dir_name/

#create a symbolic link to the latest download indicating it as current_release
rm /mnt/build/EM_Genesets/current_release
cd /mnt/build/EM_Genesets
ln -sf $dir_name/ current_release

#rm -rf ${CUR_RELEASE}

