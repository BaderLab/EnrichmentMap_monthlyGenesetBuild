
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;

/**
 * Created by
 * User: risserlin
 * Date: Nov 18, 2010
 * Time: 12:17:43 PM
 */
public class GMTParameters {


    //Input File names
    //GMT - gene set definition file
    private String GMTFileName;

    //when comparing to gmt files there is another gmt file name
    private String GMTFileName2;

    //if there is a header, track this info.
    private String version;
    private String source;

    //Expression files
    private String expressionFileName1;


    //DATA need to specify the Enrichment map
    //Hashmap stores the unique set of genes used in the gmt file
    private HashMap<String,Integer> genes;

    //when translating visual attribute of the gene list we need to be able to translate
    //the gene hash key into the gene name without tracing from the entire hash.
    //create the opposite of the gene hashmap so we can do this.
    private HashMap<Integer, String> hashkey2gene;
    private HashMap<String, Integer> datasetGenes;
    private int NumberOfGenes = 0;

    //Hashmap of all genesets in the geneset file (gmt file)
    private HashMap<String, GeneSet> genesets;
    //Hashmap of all genesets in the geneset file (gmt file)
    private HashMap<String, GeneSet> genesets_2;
    //Hashmap of the filtered Genesets.  After loading in the expression file the genesets are restricted
    //to contain only the proteins specified in the expression file.
    private HashMap<String, GeneSet> filteredGenesets;

    //Gene expression data used for the analysis.  There can be two distinct files
    //for each dataset.
    private GeneExpressionMatrix expression;

    public GMTParameters() {

        this.genes = new HashMap<String, Integer>();
        this.hashkey2gene = new HashMap<Integer, String>();
        this.datasetGenes = new HashMap<String, Integer>();
        this.genesets = new HashMap<String, GeneSet>();
        this.genesets_2 = new HashMap<String, GeneSet>();
        this.filteredGenesets = new HashMap<String, GeneSet>();
    }

    public String getGMTFileName() {
        return GMTFileName;
    }

    public void setGMTFileName(String GMTFileName) {
        this.GMTFileName = GMTFileName;
    }

    public String getExpressionFileName1() {
        return expressionFileName1;
    }

    public void setExpressionFileName1(String expressionFileName1) {
        this.expressionFileName1 = expressionFileName1;
    }

    public HashMap<String, Integer> getGenes() {
        return genes;
    }

    public void setGenes(HashMap<String, Integer> genes) {
        this.genes = genes;
    }

    public HashMap<Integer, String> getHashkey2gene() {
        return hashkey2gene;
    }

    public void setHashkey2gene(HashMap<Integer, String> hashkey2gene) {
        this.hashkey2gene = hashkey2gene;
    }

    public HashMap<String, Integer> getDatasetGenes() {
        return datasetGenes;
    }

    public void setDatasetGenes(HashMap<String, Integer> datasetGenes) {
        this.datasetGenes = datasetGenes;
    }

    public int getNumberOfGenes() {
        return NumberOfGenes;
    }

    public void setNumberOfGenes(int numberOfGenes) {
        NumberOfGenes = numberOfGenes;
    }

    public HashMap<String, GeneSet> getGenesets() {
        return genesets;
    }

    public void setGenesets(HashMap<String, GeneSet> genesets) {
        this.genesets = genesets;
    }

    public HashMap<String, GeneSet> getFilteredGenesets() {
        return filteredGenesets;
    }

    public void setFilteredGenesets(HashMap<String, GeneSet> filteredGenesets) {
        this.filteredGenesets = filteredGenesets;
    }

    public GeneExpressionMatrix getExpression() {
        return expression;
    }

    public void setExpression(GeneExpressionMatrix expression) {
        this.expression = expression;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getGMTFileName2() {
        return GMTFileName2;
    }

    public void setGMTFileName2(String GMTFileName2) {
        this.GMTFileName2 = GMTFileName2;
    }

    public HashMap<String, GeneSet> getGenesets_2() {
        return genesets_2;
    }

    public void setGenesets_2(HashMap<String, GeneSet> genesets_2) {
        this.genesets_2 = genesets_2;
    }

    public void printGenesets(HashMap<String, GeneSet> genesets, String outputFile) throws IOException{
        //print out the new gmt
        File newgsfile = new File(outputFile);
        BufferedWriter newgs = new BufferedWriter(new FileWriter(newgsfile));
        for(Iterator c = genesets.keySet().iterator();c.hasNext();){
            newgs.write(genesets.get(c.next()).toStringNames(this) + "\n");
            newgs.flush();
        }
        newgs.close();
    }
}
