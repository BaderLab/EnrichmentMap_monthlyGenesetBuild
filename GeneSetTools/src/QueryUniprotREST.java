import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.*;
import java.util.Iterator;
import java.io.IOException;
import java.net.http.HttpHeaders;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.logging.Logger;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Set;
import java.util.Arrays;

public class QueryUniprotREST
{
  private static final String UNIPROT_SERVER = "https://rest.uniprot.org/idmapping";
  private static final Logger LOG = Logger.getAnonymousLogger();

  private static final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            //.connectTimeout(Duration.ofSeconds(10))
            .build();

  private String oldID_index;
  private String newID_index;
  private int organism;
  private HashSet<String> ids;

  private ParameterNameValue[] params;

//One of the IDs need to be uniprot.  You can't convert symbol -> entrezgene with this service
//In order to get that conversion you have to convert symbol -> uniport -> entrezgene
 public QueryUniprotREST(String oldID, String newID, HashSet<String> ids, int organism){
 
 	//With the updated REST api for some reason when converting from Uniprot
	//we need to specify the type as UniProtKB_AC-ID but then we are converting to 
	//Uniprot then it needs to be specified as UniProtKB	 
	if(oldID.equalsIgnoreCase("uniprot"))
		this.oldID_index = "UniProtKB_AC-ID";
	else if(oldID.equalsIgnoreCase("entrezgene"))
		this.oldID_index = "GeneID";
	else if(oldID.equalsIgnoreCase("symbol"))
		this.oldID_index = "Gene_Name";
	else if(oldID.equalsIgnoreCase("ensembl"))
		this.oldID_index = "Ensembl";
	else
		System.out.println("No match for old id");
	
	if(newID.equalsIgnoreCase("uniprot"))
		this.newID_index = "UniProtKB";
	else if(newID.equalsIgnoreCase("entrezgene"))
		this.newID_index = "GeneID";
	else if(newID.equalsIgnoreCase("symbol"))
		this.newID_index = "Gene_Name";
	else if(newID.equalsIgnoreCase("ensembl"))
		this.newID_index = "Ensembl";
	else
		System.out.println("No match for new id");
	
	this.ids = ids;
	this.organism = organism;

 }

  
  public HashMap<String,Set<String>>  getJobResults(String[] jobIds) throws IOException, InterruptedException, Exception{
	
	  
	  HashMap<String, Set<String>> convertedIds = new HashMap<String, Set<String>>();
  
	for (String j: jobIds) {   
		boolean more_results = true;
		//get the results
		if(check_job_status(j)){
			//System.out.println("Current job id:" + j);

			//get the results
			StringBuilder Uniprot_Results_URI = new StringBuilder(UNIPROT_SERVER + "/results/" + j );
			
			while(more_results){
				HttpRequest results_request = HttpRequest.newBuilder()
                		.GET()
                		.uri(URI.create(Uniprot_Results_URI.toString()))
                		.setHeader("User-Agent", "Java 11 HttpClient Bot") // add request header
				.build();

        			HttpResponse<String> results_response = httpClient.send(results_request, HttpResponse.BodyHandlers.ofString());

        			//System.out.println(results_response.body());
				
				//if there are more than 25 results then the header will contain the link
				//to the next page of results
        			// print response headers
        			HttpHeaders headers = results_response.headers();
       				//headers.map().forEach((k, v) -> System.out.println(k + ":" + v));
				if(headers.map().containsKey("link")){
					String next_url = (headers.map().get("link")).iterator().next();
					
					//the url has ;rel="next" at the end that needs to be parsed off
					//and the url is surronded by ">"
					String next_url_split = next_url.split("; rel=",2)[0].trim().replace(">","").replace("<","");	
					Uniprot_Results_URI = new StringBuilder(next_url_split);
				}else{
					more_results = false;
				}
				// parsing file "JSONExample.json"
        			Object result_obj = new JSONParser().parse(results_response.body());
          
        			// typecasting obj to JSONObject
        			JSONObject result_jo = (JSONObject) result_obj;	
		
				// getting results
        			JSONArray ja = (JSONArray) result_jo.get("results");

        			// iterating results
        			Iterator itr2 = ja.iterator();

        			while (itr2.hasNext()){
					//each result has a from and to
					String from = "";
					String to = "";
            				Iterator<Map.Entry> itr1 = ((Map) itr2.next()).entrySet().iterator();
            				while (itr1.hasNext()) {
                				Map.Entry pair = itr1.next();
						if(pair.getKey().toString().equalsIgnoreCase("from"))
							from = pair.getValue().toString();
						else if(pair.getKey().toString().equalsIgnoreCase("to"))
							to = pair.getValue().toString();
						else
							System.out.println("Unrecognized Key value");
                			//System.out.println(pair.getKey() + " : " + pair.getValue());
            				}

					String[] mapping = to.split("\\s+");
					//if the length is not 1 then there are more than 1 ids. 
					if(mapping.length != 1){
						System.out.println("There are multiple ids in line:" + to +  "(we were trying to convert " + from + ")");
					} else{
						//check for ensembl because they are returned with version numbers - remove the version number
						if(this.newID_index.equals("Ensembl")){
							String[] ensembl_id_version = to.split("\\.");
							//System.out.println("we are trying to get rid of versions - " + ensembl_id_version);
							convertedIds.put(from, new HashSet<>(Arrays.asList(ensembl_id_version[0])));
						} else
							convertedIds.put(from, new HashSet<>(Arrays.asList(to)));
						//System.out.println("Adding:" + from + " maps to " + to); 
					}	
				
                			//System.out.println(from + " = " + to);
        			}
			}//while more results
		}else{
			System.out.println("Unable to get Finished status from job:" + j);
		}
	}//job id for loop
  	return convertedIds;
  }
  
