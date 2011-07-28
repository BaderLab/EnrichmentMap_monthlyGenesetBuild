import com.sun.org.apache.xalan.internal.xsltc.compiler.Template;
import org.json.JSONException;
import org.kohsuke.args4j.Option;
import synergizer.SynergizerClient;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: risserlin
 * Date: 11-07-28
 * Time: 10:47 AM
 * To change this template use File | Settings | File Templates.
 */
public class GeneSetTranslator {

    @Option(name = "--gmt", usage = "name of gmt file to convert", required = true)
    private String gmt_filename;

    @Option(name = "--outfile", usage = "name of output file", required = true)
    private String OutFilename;

     @Option(name = "--organism", usage = "taxonomy id of organism", required = true)
    private String TaxonomyId;

     @Option(name = "--oldID", usage = "id currently used in the gmt file", required = true)
    private String oldID;

    @Option(name = "--newID", usage = "id to convert to", required = true)
    private String newID;

    public GeneSetTranslator() {
    }

    public GeneSetTranslator(String gmt_filename, String outFilename, String taxonomyId, String oldID, String newID) {
        this.gmt_filename = gmt_filename;
        OutFilename = outFilename;
        TaxonomyId = taxonomyId;
        this.oldID = oldID;
        this.newID = newID;
    }

    public void translate() throws IOException {
        //create parameters
        GMTParameters params = new GMTParameters();

        //set file names
        params.setGMTFileName(gmt_filename);

        //parse gmt
        //Load in the GMT file
        try{
            //Load the geneset file
            GMTFileReaderTask gmtFile = new GMTFileReaderTask(params);
            System.out.println("Loading GMT File...");
            gmtFile.run();

        } catch (OutOfMemoryError e) {
            System.out.println("Out of Memory. Please increase memory allotement for cytoscape.");
            return;
        }  catch(Exception e){
            System.out.println("unable to load GMT file");
            return;
        }


        //create a set to store the genes that aren't found
        HashSet<String> unfoundIds = new HashSet<String>();
        HashSet<String> unfoundtargetIds = new HashSet<String>();
        HashMap<String, logInfo> logs = new HashMap<String, logInfo>();

        //get the Genesets
        HashMap<String,GeneSet> genesets = params.getGenesets();
        //create a new set of Geneset with the converted identifiers
        HashMap<String, GeneSet> translated_genesets = new HashMap<String, GeneSet>();

        //get the gene to hash key conversions
        HashMap<Integer, String> hash2gene = params.getHashkey2gene();

        //create synergizer connection
        SynergizerClient client = new synergizer.SynergizerClient();

        System.out.println("Querying Synergizer...");
        int count = 0;
        //Go through each geneset and translate the ids.
         for(Iterator k = genesets.keySet().iterator(); k.hasNext(); ){

             count++;

            String geneset_name = k.next().toString();
            GeneSet current_set =  genesets.get(geneset_name);
            if(count%100 == 0)
                System.out.println("Queried " + count + " genesets.");

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

             //put in a pause so we don't hit the server too often
             int temp = 0;
             for(int r = 0; r<1000000;r++)
                  temp = temp + r;


             try{
             SynergizerClient.TranslateResult res =
                client.translate("ensembl", TaxonomyId, oldID,
                   newID, GeneQuerySet);

                 //get the translation map
                 Map<String, Set<String>> translation = res.translationMap();
                 HashSet<String> new_genes = new HashSet<String>();
                 for(Iterator b = translation.keySet().iterator();b.hasNext();){
                     String current = (String) b.next();
                     if(current != null && translation.containsKey(current) && translation.get(current) != null)
                        new_genes.addAll(translation.get(current));
                 }
                 String[] new_genes_string = new String[new_genes.size()];
                 new_genes.toArray(new_genes_string);

                 GeneSet new_set = new GeneSet(current_set.getName(), current_set.getDescription());
                 new_set.addGeneList(new_genes_string,params);

                 //output the stats for this geneset
                 //only output if the number of genes not found is greater than zero
                 if(res.foundSourceIDsWithUnfoundTargetIDs().size() > 0 || res.unfoundSourceIDs().size() > 0 ){
                    //System.out.println(current_set.getName() + " " + GeneQuerySet.size()  + " " + res.foundSourceIDsWithUnfoundTargetIDs().size());

                     logs.put(current_set.getName(), new logInfo(current_set.getName(),GeneQuerySet.size(),
                             res.unfoundSourceIDs().size(),
                             res.unfoundSourceIDs().toString() ,
                            res.foundSourceIDsWithUnfoundTargetIDs().size(),
                             res.foundSourceIDsWithUnfoundTargetIDs().toString()));

                    unfoundIds.addAll(res.unfoundSourceIDs());
                    unfoundtargetIds.addAll(res.foundSourceIDsWithUnfoundTargetIDs());
                 }
                 translated_genesets.put(new_set.getName(), new_set);

             } catch(JSONException e){

             }
         }

        //open output file
        File newgsfile = new File(OutFilename);
        BufferedWriter newgs = new BufferedWriter(new FileWriter(newgsfile));
        for(Iterator c = translated_genesets.keySet().iterator();c.hasNext();){
            newgs.write(translated_genesets.get(c.next()).toStringNames(params));
            newgs.flush();
        }
        newgs.close();

        //only create a log file if it isn't empty.
        if(!logs.isEmpty()){
            //create a log file with the same name as the output file but append .log
            File logfile = new File(OutFilename + ".log");
            BufferedWriter log = new BufferedWriter(new FileWriter(logfile));
            log.write("GeneSetName \t Number of genes queried \t Number of unfound source ids \t list of unfound source ids\t Number of found source ids without target ids \t list of unfound genes without target ids \n");
            for(Iterator j = logs.keySet().iterator();j.hasNext();)
                 log.write((logs.get(j.next())).toString());
            //add to the log file the set of all IDs that weren't successfully converted
            log.write("total Number of genes in file:\t" + hash2gene.size() + "\n");
            log.write("All source Identifiers unable to map\t" + unfoundIds.size() + "\t" + unfoundIds.toString() +"\n");
            log.write("All source Identifiers without target ids\t" + unfoundtargetIds.size() + "\t" + unfoundtargetIds.toString() + "\n");
            log.flush();
            log.close();
        }
    }

    class logInfo{
        String term;
        int total;
        int numunfound;
        String unfound;
        int numunfoundTargets;
        String unfoundTargets;

        logInfo(String term, int total, int numunfound, String unfound, int numunfoundTargets, String unfoundTargets) {
            this.term = term;
            this.total = total;
            this.numunfound = numunfound;
            this.unfound = unfound;
            this.numunfoundTargets = numunfoundTargets;
            this.unfoundTargets = unfoundTargets;
        }

        public String toString(){
            return term + "\t" + total + "\t" + numunfound + "\t" + unfound + "\t"
                    + numunfoundTargets + "\t" + unfoundTargets + "\n";
        }

    }

}

