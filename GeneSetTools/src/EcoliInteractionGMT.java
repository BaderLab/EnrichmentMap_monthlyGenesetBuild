import com.hp.hpl.jena.reasoner.rulesys.builtins.Remove;
import org.kohsuke.args4j.Option;
import sun.rmi.rmic.Names;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

/**
 * Created by IntelliJ IDEA.
 * User: risserlin
 * Date: 11-08-18
 * Time: 12:49 PM
 * To change this template use File | Settings | File Templates.
 */
public class EcoliInteractionGMT {

     @Option(name = "--gmt1", usage = "name of first gmt file to compare", required = true)
    private String gmt1_filename;

    @Option(name = "--dir", usage = "name of output directory", required = true)
    private String directory;

    @Option(name = "--outfile", usage = "name of output file", required = true)
    private String outfile;


    public void create() throws IOException {
         //create an output file
         String output_filename = outfile/*gmt1_filename + "_vs_" + gmt2_filename + ".log"*/;
         File gs_stats = new File(directory + System.getProperties().getProperty("file.separator") + output_filename);
         BufferedWriter gs_log = new BufferedWriter(new FileWriter(gs_stats));

         //create parameters
         GMTParameters params = new GMTParameters();

         //set file names
         params.setGMTFileName(gmt1_filename);

         //parse gmt
         //Load in the GMT file
         try{
            //Load the geneset file
            GMTFileReaderTask gmtFile = new GMTFileReaderTask(params,1);
            gmtFile.run();

            } catch (OutOfMemoryError e) {
                System.out.println("Out of Memory. Please increase memory allotement for cytoscape.");
                return;
            }  catch(Exception e){
                System.out.println("unable to load GMT file");
                return;
            }
         //get the two geneset sets
         HashMap<String, GeneSet> genesets = params.getGenesets();
         HashMap<String, Integer> genes = params.getGenes();
         HashMap<Integer,String> names = params.getHashkey2gene();

        HashSet<String> genesets2remove = new HashSet<String>();

        //go through each genesets and compare set1 and set2
        //track the genes that are unique to each set
        for(Iterator i = genesets.keySet().iterator(); i.hasNext();){
            String current_set = (String)i.next();

            GeneSet gs1 = genesets.get(current_set);

            //get the genes in this geneset
            HashSet<Integer> cur_gs_genes = gs1.getGenes();

            //remove any geneset that has less than 3 genes
            if(cur_gs_genes.size() <= 3 || cur_gs_genes.size() > 50){
                genesets2remove.add(current_set);
                continue;
            }

            HashSet<Integer> new_genes = new HashSet<Integer>();

            //go through each of the genes and create all pairwise sets of genes
            for(Iterator j = cur_gs_genes.iterator(); j.hasNext();){
                Integer current_gene = (Integer)j.next();

                String nameA = names.get(current_gene);

                for(Iterator k = cur_gs_genes.iterator();k.hasNext();){
                    Integer current_geneB = (Integer)k.next();
                    String nameB = names.get(current_geneB);
                    String interaction = nameA + "_" + nameB;
                    //System.out.println(interaction);
                    //create the interaction pair if it doesn't already exist
                    if (genes.containsKey(interaction)) {
                            new_genes.add(genes.get(interaction));
                    }

                    //If the gene is not in the list then get the next value to be used and put it in the list
                    else{
                        //add the gene to the master list of genes
                        int value = params.getNumberOfGenes();
                        genes.put(interaction.toUpperCase(), value);
                        names.put(value,interaction.toUpperCase());
                        params.setNumberOfGenes(value+1);

                        //add the gene to the genelist
                        new_genes.add(genes.get(interaction.toUpperCase()));
                    }

                }
            }
            //set the geneset to have the new pairwise geneset.
            gs1.setGenes(new_genes);
            //System.out.println(gs1.toStringNames(params));


        }

        //remove all extra genesets
        for(Iterator<String> p = genesets2remove.iterator();p.hasNext();){
            String curgs  = p.next();
            genesets.remove(curgs);
        }

        //print out all the new genesets
         for(Iterator i = genesets.keySet().iterator(); i.hasNext();){
            String current_set = (String)i.next();

            GeneSet gs1 = genesets.get(current_set);
            gs_log.write(gs1.toStringNames(params));
            gs_log.write("\n");

         }

        gs_log.flush();
        gs_log.close();

    }

}
