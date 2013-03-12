#!/opt/local/bin/perl -w
# ===========================================================================
# parse drug bank xml file to extract information for geneset files.
# ===========================================================================

#use local::lib;
#use warnings;
#use strict;

use LWP::Simple;
use XML::Parser;
use XML::Simple;	# module to parse XML data which is the response to the query
use Data::Dumper;	# module for visualization of parsed XML data; not obligatory for function but was used for development
use Text::CSV;

use Getopt::Long; # to parse the command line options.

#variables to hold command line arguments
my (
	$input_filename,
	$output_filename,
	$conversion_filename,
	$drug_type,
	$id_type,
);

GetOptions (
	"inputfile|f=s"		=> \$input_filename,
	"outputfile|o=s"	=> \$output_filename,
	"conversion|c=s"	=> \$conversion_filename,
	"drugtype|d=s"		=> \$drug_type,
	"idtype|i=s"		=> \$id_type,
);

#load in the target information from the target_links.csv file downloaded
# with the xml file.  This is the only way to get the external ids for the targets
my $conver_list = {};

my @rows;
my $csv = Text::CSV->new ( { binary => 1 } )  # should set binary attribute.
                 or die "Cannot use CSV: ".Text::CSV->error_diag ();
 
open my $fh, "<:encoding(utf8)", "$conversion_filename" or die "$conversion_filename: $!";
while ( my $row = $csv->getline( $fh ) ) {

	#ID,Name,Gene Name,GenBank Protein ID,GenBank Gene ID,UniProt ID,Uniprot Title,PDB ID,GeneCard ID,GenAtlas ID,HGNC ID,HPRD ID
	
	#for the hash the first entry on the line is hash key and the value is 
	# the entire id array
        $conver_list->{ $row->[0] } = $row ;
}

#print Dumper($conver_list);


#open the output files:
# to store the drugs and their associated targets (internal ids to the targets)
open(DRUGS, ">$output_filename") or die "Error opening $output_filename : $!\n";

$xml = new XML::Simple;

#read xml file
#set key attr to nothing or else it will by default use the name and mess up
# the array format of the xml
$data = $xml-> XMLin("$input_filename", KeyAttr => {}, ForceArray => 1);
#print DRUGS Dumper($data);

#create a hash with all the drug information (hash key is the drug id)
my $drugs = {};

#create a hash with all the genesets (hash key is the drug id)
my $genesets_genename = {};
my $genesets_uniprot = {};

#get the results

#get the drugs
#get the proper name, group, targets
foreach my $drug (@{$data->{drug}}){

	#each item in the xml is read in as an array so even though there is
	#only one drugbank id need to print the contents of the array
	my $drugbankid = (@{$drug->{'drugbank-id'}})[0];	
	my $name = (@{$drug->{name}})[0];	
	my $casnumber = (@{$drug->{'cas-number'}})[0];
       	
	#go through the groups for each drug
	# only keep the drugs that are in the user specified category
	my $include = 0;
	
	

	if(defined $drug_type){ 
		foreach my $groups (@{$drug->{groups}}){
			foreach my $group (@{$groups->{group}}){
				#print "$group\n";
				if($group eq $drug_type){
					$include = 1;
				}
			}
		}
	}
	else{
		$include = 1;
	}
	
	#if the drug is not in the user specified category then skip it
	if($include == 1){

		#add drug information to the drug hash only if it isn't already in the hash
		if(!exists($drugs -> { $drugbankid })){
			$drugs -> { $drugbankid } = [$name,$casnumber]; 
		}
	
		#go through the targets if there are any
		foreach my $targets (@{$drug->{targets}}){
			foreach my $target (@{$targets->{target}}){
			
				#add the genename to geneset
				if(exists($genesets_genename -> { $drugbankid })){
					#only add the name if the target is human,
					# there is no taxonomy information in the links file but
					# there is a uniprot name which often has the taxonomy in it
					#i.e." _HUMAN". --> this is a hack but until drugbank adds the
					# the taxid to the file this is a quick fix.
					if($conver_list->{ $target->{partner} }[6] =~ m/_HUMAN/){
						push @{ $genesets_genename -> {$drugbankid} }, $conver_list->{ $target->{partner} }[2];
					}
				}
				else{
					#only add the name if the target is human,
					# there is no taxonomy information in the links file but
					# there is a uniprot name which often has the taxonomy in it
					#i.e." _HUMAN". --> this is a hack but until drugbank adds the
					# the taxid to the file this is a quick fix.
					if($conver_list->{ $target->{partner} }[6] =~ m/_HUMAN/){

						$genesets_genename -> {$drugbankid} = [$conver_list->{ $target->{partner} }[2]];
					}
				}


				#add the uniprot to geneset
				if(exists($genesets_uniprot -> { $drugbankid })){
					#only add the name if the target is human,
					# there is no taxonomy information in the links file but
					# there is a uniprot name which often has the taxonomy in it
					#i.e." _HUMAN". --> this is a hack but until drugbank adds the
					# the taxid to the file this is a quick fix.
					if($conver_list->{ $target->{partner} }[6] =~ m/_HUMAN/){
			
						push @{ $genesets_uniprot -> {$drugbankid} }, $conver_list->{ $target->{partner} }[5];
					}
				}
				else{
					#only add the name if the target is human,
					# there is no taxonomy information in the links file but
					# there is a uniprot name which often has the taxonomy in it
					#i.e." _HUMAN". --> this is a hack but until drugbank adds the
					# the taxid to the file this is a quick fix.
					if($conver_list->{ $target->{partner} }[6] =~ m/_HUMAN/){

						$genesets_uniprot -> {$drugbankid} = [$conver_list->{ $target->{partner} }[5]];
					}
				}
			}
		}
		#print Dumper($drugs);
	}
}
#print Dumper($genesets_genename);

