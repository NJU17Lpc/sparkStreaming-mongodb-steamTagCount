

/*
implement two methods: onStart()/onStop()
 */
import com.mongodb.client.*;
import org.apache.spark.storage.StorageLevel;
import org.apache.spark.streaming.receiver.Receiver;
import org.bson.Document;

import static java.lang.Thread.sleep;

/**
 * 我只要在这个文件里面用mongodb driver里面的操作就行，查询结果可迭代就好用，最后存储的结构也可以是string之外的对象
 * 每次读取十个之后，cursor向下加，但是要检查是否越界
最后写回数据库的操作还不清楚放到哪里去，可能是用wordCounts结果来foreach写入，然后重新连接mongodb，只不过没有通用格式，需要自己定义
 */

public class JavaMongoSteamGamesReceiver extends Receiver<String> {

    private static int count=0;
    String databaseName = null;
    String collectionName = null;

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
                if(count==20){
                    count = 0;
                    sleep(3000);
                }

            }

        } catch (Throwable t) {
            // restart if there is any other error
            restart("Error receiving data", t);
        }
    }
}


