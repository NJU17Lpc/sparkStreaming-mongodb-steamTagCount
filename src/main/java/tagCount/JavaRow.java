package tagCount;

public class JavaRow implements java.io.Serializable{
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
