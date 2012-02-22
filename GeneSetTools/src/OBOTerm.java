import java.util.HashSet;

/**
 * Created by IntelliJ IDEA.
 * User: User
 * Date: 2/22/12
 * Time: 11:03 AM
 * To change this template use File | Settings | File Templates.
 */
public class OBOTerm {
    String id;
        String name;
        String namespace;
        String definition;
        HashSet<String> is_a;

        public OBOTerm(String id, String name, String namespace, String definition, HashSet<String> is_a) {
            this.id = id;
            this.name = name;
            this.namespace = namespace;
            this.definition = definition;
            this.is_a = is_a;
        }

        public OBOTerm() {
            this.is_a = new HashSet<String>();
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getNamespace() {
            return namespace;
        }

        public void setNamespace(String namespace) {
            this.namespace = namespace;
        }

        public String getDefinition() {
            return definition;
        }

        public void setDefinition(String definition) {
            this.definition = definition;
        }

        public HashSet<String> getIs_a() {
            return is_a;
        }

        public void setIs_a(HashSet<String> is_a) {
            this.is_a = is_a;
        }

        public void addIsa(String term){
            this.is_a.add(term);
        }
    }
