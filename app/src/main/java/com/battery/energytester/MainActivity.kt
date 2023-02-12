package com.battery.energytester

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import android.os.*
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.battery.energytester.Adapters.TestAdapter
import com.battery.energytester.Database.DBHelper
import com.battery.energytester.Database.Iteration
import com.battery.energytester.Database.Test
import com.battery.energytester.Services.BroadcastTimerService
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.util.*
import kotlin.concurrent.fixedRateTimer


class MainActivity : AppCompatActivity(), TestAdapter.ClickListener {
    private var totalEnergy : Float = 0.0F
    private var avgPowerW : Float = 0.0F
    private var powerW : Float = 0.0F
    private var avgCurrentMA : Float = 0.0F
    private var currentMA : Float = 0.0F
    private var avgVoltageV : Float = 0.0F
    private var voltageV : Float = 0.0F
    private var startBatLevel : Int = 0
    private var endBatLevel : Int = 0

    private var testDuration : Long = 30
    private var testInterval : Int = 5
    private var testName : String = "test"

    private var currentTest = Test(0, testName, ArrayList(), 0, 0.0F,0.0F,0.0F,0.0F, 0.0F, 0, 0)
    private var testList = ArrayList<Test>()

    private var recording = false
    private lateinit var btnRecord : FloatingActionButton

    private val db = DBHelper(this, null)

    private val TAG = "BroadcastService"
    private val TAG_NETWORK = "CheckNetwork"

    private lateinit var adapter : TestAdapter

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        loadBatterySection()
        loadDatabaseData()
        loadTestSettings()
        addDrawers()

        /////////////////////////////////////////////////////////////////////////////
        //////////          CHANGES         //////