  public String[] submitJob() throws IOException, InterruptedException, Exception{

	if(!this.newID_index.contains("UniProtKB") && !this.oldID_index.contains("UniProtKB")){
		System.out.println("You need one identifier to be uniprot accesions orelse web service wont' work");
		return null; 
	}
	
	//how many jobs is it going to be divided into
	
	String[] jobIds = new String[((int)Math.ceil(ids.size()/1000)+1)];
	int i = 0;

	//Uniprot server will max out but can't find max query.  - batch by 10,000s
	//convert Hashset into an array
	String[] ids_batch = new String[ids.size()];
	ids.toArray(ids_batch);
	System.out.println("converting " + ids_batch.length + " ids in total");
	int start = 0;
	int end = 1000;
	if(ids_batch.length < 1000)
		end = ids_batch.length;
	while(start<ids_batch.length){

		String[] current_ids = Arrays.copyOfRange(ids_batch,start,end);
		//System.out.println("Converting:" + String.join(" ", current_ids));
		//update numbers for next round
		start = end + 1;
		end = end + 1000;
		if(end > ids_batch.length)
			end = ids_batch.length -1;
		else
			end = end;

		// form parameters
        	Map<Object, Object> data = new HashMap<>();
		data.put("from",this.oldID_index);
		data.put("to",this.newID_index);
/*		if(oldID_index.equalsIgnoreCase("Gene_Name") || oldID_index.equalsIgnoreCase("GeneID"))
			data.put("taxId",Integer.toString(this.organism));
*/		data.put("ids",String.join(",",current_ids));
		
		//System.out.println(String.join(",",current_ids));	

	  	//create the initial url 
		StringBuilder Uniprot_Run_URI = new StringBuilder(UNIPROT_SERVER + "/run" );
		
		HttpRequest request = HttpRequest.newBuilder()
                .POST(ofFormData(data))
                .uri(URI.create(Uniprot_Run_URI.toString()))
                .setHeader("User-Agent", "Java 11 HttpClient Bot") // add request header
		.header("Content-Type", "application/x-www-form-urlencoded")
		.build();

        	HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        	// print response headers
        	//HttpHeaders headers = response.headers();
       		//headers.map().forEach((k, v) -> System.out.println(k + ":" + v));

        	// print status code
       		System.out.println(response.statusCode());

        	// print response body
        	System.out.println(response.body());

		// parsing file "JSONExample.json"
        	Object obj = new JSONParser().parse(response.body());
          
        	// typecasting obj to JSONObject
        	JSONObject jo = (JSONObject) obj;	
		//System.out.println(jo.toString());

		// getting firstName and lastName
        	String jobid = (String) jo.get("jobId");
        	System.out.println(jobid);
		
		jobIds[i] = jobid;
		i++;

	}
	//System.out.println("the array of jobids" + Arrays.toString(jobIds));
	return jobIds;
}

public static HttpRequest.BodyPublisher ofFormData(Map<Object, Object> data) {
        var builder = new StringBuilder();
        for (Map.Entry<Object, Object> entry : data.entrySet()) {
            if (builder.length() > 0) {
                builder.append("&");
            }
            builder.append(URLEncoder.encode(entry.getKey().toString(), StandardCharsets.UTF_8));
            builder.append("=");
            builder.append(URLEncoder.encode(entry.getValue().toString(), StandardCharsets.UTF_8));
        }
        return HttpRequest.BodyPublishers.ofString(builder.toString());
    }


public boolean check_job_status(String jobId) throws IOException, InterruptedException, Exception{

	int attempt = 0;
	StringBuilder Uniprot_Results_URI = new StringBuilder(UNIPROT_SERVER + "/status/" + jobId );
	while(true){	
		HttpRequest status_request = HttpRequest.newBuilder()
        	.GET()
        	.uri(URI.create(Uniprot_Results_URI.toString()))
        	.setHeader("User-Agent", "Java 11 HttpClient Bot") // add request heade
		.build();

        	HttpResponse<String> results_response = httpClient.send(status_request, HttpResponse.BodyHandlers.ofString());

        	//System.out.println(results_response.body());

        	Object obj = new JSONParser().parse(results_response.body());
          
        	// typecasting obj to JSONObject
        	JSONObject jo = (JSONObject) obj;	
		//System.out.println(jo.toString());

		// getting status
        	String status = (String) jo.get("jobStatus");

		if(attempt > 100){
			System.out.println("Queried job:" + jobId + "more than 100 times and it is still not complete");
			return false;
		}else if(status.equalsIgnoreCase("RUNNING")){
			attempt = attempt++;
      			Thread.sleep(5 * 1000);
		}else if(status.equalsIgnoreCase("ERROR")){
			System.out.println("Returned errors -" + jo.get("errors"));
		       return false;	
		}else if(status.equalsIgnoreCase("FINISHED")){
			return true; 
		}
	}
	//return false;
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

		if(oldID_index.equalsIgnoreCase("Gene_Name") || oldID_index.equalsIgnoreCase("GeneID"))
    			this.params =  new ParameterNameValue[] {
      				new ParameterNameValue("from", this.oldID_index),
      				new ParameterNameValue("to", this.newID_index),
				new ParameterNameValue("format", "tab"),
				//new ParameterNameValue("taxId", Integer.toString(this.organism)),
				new ParameterNameValue("query", String.join(" ", current_ids)),
    			};
		else
    			this.params =  new ParameterNameValue[] {
      				new ParameterNameValue("from", this.oldID_index),
      				new ParameterNameValue("to", this.newID_index),
				new ParameterNameValue("format", "tab"),
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

						//Ensembl idsd come with version numbers
						//get rid of the .# and only use the base 
						//Ensembl id
						if(this.newID_index.equals("Ensembl")){
							String[] ensembl_id_version = mapping[1].split("\\.");
							System.out.println("we are trying to get rid of versions - " + ensembl_id_version);
							convertedIds.put(mapping[0], new HashSet<>(Arrays.asList(ensembl_id_version[0])));
						}
						else
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
