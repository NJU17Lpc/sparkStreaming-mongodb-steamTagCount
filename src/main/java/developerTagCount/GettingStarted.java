package developerTagCount;//package com.mongodb.spark_examples;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.Optional;
import org.apache.spark.api.java.function.Function2;
import org.apache.spark.api.java.function.VoidFunction;
import org.apache.spark.streaming.Duration;
import org.apache.spark.streaming.api.java.JavaPairDStream;
import org.apache.spark.streaming.api.java.JavaReceiverInputDStream;
import org.apache.spark.streaming.api.java.JavaStreamingContext;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import scala.Tuple2;

import java.util.Iterator;
import java.util.List;

import static com.mongodb.client.model.Filters.eq;


public final class GettingStarted {

    public static void main(final String[] args) throws Exception {
        String inDatabase = "ScrapyChina";
        String inCollection = "steamGames";
        String outAllDatabase = "ScrapyChina";
        String outAllCollection = "developerTag";


        SparkConf sparkConf = new SparkConf().setMaster("local[4]").setAppName("developerTagCount");
        JavaStreamingContext ssc = new JavaStreamingContext(sparkConf, new Duration(5000));

        ssc.checkpoint("./checkpoint/");

        JavaReceiverInputDStream<Row> lines = ssc.receiverStream(
                new JavaMongoSteamGamesReceiver(inDatabase, inCollection));


        JavaPairDStream<String, String> PTpairs = lines.mapToPair(s -> new Tuple2<>(s.getDeveloper(),s.getTag()));
        JavaPairDStream<Tuple2<String,String>, Integer> PT_1pairs = PTpairs.mapToPair(s->new Tuple2<>(s,1));
        JavaPairDStream<Tuple2<String,String>, Integer> PT_Npairs = PT_1pairs.reduceByKey((i1, i2)->i1+i2);

        PT_Npairs.print(50);

        JavaPairDStream<Tuple2<String,String>, Integer> PT_N_allPairs = PT_Npairs.
                updateStateByKey((Function2<List<Integer>, Optional<Integer>, Optional<Integer>>)
                        (values,state)->{
                            Integer sum = 0;
                            if(state.isPresent()){
                                sum = state.get();
                            }
                            for(Integer i:values){
                                sum += i;
                            }
                            return Optional.of(sum);
                        }
                ).persist();

        JavaPairDStream<String, Tuple2<String, Integer>> P_TN_allPairs = PT_N_allPairs.mapToPair(
                s->new Tuple2<>(s._1._1, new Tuple2<>(s._1._2, s._2))
        );

        PT_N_allPairs.foreachRDD(rdd->rdd.foreachPartition(records->{
            Bson filter;
            MongoCollection<JavaRow> collection = MongoUtil.getCollection(outAllDatabase,outAllCollection, JavaRow.class);
            while(records.hasNext()){

                Tuple2<Tuple2<String,String>, Integer> t = records.next();
                JavaRow row = new JavaRow();
                row.setPublisher(t._1._1);
                row.setTag(t._1._2);
                row.setCount(t._2);

                filter = Filters.and(eq("developer",row.getPublisher()),eq("tag",row.getTag()));
                collection.replaceOne(filter,row,new ReplaceOptions().upsert(true));
            }

        }));


        ssc.start();
        ssc.awaitTermination();
    }

}


class MongodbCollectionSingleton {
    private static transient MongoClient mongoClient = null;

    public static MongoClient getMongoClient(){

        if (mongoClient == null) {
            mongoClient = MongoClients.create();
        }
        return mongoClient;
    }

}

class MongoUtil{
    static <T> MongoCollection<T> getCollection(String database, String collection, Class<T> tClass) {
        final CodecRegistry codecRegistry = CodecRegistries
                .fromRegistries(
                        MongoClientSettings.getDefaultCodecRegistry(),
                        CodecRegistries.fromProviders(PojoCodecProvider.builder().automatic(true).build())
                );
        return MongodbCollectionSingleton.getMongoClient().getDatabase(database).getCollection(collection, tClass)
                .withCodecRegistry(codecRegistry);
    }
}
