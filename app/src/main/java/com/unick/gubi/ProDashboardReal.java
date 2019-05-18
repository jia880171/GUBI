package com.unick.gubi;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.gms.maps.model.LatLng;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;

import at.grabner.circleprogress.CircleProgressView;
import at.grabner.circleprogress.TextMode;

public class ProDashboardReal extends AppCompatActivity {

    private final boolean GPS_Test = false;
    private static final int speed_driving_start = 20;
    private static final int speed_driving_stop = 2;

    private int mCount = 0;
    private Double speed = 0.0;
    public int transportType = 0;
    private long timeInMilli;

    private TextView text_time;
    private TextView text_lat;
    private TextView text_lng;
    private TextView text_latLng;
    private TextView text_speed;
    private TextView text_degree;

    private CircleProgressView circleProgressView_speed;
    private CircleProgressView circleProgressView_lat;
    private CircleProgressView circleProgressView_lng;

    private ProgressBar progressBar_lat;
    private ProgressBar progressBar_lng;

    private StringBuilder stringBuilder_acc;

    Runnable mRun;
    private boolean flagFormRun;
    private boolean flagForRecording;
    private boolean flag_have_record;

    //生成一個JsonArray
    JSONArray JArray;

    public final String LM_GPS = LocationManager.GPS_PROVIDER;
    public final String LM_NETWORK = LocationManager.NETWORK_PROVIDER;

    // 定位管理器
    private LocationManager mLocationManager;

    // 定位監聽器
    private LocationListener mLocationListener;
    private LatLng latLng;
    private Date time;
    private Notification.Builder myNotificationBuilder;
    private Notification notification;

