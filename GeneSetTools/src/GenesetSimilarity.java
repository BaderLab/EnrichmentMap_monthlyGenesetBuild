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

// $Id: GenesetSimilarity.java 570 2011-02-14 20:55:41Z risserlin $
// $LastChangedDate: 2011-02-14 15:55:41 -0500 (Mon, 14 Feb 2011) $
// $LastChangedRevision: 570 $
// $LastChangedBy: risserlin $
// $HeadURL: svn+ssh://risserlin@192.168.81.21/svn/EnrichmentMap/trunk/EnrichmentMapPlugin/src/org/baderlab/csplugins/enrichmentmap/GenesetSimilarity.java $


import java.util.HashSet;


/**
 * Created by
 * User: risserlin
 * Date: Jan 9, 2009
 * Time: 10:49:55 AM
 * <p>
 * Class representing a compraison of two gene sets (represents an edge in the network)
 *
 */
public class GenesetSimilarity {

    private String geneset1_Name;
    private String geneset2_Name;

    //currently the intereaction type is pp which actually means a protein protein interaction
    ///but there is no specification of an enrichment interaction in cytoscape.
    private String interaction_type;

    //either jaccard or overlap coeffecient, depends on statistic user specified.
    private double jaccard_coeffecient;
    private double overlap_coeffecient;
    private double hypergeom_pvalue;

    //set of genes in common to both gene sets.
    private HashSet<Integer> overlapping_genes;

    //set - with the implementattion of additional species and expresion file type
    //the similiarity can come from the first or second set of enerichments --(possibly more in future version)
    //if it is zero the similarity applies to both sets.
    private int enrichment_set;

    /**
     * Class constructor
     *
     * @param geneset1_Name - gene set 1 name
     * @param geneset2_Name - gene set 2 name
     * @param jaccard_coeffecient - jaccard coeffecient for geneset 1 and geneset 2
     * @param overlap_coeffecient - verlap coeffecient for geneset 1 and geneset 2
     *
     * @param overlapping_genes - set of genes in common to gene set 1 and gene set 2
     * @param enrichment_set - the enrichment set the similarity comes from.
     */
     public GenesetSimilarity(String geneset1_Name, String geneset2_Name, double jaccard_coeffecient,double overlap_coeffecient, String interaction_type, HashSet<Integer> overlapping_genes, int enrichment_set) {
        this.geneset1_Name = geneset1_Name;
        this.geneset2_Name = geneset2_Name;
        this.jaccard_coeffecient = jaccard_coeffecient;
        this.overlap_coeffecient = overlap_coeffecient;
        this.overlapping_genes = overlapping_genes;
        //use defaults:
        this.hypergeom_pvalue = -1.0;
        this.interaction_type = interaction_type;
        this.enrichment_set = enrichment_set;
     }


    //Getters and Setters

    public String getGeneset1_Name() {
        return geneset1_Name;
    }

    public void setGeneset1_Name(String geneset1_Name) {
        this.geneset1_Name = geneset1_Name;
    }

    public String getGeneset2_Name() {
        return geneset2_Name;
    }

    public void setGeneset2_Name(String geneset2_Name) {
        this.geneset2_Name = geneset2_Name;
    }

    /**
     * @param interaction_type - set the Interaction Type
     */
    public void setInteractionType(String interaction_type) {
        this.interaction_type = interaction_type;
    }

    /**
     * @return the Interaction Type
     */
    public String getInteractionType() {
        return interaction_type;
    }

    public double getJaccard_coeffecient() {
        return jaccard_coeffecient;
    }

    public void setJaccard_coeffecient(double jaccard_coeffecient) {
        this.jaccard_coeffecient = jaccard_coeffecient;
    }

    public double getOverlap_coeffecient() {
        return overlap_coeffecient;
    }

    public void setOverlap_coeffecient(double overlap_coeffecient) {
        this.overlap_coeffecient = overlap_coeffecient;
    }

    /**
     * @param hypergeometric_pvalue the hypergeometric_pvalue to set
     */
    public void setHypergeom_pvalue(double hypergeometric_pvalue) {
        this.hypergeom_pvalue = hypergeometric_pvalue;
    }

    /**
     * @return the hypergeometric_pvalue
     */
    public double getHypergeom_pvalue() {
        return hypergeom_pvalue;
    }

    public HashSet<Integer> getOverlapping_genes() {
        return overlapping_genes;
    }

    public void setOverlapping_genes(HashSet<Integer> overlapping_genes) {
        this.overlapping_genes = overlapping_genes;
    }

    public int getSizeOfOverlap(){
        return overlapping_genes.size();
    }

    public int getEnrichment_set() {
        return enrichment_set;
    }

    public void setEnrichment_set(int enrichment_set) {
        this.enrichment_set = enrichment_set;
    }


    public String toString(){
        return this.geneset1_Name + "\t" + this.geneset2_Name + "\t" +
                this.jaccard_coeffecient + "\t" + this.overlap_coeffecient
                + "\t" + this.overlapping_genes.size() + "\n";
    }
}
