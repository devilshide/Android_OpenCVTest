package proto.ttt.cds.opencvtest_300_2.Database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import android.util.Log;

import proto.ttt.cds.opencvtest_300_2.Class.PlantData;

/**
 * Created by changdo on 17. 8. 1.
 */

public class PlantDBHandler extends SQLiteOpenHelper {
    public static final String TAG = "PlantDBHandler";

    public static final int DATABASE_VERSION = 5;
    public static final String DATABASE_NAME = "PlantInfoTable";

    private static final boolean DEBUG_PLANT_DB = true;

    private static final String SQL_CREATE_ENTRIES =
            "CREATE TABLE " + FeedEntry.TABLE_NAME + " ("
            + FeedEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + FeedEntry.COLUMN_LOCATION + " INTEGER, "
            + FeedEntry.COLUMN_NAME + " TEXT, "
            + FeedEntry.COLUMN_ORDER + " INTEGER, "
            + FeedEntry.COLUMN_AREA_SIZE + " DOUBLE, "
            + FeedEntry.COLUMN_TIME + " LONG " + ")";

    private static final String SQL_DELETE_ENTRIES = "DROP TABLE IF EXISTS " + FeedEntry.TABLE_NAME;


    public PlantDBHandler(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_ENTRIES);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVer, int newVer) {
        Log.d(TAG, "onUpgrade(): oldVer = " + oldVer + ", newVer = " + newVer);
        db.execSQL(SQL_DELETE_ENTRIES);
        onCreate(db);
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVer, int newVer) {
        onUpgrade(db, oldVer, newVer);
    }

    public void insertData(PlantData info) {
        ContentValues values = new ContentValues();
        values.put(FeedEntry.COLUMN_LOCATION, info.getLocation());
        values.put(FeedEntry.COLUMN_NAME, info.getName());
        values.put(FeedEntry.COLUMN_ORDER, info.getOrder());
        values.put(FeedEntry.COLUMN_AREA_SIZE, info.getAreaSize());
        values.put(FeedEntry.COLUMN_TIME, info.getTime());
        this.getWritableDatabase().insert(FeedEntry.TABLE_NAME, null, values);
    }

    public PlantData[] getData(String plantName) {
//        String query = "select * from " + FeedEntry.TABLE_NAME + " where "
        Cursor cursor = this.getReadableDatabase().query(
                FeedEntry.TABLE_NAME,
                new String[] {FeedEntry._ID,
                        FeedEntry.COLUMN_LOCATION,
                        FeedEntry.COLUMN_NAME,
                        FeedEntry.COLUMN_ORDER,
                        FeedEntry.COLUMN_AREA_SIZE,
                        FeedEntry.COLUMN_TIME},
                FeedEntry.COLUMN_NAME + "=?",
                new String[] {plantName},
                null,
                null,
                null);

        return getRequestedData(cursor);
    }

    public PlantData[] getData(int location) {
        Cursor cursor = this.getReadableDatabase().query(
                FeedEntry.TABLE_NAME,
                new String[] {FeedEntry._ID,
                        FeedEntry.COLUMN_LOCATION,
                        FeedEntry.COLUMN_NAME,
                        FeedEntry.COLUMN_ORDER,
                        FeedEntry.COLUMN_AREA_SIZE,
                        FeedEntry.COLUMN_TIME},
                FeedEntry.COLUMN_LOCATION + "=?",
                new String[] {""+location},
                null,
                null,
                null);

        return getRequestedData(cursor);
    }

    private PlantData[] getRequestedData(Cursor cursor) {
        if (cursor == null) {
            Log.d(TAG, "getRequestedData(): cursor is NULL");
            return null;
        }
        cursor.moveToFirst();
        PlantData[] plants = new PlantData[cursor.getCount()];
        int i = 0;
        while (cursor.moveToNext()) {
            int id = cursor.getInt(cursor.getColumnIndex(FeedEntry._ID));
            int location = cursor.getInt(cursor.getColumnIndex(FeedEntry.COLUMN_LOCATION));
            String name = cursor.getString(cursor.getColumnIndex(FeedEntry.COLUMN_NAME));
            int order = cursor.getInt(cursor.getColumnIndex(FeedEntry.COLUMN_ORDER));
            double area = cursor.getDouble(cursor.getColumnIndex(FeedEntry.COLUMN_AREA_SIZE));
            long time = cursor.getLong(cursor.getColumnIndex(FeedEntry.COLUMN_TIME));

            if (DEBUG_PLANT_DB) Log.d(TAG, "getData(): id = " + id
                    + "\tloc = " + location
                    + "\tname = " + name
                    + "\torder = " + order
                    + "\tarea = " + area
                    + "\ttime = " + time);

            plants[i++] = new PlantData(location, name, order, area, time);
        }
        return plants;
    }

    public void deleteData(String name) {
        this.getWritableDatabase().delete(FeedEntry.TABLE_NAME, FeedEntry.COLUMN_NAME + "=?", new String[] {name});
    }

    public void deleteData() {
        this.getWritableDatabase().delete(FeedEntry.TABLE_NAME, null, null);
    }

    public static class FeedEntry implements BaseColumns {
        public static final String TABLE_NAME = "PlantGrowthProgress";
        public static final String COLUMN_LOCATION = "potlocation";
        public static final String COLUMN_NAME = "name";
        public static final String COLUMN_ORDER = "biggestorder";
        public static final String COLUMN_AREA_SIZE = "areasize";
        public static final String COLUMN_TIME = "time";
    }
}
