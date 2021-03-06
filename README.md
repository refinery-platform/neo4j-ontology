# Neo4J Ontology Extensions [![Build Status](https://travis-ci.org/refinery-platform/neo4j-ontology.svg?branch=master)](https://travis-ci.org/refinery-platform/neo4j-ontology)

> Collection of plug-ins and extensions to simplify access to the ontology-related properties and the annotation set hierarchy

---

Content:

* [1. Installation](#1-installation)
* [2. Endpoints](#2-endpoints)
* [3. Usage](#3-usage)

---

## 1. Installation

For the sake of concision I assume that Neo4J has been installed in `/neo4j/`. Make sure to adjust it to your set-up accordingly.

1. Stop Neo4J

   ```
   neo4j stop

   $ neo4j start
   ```

2. Download or [compile](#compile-yourself) the JAR and copy it to Neo4J's plug-in directory.

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

5. OPTIONAL: Open `http://localhost:7474` to check if everything works. In case nothing shows up check `/path/to/neo4j/data/log/console.log` and file an issue. Cheers.

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

### Get the annotation set hierarchy

**HTTP:** GET

**URL:** `http://localhost:7474/ontology/unmanaged/annotations/<USER_NAME>`

**Parameters:**

- `objectification`: If `true` the response `nodes` property will be an object with the keys being the ontology term's URI. Default return type is an Array, i.e. `false`.

**Description:** Returns an object with all terms (or classes) directly or indirectly related to annotations of user's accessible data sets.

**HTTP:** POST

**URL:** `http://localhost:7474/ontology/unmanaged/annotations/[<USER_NAME>]`

**Description:** Labels ontology terms that belong to all (or a specific user's) annotation set hierarchy.

**HTTP:** DELETE

**URL:** `http://localhost:7474/ontology/unmanaged/annotations/`

**Description:** Remove all annotation set-related labels.

### Get number of annotations per data set across the repository

**URL:** `http://localhost:7474/db/data/ext/Annotations/graphdb/getNumAnnoPerDataSet`

**Description:** A `GET` request returns the possible parameters and a `POST` request returns the actual results. The results could be used to draw a histogram to quickly confirm whether the annotations follow a Gaussian distribution or are biased.

## 3. Usage

1. Send a _POST_ request to `http://localhost:7474/ontology/unmanaged/annotations/` to prepare annotation sets for all users. Note, this step needs to be done whenever a user uploads, deletes or shares a data set.

2. Send a _GET_ request to `http://localhost:7474/ontology/unmanaged/annotations/<USER_NAME>` to get the user's annotation set hierarchy.
