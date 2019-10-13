package publisherTagCount;

/*
implement two methods: onStart()/onStop()
 */
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.*;
import org.apache.spark.storage.StorageLevel;
import org.apache.spark.streaming.receiver.Receiver;
import org.bson.Document;
import java.util.ArrayList;
import java.util.List;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.not;
import static java.lang.Thread.sleep;

public class JavaMongoSteamGamesReceiver extends Receiver<Row> {

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
            MongoDatabase database = mongoClient.getDatabase("ScrapyChina");
            MongoCollection<Document> collection = database.getCollection("steamGames");


            MongoCursor<Document> cursor = collection.find(not(eq("developer",""))).projection(new Document("publisher",1).append("tags",1).append("_id",0)).iterator();

            while(cursor.hasNext()){

                System.out.println();

                List<Row> rows = parseRows(com.mongodb.util.JSON.serialize(cursor.next()));
                for(int i=0; i<rows.size(); i++){
                    //System.out.print(rows.get(i).getPublisher()+" "+rows.get(i).getTag()+"  ");
                    if(!rows.get(i).getPublisher().equals("")){
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

        JSONArray publisherArray = json.getJSONArray("publisher");

        JSONArray tagArray = json.getJSONArray("tags");

        for(int i=0; i<publisherArray.size(); i++){
            for(int j=0; j<tagArray.size(); j++){
                Row row = new Row();
                row.setPublisher(publisherArray.getString(i));
                row.setTag(tagArray.getString(j));
                res.add(row);
            }
        }
        return res;
    }

}




