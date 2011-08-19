import cytoscape.data.readers.TextFileReader;
import org.kohsuke.args4j.Option;

/**
 * Created by IntelliJ IDEA.
 * User: risserlin
 * Date: 11-08-15
 * Time: 4:39 PM
 * To change this template use File | Settings | File Templates.
 */
public class GeneSetTaxidConverter {

     @Option(name = "--gmt", usage = "name of gmt file to convert to new species", required = true)
    private String gmt_filename;

    @Option(name = "--homologys", usage = "name of file from homologene with all homolog conversions", required = true)
    private String homolog_file;

    @Option(name = "--newtaxid", usage = "taxid to convert gmt file to.", required = true)
    private Integer newtaxid;

    @Option(name = "--outfile", usage = "name of output file", required = true)
    private String outfile;


    public void taxidconverter(){
        //load in the gmt file.
        //create parameters
         GMTParameters params = new GMTParameters();

         //set file names
         params.setGMTFileName(gmt_filename);

         //parse gmt
         //Load in the GMT file
         try{
            //Load the geneset file
            GMTFileReaderTask gmtFile = new GMTFileReaderTask(params,1);
            gmtFile.run();

         } catch (OutOfMemoryError e) {
             System.out.println("Out of Memory. Please increase memory allotement for cytoscape.");
             return;
         }  catch(Exception e){
             System.out.println("unable to load GMT file");
             return;
         }

        //open homolog file
        if(homolog_file != null || !homolog_file.equalsIgnoreCase("")){
            TextFileReader reader = new TextFileReader(homolog_file);
            reader.read();
            String fullText = reader.getText();

            String []lines = fullText.split("\n");

            for (int i = 0; i < lines.length; i++) {

               String line = lines[i];
               String[] tokens = line.split("\t");

                //there should be 6 fields on everyline
                if(tokens.length == 6){
                    Integer homologGroup = Integer.parseInt(tokens[0]);
                    Integer taxid= Integer.parseInt(tokens[1]);
                    Integer entrezgeneid= Integer.parseInt(tokens[2]);
                    String symbol= tokens[3];
                    Integer gi= Integer.parseInt(tokens[4]);
                    String accession= tokens[5];


                }

            }
        }

    }
}
