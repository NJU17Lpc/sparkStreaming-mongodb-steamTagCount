//package com.mongodb.spark_examples;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.streaming.Duration;
import org.apache.spark.streaming.api.java.JavaDStream;
import org.apache.spark.streaming.api.java.JavaPairDStream;
import org.apache.spark.streaming.api.java.JavaReceiverInputDStream;
import org.apache.spark.streaming.api.java.JavaStreamingContext;
import scala.Tuple2;

import java.util.Arrays;

public final class GettingStarted {

    public static void main(final String[] args) throws Exception {

        SparkConf sparkConf = new SparkConf().setMaster("local[2]").setAppName("JavaCustomReceiver");
        JavaSparkContext sc = new JavaSparkContext(sparkConf);
        JavaStreamingContext ssc = new JavaStreamingContext(sc.getConf(), new Duration(500));

        JavaReceiverInputDStream<String> lines = ssc.receiverStream(
                new JavaMongoSteamGamesReceiver("steam", "steam"));

        JavaDStream<String> words = lines.flatMap(x -> Arrays.asList(x.split(" ")).iterator());
        JavaPairDStream<String, Integer> pairs = words.mapToPair(s -> new Tuple2<>(s,1));
        JavaPairDStream<String, Integer> wordCounts = pairs.reduceByKey((i1, i2)->i1+i2);

        wordCounts.print(20);

        ssc.start();
        ssc.awaitTermination();
        //sc.close();
    }
}