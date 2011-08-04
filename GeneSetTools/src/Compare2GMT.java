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
 * User: User
 * Date: 8/2/11
 * Time: 11:47 AM
 * This class compares all the genesets in two gmt files
 */
public class Compare2GMT {

    @Option(name = "--gmt1", usage = "name of first gmt file to compare", required = true)
    private String gmt1_filename;

    @Option(name = "--gmt2", usage = "name of second gmt file to compare", required = true)
    private String gmt2_filename;

    @Option(name = "--cmd", usage = "cmd, either count or content.  Count just compares the number of ids in each term and content compares the ids between each term.", required = true)
    private String cmd;

    @Option(name = "--dir", usage = "name of output directory", required = true)
    private String directory;

    @Option(name = "--outfile", usage = "name of output file", required = true)
    private String outfile;

    private String cmd_count = "count";
    private String cmd_content = "content";

    public void compare() throws IOException {
         //create an output file
         String output_filename = outfile/*gmt1_filename + "_vs_" + gmt2_filename + ".log"*/;
         File gs_stats = new File(directory + System.getProperties().getProperty("file.separator") + output_filename);
         BufferedWriter gs_log = new BufferedWriter(new FileWriter(gs_stats));

         //create parameters
         GMTParameters params = new GMTParameters();

         //set file names
         params.setGMTFileName(gmt1_filename);
         params.setGMTFileName2(gmt2_filename);

         //parse gmt
         //Load in the GMT file
         try{
            //Load the geneset file
            GMTFileReaderTask gmtFile = new GMTFileReaderTask(params,1);
            gmtFile.run();
            GMTFileReaderTask gmtFile2 = new GMTFileReaderTask(params,2);
            gmtFile2.run();

            } catch (OutOfMemoryError e) {
                System.out.println("Out of Memory. Please increase memory allotement for cytoscape.");
                return;
            }  catch(Exception e){
                System.out.println("unable to load GMT file");
                return;
            }
         //get the two geneset sets
         HashMap<String, GeneSet> genesets = params.getGenesets();
         HashMap<String, GeneSet> genesets2 = params.getGenesets_2();

        //store the genes that are not found, might just be a subset of conversions
        //or annotations that are missing
        HashSet<Integer> missing_genes1 = new HashSet<Integer>();
         HashSet<Integer> missing_genes2 = new HashSet<Integer>();

         //check to see that the two genesets have the same set of genesets
         Set<String> gs_names1 = genesets.keySet();
         Set<String> gs_names2 = genesets2.keySet();

         HashSet<String> unique1 = new HashSet<String>(gs_names1);
         HashSet<String> unique2 = new HashSet<String>(gs_names2);

        unique1.removeAll(gs_names2);
        unique2.removeAll(gs_names1);
        //if any of the sets are unique then reports the unique sets
        if(unique1.size() > 0 || unique2.size()>0){
            if(unique1.size() > 0)
                gs_log.write("Unique genesets in " + gmt1_filename + ":\t" + unique1.size() + "\t" + unique1.toString() + "\n");
            if(unique2.size() > 0)
                gs_log.write("Unique genesets in " + gmt2_filename + ":\t" + unique2.size() + "\t" + unique2.toString() + "\n");
        }

        int match_count = 0;
        int mismatch_count = 0;
        float percentage_sum_gs1 = 0;
        float percentage_sum_gs2 = 0;


        //write out the header of the file
        gs_log.write("gs1 = " + gmt1_filename + "\n");
        gs_log.write("gs2 = " + gmt2_filename + "\n");
        gs_log.write("Geneset id\t Geneset Description\t" +
                " total genes in combined set \t #unique to gs1 \t unique genes in gs1" +
                "\t #unique to gs2 \t unique genes in gs2 \t # shared genes" +
                "\t gs1 % coverage \t gs2 % coverage \n");

        //go through each genesets and compare set1 and set2
        //track the genes that are unique to each set
        for(Iterator i = genesets.keySet().iterator(); i.hasNext();){
            String current_set = (String)i.next();

            GeneSet gs1 = genesets.get(current_set);
            GeneSet gs2;
            if(genesets2.containsKey(current_set))
                gs2 = genesets2.get(current_set);
            else
                continue;

            HashSet<Integer> gs1_genes = gs1.getGenes();
            HashSet<Integer> gs2_genes = gs2.getGenes();

            //get the genes in the geneset
            HashSet<Integer> unique_gs1 = new HashSet(gs1.getGenes());

            HashSet<Integer> unique_gs2 = new HashSet(gs2.getGenes());

            HashSet<Integer> shared = new HashSet<Integer>(gs1_genes);
            HashSet<Integer> union = new HashSet<Integer>(gs1_genes);

            unique_gs1.removeAll(gs2_genes);
            unique_gs2.removeAll(gs1_genes);
            //intersection of two sets
            shared.retainAll(gs2_genes);
            //union of two sets
            union.addAll(gs2_genes);

            //add these missing genes to the set of missing genes for 2
            missing_genes1.addAll(unique_gs1);
            missing_genes2.addAll(unique_gs2);


            //if either geneset has unique genes then output the discrepancy.
            //if we assume that the union of the two sets represents the best coverage for the
            // geneset then the number of genes for the set divided by the union will give the
            // percent coverage for each geneset
            if(unique_gs1.size() >0 || unique_gs2.size() >0){
                float percent_coverage_gs1 = (float)gs1_genes.size()/union.size();
                float percent_coverage_gs2 = (float)gs2_genes.size()/union.size();
                gs_log.write(current_set +"\t"+ gs1.getDescription() + "\t" +union.size() + "\t"+
                        unique_gs1.size() + "\t" + printGenes(unique_gs1,params)
                    + "\t" + unique_gs2.size() + "\t" + printGenes(unique_gs2, params) + "\t"
                    + shared.size() + "\t" + percent_coverage_gs1
                        + "\t" + percent_coverage_gs2 + "\n");
                mismatch_count++;
                percentage_sum_gs1 = percentage_sum_gs1 + percent_coverage_gs1;
                percentage_sum_gs2 = percentage_sum_gs2 + percent_coverage_gs2;
                gs_log.flush();
            }
            else
                match_count++;
        }
        gs_log.write(match_count + " Genesets have perfect overlap\n");
        gs_log.write(mismatch_count + " Geneset do not overlap\n");
        gs_log.write("average percent coverage for mismatched sets in gs1(" + gmt1_filename + "):" + percentage_sum_gs1/mismatch_count + "\n");
        gs_log.write("number of distinct genes with unique annotations (gs1) " + missing_genes1.size() + "\n");
        gs_log.write("average percent coverage for mismatched sets in gs2(" + gmt2_filename + "):" + percentage_sum_gs2/mismatch_count + "\n");
        gs_log.write("number of distinct genes with unique annotations (gs2) " + missing_genes2.size() + "\n");
        gs_log.flush();



    }


    private String printGenes(HashSet genes, GMTParameters params){
        HashMap<Integer, String> mappings = params.getHashkey2gene();
        String genestring = "[";
        for(Iterator i = genes.iterator(); i.hasNext();){
            Integer currentkey = (Integer)i.next();
            if(mappings.containsKey(currentkey))
                genestring += mappings.get(currentkey) + ", ";
        }
        genestring += "]";
        return genestring;
    }
}
