import cytoscape.data.readers.TextFileReader;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

/**
 * Created by IntelliJ IDEA.
 * User: User
 * Date: 2/22/12
 * Time: 11:05 AM
 * To change this template use File | Settings | File Templates.
 */
public class Ontology {

    //name of obo file that will be parsed to create this ontology
    private String oboFilename;

    //create objects to hold all the terms in the ontology
    HashMap<String, OBOTerm> terms ;
    HashMap<String, HashSet<String>> descendants;

    public Ontology(String filename) {
        this.oboFilename = filename;

        terms = new HashMap<String, OBOTerm>();
        descendants = new HashMap<String, HashSet<String>>();
    }

    public void parseOBO(){
        //create a new set of Terms.  The key is the GO:id
        terms = new HashMap<String, OBOTerm>();

        if(oboFilename != null || !oboFilename.equalsIgnoreCase("")){
            TextFileReader reader = new TextFileReader(oboFilename);
            reader.read();
            String fullText = reader.getText();

            String []lines = fullText.split("\n");
            String current_id = "";
            OBOTerm current_term = new OBOTerm();

            for (int i = 0; i < lines.length; i++) {

               String line = lines[i];

                //read lines until we get to "[Term]"
                //once we get to a line with "[Term]" we need to look for id, name, namespace,definition and is_a
                //or if we come across an empty line
                if(line.contains("[Term]") || line.equalsIgnoreCase("")){
                    //add previous Term to our list of Terms
                    if(!current_id.equalsIgnoreCase("")){
                        terms.put(current_id, current_term);
                        current_id = "";
                        current_term = new OBOTerm();
                    }
                }

                if(line.startsWith("id:")){
                    String id = line.split("id:")[1].trim();
                    current_id = id;
                    current_term.setId(id);
                }

                if(line.startsWith("name:")){
                    String name = line.split("name:")[1].trim();
                    current_term.setName(name);
                }
                if(line.startsWith("namespace:")){
                    String namespace = line.split("namespace:")[1].trim();
                    current_term.setNamespace(namespace);
                }
                if(line.startsWith("def:")){
                    String def = line.split("def:")[1].trim();
                    current_term.setDefinition(def);
                }
                if(line.startsWith("is_a:")){
                    String is_a = (line.split("is_a:")[1].trim()).split("!")[0].trim();
                    current_term.addIsa(is_a);
                }

                //add the relationships: part_of, relationship: regulates, relationship: positively_regulates, relationship: negatively_regulates
                if(line.startsWith("relationship:")){
                    if(line.startsWith("relationship: part_of")) {
                        String relation = (line.split("relationship: part_of")[1].trim()).split("!")[0].trim();
                        current_term.addIsa(relation);
                    }
                    else if(line.startsWith("relationship: regulates")) {
                        String relation = (line.split("relationship: regulates")[1].trim()).split("!")[0].trim();
                        current_term.addIsa(relation);
                    }
                    else if(line.startsWith("relationship: positively_regulates")) {
                        String relation = (line.split("relationship: positively_regulates")[1].trim()).split("!")[0].trim();
                        current_term.addIsa(relation);
                    }
                    else if(line.startsWith("relationship: negatively_regulates")) {
                        String relation = (line.split("relationship: negatively_regulates")[1].trim()).split("!")[0].trim();
                        current_term.addIsa(relation);
                    }
                    else{
                        System.out.println("ERROR: The relationship has not been defined: " + line);
                    }
                }

            }
        }

        //compute the descendants
        descendants = computeDescendants(terms);
    }

    private HashMap<String, HashSet<String>> computeDescendants(HashMap<String, OBOTerm> terms){
        HashMap<String, HashSet<String>> descendants = new HashMap<String, HashSet<String>>();
        HashMap<String, HashSet<String>> parent_Child = new HashMap<String, HashSet<String>>();
        for(Iterator i = terms.keySet().iterator(); i.hasNext(); ){
            OBOTerm current_term = terms.get((String) i.next());
            String child = current_term.getId();
            HashSet<String> parents = current_term.getIs_a();
            for(Iterator<String> j = parents.iterator(); j.hasNext();){
                String parent = (String)j.next();
                if(parent_Child.containsKey(parent)){
                    HashSet<String> list = parent_Child.get(parent);
                    list.add(child);
                    parent_Child.put(parent, list);
                }
                else{
                    HashSet<String> newlist = new HashSet<String>();
                    newlist.add(child);
                    parent_Child.put(parent, newlist);
                }
            }
        }

        //Now go through the list of parents and add all the children of children to each parent
        for(Iterator i = parent_Child.keySet().iterator(); i.hasNext(); ){

            String parent = (String) i.next();
            HashSet<String> children = getChildren(parent_Child,parent);
            //for(Iterator<String> j = children.iterator(); j.hasNext();){
                //String child = (String)j.next();
                //if the parent is already in the list then add the descendant to its list
            /*    if(descendants.containsKey(parent)){
                    HashSet<String> list = descendants.get(parent);
                    list.add(child);
                    descendants.put(parent, list);
                }
                else{
                    HashSet<String> newlist = new HashSet<String>();
                    newlist.add(child);
                    descendants.put(parent, newlist);
                }
            } */
            //if the parent is already in the list then add the descendant to its list
                if(descendants.containsKey(parent)){
                    HashSet<String> list = descendants.get(parent);
                    list.addAll(children);
                    descendants.put(parent, list);
                }
                else{
                    HashSet<String> newlist = new HashSet<String>();
                    newlist.addAll(children);
                    descendants.put(parent, newlist);
                }
            }
        //}


        return descendants;
    }

    private HashSet<String> getChildren(HashMap<String, HashSet<String>> parent_Child, String term){
        HashSet<String> children = new HashSet<String>();
        if(parent_Child.get(term) == null){
            children.add(term);
            return children;
        }
        else{
            //go through the list of children and children of those children
            for(Iterator<String> i = parent_Child.get(term).iterator(); i.hasNext();){
                children.add(term);
                children.addAll(getChildren(parent_Child,i.next()));

            }
            return children;
        }
    }

    private HashSet<String> getParents(String term){

        HashSet<String> parents = new HashSet<String>();

        OBOTerm current_term = terms.get(term);
        if(current_term == null){
            System.out.println("missing term:" + term);
            parents.add(term);
            return parents;
        }
        //if the term has no parents then return the term
        else if(terms.get(term).getIs_a() == null || terms.get(term).getIs_a().isEmpty()){

            parents.add(term);
            return parents;
        }
        else{
             //go through the list and get the parents of every term
            for(Iterator<String> i = terms.get(term).getIs_a().iterator(); i.hasNext();){
                parents.addAll(getParents(i.next()));
            }
            return parents;
        }


    }

    public HashMap<String, String> getTerms_OBO(){
        HashMap<String,String> goterms = new HashMap<String, String>();
        if(!terms.isEmpty()){
            for(Iterator<String> i = terms.keySet().iterator();i.hasNext();){
                OBOTerm currentOboTerm = terms.get((String)i.next());
                String acc = currentOboTerm.getId();
                String name = currentOboTerm.getName();
                goterms.put(acc,name);
            }
        }
        return goterms;
    }


    public HashMap<String, OBOTerm> getTerms() {
        return terms;
    }

    public void setTerms(HashMap<String, OBOTerm> terms) {
        this.terms = terms;
    }

    public HashMap<String, HashSet<String>> getDescendants() {
        return descendants;
    }

    public void setDescendants(HashMap<String, HashSet<String>> descendants) {
        this.descendants = descendants;
    }
}
