import org.kohsuke.args4j.Option;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;

/**
 * Created by IntelliJ IDEA.
 * User: User
 * Date: 10/7/11
 * Time: 9:20 AM
 * To change this template use File | Settings | File Templates.
 */
public class CalculateOverlaps {

    @Option(name = "--gmt", usage = "name of gmt file to calculate overlaps", required = true)
    private String gmt_filename;

    @Option(name = "--outfile", usage = "name of output file", required = true)
    private String outfile;

    @Option(name = "--dir", usage = "name of output directory", required = true)
    private String directory;

    public void overlaps() throws IOException {

         //create parameters
         GMTParameters params = new GMTParameters();

         //set file names
         params.setGMTFileName(gmt_filename);

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

        //compute the geneset similarities
        ComputeSimilarityTask similarities = new ComputeSimilarityTask(params);
        similarities.run();

        HashMap<String, GenesetSimilarity> similarity_results = similarities.getGeneset_similarities();

        printSimilarities(similarity_results, outfile);


    }

    public void printSimilarities(HashMap<String, GenesetSimilarity> similarities, String filename) throws IOException{

        //create an output file
        File gs_stats = new File(directory + System.getProperties().getProperty("file.separator") + filename);
        BufferedWriter gs_log = new BufferedWriter(new FileWriter(gs_stats));


        for(Iterator i = similarities.keySet().iterator(); i.hasNext(); ){
            String current = i.next().toString();
            GenesetSimilarity sim = similarities.get(current);

            gs_log.write(sim.toString());
        }

        gs_log.flush();
        gs_log.close();
    }

}
