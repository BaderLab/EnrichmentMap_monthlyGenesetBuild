import org.kohsuke.args4j.Option;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: risserlin
 * Date: 11-07-28
 * Time: 11:51 AM
 * To change this template use File | Settings | File Templates.
 */
public class CompareGMT {

    @Option(name = "--gmt", usage = "name of gmt file to compare", required = true)
    private String gmt_filename;

    @Option(name = "--gct", usage = "name of gct (expression) file to compare to gmt", required = true)
    private String gct_filename;

    @Option(name = "--outfile", usage = "name of output file", required = true)
    private String output_filename;

    @Option(name = "--dir", usage = "name of output directory (optional)")
    private String directory;

    public CompareGMT() {
    }

    public void compare() throws IOException{
     //create an output file
         //create a file to store all the stats from the buils
        File gs_stats = new File(directory + System.getProperties().getProperty("file.separator") + output_filename);
        BufferedWriter gs_log = new BufferedWriter(new FileWriter(gs_stats));

         //create parameters
          GMTParameters params = new GMTParameters();

         //set file names
         params.setGMTFileName(gmt_filename);
         params.setExpressionFileName1(gct_filename);

         //parse gmt
          //Load in the GMT file
        try{
            //Load the geneset file
            GMTFileReaderTask gmtFile = new GMTFileReaderTask(params);
            gmtFile.run();

        } catch (OutOfMemoryError e) {
            System.out.println("Out of Memory. Please increase memory allotement for cytoscape.");
            return;
        }  catch(Exception e){
            System.out.println("unable to load GMT file");
            return;
        }

         //parse expression file
         ExpressionFileReaderTask expressionFile1 = new ExpressionFileReaderTask(params);
         expressionFile1.run();


         //output stats
         HashMap<String, GeneSet> genesets = params.getGenesets();
         HashMap<String, Integer> genes = params.getDatasetGenes();


         //for each gene, find out how many genesets it is in
         for(Iterator k = genes.keySet().iterator(); k.hasNext(); ){

             String current_genename = k.next().toString();
             Integer current_geneid = genes.get(current_genename);

             HashSet<Integer> current_gene = new HashSet<Integer>();
             current_gene.add(current_geneid);

             int gs_count = 0;
             String gs_list = "";

            //iterate through each geneset and filter each one
             for(Iterator j = genesets.keySet().iterator(); j.hasNext(); ){

                 String geneset2_name = j.next().toString();
                 GeneSet current_set =  genesets.get(geneset2_name);

                 //compare the HashSet of dataset genes to the HashSet of the current Geneset
                 //only keep the genes from the geneset that are in the dataset genes
                 HashSet<Integer> geneset_genes = current_set.getGenes();

                 //Get the intersection between current geneset and dataset genes
                 Set<Integer> intersection = new HashSet<Integer>(geneset_genes);
                 intersection.retainAll(current_gene);

                 if(!intersection.isEmpty()){
                     gs_count++;
                     gs_list = gs_list + " " + geneset2_name + ",";
                 }
             }

             //System.out.println(current_genename + "\t" + gs_count + "\t" + gs_list );
             gs_log.write(current_genename + "\t" + gs_count + "\t" + gs_list + "\n");
             gs_log.flush();

         }

         gs_log.flush();
         gs_log.close();
    }
}
