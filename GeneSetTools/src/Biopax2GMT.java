

/**
 * Created by IntelliJ IDEA.
 * User: risserlin
 * Date: 11-07-28
 * Time: 12:36 PM
 * toGSEA class taken from paxtools in order to modify the structure of the GSEA output file to conform
 * to EM desired gmt file format
 */
// imports

import org.biopax.paxtools.controller.PropertyEditor;
import org.biopax.paxtools.controller.SimpleEditorMap;
import org.biopax.paxtools.controller.Traverser;
import org.biopax.paxtools.controller.Visitor;
import org.biopax.paxtools.converter.OneTwoThree;
import org.biopax.paxtools.io.SimpleIOHandler;
import org.biopax.paxtools.model.BioPAXElement;
import org.biopax.paxtools.model.BioPAXLevel;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level3.*;
import org.kohsuke.args4j.Option;

import java.io.*;
import java.util.*;


/**
 * Converts a BioPAX model to GSEA (GMT format).
 *
 * Creates GSEA entries from the pathways contained in the model.
 *
 * Pathway members are derived by finding the xref who's
 * database name matches the database constructor argument and returning
 * the respective database id.  If database id is empty,
 * the rdf id of the protein is returned.
 *
 * Note, to properly enforce cross-species violations, bio-sources must
 * be annotated with "taxonomy" database name.
 *
 * Note this code assumes that the model has successfully been validated
 * using the BioPAX validator.
 */
public class Biopax2GMT implements Visitor {

    @Option(name = "--biopax", usage = "name of biopax file to convert", required = true)
    private String owl_filename;

    @Option(name = "--outfile", usage = "name of output file", required = true)
    private String OutFilename;

     @Option(name = "--id", usage = "type of id grab from the biopax file", required = true)
    private String id;

    @Option(name = "--source", usage = "name of the source, if it is not encoded in biopax file ability for the user to specify it")
    private String source;

    @Option(name = "--speciescheck", usage = "TRUE/FALSE - check that all ids are from one species", required = true)
    private String speciescheck;

	// following vars used during traversal
	String database;
	boolean crossSpeciesCheck;
	boolean visitProtein; // true we visit proteins, false we visit ProteinReference
	Map<String, String> rdfToGenes; // map of member proteins of the pathway rdf id to gene symbol
	Set<BioPAXElement> visited; // helps during traversal
	String taxID;
	Traverser traverser;
    private int proteinref_count = 0;

    public static String DBSOURCE_SEPARATOR = "?";

	/**
	 * Constructor.
	 */
	public Biopax2GMT() {
		this("", true, "");
	}

    /**
     * Constructor for when you are calling this internally.
     *
     * @param owl_filename
     * @param outFilename
     * @param id
     * @param speciescheck
     */

    public Biopax2GMT(String owl_filename, String outFilename, String id, String speciescheck) {
        this.owl_filename = owl_filename;
        OutFilename = outFilename;
        this.id = id;
        this.speciescheck = speciescheck;
    }

    public Biopax2GMT(String owl_filename, String outFilename, String id, String source, String speciescheck) {
        this.owl_filename = owl_filename;
        OutFilename = outFilename;
        this.id = id;
        this.source = source;
        this.speciescheck = speciescheck;
    }

    /**
	 * Constructor.
	 *
	 * See class declaration for more information.
	 *
	 * @param database String: the database/xref to use for grabbing participants
	 * @param crossSpeciesCheck - if true, enforces no cross species participants in output
	 *
	 */
	public Biopax2GMT(String database, boolean crossSpeciesCheck, String source) {
		this.source = source;
        this.database = database;
    	this.crossSpeciesCheck = crossSpeciesCheck;
    	this.traverser = new Traverser(SimpleEditorMap.L3, this);
	}

    public void toGSEA() throws IOException{
        SimpleIOHandler io = new SimpleIOHandler();
        Model model = io.convertFromOWL(new FileInputStream(owl_filename));
        (new Biopax2GMT(id, new Boolean(speciescheck),source)).writeToGSEA(model, new FileOutputStream(OutFilename));
    }

	/**
	 * Converts model to GSEA and writes to out.  See class declaration for more information.
	 *
	 * @param model Model
	 */
	public void writeToGSEA(final Model model, OutputStream out) throws IOException {

		Collection<? extends GeneSet> entries = convert(model);
    	if (entries.size() > 0) {
    		Writer writer = new OutputStreamWriter(out);
    		for (GeneSet entry : entries) {
    			writer.write(entry.rdftoString() + "\n");
    		}
    		writer.close();
    	}
	}

