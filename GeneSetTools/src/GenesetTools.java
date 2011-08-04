
import org.biopax.paxtools.io.SimpleIOHandler;
import org.biopax.paxtools.io.gsea.*;
import org.biopax.paxtools.model.Model;
import org.json.JSONException;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import synergizer.SynergizerClient;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.*;

/**
 * Created by
 * User: risserlin
 * Date: Nov 18, 2010
 * Time: 12:12:30 PM
 */
public class GenesetTools {


     public static void main(String[] argv) throws IOException,
             InvocationTargetException, IllegalAccessException,SQLException
    {

        if (argv.length == 0) {
            help();
        } else {
            Command.valueOf(argv[0]).run(argv);
        }
    }


    /**
     * Method to analyze a GMT file in relation to the given GCT (expression) File
     * Optional additional paramters include output file name (arg 3) and output directory (arg 4)
     *
     * Output: For each Gene in the expression File, find out how many Genesets it is in.
     * Print: GeneName <tab> Number of GeneSets in <tab> List of Genesets in.
     * @param args - array of command line arguments
     * @throws IOException
     */
     public static void compare(String args[]) throws IOException {

        CompareGMT comparator = new CompareGMT();
        CmdLineParser parser = new CmdLineParser(comparator);
        try {
           parser.parseArgument(args);
	        } catch (CmdLineException e) {
            System.err.println(e.getMessage());
            help();
            parser.printUsage(System.err);
            return;
        }
        comparator.compare();

     }


    /**
     * Method to compare two gmt files.  You can either compare the exact contents of two files
     * by comparing the genes in each genesets or just the counts in each genesets (if the identifiers)
     * don;t mathc)
     *
     * Output: for each geneset that doesn't match in the two files there is the number of and a list of unique
     * genes in gmt1 and the number of and list of genes in gmt2 followed by the number shared genes
     */
    public static void compare2gmt(String args[]) throws IOException {

            Compare2GMT comparator = new Compare2GMT();
            CmdLineParser parser = new CmdLineParser(comparator);
            try {
               parser.parseArgument(args);
                } catch (CmdLineException e) {
                System.err.println(e.getMessage());
                help();
                parser.printUsage(System.err);
                return;
            }
            comparator.compare();

         }



    /**
     * Given a gmt file, the current species, current identifier type, the desired identifier type
     * This Method translates all identifiers in each geneset to the new identifier using synergizer Java Api
     * @param args - array of command line arguments,
     * @throws IOException
     */
    public static void translate(String args[]) throws IOException {
         GeneSetTranslator translator = new GeneSetTranslator();
         CmdLineParser parser = new CmdLineParser(translator);
        try {
           parser.parseArgument(args);
	        } catch (CmdLineException e) {
            System.err.println(e.getMessage());
            help();
            parser.printUsage(System.err);
            return;
        }
        translator.translate();
    }

    /**
     * Given a biopax pathway file, output filename, Identifier type to grab, true/false species check
     * converts biopax pathway to a gene set file (gmt)
     * uses paxtools to do the conversion.
     * @param argv
     * @throws IOException
     */
    public static void toGSEA(String[] argv) throws IOException
    {

        GSEAConverter converter = new GSEAConverter();
        CmdLineParser parser = new CmdLineParser(converter);
        try {
           parser.parseArgument(argv);
	        } catch (CmdLineException e) {
            System.err.println(e.getMessage());
            help();
            parser.printUsage(System.err);
            return;
        }

        converter.toGSEA();

    }

    public static void bulk(String[] argv) throws IOException
        {

            BulkGSEAConverter converter = new BulkGSEAConverter();
            CmdLineParser parser = new CmdLineParser(converter);
            try {
               parser.parseArgument(argv);
                } catch (CmdLineException e) {
                System.err.println(e.getMessage());
                help();
                parser.printUsage(System.err);
                return;
            }

            converter.bulk();

        }


    /**
     * Given a species, branch, choice of identifier (uniprot or symbol), output file this method generates
     * a gmt file from GO (for the specified branch, bp,mf,cc or all).  GO annotations are extracted from EBI mysql
     * database of GO annotations.  (updated about once a month, each GO gmt file has a header specifying the release
     * used to create the file)
     * @param args
     * @throws IOException
     */

    public static void createGo(String args[]) throws IOException,SQLException {

        GOGeneSetFileMaker maker = new GOGeneSetFileMaker();

        CmdLineParser parser = new CmdLineParser(maker);
        try {
           parser.parseArgument(args);
	        } catch (CmdLineException e) {
            System.err.println(e.getMessage());
            help();
            parser.printUsage(System.err);
            return;
        }

        if(maker.isInfile())
            maker.parseGAF2();
        else
            maker.queryEBI();

    }

     enum Command {
        translate("fileIn fileOut species currentID newID\t\ttakes gmt file and translates all of it ids to new id", 3)
		        {public void run(String[] argv) throws IOException{translate(Arrays.copyOfRange(argv,1,argv.length));} },
        compare("GMTfile GCTfile2 outputFile Diretory\t\t\tcompares gmt file to given gct (expression file) to generate stats relating to how many genesets each gene is found in", 2)
		        {public void run(String[] argv) throws IOException{compare(Arrays.copyOfRange(argv,1,argv.length));} },
        createGo("Species Branch File", 3)
                {public void run(String[] argv) throws IOException,SQLException{createGo(Arrays.copyOfRange(argv,1,argv.length));} },
        toGSEA("owl_filename outfile id speciescheck\t\t\t convert biopax file to gmt file",4)
                {public void run(String[] argv) throws IOException{toGSEA(Arrays.copyOfRange(argv,1,argv.length));} },
        bulk("owldir outdir id speciescheck\t\t\t converts all biopax files in a directory to gmt files",4)
                { public void run(String[] argv) throws IOException{bulk(Arrays.copyOfRange(argv,1,argv.length));} },
        compare2gmt("gmt1 gmt2 outfile dir cmd \t\t\t compares the contents or counts of two gmt files",5)
                { public void run(String[] argv) throws IOException{compare2gmt(Arrays.copyOfRange(argv,1,argv.length));} },
        help("\t\t\t\t\t\tprints this screen and exits", Integer.MAX_VALUE)
		        {public void run(String[] argv) throws IOException{help();} };


        String description;
        int params;

        Command(String description, int params) {
            this.description = description;
            this.params = params;
        }

        public abstract void run(String[] argv) throws IOException,SQLException;
    }

    static void help() {

        System.out.println("Available operations:");
        for (Command cmd : Command.values()) {
            System.out.println(cmd.name() + " : " + cmd.description);
        }

    }
}
