import org.kohsuke.args4j.Option;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

import java.io.File;  // Import the File class
import java.io.FileNotFoundException;  // Import this class to handle errors
import java.util.Scanner; // Import the Scanner class to read text files

/**
 * Created by IntelliJ IDEA.
 * User: risserlin
 * Date: 11-07-28
 * Time: 10:47 AM
 * To change this template use File | Settings | File Templates.
 */
public class GeneSetTranslator_UniprotWebservice {

    @Option(name = "--gmt", usage = "name of gmt file to convert", required = true)
    private String gmt_filename;

     @Option(name = "--organism", usage = "taxonomy id of organism", required = true)
    private int taxonomyId;

     @Option(name = "--oldID", usage = "id currently used in the gmt file", required = true)
    private String oldID;

     @Option(name = "--newID", usage = "id to convert the gmt file to", required = true)
    private String newID;

     @Option(name = "--idconversionfile", usage = "file containing the id mapping from genemania flat file (extracted from ensembl)", required = false)
     private String conversionFileName;

     @Option(name = "--uniprotIDfile", usage = "file containing the id mapping from uniprot ftp site", required = false)
     private String uniprotFileName;

     @Option(name = "--ncbiIDfile", usage = "file containing the id mapping from ncbi ftp site (filtered by taxon)", required = false)
 	private String ncbiFileName;

    // variables used to define ensembl column to use 
    private String ensembl_oldID = "";
    private String ensembl_newID = "";

    private int query_set_size = 0;

    //ids to be used when printing out the file
    private String oldID_print;
    private String newID_print;
    public GeneSetTranslator_UniprotWebservice() {


    }

    public GeneSetTranslator_UniprotWebservice(String gmt_filename, int taxonomyId, String oldID, String newID, String conversionFileName)  {
        this.gmt_filename = gmt_filename;
        this.taxonomyId = taxonomyId;
        this.oldID = oldID;
	this.newID = newID;
	this.conversionFileName = conversionFileName;

    }

    public GeneSetTranslator_UniprotWebservice(String gmt_filename, int taxonomyId, String oldID, String newID, String uniprotFileName, String ncbiFileName)  {
        this.gmt_filename = gmt_filename;
        this.taxonomyId = taxonomyId;
        this.oldID = oldID;
	this.newID = newID;
	this.uniprotFileName = uniprotFileName;
	this.ncbiFileName = ncbiFileName;
    }

    public GeneSetTranslator_UniprotWebservice(String gmt_filename, int taxonomyId, String oldID, String newID, String uniprotFileName, String ncbiFileName, String conversionFileName)  {
        this.gmt_filename = gmt_filename;
        this.taxonomyId = taxonomyId;
        this.oldID = oldID;
	this.newID = newID;
	this.uniprotFileName = uniprotFileName;
	this.ncbiFileName = ncbiFileName;
	this.conversionFileName = conversionFileName;
    }


    public void setup() {

        if((this.taxonomyId == 10090) && (oldID.equalsIgnoreCase("mgi"))){
	    oldID = "MGI_ID";
        }else if((this.taxonomyId == 10116) && (oldID.equalsIgnoreCase("rgd"))){
		oldID = "RGD_ID"; 
        }

       //convert the identifiers to what will appear in the header of the conversion file
//commented out the headers specific to ensembl file.  If changing to ensembl file uncomment
 	if(oldID.equalsIgnoreCase("Uniprot")){
	       	ensembl_oldID = "UniProt ID";
		oldID_print = "uniprot";
       }else if(oldID.equalsIgnoreCase("symbol")){
		ensembl_oldID = "Gene Name";
		oldID_print = "symbol";
       }else if(oldID.equalsIgnoreCase("entrezgene")){
		ensembl_oldID = "Entrez Gene ID";
		oldID_print = "entrezgene";
       }else{
	       System.out.println("Unrecognized old identifier");
	}

       if(newID.equalsIgnoreCase("Uniprot")){
       		ensembl_newID = "UniProt ID";
		newID_print = "uniprot";
       }else if(newID.equalsIgnoreCase("symbol")){
		ensembl_newID = "Gene Name";
		newID_print = "symbol";
       }else if(newID.equalsIgnoreCase("entrezgene")){
		ensembl_newID = "Entrez Gene ID";
		newID_print = "entrezgene";
       }else{
	       System.out.println("Unrecognized new identifier");
	}
	
    }

