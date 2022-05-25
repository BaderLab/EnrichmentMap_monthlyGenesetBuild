

/**
 * Created by IntelliJ IDEA.
 * User: risserlin
 * Date: 11-07-28
 * Time: 12:36 PM
 * toGSEA class taken from paxtools in order to modify the structure of the GSEA output file to conform
 * to EM desired gmt file format
 */
// imports
import org.apache.commons.lang3.StringUtils;
import org.biopax.paxtools.controller.Fetcher;
import org.biopax.paxtools.controller.*;
import org.biopax.paxtools.converter.LevelUpgrader;
import org.biopax.paxtools.io.SimpleIOHandler;
import org.biopax.paxtools.model.BioPAXElement;
import org.biopax.paxtools.model.BioPAXLevel;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level3.*;
import org.biopax.paxtools.model.level3.Process;
import org.biopax.paxtools.util.Filter;
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
public class Biopax2GMT  {

    @Option(name = "--biopax", usage = "name of biopax file to convert", required = true)
    private String owl_filename;

    @Option(name = "--outfile", usage = "name of output file", required = true)
    private String OutFilename;

     @Option(name = "--id", usage = "type of id grab from the biopax file", required = true)
    private String id;

    @Option(name = "--source", usage = "name of the source, if it is not encoded in biopax file ability for the user to specify it")
    private String source;

    @Option(name = "--speciescheck", usage = "TRUE/FALSE - check that all ids are from one species", required = false)
    private boolean speciescheck=false;


    @Option(name = "--species", usage = "taxon of the ids that you want to extract from the biopax file", required = false)
    private String species;

	// following vars used during traversal
	//String database;
	//boolean crossSpeciesCheck;
	//String taxID;

	private final IdFetcher idFetcher;
	private boolean skipSubPathways = false;
	private boolean skipOutsidePathways = true;
	private int minNumIdsPerEntry;

    public static String DBSOURCE_SEPARATOR = "%";

	/**
	 * Constructor.
	 */
	public Biopax2GMT() {
//		this();
		this("", "",false);
	}


	public boolean isSkipSubPathways() {
		return skipSubPathways;
	}

	public void setSkipSubPathways(boolean skipSubPathways) {
		this.skipSubPathways = skipSubPathways;
	}

	/**
	 * If true, then only GMT entries that (genes) correspond to a Pathway
	 * are printed to the output.
	 * @return true/false
     */
	public boolean isSkipOutsidePathways() {
		return skipOutsidePathways;
	}

	public void setSkipOutsidePathways(boolean skipOutsidePathways) {
		this.skipOutsidePathways = skipOutsidePathways;
	}

	/**
	 * If this value is greater than 0, and the number of proteins/genes
	 * in a gene set is less than that value, then this gene set is to skip
	 * (no GMT entry is written).
	 * @return the min. value
     */
	public int getMinNumIdsPerEntry() {
		return minNumIdsPerEntry;
	}

	public void setMinNumIdsPerEntry(int minNumIdsPerEntry) {
		this.minNumIdsPerEntry = minNumIdsPerEntry;
	}

