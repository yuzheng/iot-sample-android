package com.cht.iot.iotsampleapp;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;

import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.cht.iot.persistence.entity.data.Rawdata;
import com.cht.iot.persistence.entity.data.Session;

import com.cht.iot.service.OpenIoTClientImpl;

import org.apache.commons.codec.binary.StringUtils;

import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;

import iot.cht.com.iotsampleapp.R;

public class MainActivity extends AppCompatActivity {

    private String TAG_NAME = "iot";

    private ListView listView;

    private Context context;
    private ArrayList<String> controllers;
    private ListAdapter adapter;

    private OpenIoTClientImpl openIoTClient;

    ProgressDialog progress;

    private TextView deviceTextView, sensorTextView, debugTextView;
    private Switch buttonSwitch;
    private View deviceView;
    private Button buttonRawdata;

    private SharedPreferences sp;

    //
    String apiKey;  //project key ( = "PKY9H2EBYMRF9RST2R")
    String deviceId;// = "388622157";
    String sensorId;// = "button";
    //String[] sensorIds = new String[]{sensorId};

    private static final String PREF_PROJECTKEY = "projectkey";
    private static final String PREF_DEVICEID = "deviceid";
    private static final String PREF_SENSORID = "sensorid";
    private static final String PREF_DEVICEKEY = "devicekey";
    private static final String DEFAULT_APIKEY = "YOU KEY";

    private boolean isFindLocal = false;
    private InetAddress sourceAddress = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        context = this;

        // sharedPreferences
        sp = getSharedPreferences("PREF_IOT", Context.MODE_PRIVATE);
        apiKey = sp.getString(PREF_PROJECTKEY,DEFAULT_APIKEY);
        deviceId = sp.getString(PREF_DEVICEID,"");
        sensorId = sp.getString(PREF_SENSORID,"");
        // end of sharedPreferences

        controllers = new ArrayList<String>();

        setContentView(R.layout.activity_local);

        progress = new ProgressDialog(context);

        listView = (ListView) findViewById(R.id.listView);
        listView.setEmptyView(findViewById(R.id.emptyView));

        //.simple_expandable_list_item_1
        adapter = new ArrayAdapter<String>(context, R.layout.list_controller, controllers);

        listView.setAdapter(adapter);// 資料接口

        registerForContextMenu(listView);

        // init ui elements
        deviceView = findViewById(R.id.deviceView);
        deviceTextView = (TextView)findViewById(R.id.deviceTextView);
        sensorTextView = (TextView)findViewById(R.id.sensorTextView);
        debugTextView = (TextView) findViewById(R.id.debugTextView);
        debugTextView.setMovementMethod(new ScrollingMovementMethod());
        buttonSwitch = (Switch) findViewById(R.id.buttonSwitch);
        buttonRawdata = (Button) findViewById(R.id.buttonRawdata);
        deviceView.setVisibility(View.GONE);

        buttonSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // TODO Auto-generated method stub
                String status = "0";
                if (isChecked) {
                    status = "1";
                }
                Log.v(TAG_NAME, "saveRawdata:" + deviceId + ", sensorId:" + sensorId);
                openIoTClient.saveRawdata(deviceId, sensorId, new String[]{status});
            }
        });

        // init. OpenIoTClient (set projectKey)
        //initOpenIoTClient();

        Thread thread = new Thread(sourecAddressThread);
        thread.start();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.v(TAG_NAME, "lifecycle: onStart");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.v(TAG_NAME, "lifecycle: onResume");
        initOpenIoTClient();

        //findLocal(true);
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.v(TAG_NAME, "lifecycle: onRestart");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.v(TAG_NAME, "lifecycle: onPause");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.v(TAG_NAME, "lifecycle: onStop");

        hideDeviceSensors();
        openIoTClient.stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.v(TAG_NAME, "lifecycle: onDestroy");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu){
        super.onCreateOptionsMenu(menu);

        menu.add(0, 0, Menu.NONE, "近端搜尋");
        menu.add(0, 1, Menu.NONE, "停止服務");
        menu.add(0, 2, Menu.NONE, "設定資訊");
        menu.add(0, 3, Menu.NONE, "離開APP");

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        if(item.getGroupId() == 0){
            if(item.getItemId() == 0) {
                isFindLocal = true;
                findLocal(true);
            }else if(item.getItemId() == 1){
                if(item.getTitle().toString().equals("停止服務")){
                    openIoTClient.stop();
                    item.setTitle("開始服務");
                }else{
                    initOpenIoTClient();
                    //openIoTClient.start();
                    item.setTitle("停止服務");
                    //findLocal(true);
                }
            }else if(item.getItemId() == 2){
                showProjectDialog();
            }else if(item.getItemId() == 3) {
                openIoTClient.stop();
                this.finish();
                System.exit(0);
            }
        }

        return super.onOptionsItemSelected(item);
    }


    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                                    ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.context_menu, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        // 在 context menu 中獲取 ListView positionid 關鍵程式碼
        // 取得 menuInfo
        AdapterView.AdapterContextMenuInfo menuInfo =
                (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
        // 取得 menuInfo.position <-- 指的就是 listView 當時被點選的 position id
        int position = menuInfo.position;
        switch(item.getItemId()) {
            case R.id.show:
                Toast.makeText(context,
                        listView.getItemAtPosition(position).toString(),
                        Toast.LENGTH_LONG).show();
                break;
            case R.id.link:
                Log.v(TAG_NAME,"link");
                linkDevice(menuInfo.position);
                //((BaseAdapter) adapter).notifyDataSetChanged();
                break;
            case R.id.subscribe:
                Log.v(TAG_NAME,"subscribe");
                subscribeDevice(menuInfo.position);
                //((BaseAdapter) adapter).notifyDataSetChanged();
                break;
        }
        return super.onContextItemSelected(item);
    }

    private void initOpenIoTClient(){
        Log.v(TAG_NAME,"initOpenIoTClient: "+deviceId+", "+sensorId+" ("+apiKey+")");
        controllers.clear();
        openIoTClient = new OpenIoTClientImpl();
        openIoTClient.setProjectKey(apiKey);
        if( !deviceId.isEmpty() ){
            controllers.add(deviceId+"/"+sensorId);
            ((BaseAdapter) adapter).notifyDataSetChanged();
        }
        openIoTClient.start();
    }

    private void showProjectDialog(){
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.dialog_title);
        builder.setIcon(android.R.drawable.ic_dialog_info);
        builder.setMessage(R.string.dialog_message);

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);

        TextView tvProjext = new TextView(context);
        tvProjext.setText(R.string.dialog_projectkey);
        layout.addView(tvProjext);
        final EditText editText = new EditText(context);
        editText.setText(apiKey);
        layout.addView(editText);

        TextView tvDevice = new TextView(context);
        tvDevice.setText(R.string.dialog_deviceid);
        layout.addView(tvDevice);
        final EditText etDeviceId = new EditText(context);
        etDeviceId.setText(deviceId);
        layout.addView(etDeviceId);

        TextView tvSensor = new TextView(context);
        tvSensor.setText(R.string.dialog_sensorid);
        layout.addView(tvSensor);
        final EditText etSensorId = new EditText(context);
        etSensorId.setText(sensorId);
        layout.addView(etSensorId);

        builder.setView(layout);
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                apiKey = editText.getText().toString();
                deviceId = etDeviceId.getText().toString();
                sensorId = etSensorId.getText().toString();

                SharedPreferences.Editor editor = sp.edit();
                editor.putString(PREF_PROJECTKEY,apiKey);
                editor.putString(PREF_DEVICEID,deviceId);
                editor.putString(PREF_SENSORID,sensorId);

                //確認儲存
                editor.apply();

                // reset and restart OpenIoTClient
                openIoTClient.stop();
                initOpenIoTClient();
                //openIoTClient.start();

                dialog.dismiss();
            }
        });
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.create().show();
    }

    private void subscribeDevice(int pos){
        if(isFindLocal && openIoTClient.getSessions().size() > 0) {
            Session session = openIoTClient.getSessions().get(pos);
            if (session != null) {
                deviceId = session.getSeries();
                sensorId = session.getName();
                String deviceKey = session.getModel();
                updatePerferences(deviceKey, deviceId, sensorId);
            }
        }

        deviceView.setVisibility(View.VISIBLE);

        deviceTextView.setText(deviceId);
        sensorTextView.setText(sensorId);

        openIoTClient.subscribe(deviceId, sensorId);

        // Set Callback
        openIoTClient.setCallback(new OpenIoTClientImpl.CallbackAdapter(){
            @Override
            public void onRawdata(Rawdata rawdata) {
                Log.v("iot","=== onRawdata callback === : "+ rawdata.getValue().toString());
                sensorHandle(rawdata);
            }
        });
    }

    private void linkDevice(int pos){

        // 2016.08.26 add for sourceAddress (fixed udp source ip)
        if(sourceAddress != null){
            openIoTClient.setSourceAddress(sourceAddress);
        }

        if( openIoTClient.getSessions().size() > 0) {

            /*
             * Easy test: (just for easy test)
             * vendor:
             * model: key
             * series: deviceId
             * name: sensorId
             */
            boolean isFindLinkDevice = isFindLocal;
            String deviceKey = "";
            Session session = null;
            if(isFindLocal){

                session = openIoTClient.getSessions().get(pos);

                deviceId = session.getSeries();
                sensorId = session.getName();
                deviceKey = session.getModel();
                updatePerferences(deviceKey, deviceId, sensorId);
            }else{
                for(Session isession:openIoTClient.getSessions()){
                    if(isession.getSeries().equals(deviceId) && isession.getName().equals(sensorId)){
                        isFindLinkDevice = true;
                        session = isession;
                        deviceKey = session.getModel();
                    }
                }
            }

            if(isFindLinkDevice){
                Log.v(TAG_NAME, "linkDevice:" + deviceId + ", " + sensorId + "," + deviceKey);

                final String myDeviceId = deviceId;
                final String[] mySensorIds = new String[]{sensorId};

                deviceTextView.setText(myDeviceId);
                sensorTextView.setText(sensorId);
                deviceView.setVisibility(View.VISIBLE);


                openIoTClient.link(session, deviceKey, myDeviceId, mySensorIds);

                // Set link listener
                openIoTClient.setListener(new OpenIoTClientImpl.OpenIoTClientListener() {
                    @Override
                    public void didConnectToController() {

                        showToast("Connect", "Success");
                        showDebug("Connect: connect success!");
                    }

                    @Override
                    public void didNotConnectToController(OpenIoTClientImpl.Mode mode, String info) {
                        showToast("Disconnect", info);
                        showDebug("Disconnect: connect fail! @"+info);
                    }
                });

                // subscribe
                openIoTClient.subscribe(myDeviceId, mySensorIds[0]);

                // Set Callback
                openIoTClient.setCallback(new OpenIoTClientImpl.CallbackAdapter() {
                    @Override
                    public void onRawdata(Rawdata rawdata) {
                        Log.v("iot", "=== onRawdata callback === : " + rawdata.getValue().toString());
                        showDebug("Rawdata callback:"+rawdata.getDeviceId()+" > "+ Arrays.toString(rawdata.getValue()));
                        sensorHandle(rawdata);
                    }
                });
            }else{
                showToast("Local","近端無搜尋到配對設備");
                showDebug("近端無搜尋到配對設備");
            }
        }else{
            showToast("Local","近端無搜尋到設備");
            showDebug("近端無搜尋到設備");
        }
    }

    private void updatePerferences(String pDeviceKey, String pDeviceId, String pSensorId){
        SharedPreferences.Editor editor = sp.edit();

        editor.putString(PREF_DEVICEID,pDeviceId);
        editor.putString(PREF_SENSORID,pSensorId);
        editor.putString(PREF_DEVICEKEY,pDeviceKey);

        //確認儲存
        editor.apply();
    }

    private void findLocal(boolean showProgress){
        progress.setTitle("Search");
        progress.setMessage("Search Local Devices...");
        if(showProgress) progress.show();

        controllers.clear();
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                // TODO Auto-generated method stub
                try {
                    Thread.sleep(5000L); // wait for controller's announcement
                    //String deviceId = "123";
                    //String[] sensorIds = new String[]{"MySensor"};
                    if( openIoTClient.getSessions().size() > 0) {
                        runOnUiThread(new Runnable() {        //可以使用此方法臨時交給UI做顯示
                                          @Override
                                          public void run() {
                                              for (Session session : openIoTClient.getSessions()) {
                                                  Log.v(TAG_NAME, "session: :" + session);
                                                  controllers.add(session.toString());
                                              }
                                          }
                                      });
                    }else{
                        //Toast.makeText(context, "Not found local devices!",Toast.LENGTH_LONG).show();
                        Log.v(TAG_NAME, "Not found local mode!");
                    }
                    runOnUiThread(new Runnable() {        //可以使用此方法臨時交給UI做顯示
                        public void run() {
                            ((BaseAdapter) adapter).notifyDataSetChanged();
                            progress.dismiss();
                        }
                    });
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        });
        thread.start();
    }

    private void hideDeviceSensors(){

        controllers.clear();
        ((BaseAdapter) adapter).notifyDataSetChanged();

        deviceTextView.setText("");
        sensorTextView.setText("");
        deviceView.setVisibility(View.GONE);

    }

    private void sensorHandle(Rawdata rawdata){
        if(deviceId.equals(rawdata.getDeviceId())){
            String id = rawdata.getId();
            if(sensorId.equals(id)){
                onButtonChanged(rawdata);
            }
        }
    }

    private void onButtonChanged(Rawdata rawdata) {
        String[] values = rawdata.getValue();
        if (values.length > 0) {
            String value = rawdata.getValue()[0];
            final boolean checked = (value.equals("1") || value.equalsIgnoreCase("on"));
            if (buttonSwitch.isChecked() != checked) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        buttonSwitch.setChecked(checked);
                    }
                });
            }
        }
    }

    public void clickRawdata(View view){
        Thread thread = new Thread(mutiThread);
        thread.start();
    }

    private Runnable mutiThread = new Runnable() {
        public void run() {
            Log.v(TAG_NAME, "clickRawdata:"+deviceTextView.getText().toString());
            Rawdata rawdata = openIoTClient.getRawdata(deviceTextView.getText().toString(),sensorTextView.getText().toString());
        }
    };

    private Runnable sourecAddressThread = new Runnable(){
        @Override
        public void run() {
            sourceAddress = getWifiInetAddress(context, Inet4Address.class);
            Log.v(TAG_NAME, "source ip:"+sourceAddress.getHostAddress());

            /*
            try {
                WifiManager wifiManager = (WifiManager) context.getSystemService(WIFI_SERVICE);
                int ipAddress = wifiManager.getConnectionInfo().getIpAddress();
                // Convert little-endian to big-endianif needed
                if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
                    ipAddress = Integer.reverseBytes(ipAddress);
                }

                byte[] ipByteArray = BigInteger.valueOf(ipAddress).toByteArray();
                sourceAddress = InetAddress.getByAddress(ipByteArray);

                Log.v(TAG_NAME, "get wifi host address:"+sourceAddress.getHostAddress());
                //sourceAddress = InetAddress.getLocalHost(); // it crashes here

            } catch (UnknownHostException e1) {
                Log.v(TAG_NAME, "UnknownHostException");
            }
            */
        }
    };

    private void showToast(final String title, final String message){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, title + ":" + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showDebug(final String message){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                debugTextView.setText(message + "\n" +debugTextView.getText());
            }
        });
    }

    public static Enumeration<InetAddress> getWifiInetAddresses(final Context context) {
        final WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        final WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        final String macAddress = wifiInfo.getMacAddress();
        final String[] macParts = macAddress.split(":");
        final byte[] macBytes = new byte[macParts.length];
        for (int i = 0; i< macParts.length; i++) {
            macBytes[i] = (byte)Integer.parseInt(macParts[i], 16);
        }
        try {
            final Enumeration<NetworkInterface> e =  NetworkInterface.getNetworkInterfaces();
            while (e.hasMoreElements()) {
                final NetworkInterface networkInterface = e.nextElement();
                if (Arrays.equals(networkInterface.getHardwareAddress(), macBytes)) {
                    return networkInterface.getInetAddresses();
                }
            }
        } catch (SocketException e) {
            Log.wtf("WIFIIP", "Unable to NetworkInterface.getNetworkInterfaces()");
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static<T extends InetAddress> T getWifiInetAddress(final Context context, final Class<T> inetClass) {
        final Enumeration<InetAddress> e = getWifiInetAddresses(context);
        while (e.hasMoreElements()) {
            final InetAddress inetAddress = e.nextElement();
            if (inetAddress.getClass() == inetClass) {
                return (T)inetAddress;
            }
        }
        return null;
    }
}