    public void translate() throws IOException {
        //create parameters
        GMTParameters params = new GMTParameters();

        //set file names
        params.setGMTFileName(gmt_filename);

        //setup 
        setup();

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

	HashMap<String,GeneSet> genesets = params.getGenesets();

        HashMap<String, HashMap<String, GeneSet>> translated_genesets = new HashMap<String, HashMap<String, GeneSet>>();

        //get the gene to hash key conversions
        HashMap<Integer, String> hash2gene = params.getHashkey2gene();

	String id1 = "";
	id1 = oldID;
        createNewIdTracker(id1, unfoundIds,logs,translated_genesets);

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

	//after pulling all the identifiers from the file record how many we have
	query_set_size = GeneQuerySet.size();

        //convert all the identifiers
	//create the translations
  	HashMap<String, HashMap<String, Set<String>>> translations = new HashMap<String, HashMap<String, Set<String>>>();

	
	//convert the ids (there is only one conversion.  Unlike synergizer
	// that had us going through multiple jumps to get the conversions
	//
	// Depending on the number of files that we supplied we use a different method.
	// If ensembl file has been supplied then convert with ensembl
	HashMap<String, Set<String>> translations_id = null;
	if( this.conversionFileName != null)
		translations_id = convert(GeneQuerySet,oldID, newID, unfoundIds.get(oldID),logs.get(oldID)); 
	else{
		if(oldID.equalsIgnoreCase("Uniprot"))
			translations_id = convertUniprotWebservice(GeneQuerySet,oldID, newID, unfoundIds.get(oldID),logs.get(oldID),taxonomyId); 
		else{
			if(oldID.equalsIgnoreCase("entrezgene") || oldID.equalsIgnoreCase("symbol")){
					
				translations_id = twoStepConversions(GeneQuerySet,oldID, newID, unfoundIds.get(oldID),logs.get(oldID),taxonomyId); 

			}

		}
	}

		//HashMap<String, Set<String>> translations_id = convertNCBIUniprot(GeneQuerySet,oldID, newID, unfoundIds.get(oldID),logs.get(oldID)); 
	translations.put(oldID, translations_id);

	//Go through each geneset and translate the ids.
         for(Iterator k = genesets.keySet().iterator(); k.hasNext(); ){

            String geneset_name = k.next().toString();
            GeneSet current_set =  genesets.get(geneset_name);

            //create converted genesets
            HashSet<String> new_genes_id1 = convertGeneSet(current_set,translations.get(oldID),hash2gene,unfoundIds.get(oldID),logs.get(oldID));
            String[] new_genes_string_id1 = new String[new_genes_id1.size()];
            new_genes_id1.toArray(new_genes_string_id1);

            GeneSet new_set_id1 = new GeneSet(current_set.getName(), current_set.getDescription());
            new_set_id1.addGeneList(new_genes_string_id1,params);


            //get the hash of the translated genesets
            HashMap<String, GeneSet> translated_genesets_id = translated_genesets.get(oldID);
            translated_genesets_id.put(new_set_id1.getName(), new_set_id1);

         }

         //go through all the translated sets and output them
            outputFiles(translated_genesets.get(oldID),translations.get(oldID),unfoundIds.get(oldID),logs.get(oldID),params);
        
    }


/*
 * Two step conversions - if one of the ids isn't a uniprot then you have to use uniprot as an intermediary. 
 */

