//package com.mongodb.spark_examples;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import org.apache.spark.SparkConf;

import org.apache.spark.api.java.Optional;
import org.apache.spark.api.java.function.Function2;
import org.apache.spark.streaming.Duration;
import org.apache.spark.streaming.Durations;
import org.apache.spark.streaming.api.java.JavaDStream;
import org.apache.spark.streaming.api.java.JavaPairDStream;
import org.apache.spark.streaming.api.java.JavaReceiverInputDStream;
import org.apache.spark.streaming.api.java.JavaStreamingContext;

import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import scala.Tuple2;
import java.util.Arrays;
import java.util.List;


import static com.mongodb.client.model.Updates.*;


public final class GettingStarted {

    public static void main(final String[] args) throws Exception {
        String inDatabase = "ScrapyChina";
        String inCollection = "steamGames";
        String outAllDatabase = "ScrapyChina";
        String outAllCollection = "streamResult";
        String outPeriodDatabase = "ScrapyChina";
        String outPeriodCollection = "streamPeriodResult";

        SparkConf sparkConf = new SparkConf().setMaster("local[4]").setAppName("JavaCustomReceiver");
        JavaStreamingContext ssc = new JavaStreamingContext(sparkConf, new Duration(500));

        ssc.checkpoint("./checkpoint/");

        JavaReceiverInputDStream<String> lines = ssc.receiverStream(
                new JavaMongoSteamGamesReceiver(inDatabase, inCollection));

        JavaDStream<String> words = lines.flatMap(x -> Arrays.asList(x.split(" ")).iterator());
        JavaPairDStream<String, Integer> pairs = words.mapToPair(s -> new Tuple2<>(s,1));
        JavaPairDStream<String, Integer> wordCounts = pairs.reduceByKey((i1, i2)->i1+i2);

        wordCounts.print();

        wordCounts.foreachRDD(rdd->rdd.foreachPartition(records->{
            Bson filter;
            MongoCollection<JavaRow> collection = MongoUtil.getCollection(outPeriodDatabase,outPeriodCollection,JavaRow.class);
            while(records.hasNext()){
                Tuple2<String, Integer> t = records.next();
                JavaRow row = new JavaRow();
                row.setTag(t._1);
                row.setCount(t._2);
                filter = Filters.eq("tag",row.getTag());
                collection.replaceOne(filter,row,new ReplaceOptions().upsert(true));
            }

        }));


        JavaPairDStream<String, Integer> overAllCounts = wordCounts.
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



        overAllCounts.foreachRDD(rdd->rdd.foreachPartition(records->{
            Bson filter;
            MongoCollection<JavaRow> collection = MongoUtil.getCollection(outAllDatabase,outAllCollection,JavaRow.class);
            while(records.hasNext()){

                Tuple2<String, Integer> t = records.next();
                JavaRow row = new JavaRow();
                row.setTag(t._1);
                row.setCount(t._2);

                filter = Filters.eq("tag",row.getTag());
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
