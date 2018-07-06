package com.devrobo.sdk;


import android.content.Context;
import android.content.SharedPreferences;
import android.nfc.Tag;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class DevRobo {

    private static volatile DevRobo instance;

    private static final String ApiUrl = "https://api.devrobo.com";
    private Context cnt;
    private String ApiKey;
    private String AppName;
    private String DeviceID;
    private String TAG = "DevRobo";
    private int SDKVersion = 2;

    private ArrayList<HashMap<String, String>> waiting_data;

    private RequestQueue queue;

    private Bus bus = new Bus();

    private DevRobo() {
        if (instance != null) {
            throw new RuntimeException("Use getInstance() method to get the instance of this class.");
        }
        bus.register(this);
    }

    public static void initialize(Context cnt, String apiKey, String AppName) {
        DevRobo inst = getInstance();
        inst.cnt = cnt;

        inst.queue = Volley.newRequestQueue(cnt);
        inst.queue.stop();
        inst.ApiKey = apiKey;
        inst.AppName = AppName;
        inst.getDeviceToken();
    }

    private static DevRobo getInstance() {
        if (instance == null) {
            Log.d("DevRobo", "new instance");
            synchronized (DevRobo.class) {
                if (instance == null) {
                    instance = new DevRobo();
                }
            }
        }
        return instance;
    }

    //
    @Subscribe
    public void onTokenReady(String event) {
        DevRobo el = getInstance();
        el.DeviceID = event;
        el.queue.start();

        Log.d(TAG, "Got device id " + event);

        //
        if(waiting_data != null) {
            Log.d(TAG, "Check waiting events");
            int w_size = waiting_data.size();
            Log.d(TAG, "waiting " + w_size + " event");

            for (HashMap<String, String> my_el : waiting_data) {
                send(my_el.get("type").toString(), my_el.get("message").toString());
            }
            waiting_data = new ArrayList<HashMap<String, String>>();
        }
    }

    public static void Event(String eventName, String eventValue) {
        DevRobo el = getInstance();
        el.send(eventName, eventValue);
    }

    public static void Log(String message) {
        DevRobo el = getInstance();
        el.send("Log", message);
    }

    private void getDeviceToken() {
        final SharedPreferences pref = cnt.getSharedPreferences("devrobo", 0);
        String id = pref.getString("deviceID", "");
        if (!id.equals("")) {
            Log.d(TAG, "device id " + id + " from local");
            bus.post(id);
        } else {
            RequestQueue myqueue = Volley.newRequestQueue(this.cnt);
            String url = ApiUrl + "/mobile_logs/register";

            Map<String, String> params = new HashMap<String, String>();
            params.put("device_name", helpers.getDeviceName());
            params.put("sdk_version", String.format("%d",SDKVersion));
            params.put("device_type", "android");
            params.put("app_name", AppName);
            params.put("api_key", ApiKey );

            JSONObject parameters = new JSONObject(params);

            JsonObjectRequest jsonObjectRequest = new JsonObjectRequest
                    (Request.Method.POST, url, parameters, new Response.Listener<JSONObject>() {

                        @Override
                        public void onResponse(JSONObject response) {
                            System.out.println(response);
                            String token = "";
                            try {
                                token = response.getString("token");
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                            pref.edit().putString("deviceID", token).apply();
                            pref.edit().commit();

                            bus.post(token);
                        }
                    }, new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                        }
                    }) {
            };
            myqueue.add(jsonObjectRequest);
        }

    }

    private void send(final String type, final String message) {

        final String apiKey = this.ApiKey;
        final String devId = this.DeviceID;

        if (devId == null || devId.equals("")) {
            HashMap<String, String> tmp = new HashMap<String, String>();
            tmp.put("type", type);
            tmp.put("message", message);
            if (waiting_data == null) {
                waiting_data = new ArrayList<HashMap<String, String>>();
            }
            waiting_data.add(tmp);
            return;
        }

        //
        Log.v(TAG,type + " | " + message);

        String url = ApiUrl + "/mobile_logs/log?deviceID=" + this.DeviceID;

        StringRequest reg = new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
            }
        }) {
            @Override
            public Map<String, String> getParams() {
                Map<String, String> params = new HashMap<String, String>();
                params.put("type", type);
                params.put("message", message);
                params.put("api_key", apiKey);
                params.put("deviceID", devId);
                return params;
            }
        };
        this.queue.add(reg);
    }
}