    //轉向器
    private SensorManager sensor_manager;
    private MySensorEventListener listener;
    private float degree;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pro_dashboard_real);

        text_time = (TextView) findViewById(R.id.text_time);
        text_lat = (TextView) findViewById(R.id.text_lat);
        text_lng = (TextView) findViewById(R.id.text_lng);
        text_latLng = (TextView) findViewById(R.id.text_latLng);
        text_speed = (TextView) findViewById(R.id.text_speed);
        text_degree = (TextView) findViewById(R.id.text_degree);

        circleProgressView_lat = findViewById(R.id.circleProgress_lat);
        circleProgressView_lat.setTextMode(TextMode.TEXT); // Set text mode to text to show text
        setCircleProgressView(circleProgressView_lat,100,100,50,90,23, Color.parseColor("#ffffff"));

        circleProgressView_lng = findViewById(R.id.circleProgress_lng);
        setCircleProgressView(circleProgressView_lng,100,100,0,180,120, Color.parseColor("#ffffff"));

        circleProgressView_speed = findViewById(R.id.circleProgress_speed);
        circleProgressView_speed.setBarColor(Color.parseColor("#FF8033"));
        circleProgressView_speed.setTextMode(TextMode.TEXT);
        circleProgressView_speed.setBlockCount(10);
        circleProgressView_speed.setBlockScale(0.9f);
        setCircleProgressView(circleProgressView_speed,100,100,100,200,60, Color.parseColor("#ffffff"));

        progressBar_lat = findViewById(R.id.progressBar_lat);
        progressBar_lat.setMax(90);
        progressBar_lat.setProgress(23);

        progressBar_lng = findViewById(R.id.progressBar_lng);
        progressBar_lng.setMax(180);
        progressBar_lng.setProgress(120);

        //set LocationManager
        checkLocationPermission();
        if (mLocationManager == null) {
            mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        }
        mLocationListener = new MyLocationListener();
        mLocationManager.requestLocationUpdates(LM_GPS, 0, 0, mLocationListener);
        mLocationManager.requestLocationUpdates(LM_NETWORK, 0, 0, mLocationListener);




        //set getOrientation
        sensor_manager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        Sensor aSensor = sensor_manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Sensor mfSensor = sensor_manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        listener = new MySensorEventListener();
        sensor_manager.registerListener(listener, aSensor, SensorManager.SENSOR_DELAY_NORMAL);
        sensor_manager.registerListener(listener, mfSensor, SensorManager.SENSOR_DELAY_NORMAL);

        //thread for checking speed
        stringBuilder_acc = new StringBuilder();
        flagFormRun = true;
        flag_have_record = false;
        JArray = new JSONArray();
        new Thread(mRun = new Runnable() {
            @Override
            public void run() {
                while (flagFormRun){
                    try {
                        if(GPS_Test){
                            GPSTest();
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    fakeSpeed();
                                    SetText();
                                }
                            });
                            Thread.sleep(500);//record rate : 2 per/sec
                        }else{
                            GPSReal();
                            if(flagForRecording){
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        SetText();
                                    }
                                });
                                Thread.sleep(500);//record rate : 2 per/sec
                            }else{
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        timeInMilli = System.currentTimeMillis();
                                        SetText();
                                    }
                                });
                                Thread.sleep(1000);//checking rate : 1 per/sec
                            }
                        }
                    }
                    catch (Exception e){
                    }
                }
            }
        }).start();

    }
    private void setCircleProgressView(CircleProgressView circleProgressView, int BarWidth, int RimWidth, int TextSize, int MaxValue, int Value, int TextColor){
        circleProgressView.setBarWidth(BarWidth);
        circleProgressView.setRimWidth(RimWidth);
        circleProgressView.setTextSize(TextSize);
        circleProgressView.setMaxValue(MaxValue);
        circleProgressView.setTextColor(TextColor);
        circleProgressView.setValue(Value);
    }

    private void fakeSpeed(){
        if(speed < 10){
            speed = 15.0;
        }
        if(Math.random()>0.5){
            speed = speed + Math.random()*10;
        }else{
            speed = speed - Math.random()*10;
        }

    }

    //get GPS, trigger by real speed
    private void GPSReal() {
        if(flagForRecording ==false && speed>=speed_driving_start){
            flagForRecording =true;
            Log.d("proReal","speed >= 20, start recording!");
        } else if(flagForRecording){
            Log.d("proReal","speed = " + speed);
            Log.d("proReal","recording...");
            timeInMilli = System.currentTimeMillis();
            Log.d("proREAL,get currentTime","time: " + timeInMilli);
            AppendJsonObject();
            SetText();
            if(speed > speed_driving_stop){
                Log.d("inService","speed >5, set mCount to 0");
                mCount=0;
            } else if(speed < speed_driving_stop){
                mCount = mCount +1;
                Log.d("inService","speed <5, mCount ++");
                Log.d("inService","mCount: " + mCount);
                if(mCount >= 5){
                    mCount = 0;
                    flagForRecording = false;
                    Log.d("inService","mCount>=5, mCount: " + mCount);
                    Log.d("inService","speed: " + speed);
                    Log.d("inService","start uploading!");
                    //writeToFirebase(JArray.toString());//upload to fireBase
                    JArray = new JSONArray();
                }
            }
            //Thread.sleep(500);//record rate : 2 per/sec
        } else {
            Log.d("checking","60 per/min");
            stringBuilder_acc = new StringBuilder();
            //Thread.sleep(60000);//checking rate : 1 per/min
        }
    }

    //test function
    private void GPSTest() {
        if(mCount >=10){
            Log.d("mCount>=10","!!!!");
            mCount = 0;
            JArray = new JSONArray();
        }
        mCount = mCount +1;
        Log.d("uploadService","mCount: " + mCount);
        Log.d("uploadService","transportation type" + transportType);
        Log.d("uploadService","latLng: " + latLng);
        timeInMilli = System.currentTimeMillis();
        AppendJsonObject();
    }


    private void SetText(){
        text_time.setText(String.valueOf(timeInMilli));
        Log.d("proREAL","time: " + timeInMilli);

        //須優化程式碼
        if(ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED){
            if(latLng == null){
                Log.d("ProDashTest","latLng is null, get last known");
                checkLocationPermission();
                if(mLocationManager.isProviderEnabled(LM_GPS) || mLocationManager.isProviderEnabled(LM_NETWORK)){
                    Location location = mLocationManager.getLastKnownLocation(LM_GPS);
                    if(location == null){
                        Log.d("ProDashTest","no gps use network location!");
                        location = mLocationManager.getLastKnownLocation(LM_NETWORK);
                    }
                    if(location == null){
                        text_latLng.setText("還未抓取到GPS");
                    }else{
                        latLng = new LatLng(location.getLatitude(),location.getLongitude());
                        String lng= Double.toString(latLng.longitude);
                        String lat= Double.toString(latLng.latitude);
                        Double dLng = Double.parseDouble(lng);
                        Double dLat = Double.parseDouble(lat);

                        circleProgressView_lng.setValue(dLng.intValue());
                        circleProgressView_lat.setValue(dLat.intValue());
                        circleProgressView_lat.setText(lat);

                        progressBar_lat.setProgress(dLat.intValue());
                        progressBar_lng.setProgress(dLng.intValue());

                        Log.d("ProDashTest","set text by last known!");
                        text_lng.setText("Longitude: " + lng);
                        text_lat.setText("Latitude: " + lat);
                        text_latLng.setText("(" + lat + "," + lng + ")");
                    }
                }else{
                    text_latLng.setText("還未抓取到GPS");
                }
            }else{
                Log.d("ProDashTest","has latLng!");
                String lng= Double.toString(latLng.longitude);
                String lat= Double.toString(latLng.latitude);
                Double dLng = Double.parseDouble(lng);
                Double dLat = Double.parseDouble(lat);
                circleProgressView_lng.setValue(dLng.intValue());
                circleProgressView_lat.setValue(dLat.intValue());
                progressBar_lat.setProgress(dLat.intValue());
                progressBar_lng.setProgress(dLng.intValue());
                text_lng.setText("Longitude: " + lng);
                text_lat.setText("Latitude: " + lat);
                text_latLng.setText("(" + lat + "," + lng + ")");
            }
        }


        String stringSpeed= String.valueOf(speed.intValue());
        text_speed.setText("speed: " + stringSpeed);
        circleProgressView_speed.setValue(speed.intValue());
        circleProgressView_speed.setText(stringSpeed+"KM/H");
        String stringDegree= Float.toString(degree);
        text_degree.setText(stringDegree);


    }
    public void AppendJsonObject(){
        JSONObject jsonObj =new JSONObject();
        try {
            jsonObj.put("time", timeInMilli);
            jsonObj.put("latLng", latLng);
            jsonObj.put("speed", speed);
            jsonObj.put("degree", degree);
            jsonObj.put("type", transportType);
            JArray.put(jsonObj);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // 定位監聽器實作
    private class MyLocationListener implements LocationListener {
        // GPS位置資訊已更新
        public void onLocationChanged(Location location) {
            latLng = new LatLng(location.getLatitude(),location.getLongitude());
            time = new Date(location.getTime());
            //add = GEOReverseHelper.getAddressByLatLng(latLng);
            speed = location.getSpeed()*3.6;
        }
        public void onProviderDisabled(String provider) {
        }

        public void onProviderEnabled(String provider) {
        }
        // GPS位置資訊的狀態被更新
        public void onStatusChanged(String provider, int status, Bundle extras) {
            switch (status) {
                case LocationProvider.AVAILABLE:
                    //setTitle("服務中");
                    break;
                case LocationProvider.OUT_OF_SERVICE:
                    //setTitle("沒有服務");
                    break;
                case LocationProvider.TEMPORARILY_UNAVAILABLE:
                    //setTitle("暫時不可使用");
                    break;
            }
        }
    }//end of 定位監聽器實作

    //轉向器
    private class MySensorEventListener implements SensorEventListener {

        private float[] accelerometerValues;
        private float[] magneticFieldValues;

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                accelerometerValues = event.values.clone();
            } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                magneticFieldValues = event.values.clone();
            }
            if (accelerometerValues != null && magneticFieldValues != null) {

                calculateOrientation();

            }
        }
        @Override
        public void onAccuracyChanged(Sensor arg0, int arg1) {
            // TODO Auto-generated method stub
        }

        // 計算方位
        private void calculateOrientation() {
            //(-180~180) 0:正北，90:正東，180/-180:正南，-90:正西
            float[] values = new float[3];
            float[] inR = new float[9];
            SensorManager.getRotationMatrix(inR, null, accelerometerValues, magneticFieldValues);

            // 利用重映方向參考坐標系 (非必要)
            float[] outR = new float[9];
            SensorManager.remapCoordinateSystem(inR, SensorManager.AXIS_X, SensorManager.AXIS_Z, outR);

            SensorManager.getOrientation(inR, values); // 第一個參數可以置換 inR 或 outR

            values[0] = (float) Math.toDegrees(values[0]);
            degree = values[0];
        }
    }//end of 轉向器

    public static final int MY_PERMISSIONS_REQUEST_LOCATION = 99;

    public boolean checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            Log.d("checking location","no permission");

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                new AlertDialog.Builder(this)
                        .setTitle("GPS權限請求")
                        .setMessage("是否同意提供GPS資料")
                        .setPositiveButton("同意", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                //Prompt the user once explanation has been shown
                                ActivityCompat.requestPermissions(ProDashboardReal.this,
                                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                        MY_PERMISSIONS_REQUEST_LOCATION);
                            }
                        })
                        .setNegativeButton("不同意", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                checkLocationPermission();
                            }
                        })
                        .create()
                        .show();


            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_PERMISSIONS_REQUEST_LOCATION);
            }
            return false;
        } else {
            return true;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        //set LocationManager
        checkLocationPermission();
        if (mLocationManager == null) {
            mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        }
        mLocationListener = new MyLocationListener();
        mLocationManager.requestLocationUpdates(LM_GPS, 0, 0, mLocationListener);
        mLocationManager.requestLocationUpdates(LM_NETWORK, 0, 0, mLocationListener);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // location-related task you need to do.
                    if (ContextCompat.checkSelfPermission(this,
                            Manifest.permission.ACCESS_FINE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED) {

                        //Request location updates:
                        //mLocationManager.requestLocationUpdates(provider, 400, 1, this);

                        mLocationManager.requestLocationUpdates(LM_GPS, 0, 0, mLocationListener);
                        mLocationManager.requestLocationUpdates(LM_NETWORK, 0, 0, mLocationListener);
                    }

                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.

                }
                return;
            }
        }
    }
}
