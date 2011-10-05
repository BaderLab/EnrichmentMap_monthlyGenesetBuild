import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

import com.mysql.jdbc.Driver;

import cytoscape.data.readers.TextFileReader;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.Option;
import org.springframework.jmx.export.UnableToRegisterMBeanException;
import sun.font.TrueTypeFont;

import javax.print.DocFlavor;

/**
 * Created by IntelliJ IDEA.
 * User: risserlin
 * Date: 11-07-25
 * Time: 2:00 PM
 * To change this template use File | Settings | File Templates.
 */

// Class taken from ValidationSetMaker.java in GeneMania plugin directory programmed by Jason Montojo
    // I modified the sql statement to get the geneset names to use in the description field of the gmt file
    // and modified the output gmt file.
public class GOGeneSetFileMaker {

    @Option(name = "--organism", usage = "taxonomy id of organism", required = true)
    private int fTaxonomyId;
    @Option(name = "--outfile", usage = "name of output file")
    private String fQueryFilename;
    @Option(name = "--infile", usage = "name of input gaf GO file to parse")
    private String gafFilename;
    @Option(name = "--branch", usage = "Branch of GO to use (one of: bp, mf, cc, all) - defaults to all")
    private String fBranch = "all"; //$NON-NLS-1$
    @Option(name = "--idtype", usage = "type of id to use (one of: uniprot or symbol) - Defaults to symbol")
    private String id_type="symbol";
    @Option(name = "--exclude", usage = "exclude all annotation ids attributed to IEA, RCA, or ND - defaults to true")
    private boolean exclude = false;

	private String fConnectionString = "jdbc:mysql://mysql.ebi.ac.uk:4085/go_latest?user=go_select&password=amigo"; //$NON-NLS-1$

    private static String noiea = "no_GO_iea";
    private static String withiea = "with_GO_iea";

    public GOGeneSetFileMaker() {
    }

    public GOGeneSetFileMaker(int fTaxonomyId, String fBranch,String fQueryFilename,String id_type) {
        this.fTaxonomyId = fTaxonomyId;
        this.fQueryFilename = fQueryFilename;
        this.fBranch = fBranch;
        this.id_type = id_type;
    }

    public boolean isInfile(){
        return (gafFilename != null) ;

    }

    private String createVersionQuery(){
        return "select I.release_name, I.release_type from instance_data as I";
    }

    private String createGoQuery(long taxId) {
	    String ignoreClause = " and evidence.code not in ('IEA', 'ND', 'RCA') ";
        String sql_stmt = "select" +
                      " ancestor_term.term_type as ancestor_term_type," +
                      " ancestor_term.name as term_name , ancestor_term.acc as ancestor_acc," +
                       " gene_product.symbol " +
                      " from association, term as ancestor_term, term as descendent_term," +
                      " gene_product, species, evidence, graph_path " +
                       "  where species.ncbi_taxa_id = " + taxId +
	                   " and species.id = gene_product.species_id  " +
	                      " and association.gene_product_id = gene_product.id " +
	                      " and ancestor_term.id = graph_path.term1_id   " +
	                      " and descendent_term.id = graph_path.term2_id  " +
	                      " and evidence.association_id = association.id  " +
	                      " and association.term_id = descendent_term.id  " +
	                      " and association.is_not = 0   " +
	                      " and descendent_term.is_obsolete = 0 ";
        if(exclude)
            return sql_stmt + ignoreClause + ";" ;
        else
            return sql_stmt + ";";
	    }


