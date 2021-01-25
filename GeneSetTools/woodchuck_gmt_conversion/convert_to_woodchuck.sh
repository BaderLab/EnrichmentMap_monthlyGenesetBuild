# assumption - you are running this script from a directory containing the woodchuck
# 	conversion files (simiilar to homologene file minus the last two columns)
#	that all gmt files you want to convert are present in the diretory
# Input parameter - name of woodchuck homologene file

# argument 1 - gmt file name
# argument 2 - taxonomy id
# argument 3 - taxonomy name
function convert_gmt {
		java -Xmx2G -jar ../GenesetTools.jar convertGeneSets --gmt $1 --homology homologroupHumanWoodchuck_addedcol.tsv --newtaxid $2 --outfile ${3}_${1//[[:Human:]]} 2>> convert_process.err 1>> convert_output.txt 
	}


# added gi and refseq columns to homologene file recieved from Zoe
# as it won't work unless there are 6 columns
awk '{$5=0;$6="No_refseq"} 1' OFS="\t" $1 > temp.txt
#remove the first row (with the header)
awk '{if (NR!=1) {print}}'  temp.txt > homologroupHumanWoodchuck_addedcol.tsv

#go through each of the gmt file and convert to Woodchuck "entrez genes"
# (in quotes because we are actually using best id for woodchuck.
for file in Human*.gmt ; do
		convert_gmt $file "9995" "Woodchuck"
done
