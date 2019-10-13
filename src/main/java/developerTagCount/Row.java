package developerTagCount;

public class Row implements java.io.Serializable{
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