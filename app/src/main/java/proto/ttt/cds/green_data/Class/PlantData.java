package proto.ttt.cds.green_data.Class;

/**
 * Created by changdo on 17. 8. 8.
 */

public class PlantData {

    int id;
    int mLocation;
    String mName;
    int mOrder;
    double mAreasize;
    long mTime;
    String mRecipe;

    public PlantData() {}

    public PlantData(int loc, String name, int order, double size, long time) {
        this.mLocation = loc;
        this.mName = name;
        this.mOrder = order;
        this.mAreasize = size;
        this.mTime = time;
    }

    public String getName() {
        return mName;
    }

    public int getLocation() {
        return mLocation;
    }

    public int getOrder() {
        return mOrder;
    }

    public double getAreaSize() {
        return mAreasize;
    }

    public long getTime() {
        return mTime;
    }

    public void setRecipe(String recipe) {
        mRecipe = recipe;
    }

    public String getRecipe() {
        return mRecipe;
    }
}
