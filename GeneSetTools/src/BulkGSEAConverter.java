import org.kohsuke.args4j.Option;

import java.io.File;
import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: risserlin
 * Date: 11-07-28
 * Time: 3:37 PM
 * To change this template use File | Settings | File Templates.
 */
public class BulkGSEAConverter {
    @Option(name = "--biopax", usage = "name of biopax directory to convert", required = true)
    private String owl_dirname;

    @Option(name = "--out", usage = "name of output directory", required = true)
    private String out_dirname;

     @Option(name = "--id", usage = "type of id grab from the biopax file", required = true)
    private String id;

    @Option(name = "--speciescheck", usage = "TRUE/FALSE - check that all ids are from one species", required = true)
    private String speciescheck;


    public void bulk() throws IOException{
        File current = new File(owl_dirname);
        File outdir = new File(out_dirname);
        String current_filename;
        String current_outfilename;
        //if this is a directory - go into it and get the .owl file
        if(current.isDirectory()){
            String[] children = current.list();
            for(int k=0;k<children.length;k++){
                if(children[k].endsWith(".owl")){ //if this is an owl file, process it.
                    current_filename = current.getAbsolutePath() + File.separator + children[k];

                    //create gsea file using Uniprot identifiers
                    id="UniProt";
                    current_outfilename = outdir.getAbsolutePath() + File.separator + children[k].split(".owl")[0] + "_uniprot.gmt";
                    GSEAConverter converter = new GSEAConverter(current_filename,current_outfilename,id,speciescheck);
                    converter.toGSEA();

                    //create gsea file using Entrez gene identifiers
                    id="Entrez Gene";
                    current_outfilename = outdir.getAbsolutePath() + File.separator + children[k].split(".owl")[0] +"_eg.gmt";
                    converter = new GSEAConverter(current_filename,current_outfilename,id,speciescheck);
                    converter.toGSEA();

                    //convert entrez gene file to symbols
                    String translated_filename = outdir.getAbsolutePath() + File.separator + children[k].split(".owl")[0] +"_symbol.gmt";
                    GeneSetTranslator translator = new GeneSetTranslator(current_outfilename,translated_filename,"Homo Sapiens", "entrezgene","hgnc_symbol");
                    translator.translate();

                }
            }
        }
    }
}
