package com.battery.energytester.Database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.sax.EndElementListener
import android.util.Log


class DBHelper(context: Context, factory: SQLiteDatabase.CursorFactory?) :
    SQLiteOpenHelper(context, DATABASE_NAME, factory, DATABASE_VERSION) {

    companion object{
        private val TAG_DATABASE = "DatabaseDebug"
        private val DATABASE_NAME = "ENERGY_TESTING"

        //database version
        private val DATABASE_VERSION = 1

        //tables names
        val TABLE_ITERATION_NAME = "ITERATIONS_table"
        val TABLE_TEST_NAME = "TESTS_table"


        val ID_COL = "id"
        //colums for iterations
        val TEST_ID_COL = "test_id"
        val TEST_NAME_COL = "test_name"
        val NUM_COL = "num"
        val DURATION_COl = "duration_s"
        val CURRENT_COL = "current_mA"
        val VOLTAGE_COL = "voltage_V"
        val POWER_COl = "power_W"
        val ENERGY_COL = "energy_J"

        //colums for tests
        val NAME_COl = "name"
        val TOTAL_ITERATIONS_COl = "number_of_total_iterations"
        val TOTAL_DURATION_COl = "total_duration_s"
        val AVG_CURRENT_COL = "avg_current_mA"
        val AVG_VOLTAGE_COL = "avg_voltage_V"
        val AVG_POWER_COl = "avg_power_W"
        val TOTAL_ENERGY_COL = "total_energy_J"
        val START_BAT_LEVEL = "start_battery_level"
        val END_BAT_LEVEL = "end_battery_level"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val query1 = ("CREATE TABLE " + TABLE_ITERATION_NAME + " ("
                + ID_COL + " INTEGER PRIMARY KEY, " +
                TEST_ID_COL + " INTEGER, " +
                TEST_NAME_COL + " TEXT, " +
                NUM_COL  + " INTEGER," +
                DURATION_COl + " FLOAT," +
                CURRENT_COL + " FLOAT," +
                VOLTAGE_COL + " FLOAT," +
                POWER_COl + " FLOAT," +
                ENERGY_COL + " FLOAT" + ")")
        val query2 = ("CREATE TABLE " + TABLE_TEST_NAME + " ("
                + ID_COL + " INTEGER PRIMARY KEY, " +
                NAME_COl + " TEXT," +
                TOTAL_ITERATIONS_COl + " INTEGER," +
                TOTAL_DURATION_COl + " FLOAT," +
                AVG_CURRENT_COL + " FLOAT," +
                AVG_VOLTAGE_COL + " FLOAT," +
                AVG_POWER_COl + " FLOAT," +
                TOTAL_ENERGY_COL + " FLOAT," +
                START_BAT_LEVEL + " INTEGER," +
                END_BAT_LEVEL + " INTEGER " +")")

        db.execSQL(query1)
        db.execSQL(query2)

        Log.i(TAG_DATABASE, "Database created ")
    }

    override fun onUpgrade(db: SQLiteDatabase, p1: Int, p2: Int) {
        // this method is to check if table already exists
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_ITERATION_NAME)
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_TEST_NAME)
        db.execSQL("DROP TABLE IF EXISTS ITERATIONS_table_tmp")
        db.execSQL("DROP TABLE IF EXISTS TESTS_table_tmp")
        onCreate(db)
    }

    fun deleteAllTest() : Boolean {
        val db = this.writableDatabase
        return db.delete(TABLE_TEST_NAME, null, null) > 0
    }

    fun addIteration(iteration : Iteration, testId : Long, testName: String){

        val values = ContentValues()

        values.put(TEST_ID_COL, testId)
        values.put(TEST_NAME_COL, testName)
        values.put(NUM_COL, iteration.number)
        values.put(DURATION_COl, iteration.duration)
        values.put(CURRENT_COL, iteration.current)
        values.put(VOLTAGE_COL, iteration.voltage)
        values.put(POWER_COl, iteration.power)
        values.put(ENERGY_COL, iteration.energy)

        val db = this.writableDatabase

        db.insert(TABLE_ITERATION_NAME, null, values)

        db.close()
    }
    fun addTest(test : Test) : Long{

        val values = ContentValues()

        values.put(NAME_COl, test.name)
        values.put(TOTAL_ITERATIONS_COl, test.iteration_size)
        values.put(TOTAL_DURATION_COl, test.duration)
        values.put(AVG_CURRENT_COL, test.avg_current)
        values.put(AVG_VOLTAGE_COL, test.avg_voltage)
        values.put(AVG_POWER_COl, test.avg_power)
        values.put(TOTAL_ENERGY_COL, test.energy)
        values.put(START_BAT_LEVEL, test.startBatLevel)
        values.put(END_BAT_LEVEL, test.endBatLevel)

        val db = this.writableDatabase

        val id = db.insert(TABLE_TEST_NAME, null, values)
        if (id >= 0) {
            for (iteration in test.iterations){
                addIteration(iteration, id, test.name)
            }
        }
        Log.i(TAG_DATABASE, "Added Test")

        db.close()
        return id
    }

    fun getTests(): Cursor? {

        val db = this.readableDatabase

        return db.rawQuery("SELECT * FROM " + TABLE_TEST_NAME, null)
    }

    fun deleteTest(testID: Long) {
        val db = this.writableDatabase
        db.delete(
            TABLE_TEST_NAME,
            "$ID_COL = ?",
            arrayOf(testID.toString())
        )
        db.delete(
            TABLE_ITERATION_NAME,
            "$TEST_ID_COL = ?",
            arrayOf(testID.toString())
        )
        db.close()
    }


}