#print out geneset files - genenames
if($id_type eq "genename"){
	# print drugname%drugbank%drugbankid, drug name, list of genes
	 while ( my ($key, $value) = each(%$genesets_genename) ) {
		$name = $drugs->{ $key }[0];
		print DRUGS "$name\%DrugBank\%$key\t$name";
        	#get rid of any duplicates
		my %temp_hash = map { $_ => 1} @{ $genesets_genename->{ $key }};
		my @unique = keys %temp_hash;
		for $i ( 0 .. $#unique){
			print DRUGS "\t$unique[$i]";

		}
		print DRUGS "\n";
	}
}
#print out geneset files - uniprot
elsif($id_type eq "uniprot"){
	# print drugname%drugbank%drugbankid, drug name, list of genes
 	while ( my ($key, $value) = each(%$genesets_uniprot) ) {
		$name = $drugs->{ $key }[0];
		print DRUGS "$name\%DrugBank\%$key\t$name";

        	#get rid of any duplicates
		my %temp_hash = map { $_ => 1} @{ $genesets_uniprot->{ $key }};
		my @unique = keys %temp_hash;
		for $i ( 0 .. $#unique){
			print DRUGS "\t$unique[$i]";
		}
		print DRUGS "\n";
	}
}

#no valid id seleced
else{
	print "No valid ID type was selected.  Currently only support genename or uniprot options.\n";
}

sub usage {
    print <<EOF
USAGE:
    ./parseDrugBankXml.pl --input_filename|-f exc.txt --outputdir|-o outputdir --conversion|-c converion_filename.txt

DESCRIPTION:
	Given a drugbank xml file and csv file with ids for all the targets this
	program goes through the drugbank file and creates 2 geneset files, one
	with a list of all the genenames that are targets for each drug and one
	with a list of all the uniprot accessions that are target for each drug.

OPTIONS:
    
    --conversion, -c
	A csv file containing all the conversions for the targets listed 
	in the xml file.  Must be the target file associated with the drugbank
	release as they use their own internal id system.
        
    --outputfile, -o
	  The name (and path) to output the results to.  If doesn't exist a new
        file will be created. 

    --inputfile, -f
	  The name of the input file.  Should be a drugbank XML file 

    --typeid, -i
	  The type of identifier you want to extract from the file.  
	  There are two options: "genename" or "uniprot"

    --drugtype, -d
	The type of drugs you want to extract from the file.  This value
	is matched to the xml specification under groups->group.  Currently
	the available values are: 
	approved, illicit, experimental, withdrawn, nutraceutical, and small molecule
	If option is not used then all drugs are included.
EOF
}
