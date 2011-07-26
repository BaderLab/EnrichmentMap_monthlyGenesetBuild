
import org.json.JSONException;
import synergizer.SynergizerClient;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
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
     * Given a gmt file, the current species, current identifier type, the desired identifier type
     * This Method translates all identifiers in each geneset to the new identifier using synergizer Java Api
     * @param args - array of command line arguments,
     *  First argument = command (compare/translate...)
     *  second argument = path to gmt file
     *  third argument  = species
     *  fourth argument = original identifier ("Entrez gene", "Uniprot",...)
     *  Fifth argument - new identifier
     * @throws IOException
     */
    public static void translate(String args[]) throws IOException {
        String gmt_filename,outputfile, species, oldID, newID;
        //Get the Filenames from the args, order is important
        //second argument is the gmt file
        //
        if(args.length == 6){
            gmt_filename = args[1];
            outputfile = args[2];
            species = args[3];
            oldID = args[4];
            newID = args[5];
        }
        else{
            help();
            System.out.println("USAGE: Command (GMT_filename(path to geneset file) FileOut species oldID newID");
            return;
        }
        //create parameters
        GMTParameters params = new GMTParameters();

        //set file names
        params.setGMTFileName(gmt_filename);

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

        //get the Genesets
        HashMap<String,GeneSet> genesets = params.getGenesets();
        //create a new set of Geneset with the converted identifiers
        HashMap<String, GeneSet> translated_genesets = new HashMap<String, GeneSet>();

        //get the gene to hash key conversions
        HashMap<Integer, String> hash2gene = params.getHashkey2gene();

        //create synergizer connection
        SynergizerClient client = new synergizer.SynergizerClient();

        //Go through each geneset and translate the ids.
         for(Iterator k = genesets.keySet().iterator(); k.hasNext(); ){
            String geneset_name = k.next().toString();
            GeneSet current_set =  genesets.get(geneset_name);

            //get the genes in this geneset
            HashSet<Integer> geneset_genes = current_set.getGenes();

            Set GeneQuerySet = new HashSet<String>();

            for(Iterator j = geneset_genes.iterator();j.hasNext();){
                //get corresponding Gene from hash key
                Integer current_key = (Integer)j.next();
                if(hash2gene.containsKey(current_key)){
                    String current_id = hash2gene.get(current_key);
                    GeneQuerySet.add(current_id);
                }
            }
             try{
             SynergizerClient.TranslateResult res =
                client.translate("ensembl", species, oldID,
                   newID, GeneQuerySet);

                 //get the translation map
                 Map<String, Set<String>> translation = res.translationMap();
                 HashSet<String> new_genes = new HashSet<String>();
                 for(Iterator b = translation.keySet().iterator();b.hasNext();)
                     new_genes.addAll(translation.get(b.next()));
                 String[] new_genes_string = new String[new_genes.size()];
                 new_genes.toArray(new_genes_string);

                 GeneSet new_set = new GeneSet(current_set.getName(), current_set.getDescription());
                 new_set.addGeneList(new_genes_string,params);

                 //output the stats for this geneset
                 System.out.println(current_set.getName() + " " + GeneQuerySet.size()  + " " + res.foundSourceIDsWithUnfoundTargetIDs().size());

                 translated_genesets.put(new_set.getName(), new_set);

                 System.out.println(new_set.toStringNames(params));

                //System.out.println(res.translationMap());
;
                //System.out.println(res.unfoundSourceIDs());
                //System.out.println(res.foundSourceIDsWithUnfoundTargetIDs());
                //System.out.println(res.foundSourceIDsWithFoundTargetIDs());

             } catch(JSONException e){

             }
         }

        //open output file
        File newgsfile = new File(outputfile);
        BufferedWriter newgs = new BufferedWriter(new FileWriter(newgsfile));
        for(Iterator c = translated_genesets.keySet().iterator();c.hasNext();){
            newgs.write(translated_genesets.get(c.next()).toStringNames(params));
            newgs.flush();
        }
        newgs.close();
    }

    public static void createGo(String args[]) throws IOException {
        String outputfile, branch;
        Integer species;
        //Get the Filenames from the args, order is important
        //second argument is the gmt file
        //
        if(args.length == 4){
            branch = args[2];
            outputfile = args[3];
            species = Integer.parseInt(args[1]);

        }
        else{
            help();
            System.out.println("USAGE: Command species branch(bp,mf,cc,or all) outputfile");
            return;
        }

        GOGeneSetFileMaker maker = new GOGeneSetFileMaker(species,branch,outputfile,1);

        maker.makeQuery();
    }

     enum Command {
        translate("fileIn fileOut species currentID newID\t\ttakes gmt file and translates all of it ids to new id", 3)
		        {public void run(String[] argv) throws IOException{translate(argv);} },
        compare("GMTfile GCTfile2 outputFile Diretory\t\t\tcompares gmt file to given gct (expression file) to generate stats relating to how many genesets each gene is found in", 2)
		        {public void run(String[] argv) throws IOException{compare(argv);} },
        createGo("Species Branch File", 3)
                {public void run(String[] argv) throws IOException{createGo(argv);} },
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
