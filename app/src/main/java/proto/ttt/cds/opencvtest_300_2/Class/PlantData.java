package proto.ttt.cds.opencvtest_300_2.Class;

/**
 * Created by changdo on 17. 8. 8.
 */

public class PlantData {

    int id;
    int mOrder;
    String mName;
    double mAreasize;
    long mTime;
    String mRecipe;

    public PlantData() {}

    public PlantData(int id, String name, double size, long time) {
        this.id = id;
        this.mName = name;
        this.mAreasize = size;
        this.mTime = time;
    }

    public PlantData(String name, int order, double size, long time) {
        this.mName = name;
        this.mOrder = order;
        this.mAreasize = size;
        this.mTime = time;
    }

    public String getName() {
        return mName;
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
