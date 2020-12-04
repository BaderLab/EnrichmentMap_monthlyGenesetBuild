
echo -e "Filename\tOriginal Identifier\tID translated\tTotal #genes\t #genes translated\t#genes missing\t percent not translated\t Total Annotations\t Annotations not translated \t percent annot not translated\n" > ${2}

for file in `find ${3} | grep ${1}_summary.log`; do
	awk -F '\t' '{print $2}' $file | awk '{printf "%s\t", $0}!(NR % 10){printf "\n"}' >> ${2}
done

