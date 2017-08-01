package proto.ttt.cds.opencvtest_300_2.Classes;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;

/**
 * Created by changdo on 17. 8. 1.
 */

public class PlantDBHelper extends SQLiteOpenHelper {

    public static final int DATABASE_VERSION = 1;
    public static final String DATABASE_NAME = "PlantInfo.db";

    private static final String SQL_CREATE_ENTRIES
            = "CREATE TABLE "
            + FeedEntry.TABLE_NAME + " (" +
            FeedEntry._ID + " INTEGER PRIMARY KEY, " +
            FeedEntry.COLUMN_NAME_TITLE + " TEXT, " +
            FeedEntry.COLUMN_NAME_SUBTITLE + " TEXT, " +
            FeedEntry.COLUMN_AREA_SIZE + " INTEGER PRIMARY KEY, " +
            FeedEntry.COLUMN_RECIPE + " TEXT ";

    private static final String SQL_DELETE_ENTRIES
            = "DROP TABLE IF EXISTS" + FeedEntry.TABLE_NAME;

    public PlantDBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_ENTRIES);
    }

    public void onUpgrade(SQLiteDatabase db, int oldVer, int newVer) {
        db.execSQL(SQL_DELETE_ENTRIES);
        onCreate(db);
    }

    public void onDowngrade(SQLiteDatabase db, int oldVer, int newVer) {
        onUpgrade(db, oldVer, newVer);
    }




    public static class FeedEntry implements BaseColumns {
        public static final String TABLE_NAME = "plantInfo";
        public static final String COLUMN_NAME_TITLE = "name";
        public static final String COLUMN_NAME_SUBTITLE = "subtitle";
        public static final String COLUMN_AREA_SIZE = "areasize";
        public static final String COLUMN_RECIPE = "recipe";
    }
}