    public Biopax2GMT(String id, String source,String species, boolean speciescheck) {
        this.source = source;
	this.speciescheck = speciescheck;
        //System.out.println("constructor 3");
        this.id = id;
	this.species = species;
	idFetcher = new IdFetcher().seqDbStartsWithOrEquals(this.id);
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
	public Biopax2GMT(String id,  String source,boolean speciescheck) {
		this.source = source;
		this.speciescheck = speciescheck;
        	//System.out.println("constructor 4");
		this.id=id;
		idFetcher = new IdFetcher().seqDbStartsWithOrEquals(this.id);

	}

    public void toGSEA() throws IOException{

        SimpleIOHandler io = new SimpleIOHandler();
        Model model = io.convertFromOWL(new FileInputStream(owl_filename));
	if(!speciescheck)
        	(new Biopax2GMT(id,source,this.speciescheck)).writeToGSEA(model, new FileOutputStream(OutFilename));
    	else
        	(new Biopax2GMT(id,source,species,this.speciescheck)).writeToGSEA(model, new FileOutputStream(OutFilename));
    }

	/**
	 * Converts model to GSEA and writes to out.  See class declaration for more information.
	 *
	 * @param model Model
	 */
	public void writeToGSEA(final Model model, OutputStream out) throws IOException {

		Collection<GMTEntry> entries = convert(model);
		if (entries.size() > 0)
		{
			Writer writer = new OutputStreamWriter(out);
			for (GMTEntry entry : entries) {
				if ((minNumIdsPerEntry <= 1 && !entry.identifiers().isEmpty())
						|| entry.identifiers().size() >= minNumIdsPerEntry)
				{
					writer.write(entry.toString() + "\n");
				}
			}
			writer.flush();
		}

	}

	
	private Collection<GMTEntry> createGseaEntries(String uri, final String formattedName, final String name,
												   final Set<EntityReference> ers)
	{
		final Collection<GMTEntry> toReturn = new ArrayList<GMTEntry>();
		//GMTEntry entry = new GMTEntry(uri, "", "", String.format("name: %s; datasource: %s",name, dataSource));
		GMTEntry entry = new GMTEntry(formattedName, "","",name);
		for (EntityReference er : ers)
			entry.identifiers().addAll(idFetcher.fetchID(er));
		toReturn.add(entry);
		return toReturn;
	}

	/*
	 * Gets datasource names, if any, in a consistent way/order, excl. duplicates
	 */
	private String getDataSource(Set<Provenance> provenances)
	{
		if(provenances.isEmpty()) return "N/A";

		Set<String> dsNames = new TreeSet<String>();
		for (Provenance provenance : provenances)
		{
			String name = provenance.getDisplayName();
			if(name == null)
				name = provenance.getStandardName();
			if(name == null && !provenance.getName().isEmpty())
				name = provenance.getName().iterator().next();
			if (name != null && name.length() > 0)
				dsNames.add(name.toLowerCase());
		}

		return StringUtils.join(dsNames, ";");
	}

	/**
     * Creates GSEA entries from the pathways contained in the model.
     *
     * @param model Model
     * @return a set of GSEA entries
     */
    public Collection<GMTEntry> convert(final Model model) {

/*    	// setup some vars
    	Model l3Model = null;

    	Collection<GeneSet> toReturn = new HashSet<GeneSet>();

    	// convert to level 3 in necessary
        if (model.getLevel() == BioPAXLevel.L2) {
        	l3Model = (new LevelUpgrader()).filter(model);
        }
        else if (model.getLevel() == BioPAXLevel.L3) {
        	l3Model = model;
        }
                
        // iterate over all pathways in the model
        for (Pathway aPathway : l3Model.getObjects(Pathway.class)) {
            //only add the geneset is it has genes
            GeneSet current = getGSEAEntry(model, aPathway, database);
            
            //if the datasource is Panther then iterate over the process instead of the pathways
            //The assumption is that the Panther pathways are each in a separate file
            //but their catalysis are not listed in the pathways and the easiest way to get
            //at them to recurse through the processes instead
            //IF THERE ARE MULTIPLE PATHWAYS IN THE FILE THIS WILL NOT WORK
            // iterate over all pathways in the model
            if(this.source.equalsIgnoreCase("Panther")) {
                //only add the geneset is it has genes
            		for (Process aProcess : l3Model.getObjects(Process.class)) 
            			current = addGenesFromProcess(model,aProcess , current);
                
            }
            
            //if(current.getGenes() != null && current.getGenes().size() >0 )
        	    toReturn.add(current);
        }

        // outta here
        return toReturn;
*/

	final Collection<GMTEntry> toReturn = new TreeSet<GMTEntry>(new Comparator<GMTEntry>() {
			@Override
			public int compare(GMTEntry o1, GMTEntry o2) {
				return o1.toString().compareTo(o2.toString());
			}
		});

		Model l3Model;
		// convert to level 3 in necessary
		if (model.getLevel() == BioPAXLevel.L2)
			l3Model = (new LevelUpgrader()).filter(model);
		else
			l3Model = model;

		//a modifiable copy of the set of all PRs in the model -
		//after all, it has all the ERs that do not belong to any pathway
		final Set<EntityReference> entityReferences =
				new HashSet<EntityReference>(l3Model.getObjects(EntityReference.class));

		final Set<Pathway> pathways = l3Model.getObjects(Pathway.class);
		for (Pathway pathway : pathways)
		{
			
            		GeneSet current = getFormattedGeneSetName(l3Model, pathway, id);

			
			String name = (pathway.getDisplayName() == null) ? pathway.getStandardName() : pathway.getDisplayName();
			if(name == null || name.isEmpty())
				name = pathway.getUri();

			final Pathway currentPathway = pathway;
			final String currentPathwayName = name;
			final String database = this.id;
			//System.out.println("Begin converting " + currentPathwayName + " pathway, uri=" + currentPathway.getUri());
			final Set<EntityReference> ers = new HashSet<EntityReference>();
			final Traverser traverser = new AbstractTraverser(SimpleEditorMap.L3,
					Fetcher.nextStepFilter, Fetcher.objectPropertiesOnlyFilter) {
				@Override
				protected void visit(Object range, BioPAXElement domain, Model model, PropertyEditor editor)
				{
					BioPAXElement bpe = (BioPAXElement) range; //cast is safe (due to objectPropertiesOnlyFilter)
					if(bpe instanceof EntityReference/* && !(bpe instanceof Pathway)*/) {
						boolean checkDatabase = (database != null 
								&& database.length() > 0 
								&& !database.equals("NONE"));

						if(checkEntity((EntityReference)bpe,checkDatabase) != null)
							ers.add((EntityReference) bpe);
					}
					if(bpe instanceof Pathway) {
						
						if(skipSubPathways)
						{	//do not traverse into the sub-pathway; log
							System.out.println("Skipping sub-pathway: " + bpe.getUri());
						} else {
							traverse(bpe, model);
						}
					} else {
						traverse(bpe, model);
					}
				}
			};

			String curname = "";
          		for(String names:currentPathway.getName()){
            			curname = curname + names + ";";
         		 }		
			//Pathbank files contain subpathways delineated as pathways, even if we turn off sub pathway
			//parsing the pathway still gets parsed because it is defined as a pathway.
			//maybe a result of multiple files and redefinition of the same subpathway.  If the pathways
			//were defined in one files this might not be an issue
			//If the pathway name contains subpathway and the pathway is from Pathbank - exclude it.
			//System.out.println("Pathway - " + curname);
			if(this.source.equalsIgnoreCase("Pathbank") && 
					StringUtils.containsIgnoreCase(curname,"subpathway")){
				System.out.println("Pathbank Pathway name contains subpathway - exclude it -->" + curname);	
			}
			else{
				//run it - collect all PRs from the pathway
				traverser.traverse(currentPathway, null);

				if(!ers.isEmpty()) {
					// create GMT entries
					Collection<GMTEntry> entries = createGseaEntries(currentPathway.getUri(),
						current.getName(), currentPathwayName, ers);
					if(!entries.isEmpty())
						toReturn.addAll(entries);
					entityReferences.removeAll(ers);//keep not processed PRs (a PR can be processed multiple times)
				}	
		
				//if the datasource is Panther then iterate over the process instead of the pathways
				//The assumption is that the Panther pathways are each in a separate file
	            		//but their catalysis are not listed in the pathways and the easiest way to get
				//at them to recurse through the processes instead
				//IF THERE ARE MULTIPLE PATHWAYS IN THE FILE THIS WILL NOT WORK
				// iterate over all pathways in the model
				// Some of the panther pathways have the entities in the pathway.  Only iterate over the 
				// processes if we haven't found anything in the pathways. 
				if(this.source.equalsIgnoreCase("Panther") && toReturn.isEmpty()) {
				//go through all the individual processes but add them to the same overall pathway
					for (Process aProcess : l3Model.getObjects(Process.class)){
						traverser.traverse(aProcess, null);
					
					}
					if(!ers.isEmpty()) {
						// create GMT entries
						Collection<GMTEntry> entries = createGseaEntries(currentPathway.getUri(),
							current.getName(), currentPathwayName, ers);
						if(!entries.isEmpty())
							toReturn.addAll(entries);
						entityReferences.removeAll(ers);//keep not processed PRs (a PR can be processed multiple times)
					}

				 
					}
			
			}
			//when there're no pathways, only empty pathays, pathways w/o PRs, then use all/rest of PRs -
			//organize PRs by species (GSEA s/w can handle only same species identifiers in a data row)
			if(!entityReferences.isEmpty() && !skipOutsidePathways) {
				//System.out.println("Creating entries for the rest of PRs (outside any pathway)...");
				toReturn.addAll(createGseaEntries("other","other", getDataSource(l3Model.getObjects(Provenance.class)),entityReferences));
			}	
		}
		return toReturn;

    }


    
 /*   private GeneSet addGenesFromProcess(final Model model, final Process aProcess, final GeneSet toReturn){
    			// genes
    			this.visitProtein = true;
    			this.rdfToGenes = new HashMap<String, String>();
    			this.visited = new HashSet<BioPAXElement>();
    			this.traverser.traverse(aProcess, model);
    			if (this.rdfToGenes.size() == 0) {
    				this.visitProtein = false;
    				this.visited = new HashSet<BioPAXElement>();
    				this.traverser.traverse(aProcess, model);
    			}
    			HashMap<String,String> temp = new HashMap<String,String>(toReturn.getRDFToGeneMap());
    			temp.putAll(this.rdfToGenes);
    			toReturn.setRDFToGeneMap(temp);
    		return toReturn;
    	
    }*/
    
	private GeneSet getFormattedGeneSetName(final Model model, final Pathway aPathway, final String database) {

		// the GSEAEntry to return
		final GeneSet toReturn = new GeneSet();

		// set name to description for the gmt file
		//String name = aPathway.getDisplayName();
		String name = (aPathway.getDisplayName() == null) ? aPathway.getStandardName() : aPathway.getDisplayName();
		name = (name == null) ? aPathway.getStandardName() : name;
        	if(name == null){
          		for(String names:aPathway.getName()){
            			name = name + names + ";";
         		 }		
       		 }
		name = (name == null) ? "NAME" : name;
		//toReturn.setName(name);
        	toReturn.setDataSource(name);

		// data source
		String dataSource = getDataSource(aPathway.getDataSource());
        	if(dataSource == null || dataSource.equals("")){
            		if (source != null || !source.equals(""))
                		dataSource = source;
            		else
                		dataSource = "N/A";
        	}

        	//If there is an ID available for this pathway we want to use the pathway ID as
       		//the main key in the GMT file
        	String pathwayID = dataSource ;
		String stableID = "";
		String tempID = "";
	    	for (Xref aXref: aPathway.getXref()) {

            		if(aXref.getClass().getSimpleName().equalsIgnoreCase( "UnificationXrefImpl")){

				if (aXref.getDb() != null && aXref.getId() != null && aXref.getIdVersion() != null) {
                    			pathwayID = name + DBSOURCE_SEPARATOR + aXref.getDb() + DBSOURCE_SEPARATOR + aXref.getId() + "." + aXref.getIdVersion() ;
                		}
                	else if(aXref.getDb() != null && aXref.getId() != null && aXref.getIdVersion() == null) {
                     		pathwayID = name + DBSOURCE_SEPARATOR + aXref.getDb() + DBSOURCE_SEPARATOR + aXref.getId();
                	}
                	else if(aXref.getDb() == null && aXref.getId() != null) {
                     		pathwayID = name + DBSOURCE_SEPARATOR + "NO_DATABASE_DEFINED" + DBSOURCE_SEPARATOR + aXref.getId();
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
				pathwayID =name + DBSOURCE_SEPARATOR + dataSource + DBSOURCE_SEPARATOR + stableID;
		}else if(stableID.equals("") && !tempID.equals("")){
            		pathwayID =name + DBSOURCE_SEPARATOR + dataSource + DBSOURCE_SEPARATOR + tempID;
        	}

        	name = (pathwayID.equalsIgnoreCase(dataSource)) ? name + DBSOURCE_SEPARATOR + dataSource + DBSOURCE_SEPARATOR + name : pathwayID;
		// outta here
		toReturn.setName(name);
		return toReturn;
	}

private EntityReference checkEntity(EntityReference range, boolean checkDatabase) {

	if (range instanceof Protein) {
		Protein aProtein = (Protein)range;
    		// we only process proteins that are same species as pathway
    		if (this.speciescheck && this.species.length() > 0 && !sameSpecies(aProtein, this.species)) {
			return null;
    		}
    		// if we are not checking database, just return rdf id
    		if (checkDatabase) {
			    for (Xref aXref : aProtein.getXref())
			    {
    				if (aXref.getDb() != null && StringUtils.containsIgnoreCase(aXref.getDb(),this.id)) {
    					return range;
    				}
    			}
    		}
    		else {
    			return range;
    		}
    	}

	else if (range instanceof ProteinReference) {
            
    		ProteinReference aProteinRef = (ProteinReference)range;
    		boolean isSameSpecies = sameSpecies(aProteinRef, this.species);
		// we only process protein refs that are same species as pathway
		if (this.speciescheck && 
				this.species.length() > 0 && !isSameSpecies) {
    			return null;
    		}
    		if (checkDatabase) {
				// short circuit if we are converting for pathway commons
				// Also ensure we get back primary accession - which is built into the rdf id of the protein  ref
				if (this.id.equalsIgnoreCase("uniprot") && aProteinRef.getUri().startsWith("urn:miriam:uniprot:")) {
					return range;
				}
				else {
					for (Xref aXref: aProteinRef.getXref()) {
						if (aXref.getDb() != null && 
							StringUtils.containsIgnoreCase(aXref.getDb(),this.id)) {
							if(!aXref.getId().equalsIgnoreCase("--") && 
								!aXref.getId().equalsIgnoreCase("---") && 
								!aXref.getId().equalsIgnoreCase("null")){
                                
							    return range;
                            }
						}
					}
				}
    		}
    		else {
    			return range;
    		}
    	}
	return null;
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

	private boolean sameSpecies(ProteinReference pRef, String taxID) {
		
		if (pRef != null && pRef.getOrganism() != null) {
			BioSource bs = pRef.getOrganism();
			if (bs.getXref() != null) {
				return (getTaxID(bs.getXref()).equals(taxID));
			}
		}
		return false;
	}
	
	private String getTaxID(Set<Xref> xrefs) {
		if(xrefs ==null) 
			System.out.println("the xrefs are null");
		for (Xref xref : xrefs) {
			//panther refers to the taxon as ncbitaxon instead of taxonomy.
			//rectome refers to the taxon as NCBI Taxonomy
			if ((xref.getDb().equalsIgnoreCase("ncbitaxon")) ||
				(xref.getDb().equalsIgnoreCase("NCBI Taxonomy")) || 
				(xref.getDb().equalsIgnoreCase("taxonomy"))) {
				return xref.getId();
			}
		}

		// outta here
		return "";
	}
}

