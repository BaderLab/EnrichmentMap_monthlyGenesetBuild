import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import com.mysql.jdbc.Driver;

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

    private int fTaxonomyId;

	String fConnectionString = "jdbc:mysql://mysql.ebi.ac.uk:4085/go_latest?user=go_select&password=amigo"; //$NON-NLS-1$
    String fQueryFilename;
    String fBranch = "all"; //$NON-NLS-1$
    int id_type=0;  //if id is zero then output the symbol, otherwise use the uniprots

    public GOGeneSetFileMaker(int fTaxonomyId, String fBranch,String fQueryFilename,int id_type) {
        this.fTaxonomyId = fTaxonomyId;
        this.fQueryFilename = fQueryFilename;
        this.fBranch = fBranch;
        this.id_type = id_type;
    }

    String createVersionQuery(){
        return "select I.release_name, I.release_type from instance_data as I";
    }

    String createGoQuery(long taxId) {
	        String ignoreClause = "and evidence.code not in ('IEA', 'ND', 'RCA')";
	        /*return "select" +
	            " ancestor_term.term_type as ancestor_term_type, ancestor_term.acc as ancestor_acc," +
	            " gene_product.symbol" +
	            " from association, term as ancestor_term, term as descendent_term, gene_product, species, evidence, graph_path, dbxref" +
	            " where species.ncbi_taxa_id = " +
	            taxId +
	            " and species.id = gene_product.species_id" +
	            " and association.gene_product_id = gene_product.id" +
	            " and ancestor_term.id = graph_path.term1_id" +
	            " and descendent_term.id = graph_path.term2_id" +
	            " and evidence.association_id = association.id " +
	            ignoreClause +
	            " and association.term_id = descendent_term.id" +
	            " and gene_product.dbxref_id = dbxref.id" +
	            " and association.is_not = 0" +
	            " and descendent_term.is_obsolete = 0;"; */
              return "select" +
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
	                      " and descendent_term.is_obsolete = 0;";
	    }

	    @SuppressWarnings("nls")
	    void makeQuery() throws IOException {
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
	        String query = createGoQuery(fTaxonomyId);

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
                            String name_descr = id + "\t" + name;
	                        String gene = results.getString("symbol");
                            String uniprot = results.getString("xref_key");

	                        if (!(targetBranch.equals("all") || targetBranch.equals(branch))) {
	                            continue;
	                        }

	                        Set<String> category = allCategories.get(name_descr);
	                        if (category == null) {
	                            category = new HashSet<String>();
	                            allCategories.put(name_descr, category);
	                        }
                            if(id_type == 0)
	                            category.add(gene);
                            else
                                category.add(uniprot);
	                    }

	                    ArrayList<String> terms = new ArrayList<String>(allCategories.keySet());
	                    Collections.sort(terms);
	                    int totalCategories = 0;
	                    for (String term : terms) {
	                        Set<String> genes = allCategories.get(term);
	                        totalCategories++;
	                        writer.print(term);
	                        for (String gene : genes) {
	                            writer.print("\t");
	                            writer.print(gene);
	                        }
	                        writer.println();
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

}
