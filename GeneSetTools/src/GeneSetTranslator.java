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

     @Option(name = "--organism", usage = "taxonomy id of organism", required = true)
    private int TaxonomyId;

     @Option(name = "--oldID", usage = "id currently used in the gmt file", required = true)
    private String oldID;

    private SynergizerClient client;
    private String taxon;
    private String symboldb;

    public GeneSetTranslator() {


    }

    public GeneSetTranslator(String gmt_filename, int taxonomyId, String oldID)  {
        this.gmt_filename = gmt_filename;
        TaxonomyId = taxonomyId;
        this.oldID = oldID;

    }

    public void setupSynergizer() throws IOException{
        client = new synergizer.SynergizerClient();

        //initialize the taxon and symboldb needed by synergizer
        if(TaxonomyId == 9606){
            taxon = "Homo Sapiens";
            symboldb = "hgnc_symbol";
        }else if(TaxonomyId == 10090){
            taxon = "Mus musculus";
            symboldb = "mgi_symbol";
        }
    }

    public void translate() throws IOException {
        //create parameters
        GMTParameters params = new GMTParameters();

        //set file names
        params.setGMTFileName(gmt_filename);

        //setup synergizer
        setupSynergizer();

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
        HashSet<String> unfoundIds_id1 = new HashSet<String>();
        HashMap<String, logInfo> logs_id1 = new HashMap<String, logInfo>();
        HashSet<String> unfoundIds_id2 = new HashSet<String>();
        HashMap<String, logInfo> logs_id2 = new HashMap<String, logInfo>();



        //get the Genesets
        HashMap<String,GeneSet> genesets = params.getGenesets();
        //create a new set of Geneset with the converted identifiers
        HashMap<String, GeneSet> translated_genesets_id1 = new HashMap<String, GeneSet>();
        HashMap<String, GeneSet> translated_genesets_id2 = new HashMap<String, GeneSet>();


        //get the gene to hash key conversions
        HashMap<Integer, String> hash2gene = params.getHashkey2gene();


        HashMap<Integer, SynergizerParams> conversions_id1 = new HashMap<Integer,SynergizerParams>();
        HashMap<Integer, SynergizerParams> conversions_id2 = new HashMap<Integer,SynergizerParams>();

        String id1="";
        String id2="";

        //depending on the old ID there are different paths that we can take to maximize coverage
        //if uniprot, convert to entrezgene(id1)and symbol(id2)
        // try  ensembl uniprot_swissprot_accession to entrezgene
        // then ensembl uniprot_sptrembl to entrezgene
        // then ncbi  uniprot to entrezgene
        //
        // if entrezgene convert to  uniprot(id1)  symbol(id2)
        // if symbol convert to entrezgene(id1), uniprot (id2)
        if(oldID.equalsIgnoreCase("entrezgene")){
            //for each conversion place in oldID to newID into hashmap
            id1="uniprot";
            conversions_id1.put(1,new SynergizerParams("ensembl", "entrezgene","uniprot_swissprot_accession"));
            conversions_id1.put(2,new SynergizerParams("ensembl", "entrezgene","uniprot_sptrembl"));
            conversions_id1.put(3,new SynergizerParams("ncbi", "entrezgene","uniprot"));
            //ncbi has no symbols so can only use ensembl
            id2="symbol";
            conversions_id2.put(1, new SynergizerParams("ensembl", "entrezgene",symboldb ));
        }else if(oldID.equalsIgnoreCase("uniprot")){
            //for each conversion place in oldID to newID into hashmap
            id1="entrezgene";
            conversions_id1.put(1, new SynergizerParams("ensembl", "uniprot_swissprot_accession","entrezgene" ));
            conversions_id1.put(2,new SynergizerParams("ensembl", "uniprot_sptrembl","entrezgene" ));
            conversions_id1.put(3, new SynergizerParams("ncbi", "uniprot","entrezgene" ));
            //ncbi has no symbols so can only use ensembl
            id2="symbol";
            conversions_id2.put(1, new SynergizerParams("ensembl", "uniprot_swissprot_accession",symboldb ));
            conversions_id2.put(2, new SynergizerParams("ensembl", "uniprot_sptrembl",symboldb ));
        }else if(oldID.equalsIgnoreCase("symbol")){
            id1="entrezgene";
            conversions_id1.put(1, new SynergizerParams("ensembl",symboldb, "entrezgene"));

            id2="uniprot";
            conversions_id2.put(1, new SynergizerParams("ensembl",symboldb,"uniprot_swissprot_accession"));
            conversions_id2.put(2, new SynergizerParams("ensembl",symboldb,"uniprot_sptrembl_accession"));
        }


        System.out.println("Querying Synergizer...");
        int count = 0;
        //Go through each geneset and translate the ids.
         for(Iterator k = genesets.keySet().iterator(); k.hasNext(); ){

             count++;

            String geneset_name = k.next().toString();
            GeneSet current_set =  genesets.get(geneset_name);
            if(count%10 == 0)
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

            //convert this geneset to id1
            HashSet<String> new_genes_id1 = convert(current_set,GeneQuerySet,conversions_id1, unfoundIds_id1,logs_id1);
            String[] new_genes_string_id1 = new String[new_genes_id1.size()];
            new_genes_id1.toArray(new_genes_string_id1);

            GeneSet new_set_id1 = new GeneSet(current_set.getName(), current_set.getDescription());
            new_set_id1.addGeneList(new_genes_string_id1,params);

            translated_genesets_id1.put(new_set_id1.getName(), new_set_id1);

            //convert this geneset to id12
            HashSet<String> new_genes_id2 = convert(current_set,GeneQuerySet,conversions_id2, unfoundIds_id2,logs_id2);
            String[] new_genes_string_id2 = new String[new_genes_id2.size()];
            new_genes_id2.toArray(new_genes_string_id2);

            GeneSet new_set_id2 = new GeneSet(current_set.getName(), current_set.getDescription());
            new_set_id2.addGeneList(new_genes_string_id2,params);

            translated_genesets_id2.put(new_set_id2.getName(), new_set_id2);

         }

         //output id1 file
        outputFiles(translated_genesets_id1,id1,unfoundIds_id1,logs_id1,params);

        //output id2 file
        outputFiles(translated_genesets_id2,id2,unfoundIds_id2,logs_id2,params);
    }

    public void outputFiles(HashMap<String, GeneSet> translated_genesets, String id,
                            HashSet<String> unfoundIds, HashMap<String, logInfo> logs,GMTParameters params)throws IOException{
        //open output file
        String baseFilename = gmt_filename.split(".gmt")[0];
        String OutFilename = baseFilename + "_" + id + ".gmt";
        File newgsfile = new File(OutFilename);
        BufferedWriter newgs = new BufferedWriter(new FileWriter(newgsfile));
        //write the file header
        newgs.write(params.getVersion() + "\t" + params.getSource() + "\t" + "identifiers converted from " + oldID + " to " + id + "using Synergizer" + "\n" );
        for(Iterator c = translated_genesets.keySet().iterator();c.hasNext();){
            newgs.write(translated_genesets.get(c.next()).toStringNames(params) + "\n");
            newgs.flush();
        }
        newgs.close();

        //only create a log file if it isn't empty.
        if(!logs.isEmpty()){
            //create a log file with the same name as the output file but append .log
            File logfile = new File(baseFilename+"_" + id + ".log");
            BufferedWriter log = new BufferedWriter(new FileWriter(logfile));
            log.write("GeneSetName \t Number of genes queried \t Number of unfound source ids \t list of unfound source ids \n");
            for(Iterator j = logs.keySet().iterator();j.hasNext();)
                 log.write((logs.get(j.next())).toString());
            //add to the log file the set of all IDs that weren't successfully converted
            log.write("total Number of genes in file:\t" + params.getHashkey2gene().size() + "\n");
            log.write("All source Identifiers unable to map\t" + unfoundIds.size() + "\t" + unfoundIds.toString() +"\n");
            log.flush();
            log.close();
        }
    }

    public HashSet<String> convert(GeneSet current_set, Set GeneQuerySet, HashMap<Integer,SynergizerParams> conversions,
                                   HashSet<String> unfoundIds, HashMap<String, logInfo> logs ) throws IOException{
        //put in a pause so we don't hit the server too often
        int temp = 0;
        for(int r = 0; r<1000000;r++)
            temp = temp + r;

        HashSet<String> new_genes = new HashSet<String>();
        try{
                 //convert the first ID
                 SynergizerParams syparams_id1 = conversions.get(1);

                 SynergizerClient.TranslateResult res =
                client.translate(syparams_id1.db, taxon, syparams_id1.oldID,
                   syparams_id1.newID, GeneQuerySet);

                 //get the translation map
                 Map<String, Set<String>> translation = res.translationMap();

                 for(Iterator b = translation.keySet().iterator();b.hasNext();){
                     String current = (String) b.next();
                     if(current != null && translation.containsKey(current) && translation.get(current) != null)
                        new_genes.addAll(translation.get(current));
                 }


                 //output the stats for this geneset
                 //only output if the number of genes not found is greater than zero
                 //first try and find the missing ids with other conversions available.
                 if(res.foundSourceIDsWithUnfoundTargetIDs().size() > 0 || res.unfoundSourceIDs().size() > 0 ){
                    Set missingQuerySet = new HashSet<String>();
                    if(res.unfoundSourceIDs().size() >0 )
                            missingQuerySet.addAll(res.unfoundSourceIDs());
                    if(res.foundSourceIDsWithUnfoundTargetIDs().size() > 0)
                            missingQuerySet.addAll(res.foundSourceIDsWithUnfoundTargetIDs());
                     if(conversions.size()>1){

                         for(Iterator h = conversions.keySet().iterator(); h.hasNext();){
                             Integer currentkey = (Integer)h.next();
                             if(currentkey == 1)
                                 continue;
                             SynergizerParams syparams_id1_cur = conversions.get(1);

                            SynergizerClient.TranslateResult res_missing =
                                client.translate(syparams_id1_cur.db, taxon, syparams_id1_cur.oldID,
                                    syparams_id1_cur.newID, missingQuerySet);

                             //add the new Genes to the GeneSet
                            Map<String, Set<String>> translation_missing = res_missing.translationMap();

                            for(Iterator b = translation_missing.keySet().iterator();b.hasNext();){
                                String current = (String) b.next();
                                 if(current != null && translation_missing.containsKey(current) && translation_missing.get(current) != null)
                                    new_genes.addAll(translation_missing.get(current));
                            }

                             //add all the unfound ids to the missing set
                             missingQuerySet.clear();
                            if(res.unfoundSourceIDs().size() >0 )
                                missingQuerySet.addAll(res_missing.unfoundSourceIDs());
                            if(res.foundSourceIDsWithUnfoundTargetIDs().size() > 0)
                                missingQuerySet.addAll(res_missing.foundSourceIDsWithUnfoundTargetIDs());

                         }
                     }

                     logs.put(current_set.getName(), new logInfo(current_set.getName(),GeneQuerySet.size(),
                             missingQuerySet.size(),
                             missingQuerySet.toString()) );
                            //res.foundSourceIDsWithUnfoundTargetIDs().size(),
                             //res.foundSourceIDsWithUnfoundTargetIDs().toString()));

                    unfoundIds.addAll(missingQuerySet);
                    //unfoundtargetIds.addAll(res.foundSourceIDsWithUnfoundTargetIDs());
            }
        } catch(JSONException e){

        }
        return new_genes;
  }

    class logInfo{
        String term;
        int total;
        int numunfound;
        String unfound;


        logInfo(String term, int total, int numunfound, String unfound) {
            this.term = term;
            this.total = total;
            this.numunfound = numunfound;
            this.unfound = unfound;

        }

        public String toString(){
            return term + "\t" + total + "\t" + numunfound + "\t" + unfound + "\n";
        }

    }

    class SynergizerParams{
        String db;
        String oldID;
        String newID;

        SynergizerParams(String db, String oldID, String newID) {
            this.db = db;
            this.oldID = oldID;
            this.newID = newID;
        }

    }

}

