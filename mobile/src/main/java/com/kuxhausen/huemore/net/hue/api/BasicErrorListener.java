package com.kuxhausen.huemore.net.hue.api;

import android.util.Log;

import com.android.volley.Response.ErrorListener;
import com.android.volley.VolleyError;
import com.kuxhausen.huemore.net.NetworkBulb.ConnectivityState;
import com.kuxhausen.huemore.net.hue.Route;

public class BasicErrorListener implements ErrorListener {

  ConnectionMonitor parrent;
  Route mRoute;

  public BasicErrorListener(ConnectionMonitor parrentA, Route route) {
    parrent = parrentA;
    mRoute = route;
  }

  @Override
  public void onErrorResponse(VolleyError error) {
    parrent.setHubConnectionState(mRoute, ConnectivityState.Unreachable);

    Log.d("volleyError", error.getLocalizedMessage() + "   ");
  }

}
