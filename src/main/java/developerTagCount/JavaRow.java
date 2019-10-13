package developerTagCount;

public class JavaRow implements java.io.Serializable{
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
