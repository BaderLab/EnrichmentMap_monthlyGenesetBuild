/**
 **                       EnrichmentMap Cytoscape Plugin
 **
 ** Copyright (c) 2008-2009 Bader Lab, Donnelly Centre for Cellular and Biomolecular 
 ** Research, University of Toronto
 **
 ** Contact: http://www.baderlab.org
 **
 ** Code written by: Ruth Isserlin
 ** Authors: Daniele Merico, Ruth Isserlin, Oliver Stueker, Gary D. Bader
 **
 ** This library is free software; you can redistribute it and/or modify it
 ** under the terms of the GNU Lesser General Public License as published
 ** by the Free Software Foundation; either version 2.1 of the License, or
 ** (at your option) any later version.
 **
 ** This library is distributed in the hope that it will be useful, but
 ** WITHOUT ANY WARRANTY, WITHOUT EVEN THE IMPLIED WARRANTY OF
 ** MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE.  The software and
 ** documentation provided hereunder is on an "as is" basis, and
 ** University of Toronto
 ** has no obligations to provide maintenance, support, updates, 
 ** enhancements or modifications.  In no event shall the
 ** University of Toronto
 ** be liable to any party for direct, indirect, special,
 ** incidental or consequential damages, including lost profits, arising
 ** out of the use of this software and its documentation, even if
 ** University of Toronto
 ** has been advised of the possibility of such damage.  
 ** See the GNU Lesser General Public License for more details.
 **
 ** You should have received a copy of the GNU Lesser General Public License
 ** along with this library; if not, write to the Free Software Foundation,
 ** Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.
 **
 **/

// $Id: GeneSet.java 421 2009-11-13 19:51:24Z revilo $
// $LastChangedDate: 2009-11-13 14:51:24 -0500 (Fri, 13 Nov 2009) $
// $LastChangedRevision: 421 $
// $LastChangedBy: revilo $
// $HeadURL: svn+ssh://risserlin@server1.baderlab.med.utoronto.ca/svn/EnrichmentMap/trunk/EnrichmentMapPlugin/src/org/baderlab/csplugins/enrichmentmap/GeneSet.java $


import java.util.*;


/**
 * Created by
 * User: risserlin
 * Date: Jan 8, 2009
 * Time: 11:32:40 AM
 * <p>
 * Class representing Geneset Object <br>
 * Each Geneset consists of: <br>
 * Name <br>
 * Description <br>
 * A list of genes in the geneset (represented using a HashSet) - as genes are read in they are
 * converted into an integer and stored in global unique hashmap in the enrichment map paramters.  any subsequent
 * use of the gene is stored as its integer hashkey.
 */

public class GeneSet {

    //Gene set name
    private String Name;
    //Gene set description
    private String Description;
    //genes associated with this gene set
    private HashSet<Integer> genes = null;

    //used by paxtools gmt creation
    private String taxID;
    private String datasource;
    private Map<String,String> rdfToGenes;


    /**
     * Class Constructor - creates gene set with a specified name and description with an empty
     * list of genes.
     *
     * @param name - gene set name
     * @param descrip - gene set description
     */
    public GeneSet(String name, String descrip) {
        this.Name = name;
        this.Description = descrip;

        genes = new HashSet<Integer>();

    }

    //blank constructor needed for paxtools GSEA converter
    public GeneSet(){

    }

    /**
     * Class constructor - parse the string tokenized line of a session file representation of a GMT
     * file into a gene set object.  (in the original gmt file the gene set is specified followed by
     * the list of genes, in a session file the genes are converted to their hash keys.)
     *
     * @param tokens - string tokenized line for an GMT file.
     */
    public GeneSet(String[] tokens){
        this(tokens[1],tokens[2]);

        if(tokens.length<3)
            return;

        for(int i = 3; i < tokens.length;i++)
            this.genes.add(Integer.parseInt(tokens[i]));

    }

    /* Given a Hashkey
    *
    */

    /**
     * Add the gene hashkey to the set of genes
     *
     * @param gene_hashkey - a new gene hashkey to add to current geneset
     * @return true if it was successfully added, false otherwise.
     */
    public boolean addGene(int gene_hashkey){
        if(genes != null){
            return genes.add(gene_hashkey);
        }
        else{
            return false;
        }
    }

    public void addGeneList(String[] genelist, GMTParameters params){

        HashMap<String, Integer> CurrentMappings = params.getGenes();
        HashMap<Integer, String> OppositeMappings = params.getHashkey2gene();

        //only go through the lines that have at least a gene set name and description.
        if(genelist.length >= 1){


        //All subsequent fields in the list are the geneset associated with this geneset.
            for (int j = 0; j < genelist.length; j++) {

                //Check to see if the gene is already in the hashmap of genes
                //if it is already in the hash then get its associated key and put it
                //into the set of genes
                if (CurrentMappings.containsKey(genelist[j])) {
                    this.addGene(CurrentMappings.get(genelist[j]));
                }

                //If the gene is not in the list then get the next value to be used and put it in the list
                else{
                    //add the gene to the master list of genes
                    int value = params.getNumberOfGenes();
                    CurrentMappings.put(genelist[j], value);
                    OppositeMappings.put(value,genelist[j]);
                    params.setNumberOfGenes(value+1);

                    //add the gene to the genelist
                    this.addGene(CurrentMappings.get(genelist[j]));
                }
            }


        }


    }


    //Getters and Setters

    public String getName() {
        return Name;
    }

    public void setName(String name) {
        Name = name;
    }

    public String getDescription() {
        return Description;
    }

    public void setDescription(String description) {
        Description = description;
    }

    public HashSet<Integer> getGenes() {
        return genes;
    }

    public void setGenes(HashSet<Integer> genes) {
        this.genes = genes;
    }


    public String toStringNames(GMTParameters params){
       StringBuffer geneset = new StringBuffer();
       HashMap<Integer,String> mappings = params.getHashkey2gene();
        geneset.append(Name + "\t" + Description + "\t");

        for(Iterator i = genes.iterator(); i.hasNext();){
            Integer currentkey = (Integer)i.next();
            if(mappings.containsKey(currentkey))
                geneset.append( mappings.get(currentkey) + "\t");
        }
        return geneset.toString();
    }

    public String toString(){
        StringBuffer geneset = new StringBuffer();

        geneset.append(Name + "\t" + Description + "\t");

        for(Iterator i = genes.iterator(); i.hasNext();)
            geneset.append( i.next().toString() + "\t");

        return geneset.toString();
    }

    //functions needed by paxtools implementation of Genesets
    public String getTaxID() {
    	return taxID;
    }

    public void setTaxID(String taxID) {
    	this.taxID = taxID;
    }

    public String getDataSource() {
        return datasource;
    }

    public void setDataSource(String datasource) {
        this.datasource = datasource;
    }

    public Map<String, String> getRDFToGeneMap() {
        return rdfToGenes;
    }

    public void setRDFToGeneMap(Map<String, String> rdfToGenes) {
    	this.rdfToGenes = rdfToGenes;
    }

    public Collection<String> getrdfGenes() {
    	return (rdfToGenes != null) ? rdfToGenes.values() : new HashSet<String>();
    }

    public String rdftoString() {

    	String toReturn = "";
    	if (Name != null && datasource != null && rdfToGenes != null) {
    		toReturn = Name + "\t" + datasource;
    		for (String gene : rdfToGenes.values()) {
    			toReturn += "\t" + gene;
    		}
    	}

    	// outta here
        return toReturn;
    }


}