	/**
     * Creates GSEA entries from the pathways contained in the model.
     *
     * @param model Model
     * @return a set of GSEA entries
     */
    public Collection<? extends GeneSet> convert(final Model model) {

    	// setup some vars
    	Model l3Model = null;

    	Collection<GeneSet> toReturn = new HashSet<GeneSet>();

    	// convert to level 3 in necessary
        if (model.getLevel() == BioPAXLevel.L1 ||
        	model.getLevel() == BioPAXLevel.L2) {
        	l3Model = (new OneTwoThree()).filter(model);
        }
        else if (model.getLevel() == BioPAXLevel.L3) {
        	l3Model = model;
        }

        // iterate over all pathways in the model
        for (Pathway aPathway : l3Model.getObjects(Pathway.class)) {
        	toReturn.add(getGSEAEntry(model, aPathway, database));
        }

        // outta here
        return toReturn;
    }


    public void visit(BioPAXElement domain, Object range, Model model, PropertyEditor editor) {

    	boolean checkDatabase = (this.database != null && this.database.length() > 0 && !this.database.equals("NONE"));

    	if (range != null && range instanceof BioPAXElement && !visited.contains(range)) {
    		if (visitProtein) {
    			visitProtein(range, checkDatabase);
    		}
    		else {
    			visitProteinReference(range, checkDatabase);
    		}
    		visited.add((BioPAXElement)range);
			this.traverser.traverse((BioPAXElement)range, model);
    	}
    }

	private GeneSet getGSEAEntry(final Model model, final Pathway aPathway, final String database) {

		// the GSEAEntry to return
		final GeneSet toReturn = new GeneSet();

		// set name to description for the gmt file
		String name = aPathway.getDisplayName();
		name = (name == null) ? aPathway.getStandardName() : name;
        if(name == null){
          for(String names:aPathway.getName()){
            name = name + names + ";";
          }
        }
		name = (name == null) ? "NAME" : name;
		//toReturn.setName(name);
        toReturn.setDataSource(name);


		// tax id
        String taxID = null;
        if(aPathway.getOrganism() != null)
		    taxID = getTaxID(aPathway.getOrganism().getXref());
		taxID = (taxID == null) ? "TAX-ID" : taxID;
		toReturn.setTaxID(taxID);
		// data source
		String dataSource = getDataSource(aPathway.getDataSource());
        if(dataSource == null || dataSource.equals("")){
            if (source != null || !source.equals(""))
                dataSource = source;
            else
                dataSource = "N/A";
        }

		//dataSource = (dataSource == null) ? "N/A" : dataSource;
		//toReturn.setDataSource(dataSource);

        //If there is an ID available for this pathway we want to use the pathway ID as
       	//the main key in the GMT file
        String pathwayID = dataSource ;
		String stableID = "";
		String tempID = "";
	    for (Xref aXref: aPathway.getXref()) {

            if(aXref.getClass().getSimpleName().equalsIgnoreCase( "UnificationXrefImpl")){

				if (aXref.getDb() != null && aXref.getId() != null && aXref.getIdVersion() != null) {
                    pathwayID = aXref.getDb() + DBSOURCE_SEPARATOR + aXref.getId() + "." + aXref.getIdVersion() ;
                }
                else if(aXref.getDb() != null && aXref.getId() != null && aXref.getIdVersion() == null) {
                     pathwayID = aXref.getDb() + DBSOURCE_SEPARATOR + aXref.getId();
                }
                else if(aXref.getDb() == null && aXref.getId() != null) {
                     pathwayID = "NO_DATABASE_DEFINED" + DBSOURCE_SEPARATOR + aXref.getId();
                }
				//Making a very naive assumption that only stable identifiers start with "REACT_"
                //as a hack to get the Reactome stable ID.
                if(aXref.getDb().equalsIgnoreCase("Reactome") && aXref.getId().contains("REACT_")){
					stableID = aXref.getId() + "." + aXref.getIdVersion();
				}
				else if(aXref.getDb().equalsIgnoreCase("Reactome") && aXref.getIdVersion() == null){
					tempID = aXref.getId();
				}
			}
        }
		if(!stableID.equals("")){
				pathwayID = dataSource + DBSOURCE_SEPARATOR + stableID;
		}else if(stableID.equals("") && !tempID.equals("")){
            pathwayID = dataSource + DBSOURCE_SEPARATOR + tempID;
        }

        name = (pathwayID.equalsIgnoreCase(dataSource)) ? dataSource + DBSOURCE_SEPARATOR + name : pathwayID;
        toReturn.setName(name);

		// genes
		this.taxID = taxID;
		this.visitProtein = true;
		this.rdfToGenes = new HashMap<String, String>();
		this.visited = new HashSet<BioPAXElement>();
		this.traverser.traverse(aPathway, model);
		if (this.rdfToGenes.size() == 0) {
			this.visitProtein = false;
			this.visited = new HashSet<BioPAXElement>();
			this.traverser.traverse(aPathway, model);
		}
		toReturn.setRDFToGeneMap(this.rdfToGenes);

        System.out.println("Number ProteinRefs:\t"+ proteinref_count
                + "\tNumber of GMT proteins:\t" + rdfToGenes.size());

        proteinref_count=0;


		// outta here
		return toReturn;
	}

