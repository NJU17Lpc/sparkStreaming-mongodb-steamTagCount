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
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.mongodb.client.*;
import org.apache.spark.storage.StorageLevel;
import org.apache.spark.streaming.receiver.Receiver;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.not;
import static java.lang.Thread.sleep;

public final class DeveloperGettingStarted {

    public static void main(final String[] args) throws Exception {
        String inDatabase = "steam";
        String inCollection = "steam";
        String outAllDatabase = "steam";
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
class JavaMongoSteamGamesReceiver extends Receiver<Row> {

    private static int count=0;
    String databaseName = null;
    String collectionName = null;

    public JavaMongoSteamGamesReceiver(String databaseName_, String collectionName_){
        super(StorageLevel.MEMORY_AND_DISK_2());
        databaseName = databaseName_;
        collectionName = collectionName_;
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
            MongoDatabase database = mongoClient.getDatabase("steam");
            MongoCollection<Document> collection = database.getCollection("steam");


            MongoCursor<Document> cursor = collection.find(not(eq("developer",""))).projection(new Document("developer",1).append("tags",1).append("_id",1)).iterator();

            while(!isStopped()&&cursor.hasNext()){

                System.out.println();
                count++;
                if(count==100){
                    count = 0;
                    sleep(500);
                }
                List<Row> rows = parseRows(com.mongodb.util.JSON.serialize(cursor.next()));

                for(int i=0; i<rows.size(); i++){

                    if(!rows.get(i).getDeveloper().equals("")){
                        System.out.println(rows.get(i).getDeveloper()+" "+rows.get(i).getTag()+" ");
                        store(rows.get(i));
                    }
                }
            }

        } catch (Throwable t) {
            // restart if there is any other error
            //restart("Error receiving data", t);
        }
    }

    public static List<Row> parseRows(String s){

        List<Row> res = new ArrayList<>();

        JSONObject json = JSONObject.parseObject(s);

        JSONArray developerArray = json.getJSONArray("developer");

        JSONArray tagArray = json.getJSONArray("tags");

        if(developerArray.size()==0){return res;}

        for(int i=0; i<developerArray.size(); i++){
            for(int j=0; j<tagArray.size(); j++){
                Row row = new Row();
                row.setDeveloper(developerArray.getString(i));
                row.setTag(tagArray.getString(j));
                res.add(row);
            }
        }

        return res;
    }

}

class JavaRow implements java.io.Serializable{
    private String publisher;
    private String tag;
    private int count;

    public String getPublisher(){
        return publisher;
    }

    public void setPublisher(String publisher){
        this.publisher = publisher;
    }


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
class Row implements java.io.Serializable{
    private String developer;
    private String tag;

    public String getTag(){
        return tag;
    }

    public void setTag(String tag){
        this.tag = tag;
    }


    public String getDeveloper(){
        return developer;
    }

    public void setDeveloper(String developer){
        this.developer = developer;
    }

}