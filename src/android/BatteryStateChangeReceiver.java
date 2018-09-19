package org.apache.cordova.batterystatus;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.LOG;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.widget.Toast;

public class BatteryStateChangeReceiver extends BroadcastReceiver {

    private static final String LOG_TAG = "BatteryManagerRcv";

    private boolean isRegistered;
    private BatteryListener owner;

    public boolean isScaledLevel = false;

    public BatteryStateChangeReceiver(BatteryListener owner){
      this.owner = owner;
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        //LOG.d(LOG_TAG, "battery management state change "+intent.getAction()+" -> "+intent);
        isRegistered = true;

        JSONObject info = new JSONObject();

        this.addChargingFieldsFrom(info, intent);

        float level = getLevel(intent, this.isScaledLevel);
        if(level < 0) {
            level = retrieveBatteryLevel(context, this.isScaledLevel);
        }
        this.addFieldLevel(info, level);
        this.addFieldTrigger(info, intent.getAction());

        this.owner.sendMessage(info);
    }

    public void register(Context ctx){

        if(isRegistered) {
            return;
        }

        ctx.registerReceiver(this, new IntentFilter(Intent.ACTION_POWER_CONNECTED));
        ctx.registerReceiver(this, new IntentFilter(Intent.ACTION_POWER_DISCONNECTED));


        ctx.registerReceiver(this, new IntentFilter(Intent.ACTION_BATTERY_LOW));
        ctx.registerReceiver(this, new IntentFilter(Intent.ACTION_BATTERY_OKAY));

        isRegistered = true;
    }

    public void unregister(Context ctx) {
        if(isRegistered) {
            ctx.unregisterReceiver(this);
            isRegistered = false;
        }
    }

    public JSONObject getBatteryStatus(Context context, boolean isInitial){

        JSONObject info = new JSONObject();
        Intent batteryStatus = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        this.addChargingFieldsFrom(info, batteryStatus);
        this.addFieldLevel(info, this.getLevel(batteryStatus, this.isScaledLevel));
        this.addFieldTrigger(info, isInitial? "initial" : "request");

        return info;
    }

    public void sendBatteryStatus(Context context, boolean isInitial){

      this.owner.sendMessage(this.getBatteryStatus(context, isInitial));
    }

    public float retrieveBatteryLevel(Context context, boolean scaled) {
        Intent batteryStatus = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        return  getLevel(batteryStatus, scaled);
    }

    private float getLevel(Intent batteryStatus, boolean scaled) {

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        if(!scaled){
            LOG.d(LOG_TAG, String.format("battery management: get (unscaled) level %s", level));
            return level;
        }

        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);


        if(level < 0 || scale < 0)
            return -1;

        float batteryPct = level / (float)scale;

        LOG.d(LOG_TAG, String.format("battery management: get level %s / %s -> %s", level, scale, batteryPct));

        return batteryPct;
    }

    private void addChargingFieldsFrom(JSONObject info, Intent intent){
      int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);

      if(status > -1) {
        boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
          status == BatteryManager.BATTERY_STATUS_FULL;

        this.addFieldCharging(info, isCharging);

        int chargePlug = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);

        if(chargePlug > -1){
          boolean usbCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_USB;
          boolean acCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_AC;

          this.addFieldPlug(info, chargePlug, usbCharge, acCharge);
        }

      }
    }

    private void addFieldLevel(JSONObject obj, float level){
        this.addField(obj, "level", level);
    }

    private void addFieldCharging(JSONObject obj, boolean charging){
        this.addField(obj, "isCharging", charging);
    }

    private void addFieldPlug(JSONObject obj, int details, boolean usb, boolean powerCord){
        this.addField(obj, "isPlugged", details > 0);
        this.addField(obj, "plugType", usb? "usb" : powerCord? "ac" : "unknown");
    }

    private void addFieldTrigger(JSONObject obj, String action){
      String trigger;
      if (action.equals(Intent.ACTION_POWER_CONNECTED)) {
          trigger = "pluggedin";

      } else if (action.equals(Intent.ACTION_POWER_DISCONNECTED)) {
          trigger = "unplugged";

      } else if (action.equals(Intent.ACTION_BATTERY_LOW)) {
          trigger = "battery_low";

      } else if (action.equals(Intent.ACTION_BATTERY_OKAY)) {
          trigger = "battery_ok";

      } else if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
          trigger = "battery_changed";

      } else {
          trigger = action;
      }
      this.addField(obj, "trigger", trigger);
    }

    private void addField(JSONObject obj, String name, boolean value){
        try {
            obj.put(name, value);
        } catch (JSONException e) {
            LOG.e(LOG_TAG, e.getMessage(), e);
        }
    }

    private void addField(JSONObject obj, String name, String value){
        try {
            obj.put(name, value);
        } catch (JSONException e) {
            LOG.e(LOG_TAG, e.getMessage(), e);
        }
    }

    private void addField(JSONObject obj, String name, float value){
        try {
            obj.put(name, value);
        } catch (JSONException e) {
            LOG.e(LOG_TAG, e.getMessage(), e);
        }
    }
}
