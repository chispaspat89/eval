package com.example.eval;

import static com.example.eval.LocationJobService.JOB_STATE_CHANGED;
import static com.example.eval.LocationJobService.LOCATION_ACQUIRED;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentTransaction;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.TypeEvaluator;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.Property;
import android.view.View;
import android.widget.Button;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.util.Locale;
import android.os.Handler;
import android.widget.Toast;

import java.util.logging.LogRecord;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {
    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;
    Marker marker = null;
    protected static final int REQUEST_CHECK_SETTINGS = 0x1;
    LocationRequest mLocationRequest;
    LocationCallback mLocationCallback;
    FusedLocationProviderClient mFusedLocationProviderClient;
    //Create an Array to save the permission
    private String[] permission = new String[]{
            android.Manifest.permission.ACCESS_FINE_LOCATION
    };
    //Create a class that allow us to get user location
    private LocationManager locationManager;
    private LocationListener locationListener;
    boolean registered = false, isServiceStarted=false;
    Button button,bService;
    boolean mapLoaded = false;
    Location oldLocation;
    float bearing;
    Marker carMarker;
    static MoveThread moveThread;
    static Handler handler;

    SupportMapFragment mapFragment;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        bService = (Button) findViewById(R.id.b_service);
        button   = (Button) findViewById(R.id.b_action);


        mapFragment = SupportMapFragment.newInstance();
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.add(R.id.map, mapFragment).commitAllowingStateLoss();
        handler = new Handler();

        mapFragment.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(GoogleMap googleMap) {
                mMap = googleMap;
                CameraUpdate point = CameraUpdateFactory.newLatLngZoom(new LatLng(20.971939,-89.619717),8);
                mMap.moveCamera(point);
                mMap.setOnMapLoadedCallback(new GoogleMap.OnMapLoadedCallback() {
                    @Override
                    public void onMapLoaded() {
                        mapLoaded = true;
                        mMap.getUiSettings().setAllGesturesEnabled(true);
                        mMap.getUiSettings().setZoomControlsEnabled(true);
                    }
                });
            }
        });
        isServiceStarted = getSharedPreferences("track",MODE_PRIVATE).getBoolean("isServiceStarted",false);

        changeServiceButton(isServiceStarted);
        if(!registered&&isServiceStarted) {
            IntentFilter i = new IntentFilter(JOB_STATE_CHANGED);
            i.addAction(LOCATION_ACQUIRED);
            LocalBroadcastManager.getInstance(this).registerReceiver(jobStateChanged, i);
        }


        bService.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(view.getTag().equals("s")){
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        Log.d("registered"," on start service");
                        startBackgroundService();
                    }else{
                        Toast.makeText(getBaseContext(),"service for pre lollipop will be available in next update",Toast.LENGTH_LONG).show();
                    }
                }else{
                    stopBackgroundService();
                }
            }
        });

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (view.getTag().equals("s")) {
                    createLocationRequest();
                } else {
                    Log.d("clicked", "button");
                    stopLocationUpdates();
                }
            }
        });
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {


    }



    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        for (int permissionResult : grantResults){
            if(permissionResult == PackageManager.PERMISSION_DENIED){
                sendWarning();
            }else if(permissionResult == PackageManager.PERMISSION_GRANTED){
                if (ActivityCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);

                }

            }
        }

    }
    public void sendWarning(){
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle("Permissions Denied");
        dialog.setMessage("To use this functionality necessary to accept permission!");
        dialog.setCancelable(false);
        dialog.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                finish();
            }
        });
        AlertDialog apply = dialog.create();
        apply.show();
    }


    private void startBackgroundService() {
        if(!registered) {
            IntentFilter i = new IntentFilter(JOB_STATE_CHANGED);
            i.addAction(LOCATION_ACQUIRED);
            LocalBroadcastManager.getInstance(this).registerReceiver(jobStateChanged, i);
        }
        JobScheduler jobScheduler =
                (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
        assert jobScheduler != null;
        jobScheduler.schedule(new JobInfo.Builder(LocationJobService.LOCATION_SERVICE_JOB_ID,
                new ComponentName(this, LocationJobService.class))
                .setOverrideDeadline(500)
                .setPersisted(true)
                .setRequiresDeviceIdle(false)
                .build());
    }

    private BroadcastReceiver jobStateChanged = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction()==null){
                return;
            }
            if(intent.getAction().equals(JOB_STATE_CHANGED)) {
                changeServiceButton(intent.getExtras().getBoolean("isStarted"));
            }else if (intent.getAction().equals(LOCATION_ACQUIRED)){
                if(intent.getExtras()!=null){
                    Bundle b = intent.getExtras();
                    Location l = b.getParcelable("location");
                    updateMarker(l);
                }else{
                    Log.d("intent","null");
                }
            }
        }
    };

    private void changeServiceButton(boolean isStarted) {
        if(isStarted){
            bService.setTag("f");
            bService.setText("STOP BACKGROUND TRACKING");
            button.setVisibility(View.GONE);
        }else{
            bService.setTag("s");
            bService.setText("START BACKGROUND TRACKING");
            button.setVisibility(View.VISIBLE);
        }
    }


    private void updateMarker(Location location) {
        if (location == null) {
            return;
        }
        if (mMap != null && mapLoaded) {
            if (carMarker == null) {
                oldLocation = location;
                MarkerOptions markerOptions = new MarkerOptions();
                BitmapDescriptor car = BitmapDescriptorFactory.fromResource(R.mipmap.ic_launcher);
                markerOptions.icon(car);
                markerOptions.anchor(0.5f, 0.5f); //centra el marcador en la ubicacion del dispositivo
                markerOptions.flat(true); //se define como verdadero, para mantener la direccion del dispositivo
                markerOptions.position(new LatLng(location.getLatitude(), location.getLongitude()));
                carMarker = mMap.addMarker(markerOptions);
                if (location.hasBearing()) {
                    bearing = location.getBearing();
                } else {
                    bearing = 0;
                }
                carMarker.setRotation(bearing);
                moveThread = new MoveThread();
                moveThread.setNewPoint(new LatLng(location.getLatitude(), location.getLongitude()), 16);
                handler.post(moveThread);

            } else {
                if (location.hasBearing()) {// si la ubicacion tiene un rumbo o direccion, se establece al marcador de acuerdo a la posicion del dispositivo
                    bearing = location.getBearing();
                } else { // si no, calcula el rumbo de la ubicacion anterior y elnuevo punto
                    bearing = oldLocation.bearingTo(location);
                }
                carMarker.setRotation(bearing);
                moveThread.setNewPoint(new LatLng(location.getLatitude(), location.getLongitude()), mMap.getCameraPosition().zoom); // se define un zoom al mapa, pero el usuario tiene control sobre esta accion con el touch de la pantalla
                animateMarkerToICS(carMarker, new LatLng(location.getLatitude(), location.getLongitude())); // agregar una animacion al marcador en el mapa
            }
        } else {
            Log.e("map null or not loaded", "");
        }
    }

    private class MoveThread implements Runnable {
        LatLng newPoint;
        float zoom = 16;

        void setNewPoint(LatLng latLng, float zoom){
            this.newPoint = latLng;
            this.zoom = zoom;
        }

        @Override
        public void run() {
            final CameraUpdate point = CameraUpdateFactory.newLatLngZoom(newPoint,zoom);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mMap.animateCamera(point);
                }
            });
        }
    }


    static void animateMarkerToICS(Marker marker, LatLng finalPosition) {
        TypeEvaluator<LatLng> typeEvaluator = new TypeEvaluator<LatLng>() {
            @Override
            public LatLng evaluate(float fraction, LatLng startValue, LatLng endValue) {
                return interpolate(fraction, startValue, endValue);
            }
        };
        Property<Marker, LatLng> property = Property.of(Marker.class, LatLng.class, "position");
        ObjectAnimator animator = ObjectAnimator.ofObject(marker, property, typeEvaluator, finalPosition);
        animator.setDuration(3000);
        animator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {

            }

            @Override
            public void onAnimationEnd(Animator animator) {
                handler.post(moveThread);
            }

            @Override
            public void onAnimationCancel(Animator animator) {

            }

            @Override
            public void onAnimationRepeat(Animator animator) {

            }
        });
        animator.start();
    }

    public static LatLng interpolate(float fraction, LatLng a, LatLng b) {


        //funcion que se encarga de calcular los valores intermeedios de la latlng antigua con la nueva
        //para obtener el seguimiento preciso del dispositivo, usa el api de carreteras de google
        //tener en cuenta que tiene un limite de cuota el servicio y este podria verse afecta la factura del mes

        double lat = (b.latitude - a.latitude) * fraction + a.latitude;
        double lngDelta = b.longitude - a.longitude;

        // Take the shortest path across the 180th meridian.
        if (Math.abs(lngDelta) > 180) {
            lngDelta -= Math.signum(lngDelta) * 360;
        }
        double lng = lngDelta * fraction + a.longitude;
        return new LatLng(lat, lng);
    }

    protected void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(30000);
        mLocationRequest.setFastestInterval(15000);
        mLocationRequest.setSmallestDisplacement(50);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(mLocationRequest);
        SettingsClient client = LocationServices.getSettingsClient(this);
        Task<LocationSettingsResponse> task = client.checkLocationSettings(builder.build());
        task.addOnSuccessListener(this, new OnSuccessListener<LocationSettingsResponse>() {
            @Override
            public void onSuccess(LocationSettingsResponse locationSettingsResponse) {

                bService.setVisibility(View.GONE);
                startLocationUpdates();
            }
        });
        task.addOnFailureListener(this, new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                int statusCode = ((ApiException) e).getStatusCode();
                switch (statusCode) {
                    case CommonStatusCodes.RESOLUTION_REQUIRED:

                        try {

                            ResolvableApiException resolvable = (ResolvableApiException) e;
                            resolvable.startResolutionForResult(MainActivity.this,
                                    REQUEST_CHECK_SETTINGS);
                        } catch (IntentSender.SendIntentException sendEx) {

                        }
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:

                        break;
                }
            }
        });
    }

    private void stopLocationUpdates() {
        if (button.getTag().equals("s")) {

            return;
        }
        mFusedLocationProviderClient.removeLocationUpdates(mLocationCallback);
        button.setTag("s");
        button.setText("START FOREGROUND TRACKING");
        bService.setVisibility(View.VISIBLE);
        // Toast.makeText(getApplicationContext(),"Actualizar posicion detenida.",Toast.LENGTH_SHORT).show();
    }

    private void startLocationUpdates() {
        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    // Update UI with location data
                    // ...
                    updateMarker(location);
                }
            }

            ;
        };
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            Toast.makeText(getApplicationContext(),"El dispositivo necesita permisos de ubicacion. !!",Toast.LENGTH_SHORT).show();
            return;
        }
        mFusedLocationProviderClient.requestLocationUpdates(mLocationRequest,
                mLocationCallback,
                null /* Looper */);
        button.setTag("f");
        button.setText("STOP FOREGROUND TRACKING");
//        Toast.makeText(getApplicationContext(),"Location update started",Toast.LENGTH_SHORT).show();
    }


    private void stopBackgroundService() {
        if(getSharedPreferences("track",MODE_PRIVATE).getBoolean("isServiceStarted",false)){
            Intent stopJobService = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                stopJobService = new Intent(LocationJobService.ACTION_STOP_JOB);
                LocalBroadcastManager.getInstance(getBaseContext()).sendBroadcast(stopJobService);
                changeServiceButton(false);
            }else{
                Toast.makeText(getApplicationContext(),"Detener servicio",Toast.LENGTH_SHORT).show();
            }
        }
    }
}