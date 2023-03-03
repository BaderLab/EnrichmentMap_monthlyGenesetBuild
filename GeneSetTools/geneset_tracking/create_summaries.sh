
current_date=`date '+%B_%Y'`

docker run --rm -v "$(pwd)":/home/rstudio/projects --user rstudio bioc_summaries /usr/local/bin/R -e "rmarkdown::render('/home/rstudio/projects/Geneset_tracking.Rmd',params=list(species='Human'), output_file='/home/rstudio/projects/Geneset_tracking_human_$current_date.html')" > processing_output_Human_$current_date.txt

#copy the new file over to the web page
cp "$(pwd)"/Geneset_tracking_human_$current_date.html /home/geneset-cron/geneset_build_drive/EM_Genesets/current_release/Human/README.html

docker run --rm -v "$(pwd)":/home/rstudio/projects --user rstudio bioc_summaries /usr/local/bin/R -e "rmarkdown::render('/home/rstudio/projects/Geneset_tracking.Rmd',params=list(species='Mouse'), output_file='/home/rstudio/projects/Geneset_tracking_mouse_$current_date.html')" > processing_output_Mouse_$current_date.txt

#copy the new file over to the web page
cp "$(pwd)"/Geneset_tracking_mouse_$current_date.html /home/geneset-cron/geneset_build_drive/EM_Genesets/current_release/Mouse/README.html

docker run --rm -v "$(pwd)":/home/rstudio/projects --user rstudio bioc_summaries /usr/local/bin/R -e "rmarkdown::render('/home/rstudio/projects/Geneset_tracking.Rmd',params=list(species='Rat'), output_file='/home/rstudio/projects/Geneset_tracking_rat_$current_date.html')" > processing_output_Rat_$current_date.txt

#copy the new file over to the web page
cp "$(pwd)"/Geneset_tracking_rat_$current_date.html /home/geneset-cron/geneset_build_drive/EM_Genesets/current_release/Rat/README.html


