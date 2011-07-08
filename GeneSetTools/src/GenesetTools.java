
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.HashSet;
import java.util.Set;
import java.io.File;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Created by
 * User: risserlin
 * Date: Nov 18, 2010
 * Time: 12:12:30 PM
 */
public class GenesetTools {

     public static void main(String[] argv) throws IOException,
             InvocationTargetException, IllegalAccessException
    {

        if (argv.length == 0) {
            help();
        } else {
            Command.valueOf(argv[0]).run(argv);
        }
    }


    /**
     * Method to analyze a GMT file in relation to the given GCT (expression) File
     * Optional additional paramters include output file name (arg 3) and output directory (arg 4)
     *
     * Output: For each Gene in the expression File, find out how many Genesets it is in.
     * Print: GeneName <tab> Number of GeneSets in <tab> List of Genesets in.
     * @param args - array of command line arguments
     * @throws IOException
     */
     public static void compare(String args[]) throws IOException {
          String gmt_filename, gct_filename, output_filename, directory;


         //Get the Filenames from the args, order is important
         //first argument is the gmt file
         //second argument is the gct file
          if(args.length > 3){
              gmt_filename = args[1];
              gct_filename = args[2];
          }
         else{
            System.out.println("USAGE: GMT_filename(path to geneset file) GCT_filename(path to expression file)");
            return;
          }
         //third argument is the outputfile
         if(args.length > 4)
            output_filename = args[3];
         else
            output_filename = "default_outputfile.txt";
         //fourth argument is the directory
         if(args.length>=5)
            directory = args[4];
         else
            directory = "files";

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

    /**
     *
     * @param args - array of command line arguments
     * @throws IOException
     */
    public static void translate(String args[]) throws IOException {

    }

     enum Command {
        translate("file1 currentID newID\t\ttakes gmt file and translates all of it ids to new id", 3)
		        {public void run(String[] argv) throws IOException{translate(argv);} },
        compare("GMTfile GCTfile2 outputFile Diretory\t\t\tcompares gmt file to given gct (expression file) to generate stats relating to how many genesets each gene is found in", 2)
		        {public void run(String[] argv) throws IOException{compare(argv);} },
        help("\t\t\t\t\t\tprints this screen and exits", Integer.MAX_VALUE)
		        {public void run(String[] argv) throws IOException{help();} };

        String description;
        int params;

        Command(String description, int params) {
            this.description = description;
            this.params = params;
        }

        public abstract void run(String[] argv) throws IOException;
    }

    static void help() {

        System.out.println("Available operations:");
        for (Command cmd : Command.values()) {
            System.out.println(cmd.name() + " : " + cmd.description);
        }

    }
}