    private String createGoQuery_uniprot(long taxId) {
	    String ignoreClause = "and evidence.code not in ('IEA', 'ND', 'RCA')";
        String sql_stmt = "select" +
                      " ancestor_term.term_type as ancestor_term_type," +
                      " ancestor_term.name as term_name , ancestor_term.acc as ancestor_acc," +
                       " gene_product.symbol,dbxref.xref_key" +
                      " from association, term as ancestor_term, term as descendent_term," +
                      " gene_product, species, evidence, graph_path, dbxref " +
                       "  where species.ncbi_taxa_id = " + taxId +
	                   " and species.id = gene_product.species_id  " +
	                      " and association.gene_product_id = gene_product.id " +
	                      " and ancestor_term.id = graph_path.term1_id   " +
	                      " and descendent_term.id = graph_path.term2_id  " +
	                      " and evidence.association_id = association.id  " +
	                      ignoreClause +
	                      " and association.term_id = descendent_term.id  " +
	                      " and gene_product.dbxref_id = dbxref.id   " +
	                      " and dbxref.xref_dbname = 'UniProtKB'   " +
	                      " and association.is_not = 0   " +
	                      " and descendent_term.is_obsolete = 0 ";
        if(exclude)
            return sql_stmt + ignoreClause + ";";
        else
            return  sql_stmt + ";" ;

	    }
	@SuppressWarnings("nls")
	public void queryEBI() throws IOException {
	     //checkWritable(fQueryFilename);

	     Map<String, String> branches = new HashMap<String, String>();
	     branches.put("all", "all");
	     branches.put("bp", "biological_process");
	     branches.put("mf", "molecular_function");
	     branches.put("cc", "cellular_component");

	     String targetBranch = branches.get(fBranch);
	     if (targetBranch == null) {
	         System.err.printf("Unrecognized GO branch: %s\n", fBranch);
	         return;
	     }
         String versionquery = createVersionQuery();
	     String query ;
         if(id_type.equalsIgnoreCase("uniprot"))
                 query = createGoQuery_uniprot(fTaxonomyId);
         else
             query = createGoQuery(fTaxonomyId);

	     try {
	         new Driver();

	         System.out.println("Connecting...");
	         Connection connection = DriverManager.getConnection(fConnectionString);


	         long start = System.currentTimeMillis();
	         System.out.println("Executing query...");
             Statement statement_ver = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
	         ResultSet versionRes = statement_ver.executeQuery(versionquery);

	         PrintWriter writer = new PrintWriter(new File(fQueryFilename));

             //write out the source and release at the top of the GMT file
             //comments are not supported by GMT files so put the Release and Source as if it
             //was a name and description of the GeneSet.
             //% will indicate to EM that this is a EM compatible file.
             if(versionRes.next())
                 writer.print("%RELEASE:" + versionRes.getString("release_name") + "\t%SOURCE:" + fConnectionString + "\n");
             statement_ver.close();

             Statement statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
	         statement.setFetchSize(Integer.MIN_VALUE);
             ResultSet results = statement.executeQuery(query);

	         try {
	             long annotationsFetched = 0;
	             Map<String, Set<String>> allCategories = new HashMap<String, Set<String>>();
	             try {
	                 while (results.next()) {
	                     annotationsFetched++;
	                     if (annotationsFetched % 1000 == 0) {
	                         System.out.printf("Fetched %d annotations...\n", annotationsFetched);
	                     }
	                     String branch = results.getString("ancestor_term_type");
	                     String id = results.getString("ancestor_acc");
                         String name = results.getString("term_name");
                         String name_descr = "GO"+ Biopax2GMT.DBSOURCE_SEPARATOR + id + "\t" + name;
	                     String gene = results.getString("symbol");
                         String uniprot = "";
                         if(id_type.equalsIgnoreCase("uniprot"))
                             uniprot = results.getString("xref_key");

	                     if (!(targetBranch.equals("all") || targetBranch.equals(branch))) {
	                         continue;
	                     }

	                     Set<String> category = allCategories.get(name_descr);
	                     if (category == null) {
	                         category = new HashSet<String>();
	                         allCategories.put(name_descr, category);
	                     }
                         if(id_type.equalsIgnoreCase("uniprot"))
	                         category.add(uniprot);
                         else
                             category.add(gene);
	                 }

	                 ArrayList<String> terms = new ArrayList<String>(allCategories.keySet());
	                 Collections.sort(terms);
	                 int totalCategories = 0;
	                 for (String term : terms) {
	                     Set<String> genes = allCategories.get(term);
	                     totalCategories++;
	                     //only write term if the genes are not null
                         if(genes.size() >0){
                            writer.print(term);
	                        for (String gene : genes) {
	                             writer.print("\t");
	                             writer.print(gene);
	                        }
	                        writer.println();
                         }
	                 }
	                 System.out.printf("Total GO categories: %d\n", totalCategories);
	                 System.out.println("Done.");
	             } finally {
	                 long duration = System.currentTimeMillis() - start;
	                 System.out.printf("Elapsed time: %.2fs\n", duration / 1000.0);
	                 results.close();
	                 connection.close();
	             }
	         } finally {
	             writer.close();
	         }
	     } catch (SQLException e) {
	         e.printStackTrace();

	     }
	}