    private HashMap<String, Set<String>> twoStepConversions(Set GeneQuerySet,String oldID,String newID,    
		HashSet<String> unfoundIds, HashMap<String, logInfo> logs, int taxonomyId){

	HashMap<String, Set<String>> first_step, second_step, merged_set = null;
	first_step = convertUniprotWebservice(GeneQuerySet,oldID, "Uniprot", unfoundIds,logs,taxonomyId); 
	HashSet<String> new_query_set = new HashSet<String>();
	for (String key : first_step.keySet()) {
		new_query_set.addAll(first_step.get(key));
	}
	
	second_step = convertUniprotWebservice(new_query_set,"Uniprot", newID, unfoundIds,logs, taxonomyId);
	
	//create a translations from the first set to the second set
	merged_set = new HashMap<String, Set<String>>();
	for(String key : first_step.keySet()){
		if(first_step.containsKey(key)){
			//get all the ids for the first key
			HashSet<String> first_step_ids = (HashSet <String>) first_step.get(key);
			//System.out.println("key:" + key + "maps to" + first_step_ids.toString());
		
			HashSet <String> second_step_translates = new HashSet<String>();
					
			//for this set of first steps get all the second set ids
			for(String id : first_step_ids){
				//System.out.println("id:" + id);
	
				//Check to see if we can map this id
				if(second_step.containsKey(id)){
					second_step_translates.addAll(second_step.get(id));
				
					//System.out.println(second_step.get(id).toString());
				} else
					unfoundIds.add(key);
			}
			merged_set.put(key,second_step_translates);
		} else{
			unfoundIds.add(key);
		}
	}
	return merged_set;
    
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

    public void outputFiles(HashMap<String, GeneSet> translated_genesets,  HashMap<String,Set<String>> translations,
                            HashSet<String> unfoundIds, HashMap<String, logInfo> logs,GMTParameters params)throws IOException{
        //open output file
        String baseFilename = gmt_filename.split(".gmt")[0];
        String id = newID_print.toLowerCase();

        //if the baseFileName has the old id in the name then take it out
        String baseFilename_nooldid = "";
        int index_Start = baseFilename.toLowerCase().indexOf(oldID_print.toLowerCase());

        for(int r =0 ; r< baseFilename.length(); r++)
            if(r < index_Start || r > index_Start+oldID_print.length() )
                baseFilename_nooldid += baseFilename.toCharArray()[r];

        String OutFilename = baseFilename_nooldid  + id  + ".gmt";

        params.printGenesets(translated_genesets,OutFilename);

        //only create a log file if the logs object it isn't empty.
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
	    log_sum.write("Total number of genes in the file:\t" + this.query_set_size + "\n");
            log_sum.write("total Number of genes in file successfully converted:\t" + translations.keySet().size() + "\n");
            log_sum.write("total Number Identifiers unable to map\t" + unfoundIds.size() + "\n");
            log_sum.write("Percentage ids not translated\t" + (((unfoundIds.size()/1.0) / (this.query_set_size/1.0)) * 100) + "%\n" );
            log_sum.write("Total annotations in the file\t" + totalAnnotations + "\n");
            log_sum.write("Total Untranslated annotations\t" + totalUnfoundAnnotations + "\n");
            log_sum.write("Percentage annotations not translated\t" + (((totalUnfoundAnnotations/1.0)/(totalAnnotations/1.0)) * 100) + "%\n");
            log_sum.write("===================================\n");
            log.flush();
            log.close();
            log_sum.flush();
            log_sum.close();
        }
    }

