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

// $Id: ComputeSimilarityTask.java 579 2011-06-02 20:12:11Z risserlin $
// $LastChangedDate: 2011-06-02 16:12:11 -0400 (Thu, 02 Jun 2011) $
// $LastChangedRevision: 579 $
// $LastChangedBy: risserlin $
// $HeadURL: svn+ssh://risserlin@192.168.81.21/svn/EnrichmentMap/trunk/EnrichmentMapPlugin/src/org/baderlab/csplugins/enrichmentmap/ComputeSimilarityTask.java $

import cytoscape.logger.CyLogger;
import cytoscape.task.Task;
import cytoscape.task.TaskMonitor;

import java.util.*;

/**
 * Created by
 * User: risserlin
 * Date: Jan 9, 2009
 * Time: 2:14:52 PM
 * <p>
 * Goes through all the gene sets and computes the jaccard and overlap coeffecient
 * for each
 * pair of gene sets.  (all pairwise comparisons are performed)
*/
public class ComputeSimilarityTask implements Task {
    static final int ENRICHMENT = 0, SIGNATURE = 1;

    private GMTParameters params;
    private int type;

    //Hash map of the geneset_similarities computed that pass the cutoff.
    private HashMap<String, GenesetSimilarity> geneset_similarities;

    // Keep track of progress for monitoring:
    private TaskMonitor taskMonitor = null;
    private boolean interrupted = false;
    
    private CyLogger logger = CyLogger.getLogger(ComputeSimilarityTask.class);

    /**
     * Constructor for Compute Similarity task
     *
     * @param params - enrichment map parameters for current map
     * @param taskMonitor - task monitor if it has already been set.
     */
    public ComputeSimilarityTask(GMTParameters params, TaskMonitor taskMonitor) {
          this(params);
          this.taskMonitor = taskMonitor;
      }


    public ComputeSimilarityTask(GMTParameters params) {
        this.params = params;
        this.geneset_similarities = new HashMap<String, GenesetSimilarity>();
        this.type = 0;
    }

    public ComputeSimilarityTask(GMTParameters params, int type) {
        this.params = params;
        this.geneset_similarities = new HashMap<String, GenesetSimilarity>();
        this.type = type;
    }    
    
    public boolean computeGenesetSimilarities(){
        try{
            HashMap genesetsOfInterest = params.getGenesets();
            HashMap genesetsInnerLoop;
            String edgeType = "pp";

            genesetsInnerLoop = genesetsOfInterest;
            
            int currentProgress = 0;
            int maxValue = genesetsOfInterest.size();

            //figure out if we need to compute edges for two different expression sets or one.
            int enrichment_set =0;

            //iterate through the each of the GSEA Results of interest
            for(Iterator i = genesetsOfInterest.keySet().iterator(); i.hasNext(); ){

                 // Calculate Percentage.  This must be a value between 0..100.
                int percentComplete = (int) (((double) currentProgress / maxValue) * 100);
                //  Estimate Time Remaining
                long timeRemaining = maxValue - currentProgress;
                if (taskMonitor != null) {
                   taskMonitor.setPercentCompleted(percentComplete);
                   taskMonitor.setStatus("Computing Geneset similarity " + currentProgress + " of " + maxValue);
                   taskMonitor.setEstimatedTimeRemaining(timeRemaining);
                }
                currentProgress++;

                String geneset1_name = i.next().toString();
                //for each individual geneset compute its jaccard index with all other genesets
                 for(Iterator j = genesetsInnerLoop.keySet().iterator(); j.hasNext(); ){

                    String geneset2_name = j.next().toString();

                    //Check to see if this comparison has been done
                     //The key for the set of geneset similarities is the
                     //combination of the two names.  Check for either variation name1_name2
                     //or name2_name1
                     String similarity_key1;
                     String similarity_key2;

                     similarity_key1 = geneset1_name + " ("+ edgeType  + ") " + geneset2_name;
                     similarity_key2 = geneset2_name + " ("+ edgeType  + ") " + geneset1_name;



                     //first check to see if the terms are the same
                     if(geneset1_name.equalsIgnoreCase(geneset2_name)){
                        //don't compare two identical genesets
                     }
                     else if(geneset_similarities.containsKey(similarity_key1) || geneset_similarities.containsKey(similarity_key2)){
                         //skip this geneset comparison.  It has already been done.
                     }
                     else{
                         //get the two genesets
                         GeneSet geneset1 = (GeneSet)genesetsOfInterest.get(geneset1_name);
                         GeneSet geneset2 = (GeneSet)genesetsOfInterest.get(geneset2_name);


                         HashSet<Integer> genes1 = geneset1.getGenes();
                         HashSet<Integer> genes2 = geneset2.getGenes();

                        //Get the intersection
                         Set<Integer> intersection = new HashSet<Integer>(genes1);
                         intersection.retainAll(genes2);

                         //Get the union of the two sets
                         Set<Integer> union = new HashSet<Integer>(genes1);
                         union.addAll(genes2);



                            //compute Jaccard similarity
                         double jaccard_coeffecient = (double)intersection.size() / (double)union.size();

                         double overlap_coeffecient = (double)intersection.size() / Math.min((double)genes1.size(), (double)genes2.size());

                         /*else { //else it must be combined
                             //Compute a combination of the overlap and jaccard coefecient
                             //we need both the Jaccard and the Overlap
                             double jaccard = (double)intersection.size() / (double)union.size();
                             double overlap = (double)intersection.size() / Math.min((double)genes1.size(), (double)genes2.size());

                             double k = params.getCombinedConstant();

                             coeffecient = (k * overlap) + ((1-k) * jaccard);

                         } */

                         //create Geneset similarity object
                         GenesetSimilarity comparison = new GenesetSimilarity(geneset1_name,geneset2_name, jaccard_coeffecient,overlap_coeffecient, "pp" ,(HashSet<Integer>)intersection,enrichment_set);


                         geneset_similarities.put(similarity_key1,comparison);


                     }
                 }



            }




        } catch(IllegalThreadStateException e){
            taskMonitor.setException(e, "Unable to compute similarity coeffecients");
            return false;
        }

       return true;
    }

    public HashMap<String, GenesetSimilarity> getGeneset_similarities() {
        return geneset_similarities;
    }

    /**
       * Run the Task.
       */
      public void run() {
         computeGenesetSimilarities();
      }

      /**
       * Non-blocking call to interrupt the task.
       */
      public void halt() {
          this.interrupted = true;
      }

       /**
       * Sets the Task Monitor.
       *
       * @param taskMonitor TaskMonitor Object.
       */
      public void setTaskMonitor(TaskMonitor taskMonitor) {
          if (this.taskMonitor != null) {
              throw new IllegalStateException("Task Monitor is already set.");
          }
          this.taskMonitor = taskMonitor;
      }

      /**
       * Gets the Task Title.
       *
       * @return human readable task title.
       */
      public String getTitle() {
          return new String("Computing geneset similarities");
      }

}
