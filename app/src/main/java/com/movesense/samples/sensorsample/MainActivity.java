package com.movesense.samples.sensorsample;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.google.gson.Gson;
import com.movesense.mds.Mds;
import com.movesense.mds.MdsConnectionListener;
import com.movesense.mds.MdsException;
import com.movesense.mds.MdsNotificationListener;
import com.movesense.mds.MdsResponseListener;
import com.movesense.mds.MdsSubscription;
import com.polidea.rxandroidble.RxBleClient;
import com.polidea.rxandroidble.RxBleDevice;
import com.polidea.rxandroidble.scan.ScanSettings;

import java.util.ArrayList;

import rx.Subscription;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemLongClickListener, AdapterView.OnItemClickListener  {
    private static final String LOG_TAG = MainActivity.class.getSimpleName();
    private static final int MY_PERMISSIONS_REQUEST_LOCATION = 1;

    // MDS
    private Mds mMds;
    public static final String URI_CONNECTEDDEVICES = "suunto://MDS/ConnectedDevices";
    public static final String URI_EVENTLISTENER = "suunto://MDS/EventListener";
    public static final String SCHEME_PREFIX = "suunto://";

    public static final String EXTRA_MESSAGE =
            "com.example.android.sensorsample.extra.MESSAGE";
    public int scans = 0;
    public int deadlift = 0;
    public int squat = 0;
    public int curl = 0;
    public int bench = 0;
    public double weightEntered = 0.0;
    public double oneRepPercent = 1.0;

    // BleClient singleton
    static private RxBleClient mBleClient;

    // UI
    private ListView mScanResultListView;
    private ArrayList<MyScanResult> mScanResArrayList = new ArrayList<>();
    ArrayAdapter<MyScanResult> mScanResArrayAdapter;

    // Sensor subscription
    static private String URI_MEAS_ACC_13 = "/Meas/Acc/13";
    private MdsSubscription mdsSubscription;
    private String subscribedDeviceSerial;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Init Scan UI
        mScanResultListView = (ListView)findViewById(R.id.listScanResult);
        mScanResArrayAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, mScanResArrayList);
        mScanResultListView.setAdapter(mScanResArrayAdapter);
        mScanResultListView.setOnItemLongClickListener(this);
        mScanResultListView.setOnItemClickListener(this);

        // Make sure we have all the permissions this app needs
        requestNeededPermissions();

        // Initialize Movesense MDS library
        initMds();
        findViewById(R.id.StartStop).setVisibility(View.GONE);
        findViewById(R.id.weightIn).setVisibility(View.GONE);
        findViewById(R.id.enterButton).setVisibility(View.GONE);
    }

    private RxBleClient getBleClient() {
        // Init RxAndroidBle (Ble helper library) if not yet initialized
        if (mBleClient == null)
        {
            mBleClient = RxBleClient.create(this);
        }

        return mBleClient;
    }

    private void initMds() {
        mMds = Mds.builder().build(this);
    }

    void requestNeededPermissions()
    {
        // Here, thisActivity is the current activity
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // No explanation needed, we can request the permission.
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                MY_PERMISSIONS_REQUEST_LOCATION);

        }
    }

    Subscription mScanSubscription;
    public void onScanClicked(View view) {
        findViewById(R.id.buttonScan).setVisibility(View.GONE);
        findViewById(R.id.buttonScanStop).setVisibility(View.VISIBLE);

        // Start with empty list
        mScanResArrayList.clear();
        mScanResArrayAdapter.notifyDataSetChanged();

        mScanSubscription = getBleClient().scanBleDevices(
                new ScanSettings.Builder()
                        // .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // change if needed
                        // .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES) // change if needed
                        .build()
                // add filters if needed
        )
                .subscribe(
                        scanResult -> {
                            Log.d(LOG_TAG,"scanResult: " + scanResult);

                            // Process scan result here. filter movesense devices.
                            if (scanResult.getBleDevice()!=null &&
                                    scanResult.getBleDevice().getName() != null &&
                                    scanResult.getBleDevice().getName().startsWith("Movesense")) {

                                // replace if exists already, add otherwise
                                MyScanResult msr = new MyScanResult(scanResult);
                                if (mScanResArrayList.contains(msr))
                                    mScanResArrayList.set(mScanResArrayList.indexOf(msr), msr);
                                else
                                    mScanResArrayList.add(0, msr);

                                mScanResArrayAdapter.notifyDataSetChanged();
                            }
                        },
                        throwable -> {
                            Log.e(LOG_TAG,"scan error: " + throwable);
                            // Handle an error here.

                            // Re-enable scan buttons, just like with ScanStop
                            onScanStopClicked(null);
                        }
                );
    }

    public void onScanStopClicked(View view) {
        if (mScanSubscription != null)
        {
            mScanSubscription.unsubscribe();
            mScanSubscription = null;
        }

        findViewById(R.id.buttonScan).setVisibility(View.VISIBLE);
        findViewById(R.id.buttonScanStop).setVisibility(View.GONE);
    }

    public void weightEntered(View v){
        EditText text=(EditText)findViewById(R.id.weightIn);
        weightEntered = Double.parseDouble(text.getText().toString());
        ((TextView) findViewById(R.id.output)).setText("You entered: " +weightEntered);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (position < 0 || position >= mScanResArrayList.size())
            return;

        MyScanResult device = mScanResArrayList.get(position);
        if (!device.isConnected()) {
            // Stop scanning
            onScanStopClicked(null);

            // And connect to the device
            connectBLEDevice(device);
        }
        else {
            // Device is connected, trigger showing /Info
            subscribeToSensor(device.connectedSerial);
            findViewById(R.id.listScanResult).setVisibility(View.GONE);
            findViewById(R.id.buttonScan).setVisibility(View.GONE);
            findViewById(R.id.helpText).setVisibility(View.GONE);
            findViewById(R.id.sensorMsg).setVisibility(View.GONE);
            findViewById(R.id.buttonScanStop).setVisibility(View.GONE);
            findViewById(R.id.buttonUnsubscribe).setVisibility(View.GONE);
            findViewById(R.id.StartStop).setVisibility(View.VISIBLE);
            findViewById(R.id.weightIn).setVisibility(View.VISIBLE);
            findViewById(R.id.enterButton).setVisibility(View.VISIBLE);
        }
    }

    boolean started = false;
    public void startStop(View view){
        if(!started) {
            scans =0;
            deadlift=0;
            bench=0;
            squat=0;
            totalAccList.clear();
            Handler handler = new Handler();
            ((TextView) findViewById(R.id.output)).setText("Started listening");
        }else{
            started=false;
            double [] copy = new double[totalAccList.size()];
            int tmp=0;
            for (Double d:totalAccList) {
                copy[tmp++] = d.doubleValue();
            }
            double [] movingAvg = new double[copy.length-2];
            double average=0;
            double max=0;
            double min=1000;
            for(int i=2;i<copy.length;i++){
                movingAvg[i-2]= (copy[i] + copy[i-2] + copy[i-1])/3;
                if(movingAvg[i-2]>max){
                    max=movingAvg[i-2];
                }
                if(movingAvg[i-2]<min){
                    min=movingAvg[i-2];
                }
                average=average+movingAvg[i-2];
            }
            average = (average/movingAvg.length);
            int reps=0;
            double []sqDiff = new double[movingAvg.length];
            for (int i = 0; i < movingAvg.length; i++) {
                sqDiff[i] = (movingAvg[i]-average)*(movingAvg[i]-average);
            }
            double tot=0;
            for(int i = 0; i < sqDiff.length; i++){
                tot = tot + sqDiff[i];
            }
            double stDev = Math.sqrt(tot/sqDiff.length);
            for(int i=1; i<movingAvg.length-1; i++){
                if(movingAvg[i]>movingAvg[i-1] && movingAvg[i]>movingAvg[i+1]){
                    if(movingAvg[i] > (average + stDev)){
                    reps++;
                }}
            }

            if(reps == 1){
                oneRepPercent = 1.0;
            }
            if(reps >= 2){
                oneRepPercent = .95;
            }
            if(reps >= 4){
                oneRepPercent = .90;
            }
            if(reps >= 6){
                oneRepPercent = .85;
            }
            if(reps >= 8){
                oneRepPercent = .77;
            }
            if(reps >= 10){
                oneRepPercent = .72;
            }
            if(reps >= 12){
                oneRepPercent = .67;
            }
            if(reps >= 16){
                oneRepPercent = .62;
            }
            if(reps >= 20){
                oneRepPercent = .55;
            }

            if(deadlift>bench){
                if(deadlift>curl){
                    if(deadlift>squat){
                        double maxw = weightEntered/oneRepPercent;
                        String temp = "You were deadlifting"+"\n" +
                                "with an average acceleration of "+average+" m/s.\n"+
                                "Max: "+max +"\nMin: "+min+"\nYou did "+reps+" reps\n" + "One Rep Max: " + Math.round(maxw);
                        ((TextView) findViewById(R.id.output)).setText(temp);
                    }else{
                        double maxw = weightEntered/oneRepPercent;
                        String temp = "You were squatting"+"\n" +
                                "with an average acceleration of "+average+" m/s."+"\n"+
                                "Max: "+max +"\nMin: "+min+"\nYou did "+reps+" reps\n" + "One Rep Max: " + Math.round(maxw);
                        ((TextView) findViewById(R.id.output)).setText(temp);
                    }
                }else{
                    if(squat>curl){
                        double maxw = weightEntered/oneRepPercent;
                        String temp = "You were squatting"+"\n" +
                                "with an average acceleration of "+average+" m/s."+"\n"+
                                "Max: "+max +"\nMin: "+min+"\nYou did "+reps+" reps\n" + "One Rep Max: " + Math.round(maxw);
                        ((TextView) findViewById(R.id.output)).setText(temp);
                    }else{
                        double maxw = weightEntered/oneRepPercent;
                        String temp = "You were doing biceps curls"+"\n" +
                                "with an average acceleration of "+average+" m/s."+"\n"+
                                "Max: "+max +"\nMin: "+min+"\nYou did "+reps+" reps\n" + "One Rep Max: " + Math.round(maxw);
                        ((TextView) findViewById(R.id.output)).setText(temp);
                    }
                }
            }else{
                if(bench>curl){
                    if(bench>squat){
                        double maxw = weightEntered/oneRepPercent;
                        String temp = "You were benching"+"\n" +
                                "with an average acceleration of "+average+" m/s."+"\n"+
                                "Max: "+max +"\nMin: "+min+"\nYou did "+reps+" reps\n" + "One Rep Max: " + Math.round(maxw);
                        ((TextView) findViewById(R.id.output)).setText(temp);
                    }else{
                        double maxw = weightEntered/oneRepPercent;
                        String temp = "You were squatting"+"\n" +
                                "with an average acceleration of "+average+" m/s."+"\n"+
                                "Max: "+max +"\nMin: "+min+"\nYou did "+reps+" reps\n" + "One Rep Max: " + Math.round(maxw);
                        ((TextView) findViewById(R.id.output)).setText(temp);
                    }
                }else{
                    if(curl>squat){
                        double maxw = weightEntered/oneRepPercent;
                        String temp = "You were doing biceps curls"+"\n" +
                                "with an average acceleration of "+average+" m/s."+"\n"+
                                "Max: "+max +"\nMin: "+min+"\nYou did "+reps+" reps\n" + "One Rep Max: " + Math.round(maxw);
                        ((TextView) findViewById(R.id.output)).setText(temp);
                    }else{
                        double maxw = weightEntered/oneRepPercent;
                        String temp = "You were squatting"+"\n" +
                                "with an average acceleration of "+average+" m/s."+"\n"+
                                "Max: "+max +"\nMin: "+min+"\nYou did "+reps+" reps\n" + "One Rep Max: " + Math.round(maxw);
                        ((TextView) findViewById(R.id.output)).setText(temp);
                    }
                }
            }
        }
    }

    int slowDownPrint=0;
    ArrayList <Double>totalAccList= new <Double>ArrayList();
    private void subscribeToSensor(String connectedSerial) {
        // Clean up existing subscription (if there is one)
        if (mdsSubscription != null) {
            unsubscribe();
        }

        // Build JSON doc that describes what resource and device to subscribe
        // Here we subscribe to 13 hertz accelerometer data
        StringBuilder sb = new StringBuilder();
        String strContract = sb.append("{\"Uri\": \"").append(connectedSerial).append(URI_MEAS_ACC_13).append("\"}").toString();
        Log.d(LOG_TAG, strContract);
        final View sensorUI = findViewById(R.id.sensorUI);

        subscribedDeviceSerial = connectedSerial;

        mdsSubscription = mMds.builder().build(this).subscribe(URI_EVENTLISTENER,
                strContract, new MdsNotificationListener() {
                    @Override
                    public void onNotification(String data) {
                        Log.d(LOG_TAG, "onNotification(): " + data);

                        // If UI not enabled, do it now
                        if (sensorUI.getVisibility() == View.GONE)
                            sensorUI.setVisibility(View.VISIBLE);


                        AccDataResponse accResponse = new Gson().fromJson(data, AccDataResponse.class);
                        if (accResponse != null && accResponse.body.array.length > 0) {
                            if(slowDownPrint==0) {
                                double x = accResponse.body.array[0].x;
                                double y = accResponse.body.array[0].y;
                                double z = accResponse.body.array[0].z;
                                if (Math.abs(x) < 1) { x = 0; }
                                if (Math.abs(y) < 1) { y = 0; }
                                if (Math.abs(z) < 1) { z = 0; }
                                x = Math.round(x*10.0)/10.0;
                                y = Math.round(y*10.0)/10.0;
                                z = Math.round(z*10.0)/10.0;
                                String accStr = String.format("%.02f, %.02f, %.02f", x, y, z);
                                double roll = Math.round(Math.atan2(y , z) * 57.3);
                                double pitch = Math.round(Math.atan2((-x) ,Math.sqrt(y*y+z*z)) * 57.3);
                                accStr = accStr+"\nRoll: "+roll+" Pitch:" + pitch;
                                double totalAccNoGrav;
                                double actualZ= z*(Math.cos(pitch) + Math.cos(roll)) +x*Math.sin(pitch) + y*Math.sin(roll);
                                actualZ = Math.round(actualZ*10.0)/10.0;
                                double squaredTot = x*x+y*y+z*z;
                                totalAccNoGrav = Math.sqrt(squaredTot);
                                squaredTot =Math.round(squaredTot*10.0)/10.0;
                                totalAccNoGrav = totalAccNoGrav-9.8;
                                totalAccNoGrav = Math.round(totalAccNoGrav*10.0)/10.0;
                                totalAccList.add(new Double(totalAccNoGrav));
                                double Azr = Math.acos(z/squaredTot);
                                //figure out orientation w/gyro and use that to determine lift move then worry about acc
                                ((TextView) findViewById(R.id.sensorMsg)).setText(accStr);
                                if(true){
                                    scans++;
                                    if((roll>70 && roll<100)&&(pitch>0 && pitch<20)){
                                        //benching
                                        bench++;
                                        //((TextView) findViewById(R.id.output)).setText("Benching");
                                    }else if((Math.abs(roll)<-640) &&(Math.abs(pitch)<-940)){
                                        //palm facing ground~ish

                                        //((TextView) findViewById(R.id.output)).setText("Palm facing floor");
                                    }else if((roll>660 && roll<120) && (pitch>660 && pitch<120)){
                                        //palm facing body~ish

                                        //((TextView) findViewById(R.id.output)).setText("Palm facing body");
                                    }else if((roll > -110 && roll < -75) &&(pitch > -10 && pitch < 10)){
                                        //deadlifting
                                        deadlift++;
                                        //((TextView) findViewById(R.id.output)).setText("Deadlifting");
                                    }else if((roll>70 && roll<120) && (pitch>-40 && pitch<0)){
                                        //palm facing sky~ish
                                        curl++;
                                        //((TextView) findViewById(R.id.output)).setText("Palm facing sky");
                                    }else if((roll>40 && roll<75)&&(pitch>-10 && pitch<10)){
                                        //squatting
                                        squat++;
                                        //((TextView) findViewById(R.id.output)).setText("Squatting");
                                    }else{
                                        //unknown
                                        //((TextView) findViewById(R.id.output)).setText("Unknown");
                                    }
                                }
                            }
                        }
                    }

                    @Override
                    public void onError(MdsException error) {
                        Log.e(LOG_TAG, "subscription onError(): ", error);
                        unsubscribe();
                    }
                });

    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        if (position < 0 || position >= mScanResArrayList.size())
            return false;

        MyScanResult device = mScanResArrayList.get(position);

        // unsubscribe if there
        Log.d(LOG_TAG, "onItemLongClick, " + device.connectedSerial + " vs " + subscribedDeviceSerial);
        if (device.connectedSerial.equals(subscribedDeviceSerial))
            unsubscribe();

        Log.i(LOG_TAG, "Disconnecting from BLE device: " + device.macAddress);
        mMds.disconnect(device.macAddress);

        return true;
    }

    private void connectBLEDevice(MyScanResult device) {
        RxBleDevice bleDevice = getBleClient().getBleDevice(device.macAddress);

        Log.i(LOG_TAG, "Connecting to BLE device: " + bleDevice.getMacAddress());
        mMds.connect(bleDevice.getMacAddress(), new MdsConnectionListener() {

            @Override
            public void onConnect(String s) {
                Log.d(LOG_TAG, "onConnect:" + s);
            }

            @Override
            public void onConnectionComplete(String macAddress, String serial) {
                for (MyScanResult sr : mScanResArrayList) {
                    if (sr.macAddress.equalsIgnoreCase(macAddress)) {
                        sr.markConnected(serial);
                        break;
                    }
                }
                mScanResArrayAdapter.notifyDataSetChanged();
            }

            @Override
            public void onError(MdsException e) {
                Log.e(LOG_TAG, "onError:" + e);

                showConnectionError(e);
            }

            @Override
            public void onDisconnect(String bleAddress) {

                Log.d(LOG_TAG, "onDisconnect: " + bleAddress);
                for (MyScanResult sr : mScanResArrayList) {
                    if (bleAddress.equals(sr.macAddress))
                    {
                        // unsubscribe if was subscribed
                        if (sr.connectedSerial != null && sr.connectedSerial.equals(subscribedDeviceSerial))
                            unsubscribe();

                        sr.markDisconnected();
                    }
                }
                mScanResArrayAdapter.notifyDataSetChanged();
            }
        });
    }

    private void showConnectionError(MdsException e) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Connection Error:")
                .setMessage(e.getMessage());

        builder.create().show();
    }

    private void unsubscribe() {
        if (mdsSubscription != null) {
            mdsSubscription.unsubscribe();
            mdsSubscription = null;
        }

        subscribedDeviceSerial = null;

        // If UI not invisible, do it now
        final View sensorUI = findViewById(R.id.sensorUI);
        if (sensorUI.getVisibility() != View.GONE)
            sensorUI.setVisibility(View.GONE);

    }
    public void onUnsubscribeClicked(View view) {
        unsubscribe();
    }
}
