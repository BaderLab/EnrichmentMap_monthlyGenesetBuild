import com.sun.org.apache.xalan.internal.xsltc.compiler.Template;
import com.sun.org.apache.xpath.internal.FoundIndex;
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

        HashMap<String, HashSet<String>> unfoundIds = new HashMap<String, HashSet<String>>();
        HashMap<String, HashMap<String, logInfo>> logs = new HashMap<String, HashMap<String, logInfo>>();


        //get the Genesets
        HashMap<String,GeneSet> genesets = params.getGenesets();

        HashMap<String, HashMap<String, GeneSet>> translated_genesets = new HashMap<String, HashMap<String, GeneSet>>();

        //get the gene to hash key conversions
        HashMap<Integer, String> hash2gene = params.getHashkey2gene();

        //create a hashmap to store all the conversions.
        //the key for the hashmap is the id and the object is the set of conversions to get that id
        HashMap<String, HashMap<Integer, SynergizerParams>> conversions = new HashMap<String,HashMap<Integer, SynergizerParams>>();

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
            id1="UniProt";
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

            id2="UniProt";
            conversions_id2.put(1, new SynergizerParams("ensembl",symboldb,"uniprot_swissprot_accession"));
            conversions_id2.put(2, new SynergizerParams("ensembl",symboldb,"uniprot_sptrembl_accession"));
        }
        //Mouse go files use mgi ids, we need to convert the mgi to three different dbs , instead of the standard 2
        else if(oldID.equalsIgnoreCase("mgi")){
            id1="entrezgene";
            conversions_id1.put(1,new SynergizerParams("ncbi", "mgi", "entrezgene"));
            conversions_id1.put(2,new SynergizerParams("ensembl", "mgi_id", "entrezgene"));

            id2="symbol";
            conversions_id2.put(1,new SynergizerParams("ensembl", "mgi_id", symboldb));

            //add an additional id.
            String id3="UniProt";
            HashMap<Integer, SynergizerParams> conversions_id3 = new HashMap<Integer,SynergizerParams>();
            conversions_id3.put(1,new SynergizerParams("ensembl", "mgi_id", "uniprot_swissprot_accession") );
             conversions_id3.put(2,new SynergizerParams("ensembl", "mgi_id", "uniprot_sptrembl_accession") );
             conversions_id3.put(3,new SynergizerParams("ncbi", "mgi", "uniprot"));
            conversions.put(id3, conversions_id3);
            createNewIdTracker(id3,unfoundIds,logs,translated_genesets );
        }

        //there will always be at least 2 ids.
        conversions.put(id1, conversions_id1);
        createNewIdTracker(id1, unfoundIds,logs,translated_genesets);
        conversions.put(id2, conversions_id2);
        createNewIdTracker(id2,unfoundIds,logs, translated_genesets);

        System.out.println("Querying Synergizer...");

        //to slow to query synergizer for each geneset.  We need to query synergizer once for each id conversion
        //Go through each geneset and translate the ids.
        Set GeneQuerySet = new HashSet<String>();
         for(Iterator k = genesets.keySet().iterator(); k.hasNext(); ){
         String geneset_name = k.next().toString();
            GeneSet current_set =  genesets.get(geneset_name);

            //get the genes in this geneset
            HashSet<Integer> geneset_genes = current_set.getGenes();


            for(Iterator j = geneset_genes.iterator();j.hasNext();){
                //get corresponding Gene from hash key
                Integer current_key = (Integer)j.next();
                if(hash2gene.containsKey(current_key)){
                    String current_id = hash2gene.get(current_key);
                    GeneQuerySet.add(current_id);
                }
            }
         }

        //convert all the identifiers
        //create a set of translations
        HashMap<String, HashMap<String, Set<String>>> translations = new HashMap<String, HashMap<String, Set<String>>>();


        //go through each of the conversions and get the translations for them
        for(Iterator r = conversions.keySet().iterator(); r.hasNext();){
            String current_id = r.next().toString();
            HashMap<String, Set<String>> translations_id = convert(GeneQuerySet,conversions.get(current_id), unfoundIds.get(current_id),logs.get(current_id));

            translations.put(current_id, translations_id);
        }
        //Go through each geneset and translate the ids.
         for(Iterator k = genesets.keySet().iterator(); k.hasNext(); ){

            String geneset_name = k.next().toString();
            GeneSet current_set =  genesets.get(geneset_name);

            //for each conversion - create converted genesets
             for(Iterator p = conversions.keySet().iterator();p.hasNext();){
                 String current_id = p.next().toString();
                 HashSet<String> new_genes_id1 = convertGeneSet(current_set,translations.get(current_id),
                            hash2gene,unfoundIds.get(current_id),logs.get(current_id));
                 String[] new_genes_string_id1 = new String[new_genes_id1.size()];
                 new_genes_id1.toArray(new_genes_string_id1);

                 GeneSet new_set_id1 = new GeneSet(current_set.getName(), current_set.getDescription());
                 new_set_id1.addGeneList(new_genes_string_id1,params);


                 //for this id get the hash of the translated genesets
                 HashMap<String, GeneSet> translated_genesets_id = translated_genesets.get(current_id);
                 translated_genesets_id.put(new_set_id1.getName(), new_set_id1);

             }


         }

         //go through all the translated sets and output them
        for(Iterator q = translated_genesets.keySet().iterator();q.hasNext();){
            String current_id = q.next().toString();
            outputFiles(translated_genesets.get(current_id),current_id,translations.get(current_id),
                    unfoundIds.get(current_id),logs.get(current_id),params);
        }
    }


    /* for each new id that we are going to convert we need to store the unfoundids, log messages, and translated genesets
        create all the objects needed for a new identifier in a set of hashmaps where the key is the identifier converting to.

        given - the new id, and references to the the hashs for unfoundids, logs, and translated geneset
     */
    private void createNewIdTracker(String id, HashMap<String, HashSet<String>> unfoundIds,HashMap<String,
            HashMap<String, logInfo>> logs, HashMap<String, HashMap<String, GeneSet>> translated_genesets){

        unfoundIds.put(id, new HashSet<String>());
        logs.put(id, new HashMap<String, logInfo>());
        translated_genesets.put(id, new HashMap<String, GeneSet>());

    }

    public void outputFiles(HashMap<String, GeneSet> translated_genesets, String id, HashMap<String,Set<String>> translations,
                            HashSet<String> unfoundIds, HashMap<String, logInfo> logs,GMTParameters params)throws IOException{
        //open output file
        String baseFilename = gmt_filename.split(".gmt")[0];

        //if the baseFileName has the old id in the name then take it out
        String baseFilename_nooldid = "";
        int index_Start = baseFilename.toLowerCase().indexOf(oldID.toLowerCase());

        for(int r =0 ; r< baseFilename.length(); r++)
            if(r < index_Start || r > index_Start+oldID.length() )
                baseFilename_nooldid += baseFilename.toCharArray()[r];

        String OutFilename = baseFilename_nooldid  + id + ".gmt";

        params.printGenesets(translated_genesets,OutFilename);

        //only create a log file if it isn't empty.
        if(!logs.isEmpty()){
            //create two log file with the same name as the output file but append .log
            //one log file has the detailed missing conversions
            //one log file has just the summary
            File logfile_detailed = new File(baseFilename_nooldid + id + "_detailed.log");
            BufferedWriter log = new BufferedWriter(new FileWriter(logfile_detailed));
            File logfile_summary = new File(baseFilename_nooldid + id + "_summary.log");
            BufferedWriter log_sum = new BufferedWriter(new FileWriter(logfile_summary));
            log.write("GeneSetName \t Number of genes queried \t Number of unfound source ids \t list of unfound source ids \n");
            int totalUnfoundAnnotations = 0;
            int totalAnnotations = 0;
            for(Iterator j = logs.keySet().iterator();j.hasNext();){
                //only write the log out if the number of
                logInfo current = logs.get(j.next());
                totalAnnotations += current.total;
                if(current.numunfound > 0){
                    totalUnfoundAnnotations+=current.numunfound;
                    log.write(current.toString());
                }
            }

            //add to the log file the set of all IDs that weren't successfully converted
            log_sum.write("File name:\t" + gmt_filename + "\n");
            log_sum.write("original Identifier\t" + oldID + "\n");
            log_sum.write("ID translated to\t" + id + "\n");
            log_sum.write("total Number of genes in file:\t" + translations.keySet().size() + "\n");
            log_sum.write("total Number Identifiers unable to map\t" + unfoundIds.size() + "\n");
            log_sum.write("Percentage ids not translated\t" + (((unfoundIds.size()/1.0) / (translations.keySet().size()/1.0)) * 100) + "%\n" );
            log_sum.write("Total annotations in the file\t" + totalAnnotations + "\n");
            log_sum.write("Total Untranslated annotations\t" + totalUnfoundAnnotations + "\n");
            log_sum.write("Percentage annotations not translated\t" + (((totalUnfoundAnnotations/1.0)/(totalAnnotations/1.0)) * 100) + "%\n");

            log.flush();
            log.close();
            log_sum.flush();
            log_sum.close();
        }
    }

    public HashMap<String, Set<String>> convert(Set GeneQuerySet, HashMap<Integer,SynergizerParams> conversions,
                                   HashSet<String> unfoundIds, HashMap<String, logInfo> logs ) throws IOException{

        HashMap<String,Set<String>> new_genes = new HashMap<String,Set<String>>();
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
                        new_genes.put(current, translation.get(current));
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
                             SynergizerParams syparams_id1_cur = conversions.get(currentkey);

                            SynergizerClient.TranslateResult res_missing =
                                client.translate(syparams_id1_cur.db, taxon, syparams_id1_cur.oldID,
                                    syparams_id1_cur.newID, missingQuerySet);

                             //add the new Genes to the GeneSet
                            Map<String, Set<String>> translation_missing = res_missing.translationMap();

                            for(Iterator b = translation_missing.keySet().iterator();b.hasNext();){
                                String current = (String) b.next();
                                 if(current != null && translation_missing.containsKey(current) && translation_missing.get(current) != null)
                                    new_genes.put(current, translation_missing.get(current));
                            }

                             //add all the unfound ids to the missing set
                             missingQuerySet.clear();
                            if(res.unfoundSourceIDs().size() >0 )
                                missingQuerySet.addAll(res_missing.unfoundSourceIDs());
                            if(res.foundSourceIDsWithUnfoundTargetIDs().size() > 0)
                                missingQuerySet.addAll(res_missing.foundSourceIDsWithUnfoundTargetIDs());

                         }
                     }



                    unfoundIds.addAll(missingQuerySet);
                    //unfoundtargetIds.addAll(res.foundSourceIDsWithUnfoundTargetIDs());
            }
        } catch(JSONException e){

        }
        return new_genes;
  }

  public HashSet<String> convertGeneSet(GeneSet current_set, HashMap<String, Set<String>> conversions, HashMap<Integer, String> hash2gene,
                                   HashSet<String> unfoundIds, HashMap<String, logInfo> logs){

      //go through all the genes in this geneset
      //and convert them with the conversion map.
      HashSet<String> convertedGenes = new HashSet<String>();
      HashSet<String> missingQuerySet = new HashSet<String>();

      Set geneset_genes = current_set.getGenes();
      for(Iterator j = geneset_genes.iterator();j.hasNext();){
            //get corresponding Gene from hash key
            Integer current_key = (Integer)j.next();
            if(hash2gene.containsKey(current_key)){
                String current_id = hash2gene.get(current_key);
                //is this id converted or not
                if(conversions.containsKey(current_id))
                    convertedGenes.addAll(conversions.get(current_id));
                else if(unfoundIds.contains(current_id))
                    missingQuerySet.add(current_id);

            }
      }


      logs.put(current_set.getName(), new logInfo(current_set.getName(),geneset_genes.size(),
    missingQuerySet.size(),
    missingQuerySet.toString()) );

      return convertedGenes;
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

