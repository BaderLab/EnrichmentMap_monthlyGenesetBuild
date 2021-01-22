import cytoscape.data.readers.TextFileReader;
import org.kohsuke.args4j.Option;

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
 * Date: 11-08-15
 * Time: 4:39 PM
 * To change this template use File | Settings | File Templates.
 */
public class GeneSetTaxidConverter {

     @Option(name = "--gmt", usage = "name of gmt file to convert to new species", required = true)
    private String gmt_filename;

    @Option(name = "--homology", usage = "name of file from homologene with all homolog conversions", required = true)
    private String homolog_file;

    @Option(name = "--newtaxid", usage = "taxid to convert gmt file to.", required = true)
    private Integer newtaxid;

    @Option(name = "--outfile", usage = "name of output file", required = true)
    private String outfile;


    public void taxidconverter() throws IOException{
        //load in the gmt file.
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

        //open homolog file
         //create a bunch of parameters to store the homologys
         HashMap<Integer, HashMap<Integer, HomoloGene>> homologGroups = new  HashMap<Integer, HashMap<Integer, HomoloGene>>();

         //also track the entrez gene id to the homolog group it belongs to
         //hashmap of eg to homolog group
         HashMap<String, Integer> eg2homologgroup = new HashMap<String, Integer>();

        if(homolog_file != null || !homolog_file.equalsIgnoreCase("")){
            TextFileReader reader = new TextFileReader(homolog_file);
            reader.read();
            String fullText = reader.getText();

            String []lines = fullText.split("\n");

            for (int i = 0; i < lines.length; i++) {

               String line = lines[i];
               String[] tokens = line.split("\t");

                //there should be 6 fields on everyline
                if(tokens.length == 6){
                    Integer homologGroup = Integer.parseInt(tokens[0]);
                    Integer taxid= Integer.parseInt(tokens[1]);
                    String entrezgeneid= tokens[2];
                    String symbol= tokens[3];
                    Integer gi= Integer.parseInt(tokens[4]);
                    String accession= tokens[5];

                    //create a homoloGene
                    HomoloGene newhomolog = new HomoloGene(homologGroup,taxid,entrezgeneid,symbol,gi,accession);

                    //add entrezgene to eg 2 homolog id map
                    eg2homologgroup.put(entrezgeneid,homologGroup);

                    //check to see if this homologGroup has already been added to the homolog groups
                    if(homologGroups.containsKey(homologGroup)){
                        HashMap<Integer, HomoloGene> curHomologs = homologGroups.get(homologGroup);
                        curHomologs.put(taxid,newhomolog);
                        homologGroups.put(homologGroup,curHomologs);
                    }
                    //otherwise create a new group
                    else{
                        HashMap<Integer, HomoloGene> curHomologs = new HashMap<Integer,HomoloGene>();

                        curHomologs.put(taxid, newhomolog);

                        homologGroups.put(homologGroup, curHomologs);
                    }

                }

            }
        }

        //once all the homologs have been stored
        //go through the gmt file and convert all the ids.
        HashMap<String, GeneSet> genesets = params.getGenesets();
        HashMap<Integer, String> hash2gene = params.getHashkey2gene();
        HashMap<String, Integer> genes = params.getGenes();
        HashMap<String, GeneSet> converted_genesets = new HashMap<String, GeneSet>();

        //get the number of unique genes in the original gmt file
        int num_genes_original = genes.size();
        int num_annotations_original = 0;
        int num_missing_annotations = 0;

        HashSet<String> all_missing_genes = new HashSet<String>();

        for(Iterator k = genesets.keySet().iterator(); k.hasNext(); ){
            String geneset_name = k.next().toString();
            GeneSet current_set =  genesets.get(geneset_name);

            //create a new geneset
            GeneSet new_geneset = new GeneSet(geneset_name, current_set.getDescription());

            //get the genes in this geneset
            HashSet<Integer> geneset_genes = current_set.getGenes();
            num_annotations_original += geneset_genes.size();

            int num_missing_genes = 0;
            HashSet<String> missing_egs = new HashSet<String>();

            for(Iterator j = geneset_genes.iterator();j.hasNext();){
                //get corresponding Gene from hash key
                Integer current_key = (Integer)j.next();
                if(hash2gene.containsKey(current_key)){
                    String current_id = hash2gene.get(current_key);

                    //get the homolog of this gene
                    if(eg2homologgroup.containsKey(current_id)){
                        Integer homologgroup = eg2homologgroup.get(current_id);

                        if(homologGroups.containsKey(homologgroup)){
                            HashMap<Integer, HomoloGene> homologs = homologGroups.get(homologgroup);

                            //check to see if there is a homolog in the set from the desired species.
                            if(homologs.containsKey(newtaxid)){
                                HomoloGene homolog = homologs.get(newtaxid);

                                if (genes.containsKey(homolog.getEntrezgeneid().toString())) {
                                    new_geneset.addGene(genes.get(homolog.getEntrezgeneid().toString()));
                                }

                                //If the gene is not in the list then get the next value to be used and put it in the list
                                else{
                                    //add the gene to the master list of genes
                                    int value = params.getNumberOfGenes();
                                    genes.put(homolog.getEntrezgeneid().toString(), value);
                                    hash2gene.put(value,homolog.getEntrezgeneid().toString());
                                    params.setNumberOfGenes(value+1);

                                    //add the gene to the genelist
                                    new_geneset.addGene(genes.get(homolog.getEntrezgeneid().toString()));
                                }
                            }
                        }
                    }
                    else{
                        num_missing_genes++;
                        missing_egs.add(current_id);
                        all_missing_genes.add(current_id);
                        num_missing_annotations++;

                    }
                }
            }
            //add converted genesets to the set of new genesets
            converted_genesets.put(geneset_name, new_geneset);
            //if(num_missing_genes > 0)
                //System.out.println(geneset_name + "\t" + current_set.getGenes().size() + "\t" + num_missing_genes + "\t" + missing_egs.toString());
         }

        params.printGenesets(converted_genesets, outfile);

        //print a log file
        File logfile = new File(outfile + newtaxid + "_conversion.log");
        BufferedWriter log = new BufferedWriter(new FileWriter(logfile));
        log.write("GMT file input\t" + gmt_filename + "\n");
        log.write("New Taxid\t" + newtaxid+ "\n");
        log.write("Number of genes (original gmt)\t" + num_genes_original+ "\n");
        log.write("Number of genes with no homolog\t" + all_missing_genes.size()+ "\n");
        log.write("Percentage of missing homologs\t" + ((all_missing_genes.size()/1.0)/(num_genes_original/1.0)) * 100 + "%\n" );
        log.write("Number of annotations (original gmt)\t" + num_annotations_original+ "\n");
        log.write("Number of lost annotations\t" +num_missing_annotations + "\n");
        log.write("Percentage of missing annotations\t" + ((num_missing_annotations/1.0)/(num_annotations_original/1.0)) * 100 + "%\n" );
        log.write("===================================\n");
        log.flush();
        log.close();

    }
}
