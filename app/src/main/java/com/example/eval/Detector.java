package com.example.eval;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

public class Detector {

    private Context _context;

    public Detector(Context context){
        this._context = context;
    }

    public boolean isConnectingToInternet(){
        ConnectivityManager connectivity = (ConnectivityManager) _context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivity != null)
        {
            NetworkInfo networkInfo = connectivity.getActiveNetworkInfo();
            if (networkInfo != null && networkInfo.isConnected()) {
                // el dispositivo se encuentra conectado a la red
                return true;
            }else{
                return false;
            }
        }
        return false;
    }
}
