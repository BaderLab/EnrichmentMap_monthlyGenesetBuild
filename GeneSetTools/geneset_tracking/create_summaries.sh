#!/bin/bash

current_date=`date '+%B_%Y'`

#where the README.html for each species is published (can be overridden for testing)
RELEASE_DIR=${RELEASE_DIR:-/home/geneset-cron/geneset_build_drive/EM_Genesets/current_release}

status=0

#render the summary report for one species and, only if that worked, copy it over the web page.
#R's error messages (stderr) are saved in processing_output_<species>_<date>.txt too.
# argument 1 - species (Human, Mouse or Rat)
function render_summary {
	species=$1
	lower_species=`echo ${species} | tr '[:upper:]' '[:lower:]'`
	html_file="$(pwd)"/Geneset_tracking_${lower_species}_$current_date.html
	output_file=processing_output_${species}_$current_date.txt

	#remove any report left over from an earlier run this month so an old file can't be published
	rm -f "${html_file}"

	if docker run --rm -v "$(pwd)":/home/rstudio/projects --user rstudio bioc_summaries /usr/local/bin/R -e "rmarkdown::render('/home/rstudio/projects/Geneset_tracking.Rmd',params=list(species='${species}'), output_file='/home/rstudio/projects/Geneset_tracking_${lower_species}_$current_date.html')" > ${output_file} 2>&1 \
		&& [ -s "${html_file}" ] ; then
		#copy the new file over to the web page
		cp "${html_file}" ${RELEASE_DIR}/${species}/README.html
	else
		echo "ERROR: ${species} summary failed, README not updated (see ${output_file})"
		status=1
	fi
}

render_summary Human
render_summary Mouse
render_summary Rat

exit $status