        /////////////////////////////////////////////////////////////////////////////

    }

    private fun openShowTestDialog() {
        val recyclerview = findViewById<RecyclerView>(R.id.test_recyler_view)

        recyclerview.layoutManager = LinearLayoutManager(this)
        adapter = TestAdapter(this, testList, this)
        recyclerview.adapter = adapter

        findViewById<Button>(R.id.btn_delete_tests).setOnClickListener {
            if (db.deleteAllTest()) {
                val size = testList.size
                testList.clear()
                adapter.notifyItemRangeRemoved(0, size)
            }
        }
    }

    override fun showInfo(position: Int) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_test_info);
        dialog.setCancelable(true)

        val test = testList[position]
        dialog.findViewById<TextView>(R.id.dialog_test_id).text = test.id.toString()
        dialog.findViewById<TextView>(R.id.dialog_test_name).text = test.name
        dialog.findViewById<TextView>(R.id.dialog_test_size).text = test.iteration_size.toString()
        dialog.findViewById<TextView>(R.id.dialog_test_duration).text = getString(R.string.duration_unit,test.duration.toString())
        dialog.findViewById<TextView>(R.id.dialog_test_voltage).text = getString(R.string.voltage_unit,test.avg_voltage.toString())
        dialog.findViewById<TextView>(R.id.dialog_test_current).text = getString(R.string.current_unit,test.avg_current.toString())
        dialog.findViewById<TextView>(R.id.dialog_test_power).text = getString(R.string.power_unit,test.avg_power.toString())
        dialog.findViewById<TextView>(R.id.dialog_test_energy).text = getString(R.string.energy_unit,test.energy.toString())
        dialog.findViewById<TextView>(R.id.dialog_test_start_bat_lev).text = test.startBatLevel.toString()
        dialog.findViewById<TextView>(R.id.dialog_test_end_bat_lev).text = test.endBatLevel.toString()

        dialog.findViewById<Button>(R.id.btn_test_del).setOnClickListener {
            val alertDialogBuilder = AlertDialog.Builder(this)
            alertDialogBuilder.setTitle("Delete test?")
            alertDialogBuilder.setMessage("Are you sure you want to delete this test?")
            alertDialogBuilder.setPositiveButton(android.R.string.yes) { _, _ ->
                db.deleteTest(test.id)
                testList.removeAt(position)
                adapter.notifyItemRemoved(position)
                adapter.notifyDataSetChanged()
                dialog.dismiss()
            }
            alertDialogBuilder.setNegativeButton(android.R.string.no) { _, _ ->
            }
            alertDialogBuilder.show()

        }
        dialog.show()

    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun loadTestSettings() {
        btnRecord = findViewById(R.id.btn_record)
        btnRecord.setOnClickListener {
            if (recording){
                Log.d(TAG, "STOPPED recording")
                recording = false
                BroadcastTimerService.stopService(this)
                btnRecord.setImageResource(android.R.drawable.ic_media_play)
            } else {
                resetEnvParameters()
                resetVariables()
                if (isOnline(this) || bluetoothOn()) {
                    Toast.makeText(this, "Check your network, bluetooth and others...", Toast.LENGTH_SHORT).show()
                }
                Log.d(TAG, "STARTED recording")
                recording = true
                loadBatterySection()
                BroadcastTimerService.startService(this, testInterval, testDuration)
                val filter = IntentFilter()
                filter.addAction("$packageName.countdown_br")
                registerReceiver(countDownTimerReceiver, filter)
                btnRecord.setImageResource(android.R.drawable.checkbox_off_background)
            }
        }
    }

    private fun bluetoothOn(): Boolean {
        val mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        return mBluetoothAdapter.isEnabled
    }

    private fun resetEnvParameters() {
        //brightness 50%
        val layout = window.attributes
        layout.screenBrightness = 0.5f
        window.attributes = layout

        //set audio to 50%
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        var max = audio.getStreamMaxVolume(AudioManager.STREAM_RING)
        audio.setStreamVolume(AudioManager.STREAM_RING, max/2, AudioManager.FLAG_PLAY_SOUND)
        max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        audio.setStreamVolume(AudioManager.STREAM_ALARM, max/2, AudioManager.FLAG_PLAY_SOUND)
        max = audio.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
        audio.setStreamVolume(AudioManager.STREAM_VOICE_CALL, max/2, AudioManager.FLAG_PLAY_SOUND)
        max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, max/2, AudioManager.FLAG_PLAY_SOUND)
    }

    private fun turnOffServices(){
        //turn off wifi
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val panelIntent = Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
            //(panelIntent, 0)
            startForResult.launch(panelIntent)
        } else {
            // for previous android version
            val wifiManager = this.applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            wifiManager.isWifiEnabled = true
        }

        //turn off bluetooth
        val mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (mBluetoothAdapter.isEnabled) {
            mBluetoothAdapter.disable()
        }
    }
    @RequiresApi(Build.VERSION_CODES.M)
    val startForResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){}

    private fun isOnline(context: Context): Boolean {
        val connManager = context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val networkCapabilities = connManager.getNetworkCapabilities(connManager.activeNetwork)
            if (networkCapabilities == null) {
                Log.d(TAG_NETWORK, "Device Offline")
                return false
            } else {
                Log.d(TAG_NETWORK, "Device Online")
                return if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
                    networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)
                ) {
                    Log.d(TAG_NETWORK, "Connected to Internet")
                    true
                } else {
                    Log.d(TAG_NETWORK, "Not connected to Internet")
                    false
                }
            }
        } else {
            // below Marshmallow
            val activeNetwork = connManager.activeNetworkInfo
            if (activeNetwork?.isConnectedOrConnecting == true && activeNetwork.isAvailable) {
                Log.d(TAG_NETWORK, "Device Online")
                return when (activeNetwork.state) {
                    NetworkInfo.State.CONNECTED -> {
                        Log.d(TAG_NETWORK, " CONNECTED ")
                        true
                    }
                    NetworkInfo.State.CONNECTING -> {
                        Log.d(TAG_NETWORK, " CONNECTING ")
                        true
                    }
                    else -> {
                        Log.d(TAG_NETWORK, "NO Connection")
                        false
                    }
                }
            } else {
                Log.d(TAG_NETWORK, "Device Offline")
                return false
            }
        }
    }


    private fun resetVariables() {
        totalEnergy = 0.0F
        avgPowerW = 0.0F
        avgCurrentMA = 0.0F
        avgVoltageV = 0.0F
        startBatLevel = 0
        endBatLevel = 0

        currentTest.iterations.clear()
        currentTest.iteration_size = 0
        testName = findViewById<EditText>(R.id.test_name).text.toString() + "_" +System.currentTimeMillis()
        currentTest.name = testName
    }

    @SuppressLint("Range")
    private fun loadDatabaseData() {
        val cursor = db.getTests()
        // moving the cursor to first position and
        // appending value in the text view
        var tmpTest : Test
        if (cursor!=null && cursor.count >0) {
            cursor.moveToFirst()
            tmpTest = Test(
                cursor.getLong(cursor.getColumnIndex(DBHelper.ID_COL)),
                cursor.getString(cursor.getColumnIndex(DBHelper.NAME_COl)),
                ArrayList(),
                cursor.getInt(cursor.getColumnIndex(DBHelper.TOTAL_ITERATIONS_COl)),
                cursor.getFloat(cursor.getColumnIndex(DBHelper.TOTAL_DURATION_COl)),
                cursor.getFloat(cursor.getColumnIndex(DBHelper.AVG_CURRENT_COL)),
                cursor.getFloat(cursor.getColumnIndex(DBHelper.AVG_VOLTAGE_COL)),
                cursor.getFloat(cursor.getColumnIndex(DBHelper.AVG_POWER_COl)),
                cursor.getFloat(cursor.getColumnIndex(DBHelper.TOTAL_ENERGY_COL)),
                cursor.getInt(cursor.getColumnIndex(DBHelper.START_BAT_LEVEL)),
                cursor.getInt(cursor.getColumnIndex(DBHelper.END_BAT_LEVEL)))
            testList.add(0, tmpTest)

            // moving our cursor to next
            // position and appending values
            while (cursor.moveToNext()) {
                tmpTest = Test(
                    cursor.getLong(cursor.getColumnIndex(DBHelper.ID_COL)),
                    cursor.getString(cursor.getColumnIndex(DBHelper.NAME_COl)),
                    ArrayList(),
                    cursor.getInt(cursor.getColumnIndex(DBHelper.TOTAL_ITERATIONS_COl)),
                    cursor.getFloat(cursor.getColumnIndex(DBHelper.TOTAL_DURATION_COl)),
                    cursor.getFloat(cursor.getColumnIndex(DBHelper.AVG_CURRENT_COL)),
                    cursor.getFloat(cursor.getColumnIndex(DBHelper.AVG_VOLTAGE_COL)),
                    cursor.getFloat(cursor.getColumnIndex(DBHelper.AVG_POWER_COl)),
                    cursor.getFloat(cursor.getColumnIndex(DBHelper.TOTAL_ENERGY_COL)),
                    cursor.getInt(cursor.getColumnIndex(DBHelper.START_BAT_LEVEL)),
                    cursor.getInt(cursor.getColumnIndex(DBHelper.END_BAT_LEVEL)))
                testList.add(0, tmpTest)
            }
        }
        cursor!!.close()
    }

    private val batteryInfoReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.i(TAG, "Updating battery informations")
            updateBatteryData(intent)
        }
    }

    private fun loadBatterySection() {
        val intentFilter = IntentFilter()
        intentFilter.addAction(Intent.ACTION_POWER_CONNECTED)
        intentFilter.addAction(Intent.ACTION_POWER_DISCONNECTED)
        intentFilter.addAction(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryInfoReceiver, intentFilter)
    }

    private val countDownTimerReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateTimer(intent)
        }
    }

    private fun updateTimer(intent: Intent) {
        //Log.i(TAG, "Upadate Timer")
        if (intent.extras != null) {
            handleTestData(intent)

        }
    }

    private fun handleTestData(intent: Intent){
        val durationTv = findViewById<TextView>(R.id.recording_duration_left)
        val energyTv = findViewById<TextView>(R.id.current_energy)
        durationTv.text = resources.getQuantityString(
            R.plurals.time_left,
            intent.getLongExtra("countdown_seconds", 0).toInt(),
            intent.getLongExtra("countdown_seconds", 0).toInt()
        )

        val newPeriod = intent.getBooleanExtra("countdown_new_period", false)
        val finish = intent.getBooleanExtra("countdown_finish", false)
        if (newPeriod) {
            //save iteration data
            Log.i(TAG, "NEW PERIOD")
            val iteration = intent.getIntExtra("countdown_iteration", 0)
            val duration = intent.getFloatExtra("countdown_time_period", 0F)
            val totalDuration = intent.getLongExtra("countdown_total_duration", 0)
            val iterationData = Iteration(
                iteration,
                duration,
                currentMA,
                voltageV,
                powerW,
                energy = powerW * duration
            )
            currentTest.iterations.add(iterationData)
            avgPowerW += powerW
            avgCurrentMA += currentMA
            avgVoltageV += voltageV

            //save test data
            totalEnergy += powerW * duration
            energyTv.text = String.format(
                resources.getString(R.string.energy_text),
                String.format("%.3f", totalEnergy)
            )
            currentTest.duration = totalDuration.toFloat()
            currentTest.avg_current = avgCurrentMA / currentTest.iterations.size
            currentTest.avg_voltage = avgVoltageV / currentTest.iterations.size
            currentTest.avg_power = avgPowerW / currentTest.iterations.size
            currentTest.energy = totalEnergy
            currentTest.startBatLevel = startBatLevel
            currentTest.endBatLevel = endBatLevel
            loadBatterySection()
        }
        if (finish){
            recording = false
            currentTest.iteration_size = currentTest.iterations.size
            btnRecord.setImageResource(android.R.drawable.ic_media_play)
            durationTv.text = getString(R.string.timer_stopped)
            BroadcastTimerService.stopService(this)
            Log.i(TAG, "FINISHED - add new test")
            currentTest.id = db.addTest(currentTest)
            testList.add(0, currentTest.copy())
        }
    }

    private fun getBatteryCapacity(ctx: Context) {
        val mBatteryManager = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val capacity = mBatteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val totalCapacity = getBatteryTotalCapacity()
        val currCapacity = (capacity * totalCapacity / 100)
        findViewById<TextView>(R.id.capacityTv).text = "$currCapacity/$totalCapacity"

    }

    private fun getCurrent(ctx: Context): Long {
        val mBatteryManager = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return mBatteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
    }

    fun updateBatteryData(intent: Intent) {
        val present = intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false)
        val healthTv = findViewById<TextView>(R.id.healthTv)
        val batteryPctTv = findViewById<TextView>(R.id.batteryPctTv)
        val pluggedTv = findViewById<TextView>(R.id.pluggedTv)
        val chargingStatusTv = findViewById<TextView>(R.id.chargingStatusTv)
        val tempTv = findViewById<TextView>(R.id.tempTv)
        val voltageTv = findViewById<TextView>(R.id.voltageTv)
        val currentTv = findViewById<TextView>(R.id.currentTv)
        val powerTv = findViewById<TextView>(R.id.powerTv)

        if (present) {
            getBatteryCapacity(this)

            val health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)
            var healthLbl = -1

            when (health) {
                BatteryManager.BATTERY_HEALTH_COLD ->
                    healthLbl = R.string.battery_health_cold

                BatteryManager.BATTERY_HEALTH_DEAD ->
                    healthLbl = R.string.battery_health_dead

                BatteryManager.BATTERY_HEALTH_GOOD ->
                    healthLbl = R.string.battery_health_good

                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE ->
                    healthLbl = R.string.battery_health_over_voltage

                BatteryManager.BATTERY_HEALTH_OVERHEAT ->
                    healthLbl = R.string.battery_health_overheat

                BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE ->
                    healthLbl = R.string.battery_health_unspecified_failure

                BatteryManager.BATTERY_HEALTH_UNKNOWN ->
                    healthLbl = R.string.battery_health_unokwn
            }

            if (healthLbl != -1) {
                // display battery health ...
                healthTv.text = getString(healthLbl)
            }

            // Calculate Battery Percentage ...
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)

            if (level != -1 && scale != -1) {
                val batteryPct = ((level / scale.toFloat()) * 100f).toInt()
                batteryPctTv.text =  String.format(resources.getString(R.string.battery_pct_text), batteryPct)
                if (startBatLevel == 0){
                    startBatLevel = batteryPct
                }
                endBatLevel = batteryPct
            }

            val pluggedLbl = when (intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
                BatteryManager.BATTERY_PLUGGED_WIRELESS ->
                    R.string.battery_plugged_wireless

                BatteryManager.BATTERY_PLUGGED_USB ->
                    R.string.battery_plugged_usb

                BatteryManager.BATTERY_PLUGGED_AC ->
                    R.string.battery_plugged_ac

                else -> {
                    R.string.battery_plugged_none
                }
            }

            // display plugged status ...
            pluggedTv.text =  String.format(resources.getString(R.string.plugged_text),getString(pluggedLbl))

            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            var statusLbl = R.string.battery_status_discharging

            when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING ->
                    statusLbl = R.string.battery_status_charging

                BatteryManager.BATTERY_STATUS_DISCHARGING ->
                    statusLbl = R.string.battery_status_discharging

                BatteryManager.BATTERY_STATUS_FULL ->
                    statusLbl = R.string.battery_status_full

                BatteryManager.BATTERY_STATUS_UNKNOWN ->
                    statusLbl = -1

                BatteryManager.BATTERY_STATUS_NOT_CHARGING ->
                    statusLbl = R.string.battery_status_discharging
            }

            if (statusLbl != -1) {
                chargingStatusTv.text = String.format(resources.getString(R.string.battery_charging_status), getString(statusLbl)) //"Battery Charging Status
            }

            val temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)

            if (temperature > 0) {
                val temp = temperature.toFloat() / 10f
                tempTv.text = String.format(resources.getString(R.string.temperature_text), temp)
            }

            val voltageTmp = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)

            voltageV = voltageTmp.toFloat() //mV
            //voltageV /= 1000 //V
            voltageTv.text =  String.format(resources.getString(R.string.voltage_text), voltageV)

            val currentTmp = getCurrent(this) // μA - microAmpers

            currentMA = currentTmp.toFloat() / 1000 // mA - miliAmpers

            currentTv.text = String.format(resources.getString(R.string.current_text), currentMA)


            powerW = (voltageV  * currentMA / 1000) //Volt * Amp
            powerTv.text =  String.format(resources.getString(R.string.power_text), powerW)

        } else {
            Toast.makeText(this, "No Battery present", Toast.LENGTH_SHORT).show()
        }

    }

    @SuppressLint("PrivateApi")
    fun getBatteryTotalCapacity(): Double {
        var battCapacity = 0.0
        var mPowerProfile: Any? = null
        val POWER_PROFILE_CLASS = "com.android.internal.os.PowerProfile"
        try {
            mPowerProfile = Class.forName(POWER_PROFILE_CLASS)
                .getConstructor(Context::class.java).newInstance(this)
        } catch (e: Exception) {
            e.printStackTrace()
            Log.d(TAG, "Try 1 - Exception e: $e")
        }
        try {
            battCapacity = Class
                .forName(POWER_PROFILE_CLASS)
                .getMethod("getAveragePower", String::class.java)
                .invoke(mPowerProfile, "battery.capacity") as Double
        } catch (e: Exception) {
            e.printStackTrace()
            Log.d(TAG, "Try 2 - Exception e: $e")
        }
        return battCapacity
    }

    override fun onDestroy() {

        Log.i(TAG, "Main destroyed")
        super.onDestroy()

    }

    override fun onPause() {
        Log.i(TAG, "Main Paused")
        super.onPause()
    }

    override fun onResume() {
        Log.i(TAG, "Main resumed")
        super.onResume()
    }

    private fun addDrawers() {
        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        findViewById<View>(R.id.btn_drawer_open).setOnClickListener {
            if (!recording) drawerLayout.openDrawer(GravityCompat.START)
        }
        findViewById<View>(R.id.btn_drawer_open).setOnLongClickListener {
            if (!recording) drawerLayout.openDrawer(GravityCompat.END)
            openShowTestDialog()
            true
        }
        val numberPicker = findViewById<NumberPicker>(R.id.recording_duration)
        numberPicker.maxValue = 60
        numberPicker.minValue = 1
        numberPicker.value = testDuration.toInt()
        numberPicker.wrapSelectorWheel = true
        numberPicker.setOnValueChangedListener {_, _, newVal ->
            testDuration = newVal.toLong()
        }
        val numberPicker1 = findViewById<NumberPicker>(R.id.interval_duration)
        numberPicker1.maxValue = 60
        numberPicker1.minValue = 1
        numberPicker1.value = testInterval
        numberPicker1.wrapSelectorWheel = true
        numberPicker1.setOnValueChangedListener {_, _, newVal ->
            testInterval = newVal
        }

        findViewById<ImageButton>(R.id.btn_refresh_battery_info).setOnClickListener {
            loadBatterySection()
        }
        findViewById<Button>(R.id.btn_turn_off_services).setOnClickListener {
            turnOffServices()
        }
        findViewById<Button>(R.id.btn_reset_env).setOnClickListener {
            resetEnvParameters()
        }
        drawerLayout.setDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(view: View, v: Float) {
            }
            override fun onDrawerOpened(view: View) {

            }

            override fun onDrawerClosed(view: View) {
            }

            override fun onDrawerStateChanged(i: Int) {
            }
        })
    }


}