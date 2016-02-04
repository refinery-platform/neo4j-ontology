# Neo4J Ontology Extensions

> Collection of plugins and extensions to simplify access to the ontolog-related properties and the annotation set hierarchy

---

Content:

* [1. Installation](#1-installation)
* [2. Endpoints](#2-endpoints)

---

## 1. Installation

For the sake of concision I assume that Neo4J has been installed in `/neo4j/`. Make sure to adjust it to your setup accordingly.

1. Stop Neo4J

   ```
   neo4j stop

   $ neo4j start
   ```

2. Download or [compile](#compile-yourself) the JAR and copy it to Neo4J's plugin directoy.

   ```
   cp ./dist/ontology.jar /neo4j/plugins/
   ```

3. Enable unmanaged JAX-RS extensions. Open `/neo4j/conf/neo4j-server.properties` and ensure that the line starting with:

   ```
   org.neo4j.server.thirdparty_jaxrs_classes=
   ```

   looks like this:

   ```
   org.neo4j.server.thirdparty_jaxrs_classes=org.neo4j.ontology.server.unmanaged=/ontology/unmanaged
   ```

4. Start Neo4J

   ```
   neo4j start
   ```

5. OPTIONAL: Open `http://localhost:7474` to check if nothing failed. In case nothing shows up check `/path/to/neo4j/data/log/console.log` and file an issue. Cheers.

### Compile yourself

Travis-CI is automatically compiling the JAR for every release and attaches it to the release but you can surely compile the JAR yourself.

Requirements:

* [Java RE 7](jre7)
* [Gradle](gradle)

Compile:

```
gradlew build
```

## 2. Endpoints

**Get the annotation set hierarchy:***

URL: `http://localhost:7474/ontology/unmanaged/annotations/<USER_NAME>`
Description: Returns an object with all terms (or classes) directly or indirectly related to annotations of user's accessible data sets.

