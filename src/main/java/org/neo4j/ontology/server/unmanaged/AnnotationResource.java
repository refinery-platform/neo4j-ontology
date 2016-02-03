/*
 * Licensed to Neo Technology under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Neo Technology licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.neo4j.ontology.server.unmanaged;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.neo4j.graphdb.*;

import static org.neo4j.graphdb.Direction.INCOMING;
import static org.neo4j.graphdb.Direction.OUTGOING;


@Path("/annotations")
public class AnnotationResource
{
    private final ObjectMapper objectMapper;
    private GraphDatabaseService graphDb;

    // Relationships
    private static final DynamicRelationshipType SUBCLASS_OF = DynamicRelationshipType.withName( "RDFS:subClassOf" );
    private static final DynamicRelationshipType ANNOTATED_WITH = DynamicRelationshipType.withName( "annotated_with" );
    private static final DynamicRelationshipType READ_ACCESS = DynamicRelationshipType.withName( "read_access" );

    // Labels
    private static final Label CLAZZ = DynamicLabel.label( "Class" );
    private static final Label DATA_SET = DynamicLabel.label( "DataSet" );
    private static final Label USER = DynamicLabel.label( "User" );

    public AnnotationResource( @Context GraphDatabaseService graphDb )
    {
        this.graphDb = graphDb;
        this.objectMapper = new ObjectMapper();
    }

    @GET
    @Path("/{userName}")
    public Response getAnnotationSet( final @PathParam("userName") String userName ) {
        StreamingOutput stream = new StreamingOutput() {
            @Override
            public void write(OutputStream os) throws IOException, WebApplicationException {
                List<Node> accessibleDataSets;
                List<Node> directAnnotationTerms;
                Map<Long, Boolean> visited = new HashMap<>();
                Map<Long, List<Long>> associatedDataSets = new HashMap<>();

                JsonGenerator jg = objectMapper.getFactory().createGenerator(os, JsonEncoding.UTF8);
                jg.writeStartObject();
                jg.writeFieldName("nodes");
                jg.writeStartArray();

                try (Transaction tx = graphDb.beginTx();
                     ResourceIterator<Node> users = graphDb.findNodes(USER, "name", userName)) {
                    while (users.hasNext()) {
                        Node user = users.next();
                        accessibleDataSets = getAccessibleDataSets(user);
                        directAnnotationTerms = getDirectAnnotationTerms(accessibleDataSets, associatedDataSets);

                        for (Node term : directAnnotationTerms) {
                            if (!visited.containsKey(term.getId())) {
                                writeJsonNodeObjectStart(jg, term);
                                writeJsonNodeObjectParents(jg, term);
                                writeJsonNodeObjectDataSets(jg, term, associatedDataSets.get(term.getId()));
                                writeJsonNodeObjectEnd(jg);

                                visited.put(term.getId(), true);

                                // Runtime-wise inefficient but I don't know a good way around.
                                // In order to finalize the JSON node object and properly write an array of parents I have to loop over all
                                // superClass nodes before traversing to them.
                                for (Relationship subClassOf : term.getRelationships(SUBCLASS_OF, OUTGOING)) {
                                    traverseToRoot(subClassOf.getEndNode(), visited, jg);
                                }
                            }
                        }
                    }
                    tx.success();
                }

                jg.writeEndArray();
                jg.writeEndObject();
                jg.flush();
                jg.close();
            }
        };

        return Response.ok().entity(stream).type(MediaType.APPLICATION_JSON).build();
    }

    private void traverseToRoot (Node term, Map<Long, Boolean> visited, JsonGenerator jg) throws IOException {
        if (!visited.containsKey(term.getId())) {
            writeJsonNodeObjectStartNoDs(jg, term);
            writeJsonNodeObjectParents(jg, term);
            writeJsonNodeObjectEnd(jg);

            visited.put(term.getId(), true);

            // Runtime-wise inefficient but I don't know a good way around.
            // In order to finalize the JSON node object and properly write an array of parents I have to loop over all
            // superClass nodes before traversing to them.
            for (Relationship subClassOf : term.getRelationships(SUBCLASS_OF, OUTGOING)) {
                traverseToRoot(subClassOf.getEndNode(), visited, jg);
            }
        }
    }

    private List<Node> getAccessibleDataSets (Node user) {
        List<Node> dataSets = new ArrayList<>();

        for (Relationship readAccess : user.getRelationships(READ_ACCESS, OUTGOING)) {
            dataSets.add(readAccess.getEndNode());
        }

        return dataSets;
    }

    private List<Node> getDirectAnnotationTerms (List<Node> dataSets, Map<Long, List<Long>> associatedDataSets) {
        List<Node> terms = new ArrayList<>();
        Node term;

        for (Node dataSet : dataSets) {
            for (Relationship annotatedWith : dataSet.getRelationships(ANNOTATED_WITH, OUTGOING)) {
                term = annotatedWith.getEndNode();
                terms.add(term);
                if(!associatedDataSets.containsKey(term.getId())) {
                    associatedDataSets.put(term.getId(), new ArrayList<Long>());
                }
                associatedDataSets.get(term.getId()).add(((Integer) dataSet.getProperty("id")).longValue());
            }
        }

        return terms;
    }

    private void writeJsonNodeObjectParents (JsonGenerator jg, Node term) throws IOException {
        jg.writeFieldName("parents");  // dataSets:
        jg.writeStartArray();  // [
        for (Relationship subClassOf : term.getRelationships(SUBCLASS_OF, OUTGOING)) {
            jg.writeString(subClassOf.getEndNode().getProperty("uri").toString());
        }
        jg.writeEndArray();  // ]
    }

    private void writeJsonNodeObjectDataSets (JsonGenerator jg, Node term, List<Long> dataSetsId) throws IOException {
        jg.writeFieldName("dataSets");  // dataSets:
        jg.writeStartArray();  // [
        for (Long dataSetId : dataSetsId) {
            jg.writeNumber(dataSetId);  // 123
        }
        jg.writeEndArray();  // ]
    }

    private void writeJsonNodeObjectStart (JsonGenerator jg, Node term) throws IOException {
        jg.writeStartObject();  // {
        jg.writeStringField("uri", term.getProperty("uri").toString());  // uri: "http://www.w3.org/2002/07/owl#Thing"
        jg.writeStringField("ontId", term.getProperty("name").toString());  // ontId: "OWL:Thing"
        jg.writeStringField("label", term.getProperty("rdfs:label", term.getProperty("name")).toString());  // ontId: "OWL:Thing"
    }

    private void writeJsonNodeObjectStartNoDs (JsonGenerator jg, Node term) throws IOException {
        jg.writeStartObject();  // {
        jg.writeStringField("uri", term.getProperty("uri").toString());  // uri: "http://www.w3.org/2002/07/owl#Thing"
        jg.writeStringField("ontId", term.getProperty("name").toString());  // ontId: "OWL:Thing"
        jg.writeStringField("label", term.getProperty("rdfs:label", term.getProperty("name")).toString());  // ontId: "OWL:Thing"
        jg.writeFieldName("dataSets");  // dataSets:
        jg.writeStartArray();  // [
        jg.writeEndArray();  // ]
    }

    private void writeJsonNodeObjectEnd (JsonGenerator jg) throws IOException {
        jg.writeEndObject();  // }
    }
}