    // Function loads in NCBI file.  NCBI file maps from entrez gene ids to gene symbols
    // The NCBI file converts between gene symbols and entrez gene ids.  As the assumption is that we never
    // start from symbols (based on the files that we convert) only create the mapping from entrez gene id to 
    // symbol
    //
    // Returns - Hashmap of entrez gene id to symbol
public HashMap<String, String> loadNCBIdata(){
	HashMap<String, String> ncbi_genes = new HashMap<String, String>();

	int gene_id_index = 1;
	int symbol_index = 15;
	try{
		File myObj = new File(this.ncbiFileName);
		Scanner myReader = new Scanner(myObj);
		while(myReader.hasNextLine()){
			String data = myReader.nextLine();

			//In the NCBI file the second column is entrez gene id
			//and the 16th column is the symbol
			String[] current_row = data.split("\t");
				
			if(current_row[gene_id_index].equalsIgnoreCase("-") || current_row[symbol_index].equalsIgnoreCase("-")){
				       continue;
			} else{
				//System.out.println("Adding gene: " + current_row[gene_id_index] + "that maps to:" + current_row[symbol_index]);
				ncbi_genes.put(current_row[gene_id_index],current_row[symbol_index]);
			}		
		}
	} catch (FileNotFoundException e) {
		System.out.println("An error occurred.");
	        e.printStackTrace();
	     }
	return ncbi_genes;
}


// Function loads in Uniprot file.  Uniprot file maps from entrez gene ids to uniprots
// Given: uniprot2gene - if TRUE return mapping for uniprot to genes otherwise return the inverse
// 	entrez gene to uniprot
// Assumption is that the entrez gene id is in the third column and uniprot accession is the first column
//
// Returns - Hashmap of uniprot to set of entrez gene ids (or the inverse depending on the uniprot2gene boolean)
public HashMap<String, Set<String>> loadUniprotdata(boolean uniprot2gene){
	HashMap<String, Set<String>> uniprot_genes = new HashMap<String, Set<String>>();

	int gene_id_index = 2;
	int uniprot_index = 0;
	try{
		File myObj = new File(this.uniprotFileName);
		Scanner myReader = new Scanner(myObj);
		while(myReader.hasNextLine()){
			String data = myReader.nextLine();

			//In the Uniprot file the third column is entrez gene id
			//and the 1st column is the symbol
			String[] current_row = data.split("\t");
				
			if(current_row[gene_id_index].equalsIgnoreCase("") || current_row[uniprot_index].equalsIgnoreCase("")){
				       continue;
			} else{
				if(uniprot2gene){
					//System.out.println("Loading Uniprot genes:" + current_row[uniprot_index].trim() + "maps to: " + current_row[gene_id_index].trim());
					uniprot_genes.put(current_row[uniprot_index].trim(),new HashSet<>(Arrays.asList(current_row[gene_id_index].trim())));
				} else {
					//System.out.println("Loading Genes to uniprot:" + current_row[gene_id_index].trim() + "maps to: " + current_row[uniprot_index].trim());
					uniprot_genes.put(current_row[gene_id_index].trim(),new HashSet<>(Arrays.asList(current_row[uniprot_index].trim())));
				}
			}		
		}
	} catch (FileNotFoundException e) {
		System.out.println("An error occurred.");
	        e.printStackTrace();
	     }
	return uniprot_genes;
}

/*
 * convert query genes using NCBI and Uniprot directly downloaded files.
 *
 * GeneQuerySet - a Set of identifiers that we need to convert
 * oldID - the type of ID that we are converting from (expecting Uniprot, symbol or entrezgene)
 * newID - the type of ID that we are converted to (exprecting Uniprot, symbol or entrezgene)
 * unfoundIds - a hashset of strings that store the ids that are not converted.
 * logs - a hashmap of all the messages that we want logged in the log file.
 */
public HashMap<String, Set<String>> convertNCBIUniprot(Set GeneQuerySet,String oldID,String newID , HashSet<String> unfoundIds, HashMap<String, logInfo> logs){

	    HashMap<String, Set<String>> new_genes_querysubset = new HashMap<String, Set<String>>();
    	
	//depending which conversions we are doing we might not need the uniprot file
	    HashMap<String, Set<String>> uni2gene=null, gene2uni=null;
	    HashMap<String, String> gene2symbol=null;
	if(oldID.equalsIgnoreCase("Uniprot")){
		uni2gene = loadUniprotdata(true);
	  System.out.println("There are "+ uni2gene.size() + " uniprots to genes");
	} else if(newID.equalsIgnoreCase("Uniprot")){
		gene2uni = loadUniprotdata(false);
	  System.out.println("There are "+ gene2uni.size() + " genes to uni");
	}

	// only require the NCBI file if the new ID is symbol
	if(newID.equalsIgnoreCase("symbol")){
		gene2symbol = loadNCBIdata();

	  System.out.println("There are "+ gene2symbol.size() + " genes to symbols");
	}

	//Go through the query genes and see how many of then can be converted
	for(Iterator k = GeneQuerySet.iterator(); k.hasNext();){
		//get the current genes
		String current_gene = ((String) k.next()).trim();
		//System.out.print("looking for: " + current_gene);
		
		//if the old ID is a uniprot then get the conversion from the uni2gene
		if(oldID.equalsIgnoreCase("uniprot") && newID.equalsIgnoreCase("symbol")){
			if((uni2gene != null) && !uni2gene.containsKey(current_gene)){
				unfoundIds.add(current_gene);
				//System.out.println("ID not found: " + current_gene);
			} else if(gene2symbol != null && !gene2symbol.containsKey(uni2gene.get(current_gene).iterator().next())){
				unfoundIds.add(current_gene);
				//System.out.println("ID not found (step two, gene found, symbol not found:" + current_gene + "that gets mapped to: " + uni2gene.get(current_gene) + "gene 2 symbols is still populated: " + gene2symbol.size() + "key not found = " + gene2symbol.containsKey(uni2gene.get(current_gene)));
			} else if((uni2gene != null) && (gene2symbol != null)){ 
				new_genes_querysubset.put(current_gene, new HashSet<>(Arrays.asList(gene2symbol.get(uni2gene.get(current_gene).iterator().next()))));
				//System.out.println("Current id found:" + current_gene + " maps to: " + gene2symbol.get(uni2gene.get(current_gene).iterator().next()));
			} else {
				System.out.println("one of the identifier mappers is null");
			}
		}
		
		//if the old ID is a uniprot and new id is entrezgene only one jump is required
		if(oldID.equalsIgnoreCase("uniprot")&& newID.equalsIgnoreCase("entrezgene")){
			if((uni2gene != null) && !uni2gene.containsKey(current_gene)){
				unfoundIds.add(current_gene);
				//System.out.println("ID not found: " + current_gene);
			} else if(uni2gene != null) {
				new_genes_querysubset.put(current_gene,new HashSet<>(uni2gene.get(current_gene)));
				//System.out.println("Current id found:" + current_gene + " maps to: " + uni2gene.get(current_gene));
			} else{
				System.out.println("Uniprot 2 gene conversions are empty");
			}
		}

		//if the old ID is entrezgeneid  and new id is uniprot only one jump is required
		if(oldID.equalsIgnoreCase("entrezgene")&& newID.equalsIgnoreCase("uniprot")){
			if((gene2uni != null) && !gene2uni.containsKey(current_gene)){
				unfoundIds.add(current_gene);
				//System.out.println("ID not found: " + current_gene);
			} else if(gene2uni != null ){
				new_genes_querysubset.put(current_gene,new HashSet<>( gene2uni.get(current_gene)));
				//System.out.println("Current id found:" + current_gene + " maps to: " + gene2uni.get(current_gene));
			} else {
				System.out.println("Gene 2 uniprot conversions are empty");
			}
		}

		//if the old ID is entrezgeneid  and new id is symbol only one jump is required
		if(oldID.equalsIgnoreCase("entrezgene")&& newID.equalsIgnoreCase("symbol")){
			if((gene2symbol != null) && !gene2symbol.containsKey(current_gene)){
				unfoundIds.add(current_gene);
				//System.out.println("ID not found: " + current_gene);
			} else if(gene2symbol != null) {
				new_genes_querysubset.put(current_gene,new HashSet<>(Arrays.asList(gene2symbol.get(current_gene))));
				//System.out.println("Current id found:" + current_gene + " maps to: " + gene2symbol.get(current_gene));
			} else {
				System.out.println("gene 2 symbol conversions are empty");
			}	
		}

	}
	return new_genes_querysubset;
}

/*
 * convertUniprotWebservice - use the uniprot rest server to convert the ids given.
 *
 * GeneQuerySet - a Set of identifiers that we need to convert
 * oldID - the type of ID that we are converting from (expecting Uniprot, symbol or entrezgene)
 * newID - the type of ID that we are converted to (exprecting Uniprot, symbol or entrezgene)
 * unfoundIds - a hashset of strings that store the ids that are not converted.
 * logs - a hashmap of all the messages that we want logged in the log file.
 */
public HashMap<String, Set<String>> convertUniprotWebservice(Set GeneQuerySet,String oldID,String newID , 
		HashSet<String> unfoundIds, HashMap<String, logInfo> logs, int taxonomyId){

	    HashMap<String, Set<String>> new_genes = null;
	    HashSet<String> queryset = new HashSet<String>();
	    queryset.addAll(GeneQuerySet);


		try {
			QueryUniprotWebservice uniprot_query = new QueryUniprotWebservice(oldID,newID, queryset,taxonomyId);

			new_genes = uniprot_query.runQuery("uploadlists");
			
			//check to see which ids we were still unable to translate
			queryset.removeAll(new_genes.keySet());

			System.out.println(" There are " + queryset.size() + " that are still not found");

			unfoundIds.addAll(queryset);

		} catch (Exception e){
			System.out.println("Something is wrong with the uniprot web service");
		}

	return new_genes;
}

/*
 * convert - use the ensembl file to convert ids.  If NCBI and Uniprot files are supplied then 
 * 	for missing ids use them.  If only ensembl is supplied then use uniprot webservice
 *
 * GeneQuerySet - a Set of identifiers that we need to convert
 * oldID - the type of ID that we are converting from (expecting Uniprot, symbol or entrezgene)
 * newID - the type of ID that we are converted to (exprecting Uniprot, symbol or entrezgene)
 * unfoundIds - a hashset of strings that store the ids that are not converted.
 * logs - a hashmap of all the messages that we want logged in the log file.
 */
public HashMap<String, Set<String>> convert(Set GeneQuerySet,String oldID,String newID , HashSet<String> unfoundIds, HashMap<String, logInfo> logs){
    
	    HashMap<String, Set<String>> new_genes = new HashMap<String, Set<String>>();
    
           //load in the conversion file
	   int line_number = 0;
	   try {
		File myObj = new File(this.conversionFileName);
		Scanner myReader = new Scanner(myObj);
		int oldID_index = 0;
		int newID_index = 0;
		while (myReader.hasNextLine()) {
			String data = myReader.nextLine();
			
			//if this is the header line determine which columns we want to use
			//for the conversions
			if(line_number == 0){
				//System.out.println("In header definition.  Looking for header : " + oldID + " and " + newID);
				//tokenize each line by "\t"
				String[] header = data.split("\\t");
				for(int i=0;i<header.length;i++){
					//System.out.println("in header tokens definition" + header[i]);
					if(header[i].equalsIgnoreCase(ensembl_oldID)){
						oldID_index = i;
						//System.out.println("The identifier has been identifed as" + oldID + " in column number" + oldID_index);
					} else if(header[i].equalsIgnoreCase(ensembl_newID)){
						newID_index = i;
						//System.out.println("The new identifier has been identified as" + newID + " in column number:" + newID_index);
					}
				}
				line_number++;
			}
			//otherwise add the id to the conversion hashmap
			else{
				String[] current_row = data.split("\t");
				
				if(current_row[oldID_index].equalsIgnoreCase("N/A") || current_row[newID_index].equalsIgnoreCase("N/A")){
				       continue;
				}	       

				//if the old id contains a list of ids then we need to separate them out as the oldid is the key
				if(current_row[oldID_index].contains(";")){
					String[] list_old_ids = current_row[oldID_index].split(";");
					for(int l=0;l<list_old_ids.length;l++){

					  //check to see if the new ids contains a list of ids
					  if(current_row[newID_index].contains(";")){
						new_genes.put(list_old_ids[l],new HashSet<>(Arrays.asList(current_row[newID_index].split(";"))));
					  } else{
						new_genes.put(list_old_ids[l],new HashSet<>(Arrays.asList(current_row[newID_index])));
						//System.out.println("Added " + list_old_ids[l] + " mapping to " + current_row[newID_index]);
					 	}
					}
				} else {
					if(current_row[newID_index].contains(";")){
						new_genes.put(current_row[oldID_index],new HashSet<>(Arrays.asList(current_row[newID_index].split(";"))));
					} else{
						new_genes.put(current_row[oldID_index],new HashSet<>(Arrays.asList(current_row[newID_index])));
						//System.out.println("Added " + current_row[oldID_index] + " mapping to " + current_row[newID_index]);

		}
			
				}
			}
			//System.out.println(data);
		}
		myReader.close();
	} catch (FileNotFoundException e) {
		System.out.println("An error occurred.");
	        e.printStackTrace();
	     }
	  System.out.println("There are "+ GeneQuerySet.size() + "query genes");

	    HashMap<String, Set<String>> new_genes_querysubset = new HashMap<String, Set<String>>();
    	
	    HashSet<String> temp_unfoundIds = new HashSet<String>();

	//Go through the query genes and see how many of then can be converted
	for(Iterator k = GeneQuerySet.iterator(); k.hasNext();){
		//get the current genes
		String current_gene = (String) k.next();
		//System.out.println("looking for: " + current_gene);
		//if the gene is not found in the translations then add it to the unfound ids
		if(!new_genes.containsKey(current_gene)){
			temp_unfoundIds.add(current_gene);
			//System.out.println("ID not found: " + current_gene);
		} else{
			new_genes_querysubset.put(current_gene, new_genes.get(current_gene));
			//System.out.println("Current id found: " + current_gene + "maps to:" + new_genes.get(current_gene));
		}
	}

	//Check NCBI and Uniprot for the Ids that are missing.
	if(temp_unfoundIds.size() > 0) {
		System.out.println("There are " + temp_unfoundIds.size() + "identifiers not found using the ensembl conversion file");
		//if the NCBI and uniprot files have been supplied then use them.  
		HashMap<String, Set<String>> additional_ids = null;
		if(this.ncbiFileName != null && this.uniprotFileName != null)
			additional_ids = convertNCBIUniprot(temp_unfoundIds,oldID,newID,unfoundIds,logs);
		else{
			try {
				QueryUniprotWebservice uniprot_query = new QueryUniprotWebservice(oldID, newID, temp_unfoundIds,taxonomyId);

				additional_ids = uniprot_query.runQuery("uploadlists");
			
				//check to see which ids we were still unable to translate
				temp_unfoundIds.removeAll(additional_ids.keySet());

				System.out.println(" There are " + temp_unfoundIds.size() + " that are still not found");

				unfoundIds.addAll(temp_unfoundIds);


			} catch (Exception e){
				System.out.println("Uniprot web service not working");
			}
		}
		System.out.println("We found an additional  " + additional_ids.size() + " identifiers using additional tools");
		new_genes_querysubset.putAll(additional_ids);
	}

	    return new_genes_querysubset;
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


}

