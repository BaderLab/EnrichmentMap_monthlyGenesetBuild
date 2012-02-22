import cytoscape.data.readers.TextFileReader;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: User
 * Date: 2/22/12
 * Time: 10:34 AM
 * To change this template use File | Settings | File Templates.
 */
public class HPO2GMT {

    @Option(name = "--annotfile", usage = "name of input annotation HPO file to parse")
    private String annotFilename;
    @Option(name = "--obofile", usage = "name of input obo HPO file to parse")
    private String oboFilename;
    @Option(name = "--outfile", usage = "name of output file")
    private String outFilename;

    public HPO2GMT() {
    }

    public void parseAnnotation() throws FileNotFoundException{
        //create an onotology object from the obo file
        Ontology ont = new Ontology(oboFilename);

        //parse the GO information from the OBO file.
        ont.parseOBO();

        //g        et the terms and descendants
        HashMap<String, OBOTerm> terms = ont.getTerms() ;
        HashMap<String, HashSet<String>> descendants = ont.getDescendants();

        HashMap<String, HashSet<String>> file_gs = new HashMap<String, HashSet<String>>();
        HashMap<String, HashSet<String>> file_gs_symbol = new HashMap<String, HashSet<String>>();

        if(annotFilename != null || !annotFilename.equalsIgnoreCase("")){
            TextFileReader reader = new TextFileReader(annotFilename);
            reader.read();
            String fullText = reader.getText();

            String []lines = fullText.split("\n");

            for (int i = 0; i < lines.length; i++) {

               String line = lines[i];
               String[] tokens = line.split("\t");

               //if the first line starts with "#" then it is a commented line - ignore it.
               if(line.startsWith("#")){
                    continue;
               }

               //there should be 3 columns, the first one is entrez gene id, second is gene name, and
                //the third is the list of phenotype terms this gene belongs to.
               if(tokens.length >= 3){

                   //read each line
                   //first token is entrezgene id.
                   String entrezgeneid = tokens[0];

                   //second token is the gene name
                   String symbol = tokens[1];
                   //third token is the set of phenotypes
                   String phenotypes = tokens[2];

                   //Go through each phenotype and add this gene to that phenotype.
                   //Each phenotype is separated by a ";"
                   String[] phenotype = phenotypes.split(";");
                   for(int j=0;j<phenotype.length;j++){

                       //each phenotype consists of name(HPO ID)
                       String name = phenotype[j].split("\\(HP:")[0];
                       String HPOid = "HP:" + (phenotype[j].split("\\(HP:")[1]).split("\\)")[0];

                        //create a new HPOGeneset for this line
                        String current_set = HPOid;

                   if(file_gs.containsKey(current_set)){
                        HashSet<String> current_list = file_gs.get(current_set);
                        current_list.add(entrezgeneid);
                        file_gs.put(current_set,current_list);
                        HashSet<String> current_list_symbol = file_gs_symbol.get(current_set);
                        current_list_symbol.add(symbol);
                        file_gs_symbol.put(current_set,current_list_symbol);

                   }
                   //this is a new set
                   else{
                       HashSet<String> new_set = new HashSet<String>();
                       new_set.add(entrezgeneid);
                       file_gs.put(current_set, new_set);

                       HashSet<String> new_set_symbol = new HashSet<String>();
                       new_set_symbol.add(symbol);
                       file_gs_symbol.put(current_set, new_set_symbol);

                   }

               }
            }
        }

        //output the genesets into output file

            PrintWriter writer = new PrintWriter(new File(outFilename));
            PrintWriter writer_symbol = new PrintWriter(new File(outFilename + "_symbol"));
            //HashMap<String,String> goterms = getGoterms();

            //use OBO instead of DB
            HashMap<String,String> hpoterms = ont.getTerms_OBO();
            //get all the descendants for the terms
            //HashMap<String, HashSet<String>> descendants = getDescendants();


            //go through all the GAF genesets and create genesets for them.
            for(Iterator j = file_gs.keySet().iterator();j.hasNext();){
                String current = (String)j.next();
                Set<String> genes = file_gs.get(current);
                Set<String> genes_symbol = file_gs_symbol.get(current);
                //gs name = GO|goid
                String name = "HPO" +Biopax2GMT.DBSOURCE_SEPARATOR + current;

                //up propagate the annotations for the terms that have descendants
                HashSet<String> children = null;
                if(descendants.containsKey(current)){
                    children = descendants.get(current);
                    //for each of the descendants, add their genes to this set
                    for(String child : children){
                        if(file_gs.containsKey(child))
                            genes.addAll(file_gs.get(child));
                        if(file_gs_symbol.containsKey(child))
                            genes_symbol.addAll(file_gs_symbol.get(child));
                    }
                }

                //gs descrip = goid name --> needs to be retrieved from db
                String descrip;
                if(hpoterms.containsKey(current))
                    descrip = hpoterms.get(current);
                else
                    descrip = "HPO ID not found in OBO file ontology definitions";
                writer.print(descrip + Biopax2GMT.DBSOURCE_SEPARATOR + name + "\t" + descrip);

                //list of genes
                for (String gene : genes) {
	                writer.print("\t");
	                writer.print(gene);
	            }
	            writer.println();

                writer_symbol.print(name + "\t" + descrip);
                //list of genes
                for (String gene_symbol : genes_symbol) {
	                writer_symbol.print("\t");
	                writer_symbol.print(gene_symbol);
	            }
	            writer_symbol.println();
                writer.flush();
                writer_symbol.flush();
            }

            //Add terms that don't exist in the annotation file but through uppropagation should be in the file.
            //There might be HPO terms that are not directly annotated in the annotation file but because
            //of the structure of GO should still be in the file because some of their children terms
            //have annotations.
            //check to see which terms with descendants aren't in our file_gs set.
            Set<String> annotSets = file_gs.keySet();
            //Set<String> missingGoSets =  descendants.keySet();
            Set<String> missingGoSets =  new HashSet<String>(hpoterms.keySet());

            //calculate the missing genesets
            missingGoSets.removeAll(annotSets);
            for(Iterator j = missingGoSets.iterator();j.hasNext();){

                String current = (String)j.next();

                //there are no genes associated with these terms that is why we have to go through them.
                Set<String> genes = new HashSet<String>();
                Set<String> genes_symbol = new HashSet<String>();
                //gs name = HPO|hpoid
                String name = "HPO" + Biopax2GMT.DBSOURCE_SEPARATOR + current;

                //up propagate the annotations for the terms that have descendants
                HashSet<String> children = null;
                if(descendants.containsKey(current)){
                    children = descendants.get(current);
                    //for each of the descendants, add their genes to this set
                    for(String child : children){
                        if(file_gs.containsKey(child))
                            genes.addAll(file_gs.get(child));
                        if(file_gs_symbol.containsKey(child))
                            genes_symbol.addAll(file_gs_symbol.get(child));
                    }
                }

                //gs descrip = goid name --> needs to be retrieved from db
                String descrip;
                if(hpoterms.containsKey(current))
                    descrip = hpoterms.get(current);
                else
                    descrip = "GO ID not found in OBO ontology definition file";

                if(genes != null && genes.size() > 0){

                    writer.print(name + "\t" + descrip);

                    //list of genes
                    for (String gene : genes) {
	                    writer.print("\t");
	                    writer.print(gene);
	                }
	                writer.println();

                    writer_symbol.print(name + "\t" + descrip);
                    //list of genes
                    for (String gene_symbol : genes_symbol) {
	                    writer_symbol.print("\t");
	                    writer_symbol.print(gene_symbol);
	                }
	                writer_symbol.println();
                    writer.flush();
                    writer_symbol.flush();
                }
            }

            writer.close();
            writer_symbol.close();
        }
    }
}
