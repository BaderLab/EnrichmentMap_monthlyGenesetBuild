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
                    Biopax2GMT converter = new Biopax2GMT(current_filename,current_outfilename,id,speciescheck);
                    converter.toGSEA();

                    //create gsea file using Entrez gene identifiers
                    id="Entrez Gene";
                    current_outfilename = outdir.getAbsolutePath() + File.separator + children[k].split(".owl")[0] +"_eg.gmt";
                    converter = new Biopax2GMT(current_filename,current_outfilename,id,speciescheck);
                    converter.toGSEA();

                    //convert entrez gene file to symbols
                    String translated_filename = outdir.getAbsolutePath() + File.separator + children[k].split(".owl")[0] +"_symbol.gmt";
                    GeneSetTranslator translator = new GeneSetTranslator(current_outfilename,9606, "entrezgene");
                    translator.translate();

                }
            }
        }
/*
        //create all the GO subsets
        //human - mf
        current_outfilename = outdir.getAbsolutePath() + File.separator +"GOMF_human_symbol.gmt";
        GOGeneSetFileMaker gomaker = new GOGeneSetFileMaker(9606,"mf",current_outfilename,"symbol");
        gomaker.queryEBI();

        //translate
        //convert symbols to entrez gene ids
         GeneSetTranslator translator = new GeneSetTranslator(current_outfilename,9606, "symbol");
        translator.translate();

        //try convert the uniprots
        current_outfilename = outdir.getAbsolutePath() + File.separator +"GOMF_human_uniprot.gmt";
        gomaker = new GOGeneSetFileMaker(9606,"mf",current_outfilename,"uniprot");
        gomaker.queryEBI();

        translator = new GeneSetTranslator(current_outfilename,9606, "uniprot");
        translator.translate();

        //human - bp uniprot
        current_outfilename = outdir.getAbsolutePath() + File.separator +"GOBP_human_uniprot.gmt";
        gomaker = new GOGeneSetFileMaker(9606,"bp",current_outfilename,"uniprot");
        gomaker.queryEBI();
        translator = new GeneSetTranslator(current_outfilename,9606, "uniprot");
        translator.translate();

        //human - bp symbols
        current_outfilename = outdir.getAbsolutePath() + File.separator +"GOBP_human_symbol.gmt";
        gomaker = new GOGeneSetFileMaker(9606,"bp",current_outfilename,"symbol");
        gomaker.queryEBI();
        translator = new GeneSetTranslator(current_outfilename,9606, "symbol");
        translator.translate();

        //human - cc uniprots
        current_outfilename = outdir.getAbsolutePath() + File.separator +"GOCC_human_uniprot.gmt";
        gomaker = new GOGeneSetFileMaker(9606,"cc",current_outfilename,"uniprot");
        gomaker.queryEBI();
        translator = new GeneSetTranslator(current_outfilename,9606, "uniprot");
        translator.translate();

        //human - cc symbol
        current_outfilename = outdir.getAbsolutePath() + File.separator +"GOCC_human_symbol.gmt";
        gomaker = new GOGeneSetFileMaker(9606,"cc",current_outfilename,"symbol");
        gomaker.queryEBI();
        translator = new GeneSetTranslator(current_outfilename,9606, "symbol");
        translator.translate();


        //mouse - mf
        current_outfilename = outdir.getAbsolutePath() + File.separator +"GOMF_mouse_symbol.gmt";
        gomaker = new GOGeneSetFileMaker(10090,"mf",current_outfilename,"symbol");
        gomaker.queryEBI();

        //translate
        //convert symbols to entrez gene ids
        translator = new GeneSetTranslator(current_outfilename,10090, "symbol");
        translator.translate();

        //mouse - bp
        current_outfilename = outdir.getAbsolutePath() + File.separator +"GOBP_mouse_symbol.gmt";
        gomaker = new GOGeneSetFileMaker(10090,"bp",current_outfilename,"symbol");
        gomaker.queryEBI();

        //translate
        //convert symbols to entrez gene ids
        translator = new GeneSetTranslator(current_outfilename,10090, "symbol");
        translator.translate();

        //mouse - cc
        current_outfilename = outdir.getAbsolutePath() + File.separator +"GOCC_mouse_symbol.gmt";
        gomaker = new GOGeneSetFileMaker(10090,"cc",current_outfilename,"symbol");
        gomaker.queryEBI();

        //translate
        //convert symbols to entrez gene ids
        translator = new GeneSetTranslator(current_outfilename,10090, "symbol");
        translator.translate();
*/
    }
}
