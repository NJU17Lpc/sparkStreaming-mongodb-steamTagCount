package publisherTagCount;

import java.util.List;

public class Row implements java.io.Serializable{
    private String publisher;
    private String tag;

    public String getTag(){
        return tag;
    }

    public void setTag(String tag){
        this.tag = tag;
    }


    public String getPublisher(){
        return publisher;
    }

    public void setPublisher(String publisher){
        this.publisher = publisher;
    }

}