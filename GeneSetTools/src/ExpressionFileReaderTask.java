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

// $Id: ExpressionFileReaderTask.java 371 2009-09-25 20:24:18Z risserlin $
// $LastChangedDate: 2009-09-25 16:24:18 -0400 (Fri, 25 Sep 2009) $
// $LastChangedRevision: 371 $
// $LastChangedBy: risserlin $
// $HeadURL: svn+ssh://risserlin@server1.baderlab.med.utoronto.ca/svn/EnrichmentMap/trunk/EnrichmentMapPlugin/src/org/baderlab/csplugins/enrichmentmap/ExpressionFileReaderTask.java $


import cytoscape.task.Task;
import cytoscape.task.TaskMonitor;
import cytoscape.data.readers.TextFileReader;

import java.util.HashMap;


/**
 * Created by
 * User: risserlin
 * Date: Jan 21, 2009
 * Time: 9:07:34 AM
 * <p>
 * Parse expression file.  The user can also use a rank file instead of an expression file so this class
 * also handles reading of rank files.
 */
public class ExpressionFileReaderTask implements Task {

    private GMTParameters params;

    //expression file name
    private String expressionFileName;

    //which dataset is this expression file associated with
    private int dataset;

    // Keep track of progress for monitoring:
    private int maxValue;
    private TaskMonitor taskMonitor = null;
    private boolean interrupted = false;

    /**
     * Class constructor specifying current task
     *
     * @param params- enrichment map parameters associated with current map
     * @param taskMonitor - current task monitor
     */
     public ExpressionFileReaderTask(GMTParameters params,  TaskMonitor taskMonitor) {
        this(params);
        this.taskMonitor = taskMonitor;
    }

    /**
     * Class constructor
     *
     * @param params - enrichment map parameters associated with current map
     */
    public ExpressionFileReaderTask(GMTParameters params )   {
        this.params = params;

        //expression file

            this.expressionFileName = params.getExpressionFileName1();

    }

    /**
     * Parse expression/rank file
     */
    public void parse() {

          //Need to check if the file specified as an expression file is actually a rank file
          //If it is a rank file it can either be 5 or 2 columns but it is important that the rank
          //value is extracted from the right column and placed in the expression matrix as if it
          //was an expression value in order for other features to work.

          //Also a problem with old session files that imported a rank file so it also
          //important to check if the file only has two columns.  If it only has two columns,
          //check to see if the second column is a double.  If it is then consider that column
          //expression

          boolean twoColumns = false;


        HashMap<String, Integer> datasetGenes= params.getDatasetGenes();
        HashMap genes = params.getGenes();
        HashMap hashkey2gene = params.getHashkey2gene();

        TextFileReader reader = new TextFileReader(expressionFileName);
        reader.read();
        String fullText = reader.getText();

        String[] lines = fullText.split("\n");
        int currentProgress = 0;
        maxValue = lines.length;
        GeneExpressionMatrix expressionMatrix = null;
        //GeneExpressionMatrix expressionMatrix = new GeneExpressionMatrix(lines[0].split("\t"));
        HashMap<Integer,GeneExpression> expression = new HashMap<Integer, GeneExpression>();

        for (int i = 0; i < lines.length; i++) {
            Integer genekey ;

            String line = lines[i];

            String [] tokens = line.split("\t");

            //The first column of the file is the name of the geneset
            String Name = tokens[0].toUpperCase();

            if(i==0 && expressionMatrix == null){
                //otherwise the first line is the header
                if(Name.equalsIgnoreCase("#1.2")){
                   line = lines[2];
                   i=2;
                }
                else{
                    line = lines[0];
                    //ignore all comment lines
                    int k = 0;
                    while (line.startsWith("#")){
                        k++;
                        line = lines[k];
                    }
                    i = k;
                }
                tokens = line.split("\t");

                //check to see how many columns there are
                //if there are only 2 columns then we could be dealing with a ranked file
                //check to see if the second column contains expression values.
                if(tokens.length == 2){
                    twoColumns = true;
                    //the assumption is the first line is the column names but
                    //if we are loading a GSEA edb rnk file then their might not be column names
                    try{
                        int temp = Integer.parseInt(tokens[1]);
                        i = -1;
                        tokens[0] = "Name";
                        tokens[1] = "Rank/Score";
                    } catch (NumberFormatException v){
                        try{
                            double temp2 = Double.parseDouble(tokens[1]);
                            i = -1;
                            tokens[0] = "Name";
                            tokens[1] = "Rank/Score";

                        }  catch (NumberFormatException v2){
                            //if it isn't a double or int then we have a title line.
                        }
                    }
                }

                expressionMatrix = new GeneExpressionMatrix(tokens);
                expressionMatrix.setExpressionMatrix(expression);
                continue;

            }


             //if the Gene is not in the gene list then add it
             if(!genes.containsKey(Name)){
                int value = params.getNumberOfGenes();
                genes.put(Name.toUpperCase(), value);
                hashkey2gene.put(value,Name.toUpperCase());
                params.setNumberOfGenes(value+1);
            }

            //Check to see if this gene is in the genes list
            if(genes.containsKey(Name)){
                genekey = (Integer)genes.get(Name);
                //we want the genes hashmap and dataset genes hashmap to have the same keys so it is
                //easier to compare.
                datasetGenes.put(Name, genekey);

                String description = "";
                //check to see if the second column is parseable
                if(twoColumns){
                    try{
                        Double.parseDouble(tokens[1]);
                    }catch(NumberFormatException e){
                        description = tokens[1];
                    }
                }
                else
                    description = tokens[1];

                GeneExpression expres = new GeneExpression(Name, description);
                expres.setExpression(tokens);

                double newMax = expres.newMax(expressionMatrix.getMaxExpression());
                if(newMax != -100)
                    expressionMatrix.setMaxExpression(newMax);
                double newMin = expres.newMin(expressionMatrix.getMinExpression());
                if (newMin != -100)
                    expressionMatrix.setMinExpression(newMin);

                expression.put(genekey,expres);

            }



            // Calculate Percentage.  This must be a value between 0..100.
            int percentComplete = (int) (((double) currentProgress / maxValue) * 100);
            //  Estimate Time Remaining
            long timeRemaining = maxValue - currentProgress;
            if (taskMonitor != null) {
                    taskMonitor.setPercentCompleted(percentComplete);
                    taskMonitor.setStatus("Parsing GCT file " + currentProgress + " of " + maxValue);
                    taskMonitor.setEstimatedTimeRemaining(timeRemaining);
                }
            currentProgress++;

        }

        //set the number of genes
        expressionMatrix.setNumGenes(expressionMatrix.getExpressionMatrix().size());


        params.setExpression(expressionMatrix);
        
    }

   
 /**
     * Run the Task.
     */
    public void run() {
        parse();
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
        return new String("Parsing GCT file");
    }
}
