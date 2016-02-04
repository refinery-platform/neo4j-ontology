package org.neo4j.ontology.server.plugins;

import java.util.ArrayList;
import java.util.Iterator;

import org.neo4j.graphdb.*;
import org.neo4j.helpers.collection.IteratorUtil;
import org.neo4j.server.plugins.*;
import org.neo4j.server.rest.repr.ListRepresentation;

@Description( "An extension to the Neo4j Server around ontologies" )
public class Annotations extends ServerPlugin
{
    @Description( "Get distribution of number of annotations per dataset" )
    @PluginTarget( GraphDatabaseService.class )
    public ListRepresentation getNumAnnoPerDataSet(
        @Source GraphDatabaseService db,
        @Description( "Restrict to a certain ontology. Specify the ontology's acronym, e.g. CL" )
            @Parameter( name = "ontology", optional = true ) String ontology)
    {
        ArrayList<Integer> counts = new ArrayList<>();
        long[] countArr;
        long tmp;
        int countIndex;
        int countNum;
        int size = 0;
        int numDs = 0;

        try (
            Transaction tx = db.beginTx();
            Result result = db.execute( "MATCH (ds:DataSet)-[]-(c:Class) RETURN ds, COUNT(c) AS count" )
        )
        {
            Iterator<Long> counts_col = result.columnAs( "count" );
            while ( counts_col.hasNext() ) {
                ++numDs;

                countIndex = counts_col.next().intValue();
                if (size < countIndex) {
                    for (int i = size; i < countIndex; ++i) {
                        counts.add(0);
                    }
                    size = countIndex;
                }

                countNum = counts.get(countIndex - 1);

                counts.set(countIndex - 1, ++countNum);
            }

            countArr = new long[size];
            Iterator<Integer> iterator = counts.iterator();
            for (int i = 0; i < size; i++)
            {
                countArr[i] = iterator.next();
            }
        }

        return ListRepresentation.numbers(countArr);
    }
}
