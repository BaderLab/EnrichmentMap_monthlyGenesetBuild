/**
 * Created by IntelliJ IDEA.
 * User: risserlin
 * Date: 11-08-18
 * Time: 3:39 PM
 * To change this template use File | Settings | File Templates.
 */
public class HomoloGene {
    /*homologene.data is a tab delimited file containing the following
    columns:

    1) HID (HomoloGene group id)
    2) Taxonomy ID
    3) Gene ID
    4) Gene Symbol
    5) Protein gi
    6) Protein accession
    */

    private Integer homologGroup;

    private Integer taxid;

    //In order to use with woodchuck which doesn't have entrezgene ids we want to 
    // convert to their native ids which aren't integers.  Change this to be a STring
    // eventhough for other species it is integer
    private String entrezgeneid;

    private String symbol;

    private Integer gi;

    private String accession;

    public HomoloGene(Integer homologGroup, Integer taxid, String entrezgeneid, String symbol, Integer gi, String accession) {
        this.homologGroup = homologGroup;
        this.taxid = taxid;
        this.entrezgeneid = entrezgeneid;
        this.symbol = symbol;
        this.gi = gi;
        this.accession = accession;
    }

    public Integer getHomologGroup() {
        return homologGroup;
    }

    public void setHomologGroup(Integer homologGroup) {
        this.homologGroup = homologGroup;
    }

    public Integer getTaxid() {
        return taxid;
    }

    public void setTaxid(Integer taxid) {
        this.taxid = taxid;
    }

    public String getEntrezgeneid() {
        return entrezgeneid;
    }

    public void setEntrezgeneid(String entrezgeneid) {
        this.entrezgeneid = entrezgeneid;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public Integer getGi() {
        return gi;
    }

    public void setGi(Integer gi) {
        this.gi = gi;
    }

    public String getAccession() {
        return accession;
    }

    public void setAccession(String accession) {
        this.accession = accession;
    }
}