	private void visitProtein(Object range, boolean checkDatabase) {

    	if (range instanceof Protein) {
    		Protein aProtein = (Protein)range;
    		// we only process proteins that are same species as pathway
    		if (crossSpeciesCheck && this.taxID.length() > 0 && !sameSpecies(aProtein, this.taxID)) {
    			return;
    		}
    		// if we are not checking database, just return rdf id
    		if (checkDatabase) {
			    for (Xref aXref : aProtein.getXref())
			    {
    				if (aXref.getDb() != null && aXref.getDb().equalsIgnoreCase(this.database)) {
    					this.rdfToGenes.put(aProtein.getRDFId(), aXref.getId());
    					break;
    				}
    			}
    		}
    		else {
    			this.rdfToGenes.put(aProtein.getRDFId(), aProtein.getRDFId());
    		}
    	}
	}

    private void visitProteinReference(Object range, boolean checkDatabase) {

    	if (range instanceof ProteinReference) {
            proteinref_count++;
    		ProteinReference aProteinRef = (ProteinReference)range;
    		// we only process protein refs that are same species as pathway
    		if (crossSpeciesCheck && this.taxID.length() > 0 && !getTaxID(aProteinRef.getOrganism().getXref()).equals(this.taxID)) {
    			return;
    		}
    		if (checkDatabase) {
				// short circuit if we are converting for pathway commons
				// Also ensure we get back primary accession - which is built into the rdf id of the protein  ref
				if (database.equalsIgnoreCase("uniprot") && aProteinRef.getRDFId().startsWith("urn:miriam:uniprot:")) {
					String accession = aProteinRef.getRDFId();
					accession = accession.substring(accession.lastIndexOf(":")+1);
					this.rdfToGenes.put(aProteinRef.getRDFId(), accession);
				}
				else {
					for (Xref aXref: aProteinRef.getXref()) {
						if (aXref.getDb() != null && aXref.getDb().equalsIgnoreCase(database)) {
							this.rdfToGenes.put(aProteinRef.getRDFId(), aXref.getId());
							break;
						}
					}
				}
    		}
    		else {
    			this.rdfToGenes.put(aProteinRef.getRDFId(), aProteinRef.getRDFId());
    		}
    	}
	}

    private String getDataSource(Set<Provenance> provenances) {

		for (Provenance provenance : provenances) {
			String name = provenance.getDisplayName();
			name = (name == null) ? provenance.getStandardName() : name;
            for(String cur_name : provenance.getName())
                name = (name == null) ? cur_name : name;
			if (name != null && name.length() > 0) return name;
		}

		// outta here
		return "";
	}

	private boolean sameSpecies(Protein aProtein, String taxID) {

		ProteinReference pRef = (ProteinReference)aProtein.getEntityReference();
		if (pRef != null && pRef.getOrganism() != null) {
			BioSource bs = pRef.getOrganism();
			if (bs.getXref() != null) {
				return (getTaxID(bs.getXref()).equals(taxID));
			}
		}

		// outta here
		return false;
	}

	private String getTaxID(Set<Xref> xrefs) {

		for (Xref xref : xrefs) {
			if (xref.getDb().equalsIgnoreCase("taxonomy")) {
				return xref.getId();
			}
		}

		// outta here
		return "";
	}
}

