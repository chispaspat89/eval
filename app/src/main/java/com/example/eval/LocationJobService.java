package com.example.eval;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.location.LocationRequest;

import java.util.ArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

public class LocationJobService extends JobService implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    Handler handler;
    Detector cd;
    FusedLocationProviderClient mFusedLocationProviderClient;
    public static final int LOCATION_SERVICE_JOB_ID = 111;
    LocationRequest mLocationRequest;
    LocationCallback mLocationCallback;
    JobParameters jobParameters;
    public static boolean isJobRunning = false;
    GoogleApiClient mGoogleApiClient;
    ArrayList<Location> updatesList = new ArrayList<>();

    public static final String ACTION_STOP_JOB = "actionStopJob";
    public static final String JOB_STATE_CHANGED = "jobStateChanged";
    public static final String LOCATION_ACQUIRED = "locAcquired";
    private BroadcastReceiver stopJobReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction()!=null && intent.getAction().equals(ACTION_STOP_JOB)) {
                Log.d("unregister"," job stop receiver");

                onJobFinished();
            }
        }
    };

    private void onJobFinished() {
        Log.d("job finish"," called");
        isJobRunning = false;
        stopLocationUpdates();
        jobFinished(jobParameters,false);
    }

    @Override
    public boolean onStartJob(JobParameters jobParameters) {
        handler = new Handler() {
            @Override
            public void publish(LogRecord logRecord) {

            }

            @Override
            public void flush() {

            }

            @Override
            public void close() throws SecurityException {

            }
        };
        this.jobParameters = jobParameters;

        buildGoogleApiClient();
        config();
        isJobRunning = true;
        return true;
    }

    protected  void config() {
        mLocationRequest = LocationRequest.create();
        mLocationRequest.setInterval(10000);
        mLocationRequest.setFastestInterval(5000);
        mLocationRequest.setSmallestDisplacement(10);
        mLocationRequest.setPriority(Priority.PRIORITY_HIGH_ACCURACY);
        cd = new Detector(getApplicationContext());
        startLocationUpdates();
        LocalBroadcastManager.getInstance(LocationJobService.this).registerReceiver(stopJobReceiver , new IntentFilter(ACTION_STOP_JOB));
    }

    private void startLocationUpdates() {
        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    // actualiza la vista con los datos de la ubicacion del usuario
                    Intent i = new Intent(LOCATION_ACQUIRED);
                    i.putExtra("location",location);

                    LocalBroadcastManager.getInstance(getBaseContext()).sendBroadcast(i);

                    updatesList.add(location); // agrega la ubicacion al listado
                }
            }

            ;
        };
        if (ActivityCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(getApplicationContext(),"permission required !!",Toast.LENGTH_SHORT).show();
            return;
        }
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(LocationJobService.this);
        mFusedLocationProviderClient.requestLocationUpdates(mLocationRequest,
                mLocationCallback,
                Looper.getMainLooper());

        ;
        getSharedPreferences("track",MODE_PRIVATE).edit().putBoolean("isServiceStarted",true).apply();
        Intent jobStartedMessage = new Intent(JOB_STATE_CHANGED);
        jobStartedMessage.putExtra("isStarted",true);
        Log.d("send broadcast"," as job started");
        LocalBroadcastManager.getInstance(getBaseContext()).sendBroadcast(jobStartedMessage);
        createNotification();
        Toast.makeText(getApplicationContext(),"Location job service started",Toast.LENGTH_SHORT).show();
    }

    private void buildGoogleApiClient() {
        if(mGoogleApiClient==null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(LocationServices.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
            mGoogleApiClient.connect();
        }else{
            Log.e("api client","not null");
        }
    }



    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        Log.d("job","stopped");
        if(mGoogleApiClient!=null){
            mGoogleApiClient.disconnect();
        }
        isJobRunning = false;
        stopLocationUpdates();
        return true;
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.i("track.JobService","google API client connected");
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.i("track.JobService","google API client suspended");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.i("track.JobService","google API client failed");
    }

    private void createNotification() {
        Notification.Builder mBuilder = new Notification.Builder(
                getBaseContext());
        Notification notification = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notification = mBuilder.setSmallIcon(R.drawable.ic_launcher_background).setTicker("Tracking").setWhen(0)
                    .setAutoCancel(false)
                    .setCategory(Notification.EXTRA_BIG_TEXT)
                    .setContentTitle("Tracking")
                    .setContentText("Your trip in progress")
                    .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                    .setColor(ContextCompat.getColor(getBaseContext(),R.color.black))
                    .setStyle(new Notification.BigTextStyle()
                            .bigText("Track in progress"))
                    .setChannelId("track_marty")
                    .setShowWhen(true)
                    .setOngoing(true)
                    .build();
        }else{
            notification = mBuilder.setSmallIcon((Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)? R.drawable.ic_launcher_background:R.mipmap.ic_launcher).setTicker("Tracking").setWhen(0)
                    .setAutoCancel(false)
                    .setCategory(Notification.EXTRA_BIG_TEXT)
                    .setContentTitle("Tracking")
                    .setContentText("Track in progress")
                    .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                    .setColor(ContextCompat.getColor(getBaseContext(),R.color.black))
                    .setStyle(new Notification.BigTextStyle()
                            .bigText("Track in progress"))
                    .setPriority(Notification.PRIORITY_HIGH)
                    .setShowWhen(true)
                    .setOngoing(true)
                    .build();
        }
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel mChannel = new NotificationChannel("track_marty", "Track", NotificationManager.IMPORTANCE_HIGH);
            assert notificationManager != null;
            notificationManager.createNotificationChannel(mChannel);
        }

        startForeground(1,notification); //para el servicio en primer plano no utilizar el valor 0 como indicador, no va ha funcionar
    }

    private void removeNotification(){
        //detener el servicio en primer plano
        stopForeground(true);
    }

    private void stopLocationUpdates() {

        //detiene la solicitud de ubicacion
        Log.d("stop location "," updates called");
        if(mLocationCallback!=null && mFusedLocationProviderClient!=null) {
            mFusedLocationProviderClient.removeLocationUpdates(mLocationCallback);
            Toast.makeText(getApplicationContext(), "Location job service stopped.", Toast.LENGTH_SHORT).show();
        }
        getSharedPreferences("track",MODE_PRIVATE).edit().putBoolean("isServiceStarted",false).apply();
        Intent jobStoppedMessage = new Intent(JOB_STATE_CHANGED);
        jobStoppedMessage.putExtra("isStarted",false);
        Log.d("broadcasted","job state change");
        removeNotification();
        LocalBroadcastManager.getInstance(getBaseContext()).sendBroadcast(jobStoppedMessage);
    }
}