    public void parseGAF2()throws IOException,SQLException{
        //track the distinct dbnames,products and taxons to make sure that if there are multiple dbs that we track them all.
        HashMap<String,Integer> dbnames = new HashMap<String,Integer>();
        HashMap<String,Integer> products = new HashMap<String,Integer>();
        HashMap<String,Integer> taxons = new HashMap<String,Integer>();

        //track all the genesets as we go through the file before converting them into proper genesets
        //HashMap<GAFGeneset, HashSet<String>> file_gs = new HashMap<GAFGeneset, HashSet<String>>();
        //HashMap<GAFGeneset, HashSet<String>> file_gs_symbol = new HashMap<GAFGeneset, HashSet<String>>();

        HashMap<String, HashSet<String>> file_gs = new HashMap<String, HashSet<String>>();
        HashMap<String, HashSet<String>> file_gs_symbol = new HashMap<String, HashSet<String>>();

        //open GMT file
        if(gafFilename != null || !gafFilename.equalsIgnoreCase("")){
            TextFileReader reader = new TextFileReader(gafFilename);
            reader.read();
            String fullText = reader.getText();

            String []lines = fullText.split("\n");

            for (int i = 0; i < lines.length; i++) {

               String line = lines[i];
               String[] tokens = line.split("\t");

               //if the first line starts with "!" then it is a commented line - ignore it.
               if(line.startsWith("!")){
                   /* if(!line.equalsIgnoreCase("!gaf-version: 2.0")){
                        System.out.println("The files is in the wrong format.  It should be gaf-version: 2.0 but +" +
                                "the header specifies that it is " + line);
                        return;
                    }*/
                    continue;
               }

               //even though there are 11 required fields the last required field should be found in column 15
               //so there needs to be at least 15 columns.
               if(tokens.length >= 15){

                   //read each line
                   //first token is the db name for the id in column 2
                   String dbname = tokens[0];
                   if(!dbnames.containsKey(dbname))
                       dbnames.put(dbname, 1);
                   else
                        dbnames.put(dbname, dbnames.get(dbname) + 1 );

                   //second token is the db id
                   String dbid = tokens[1];
                   //third token is a symbol
                   String symbol = tokens[2];

                   //fifth token is the GOID
                   String goid = tokens[4];
                   //seventh token is the evidence code
                   String evidence = tokens[6];
                   //ninth token is the branch (bp(P), mf(F), cc(C))
                   String branch = tokens[8];

                   //the twelfth column is the type
                   String product = tokens[11];
                   if(!products.containsKey(product))
                       products.put(product, 1);
                   else
                        products.put(product, products.get(product) + 1);

                   //the thirteenth column is the taxon id
                   String taxon = tokens[12];
                   if(!taxons.containsKey(taxon))
                       taxons.put(taxon,1);
                   else
                        taxons.put(taxon, taxons.get(taxon) + 1 );

                   //check to see if this term has the right evidence code
                   if(exclude){
                       if(evidence.equalsIgnoreCase("IEA") || evidence.equalsIgnoreCase("RCA") || evidence.equalsIgnoreCase("ND"))
                           continue;
                   }

                   //if we are resticting to just one branch
                   if(fBranch.equalsIgnoreCase("bp") && (!branch.equalsIgnoreCase("P")))
                        continue;
                   if(fBranch.equalsIgnoreCase("mf") && (!branch.equalsIgnoreCase("F")))
                        continue;
                   if(fBranch.equalsIgnoreCase("cc") && (!branch.equalsIgnoreCase("C")))
                        continue;


                   //create a new GAFGeneset for this line
                   //GAFGeneset current_set = new GAFGeneset(goid,product,dbname,taxon);
                   //GAFGeneset current_set_symbol = new GAFGeneset(goid,product,"symbol",taxon);
                   String current_set = goid;

		   //if the current_set is "All" then ignore it
		   if(current_set.equalsIgnoreCase("all"))
			continue;

                   if(file_gs.containsKey(current_set)){
                        HashSet<String> current_list = file_gs.get(current_set);
                        current_list.add(dbid);
                        file_gs.put(current_set,current_list);
                        HashSet<String> current_list_symbol = file_gs_symbol.get(current_set);
                        current_list_symbol.add(symbol);
                        file_gs_symbol.put(current_set,current_list_symbol);

                   }
                   //this is a new set
                   else{
                       HashSet<String> new_set = new HashSet<String>();
                       new_set.add(dbid);
                       file_gs.put(current_set, new_set);

                       HashSet<String> new_set_symbol = new HashSet<String>();
                       new_set_symbol.add(symbol);
                       file_gs_symbol.put(current_set, new_set_symbol);

                   }

               }
            }

            //report if the files uses multiple identifiers/taxons or products
            if(dbnames.size() > 1)
                System.out.println("The file uses multiple db identifiers:" + dbnames.toString());
            if(taxons.size() >1)
                System.out.println("The file uses multiple taxons identifiers:" + taxons.toString());
            if(products.size() > 1)
                System.out.println("The file uses multiple product types:" + products.toString());

            if(fQueryFilename == null || fQueryFilename.equalsIgnoreCase("")){

                String id = "";
		if(fTaxonomyId == 9606){
			fQueryFilename = "Human_GO";
            id = "UniProt";
        }
		else if(fTaxonomyId == 10090){
			fQueryFilename = "MOUSE_GO";
            id = "MGI";
        }
		else
			fQueryFilename = fTaxonomyId + "_GO";

        if(exclude)
            fQueryFilename = fQueryFilename + "_" + fBranch + "_" + noiea +"_"+id+".gmt";
        else
            fQueryFilename = fQueryFilename + "_" + fBranch + "_"+ withiea + "_"+id+".gmt";
            }

            PrintWriter writer = new PrintWriter(new File(fQueryFilename));
            PrintWriter writer_symbol = new PrintWriter(new File(fQueryFilename + "_symbol"));
            HashMap<String,String> goterms = getGoterms();
            //get all the descendants for the terms
            HashMap<String, HashSet<String>> descendants = getDescendants();
            //go through all the GAF genesets and create genesets for them.
            for(Iterator j = file_gs.keySet().iterator();j.hasNext();){
                //GAFGeneset current = (GAFGeneset)j.next();
                //GAFGeneset current_set_symbol = new GAFGeneset(current.goid,current.product,"symbol",current.taxon);
                String current = (String)j.next();
                Set<String> genes = file_gs.get(current);
                Set<String> genes_symbol = file_gs_symbol.get(current);
                //gs name = GO|goid
                String name = "GO" +Biopax2GMT.DBSOURCE_SEPARATOR + current;

                //if current is the set name "All" ignore it.
                if(current.equalsIgnoreCase("all"))
                    continue;

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
                if(goterms.containsKey(current))
                    descrip = goterms.get(current);
                else
                    descrip = "GO ID not found in EBI mysql db";
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

            //There might be GO terms that are not directly annotated in the GAF file but because
            //of the structure of GO should still be in the file because some of their children terms
            //have annotations.
            //check to see which terms with descendants aren't in our file_gs set.
            Set<String> gafSets = file_gs.keySet();
            Set<String> missingGoSets =  descendants.keySet();

            //calculate the missing genesets
            missingGoSets.removeAll(gafSets);
            for(Iterator j = missingGoSets.iterator();j.hasNext();){

                String current = (String)j.next();

		//if the current_set is "All" then ignore it
		if(current.equalsIgnoreCase("all"))
			continue;

                //there are no genes associated with these terms that is why we have to go through them.
                Set<String> genes = new HashSet<String>();
                Set<String> genes_symbol = new HashSet<String>();
                //gs name = GO|goid
                String name = "GO" + Biopax2GMT.DBSOURCE_SEPARATOR + current;

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
                if(goterms.containsKey(current))
                    descrip = goterms.get(current);
                else
                    descrip = "GO ID not found in EBI mysql db";

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
        else
            return;

    }

    private HashMap<String,String> getGoterms() throws SQLException{

        HashMap<String,String> goterms = new HashMap<String, String>();
        String termquery = "select name,acc from term ;" ;
        new Driver();

        System.out.println("Executing query...");
	    Connection connection = DriverManager.getConnection(fConnectionString);

        Statement statement_term = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
	    ResultSet termRes = statement_term.executeQuery(termquery);

        while(termRes.next()){
            String name = termRes.getString("name") ;
            String acc = termRes.getString("acc");
            goterms.put(acc,name);
        }
        statement_term.close();


        return goterms;
    }

    private HashMap<String, HashSet<String>> getDescendants() throws SQLException{
        HashMap<String, HashSet<String>> descendants = new HashMap<String, HashSet<String>>();
        String termquery = "select term.acc as parent, descendant.acc as child from term, graph_path, term as descendant " +
                "where " +
                "term.id = graph_path.term1_id and descendant.id=graph_path.term2_id" ;
        new Driver();

        System.out.println("Executing query...");
	    Connection connection = DriverManager.getConnection(fConnectionString);

        Statement statement_term = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
	    ResultSet termRes = statement_term.executeQuery(termquery);

        while(termRes.next()){
            String parent = termRes.getString("parent");
            String child = termRes.getString("child");
            //if the parent is already in the list then add the descendant to its list
            if(descendants.containsKey(parent)){
                HashSet<String> list = descendants.get(parent);
                list.add(child);
                descendants.put(parent, list);
            }
            else{
                HashSet<String> newlist = new HashSet<String>();
                newlist.add(child);
                descendants.put(parent, newlist);
            }
        }
        statement_term.close();


        return descendants;
    }

    public class GAFGeneset{
        String goid;
        String product;
        String dbname;
        String taxon;

        public GAFGeneset(String goid, String product, String dbname, String taxon) {
            this.goid = goid;
            this.product = product;
            this.dbname = dbname;
            this.taxon = taxon;
        }

        public boolean equals(Object currentset){
            GAFGeneset newset = (GAFGeneset)currentset;
            return (this.goid.equalsIgnoreCase(newset.goid) && this.product.equalsIgnoreCase(newset.product) &&
                    this.dbname.equalsIgnoreCase(newset.dbname) && this.taxon.equalsIgnoreCase(newset.taxon));
        }

        public int hashCode(){
            return this.goid.hashCode() + this.product.hashCode() + this.dbname.hashCode() + this.taxon.hashCode();
        }

    }

}
