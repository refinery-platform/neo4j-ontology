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

import javax.ws.rs.*;
import javax.ws.rs.Path;
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
    private static final Label USER = DynamicLabel.label( "User" );

    public AnnotationResource( @Context GraphDatabaseService graphDb )
    {
        this.graphDb = graphDb;
        this.objectMapper = new ObjectMapper();
    }

    @GET
    @Produces( MediaType.APPLICATION_JSON )
    @Path("/{userName}")
    public Response getAnnotationSets(
        final @PathParam("userName") String userName,
        final @DefaultValue("0") @QueryParam("objectification") int objectification
    ) {
        StreamingOutput stream = new StreamingOutput() {
            @Override
            public void write(OutputStream os) throws IOException, WebApplicationException {
                Map<Long, List<Long>> associatedDataSets = new HashMap<>();
                Label annotationLabel = DynamicLabel.label("AnnotationSets" + capitalize(userName));

                JsonGenerator jg = objectMapper.getFactory().createGenerator(os, JsonEncoding.UTF8);
                jg.writeStartObject();
                jg.writeFieldName("nodes");

                if (objectification > 0) {
                    jg.writeStartObject();
                } else {
                    jg.writeStartArray();
                }

                try (Transaction tx = graphDb.beginTx();
                     ResourceIterator<Node> users = graphDb.findNodes(USER, "name", userName)
                ) {
                    if (users.hasNext()) {
                        getDirectAnnotationTerms(getAccessibleDataSets(users.next()), associatedDataSets);
                    }
                    tx.success();
                }

                try (Transaction tx = graphDb.beginTx();
                     ResourceIterator<Node> terms = graphDb.findNodes(annotationLabel)
                ) {
                    while (terms.hasNext()) {
                        Node term = terms.next();
                        if (objectification > 0) {
                            jg.writeFieldName(term.getProperty("uri").toString());
                        }
                        if (objectification > 1) {
                            if (associatedDataSets.containsKey(term.getId())) {
                                writeJsonNodeObjectifiedObject(jg, term, annotationLabel, associatedDataSets.get(term.getId()));
                            } else {
                                writeJsonNodeObjectifiedObject(jg, term, annotationLabel);
                            }
                        } else {
                            if (associatedDataSets.containsKey(term.getId())) {
                                writeJsonNodeObject(jg, term, annotationLabel, associatedDataSets.get(term.getId()));
                            } else {
                                writeJsonNodeObject(jg, term, annotationLabel);
                            }
                        }
                    }
                    tx.success();
                }

                if (objectification > 0) {
                    jg.writeEndObject();
                } else {
                    jg.writeEndArray();
                }
                jg.writeEndObject();
                jg.flush();
                jg.close();
            }
        };

        return Response.ok().entity(stream).type(MediaType.APPLICATION_JSON).build();
    }

    @DELETE
    @Produces( MediaType.APPLICATION_JSON )
    @Path("/")
    public Response removeAnnotationSetLabels() {
        try (Transaction tx = graphDb.beginTx();
             ResourceIterator<Node> users = graphDb.findNodes(USER)) {
            while (users.hasNext()) {
                removeAnnotationSetLabels(
                    DynamicLabel.label(
                        "AnnotationSets" + capitalize(users.next().getProperty("name").toString())
                    )
                );
            }
            tx.success();
        }

        return Response.ok().build();
    }

    @POST
    @Produces( MediaType.APPLICATION_JSON )
    @Path("/")
    public Response labelAnnotationSets() {
        try (Transaction tx = graphDb.beginTx();
             ResourceIterator<Node> users = graphDb.findNodes(USER)) {
            while (users.hasNext()) {
                labelAnnotationSetsPerUser(users.next());
            }
            tx.success();
        }

        return Response.ok().build();
    }

    @POST
    @Produces( MediaType.APPLICATION_JSON )
    @Path("/{userName}")
    public Response labelAnnotationSets( final @PathParam("userName") String userName ) {
        try (Transaction tx = graphDb.beginTx();
             ResourceIterator<Node> users = graphDb.findNodes(USER, "name", userName)) {
            if (users.hasNext()) {
                labelAnnotationSetsPerUser(users.next());
            }
            tx.success();
        }

        return Response.ok().build();
    }

    private String capitalize(final String string) {
        return Character.toUpperCase(string.charAt(0)) + string.substring(1);
    }

    private void labelAnnotationSetsPerUser (Node user) {
        String userName = capitalize(user.getProperty("name").toString());

        removeAnnotationSetLabels(DynamicLabel.label("AnnotationSets" + userName));

        traverseUpAndDown(getDirectAnnotationTerms(getAccessibleDataSets(user)), userName);
    }

    private void removeAnnotationSetLabels (Label label) {
        try (Transaction tx = graphDb.beginTx();
             ResourceIterator<Node> nodes = graphDb.findNodes(label)) {
            while (nodes.hasNext()) {
                nodes.next().removeLabel(label);
            }
            tx.success();
        }
    }

    private void traverseUpAndDown (List<Node> leafs, String label) {
        Map<Long, Boolean> visited = new HashMap<>();

        // 1. Traverse up and cache the node id of every visited node
        for (Node leaf : leafs) {
            traverseUp(leaf, visited);
        }

        // Traverse down all previously visited nodes
        try (Transaction tx = graphDb.beginTx();
             ResourceIterator<Node> roots = graphDb.findNodes(CLAZZ, "name", "OWL:Thing")) {
            while (roots.hasNext()) {
                traverseDown(roots.next(), visited, label);
            }
            tx.success();
        }
    }

    private void traverseUp (Node term, Map<Long, Boolean> visited) {
        visited.put(term.getId(), true);
        for (Relationship subClassOf : term.getRelationships(SUBCLASS_OF, OUTGOING)) {
            traverseUp(subClassOf.getEndNode(), visited);
        }
    }

    public void traverseDown (Node term, Map<Long, Boolean> visited, String label) {
        term.addLabel(DynamicLabel.label("AnnotationSets" + label));
        for (Relationship subClassOf : term.getRelationships(SUBCLASS_OF, INCOMING)) {
            if (visited.containsKey(subClassOf.getStartNode().getId())) {
                traverseDown(subClassOf.getStartNode(), visited, label);
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

    private List<Node> getDirectAnnotationTerms (List<Node> dataSets) {
        List<Node> terms = new ArrayList<>();
        Node term;

        for (Node dataSet : dataSets) {
            for (Relationship annotatedWith : dataSet.getRelationships(ANNOTATED_WITH, OUTGOING)) {
                term = annotatedWith.getEndNode();
                terms.add(term);
            }
        }

        return terms;
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

    private void writeJsonNodeObject (JsonGenerator jg, Node term, Label annotationLabel) throws IOException {
        jg.writeStartObject();  // {
        jg.writeStringField("uri", term.getProperty("uri").toString());  // uri: "http://www.w3.org/2002/07/owl#Thing"
        jg.writeStringField("ontId", term.getProperty("name").toString());  // ontId: "OWL:Thing"
        jg.writeStringField("label", term.getProperty("rdfs:label", term.getProperty("name")).toString());  // ontId: "OWL:Thing"
        jg.writeFieldName("dataSets");  // dataSets:
        jg.writeStartArray();  // [
        jg.writeEndArray();  // ]
        writeJsonNodeObjectParents(jg, term, annotationLabel);
        jg.writeEndObject();  // }
    }

    private void writeJsonNodeObjectifiedObject (JsonGenerator jg, Node term, Label annotationLabel) throws IOException {
        jg.writeStartObject();  // {
        jg.writeStringField("uri", term.getProperty("uri").toString());  // uri: "http://www.w3.org/2002/07/owl#Thing"
        jg.writeStringField("ontId", term.getProperty("name").toString());  // ontId: "OWL:Thing"
        jg.writeStringField("label", term.getProperty("rdfs:label", term.getProperty("name")).toString());  // ontId: "OWL:Thing"
        jg.writeFieldName("dataSets");  // dataSets:
        jg.writeStartObject();  // {
        jg.writeEndObject();  // }
        writeJsonNodeObjectifiedObjectParents(jg, term, annotationLabel);
        jg.writeEndObject();  // }
    }

    private void writeJsonNodeObject (JsonGenerator jg, Node term, Label annotationLabel, List<Long> dataSetsId) throws IOException {
        jg.writeStartObject();  // {
        jg.writeStringField("uri", term.getProperty("uri").toString());  // uri: "http://www.w3.org/2002/07/owl#Thing"
        jg.writeStringField("ontId", term.getProperty("name").toString());  // ontId: "OWL:Thing"
        jg.writeStringField("label", term.getProperty("rdfs:label", term.getProperty("name")).toString());  // ontId: "OWL:Thing"
        jg.writeFieldName("dataSets");  // dataSets:
        jg.writeStartArray();  // [
        for (Long dataSetId : dataSetsId) {
            jg.writeNumber(dataSetId);  // 123
        }
        jg.writeEndArray();  // ]
        writeJsonNodeObjectParents(jg, term, annotationLabel);
        jg.writeEndObject();  // }
    }

    private void writeJsonNodeObjectifiedObject (JsonGenerator jg, Node term, Label annotationLabel, List<Long> dataSetsId) throws IOException {
        jg.writeStartObject();  // {
        jg.writeStringField("uri", term.getProperty("uri").toString());  // uri: "http://www.w3.org/2002/07/owl#Thing"
        jg.writeStringField("ontId", term.getProperty("name").toString());  // ontId: "OWL:Thing"
        jg.writeStringField("label", term.getProperty("rdfs:label", term.getProperty("name")).toString());  // ontId: "OWL:Thing"
        jg.writeFieldName("dataSets");  // dataSets:
        jg.writeStartObject();  // {
        for (Long dataSetId : dataSetsId) {
            jg.writeBooleanField(dataSetId.toString(), true);  // 123
        }
        jg.writeEndObject();  // }
        writeJsonNodeObjectifiedObjectParents(jg, term, annotationLabel);
        jg.writeEndObject();  // }
    }

    private void writeJsonNodeObjectParents (JsonGenerator jg, Node term, Label annotationLabel) throws IOException {
        jg.writeFieldName("parents");  // parents:
        jg.writeStartArray();  // [
        for (Relationship subClassOf : term.getRelationships(SUBCLASS_OF, OUTGOING)) {
            if (subClassOf.getEndNode().hasLabel(annotationLabel)) {
                jg.writeString(subClassOf.getEndNode().getProperty("uri").toString());
            }
        }
        jg.writeEndArray();  // ]
    }

    private void writeJsonNodeObjectifiedObjectParents (JsonGenerator jg, Node term, Label annotationLabel) throws IOException {
        jg.writeFieldName("parents");  // parents:
        jg.writeStartObject();  // {
        for (Relationship subClassOf : term.getRelationships(SUBCLASS_OF, OUTGOING)) {
            if (subClassOf.getEndNode().hasLabel(annotationLabel)) {
                jg.writeBooleanField(subClassOf.getEndNode().getProperty("uri").toString(), true);
            }
        }
        jg.writeEndObject();  // }
    }
}
