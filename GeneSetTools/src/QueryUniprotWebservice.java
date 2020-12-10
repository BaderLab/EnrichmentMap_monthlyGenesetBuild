import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.logging.Logger;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Set;
import java.util.Arrays;

public class QueryUniprotWebservice
{
  private static final String UNIPROT_SERVER = "https://www.uniprot.org/";
  private static final Logger LOG = Logger.getAnonymousLogger();


  private String oldID_index;
  private String newID_index;
  private int organism;
  private HashSet<String> ids;

  private ParameterNameValue[] params;

//One of the IDs need to be uniprot.  You can't convert symbol -> entrezgene with this service
//In order to get that conversion you have to convert symbol -> uniport -> entrezgene
 public QueryUniprotWebservice(String oldID, String newID, HashSet<String> ids, int organism){
 	
	if(oldID.equalsIgnoreCase("uniprot"))
		this.oldID_index = "ACC";
	else if(oldID.equalsIgnoreCase("entrezgene"))
		this.oldID_index = "P_ENTREZGENEID";
	else if(oldID.equalsIgnoreCase("symbol"))
		this.oldID_index = "GENENAME";
	else
		System.out.println("No match for old id");
	
	if(newID.equalsIgnoreCase("uniprot"))
		this.newID_index = "ACC";
	else if(newID.equalsIgnoreCase("entrezgene"))
		this.newID_index = "P_ENTREZGENEID";
	else if(newID.equalsIgnoreCase("symbol"))
		this.newID_index = "GENENAME";
	else
		System.out.println("No match for new id");
	
	this.ids = ids;
	this.organism = organism;

 }
 
  public HashMap<String, Set<String>> runQuery(String tool)
    throws Exception
  {


	
	if(!this.newID_index.equals("ACC") && !this.oldID_index.equals("ACC")){
		System.out.println("You need one identifier to be uniprot accesions orelse web service wont' work");
		return null; 
	}
	HashMap<String, Set<String>> convertedIds = new HashMap<String, Set<String>>();
	
	  //Uniprot server will max out but can't find max query.  - batch by 500s
	//convert Hashset into an array
	String[] ids_batch = new String[ids.size()];
	ids.toArray(ids_batch);

	int start = 0;
	int end = 500;
	if(ids_batch.length < 500)
		end = ids_batch.length;
	while(start<ids_batch.length){

		String[] current_ids = Arrays.copyOfRange(ids_batch,start,end);
	//	System.out.println("Converting:" + String.join(" ", current_ids));
		//update numbers for next round
		start = end + 1;
		if(start > ids_batch.length)
			end = ids_batch.length -1;
		else
			end = end + 500;

    		this.params =  new ParameterNameValue[] {
      			new ParameterNameValue("from", this.oldID_index),
      			new ParameterNameValue("to", this.newID_index),
			new ParameterNameValue("format", "tab"),
			new ParameterNameValue("Organism", Integer.toString(this.organism)),
			new ParameterNameValue("query", String.join(" ", current_ids)),
    		};
	
   	
	  	StringBuilder locationBuilder = new StringBuilder(UNIPROT_SERVER + tool + "/?");
    		for (int i = 0; i < this.params.length; i++){
      			if (i > 0)
        			locationBuilder.append('&');
      				locationBuilder.append(this.params[i].name).append('=').append(this.params[i].value);
    			}
    			String location = locationBuilder.toString();
    			URL url = new URL(location);
   			LOG.info("Submitting...");
    			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    			HttpURLConnection.setFollowRedirects(true);
    			conn.setDoInput(true);
    			conn.connect();

    			int status = conn.getResponseCode();
    			while (true){
      				int wait = 0;
      				String header = conn.getHeaderField("Retry-After");
      				if (header != null)
        				wait = Integer.valueOf(header);
      				if (wait == 0)
        				break;
      				LOG.info("Waiting (" + wait + ")...");
      				conn.disconnect();
      				Thread.sleep(wait * 1000);
      				conn = (HttpURLConnection) new URL(location).openConnection();
      				conn.setDoInput(true);
      				conn.connect();
      				status = conn.getResponseCode();
    			}
    	
			if (status == HttpURLConnection.HTTP_OK){
      				LOG.info("Got a OK reply");
      				InputStream reader = conn.getInputStream();
      				URLConnection.guessContentTypeFromStream(reader);
      				StringBuilder builder = new StringBuilder();
      				int a = 0;
      				while ((a = reader.read()) != -1){
        				builder.append((char) a);
      				}
      				//System.out.println(builder.toString());

				//Split the returned string by new lines to get all the conversion
				String[] conversions = builder.toString().split("\n");
				//go through each conversion
				for(int i = 0 ; i<conversions.length; i++){
					//System.out.println(conversions[i]);
					//split the lines by white space not sure how much
					String[] mapping = conversions[i].split("\\s+");
					//if the length is not 2 then there are more than 2 ids. 
					if(mapping.length != 2){
						System.out.println("There are multiple ids in line:" + conversions[i]);
					} else{
						convertedIds.put(mapping[0], new HashSet<>(Arrays.asList(mapping[1])));
						//System.out.println("Adding:" + mapping[0] + " maps to " + mapping[1]); 
					}
				}

    			} else
      				LOG.severe("Failed, got " + conn.getResponseMessage() + " for " + location);
    			conn.disconnect();
	}//end of while loop for batches. 

    	return convertedIds;
  }

  private class ParameterNameValue
  {
    private final String name;
    private final String value;

    public ParameterNameValue(String name, String value)
      throws UnsupportedEncodingException
    {
      this.name = URLEncoder.encode(name, "UTF-8");
      this.value = URLEncoder.encode(value, "UTF-8");
    }
  }
}
