package tagCount;//package com.mongodb.spark_examples;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import org.apache.spark.SparkConf;

import org.apache.spark.api.java.Optional;
import org.apache.spark.api.java.function.Function2;
import org.apache.spark.streaming.Duration;
import org.apache.spark.streaming.api.java.JavaDStream;
import org.apache.spark.streaming.api.java.JavaPairDStream;
import org.apache.spark.streaming.api.java.JavaReceiverInputDStream;
import org.apache.spark.streaming.api.java.JavaStreamingContext;

import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import scala.Tuple2;
import java.util.Arrays;
import java.util.List;
import com.mongodb.client.*;
import org.apache.spark.storage.StorageLevel;
import org.apache.spark.streaming.receiver.Receiver;
import org.bson.Document;

import static java.lang.Thread.sleep;

public final class TagCountGettingStarted {

    public static void main(final String[] args) throws Exception {
        String inDatabase = "steam";
        String inCollection = "steam";
        String outAllDatabase = "steam";
        String outAllCollection = "streamResult";
        String outPeriodDatabase = "steam";
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
class JavaMongoSteamGamesReceiver extends Receiver<String> {

    private static int count=0;
    String databaseName = "steam";
    String collectionName = "steam";

    public JavaMongoSteamGamesReceiver(String databaseName_, String collectionName_){
        super(StorageLevel.MEMORY_AND_DISK_2());
        databaseName = databaseName_;
        collectionName = collectionName_;
    }

    public static String parseTagString(Object document){

        return document.toString().replaceAll("Document","").replaceAll("tags=","")
                .replaceAll(",","").replaceAll("\\]","").replaceAll("\\}","")
                .replaceAll("\\[","").replaceAll("\\{","");
    }

    public void onStart() {
        // Start the thread that receives data over a connection
        new Thread()  {
            @Override public void run() {
                receive();
            }
        }.start();
    }

    public void onStop() {
        // There is nothing much to do as the thread calling receive()
        // is designed to stop by itself if isStopped() returns false
    }

    private void receive() {

        try {

            MongoClient mongoClient = MongoClients.create();
            MongoDatabase database = mongoClient.getDatabase(databaseName);
            MongoCollection<Document> collection = database.getCollection(collectionName);

            FindIterable iterable = collection.find().projection(new Document("tags",1).append("_id",0));
            MongoCursor cursor = iterable.iterator();
            while((!isStopped())&&cursor.hasNext()){
                String tagsString = parseTagString(cursor.next());
                if(!tagsString.equals("")){
                    store(tagsString);
                    //System.out.println(tagsString);
                }
                count++;
                if(count==100){
                    count = 0;
                    sleep(500);
                }

            }

        } catch (Throwable t) {
            // restart if there is any other error
            restart("Error receiving data", t);
        }
    }
}
class JavaRow implements java.io.Serializable{
    private String tag;
    private int count;

    public String getTag(){
        return tag;
    }

    public void setTag(String tag){
        this.tag = tag;
    }


    public int getCount(){
        return count;
    }

    public void setCount(int count){
        this.count = count;
    }

}
