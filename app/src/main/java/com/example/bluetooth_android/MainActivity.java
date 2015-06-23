package com.example.bluetooth_android;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import org.apache.http.entity.StringEntity;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;


public class MainActivity extends Activity implements AdapterView.OnItemClickListener {

    ArrayAdapter<String> listAdapter;
    ListView listView;
    BluetoothAdapter btAdapter;
    Set<BluetoothDevice> devicesArray;
    ArrayList<String> pairedDevices;
    ArrayList<BluetoothDevice> btDevices;
    IntentFilter filter;
    BroadcastReceiver receiver;
    public static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    public static final int SUCCESS_CONNECT = 0;
    public static final int MESSAGE_READ = 1;
    Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg){
            super.handleMessage(msg);
            switch(msg.what){
                case SUCCESS_CONNECT:
                    //Do something
                    ConnectedThread connectedThread = new ConnectedThread((BluetoothSocket)msg.obj);
                    Toast.makeText(getApplicationContext(),"Connected!",Toast.LENGTH_SHORT).show();
                    String s = "Successfully connected";
                    connectedThread.write(s.getBytes());
                    break;
                case MESSAGE_READ:
                    byte[] readBuf = (byte[])msg.obj;
                    String str = new String(readBuf);
                    Toast.makeText(getApplicationContext(),str,Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init();
        if(btAdapter == null){
            Toast.makeText(getApplicationContext(),"No bluetooth dected",0).show();
            finish();
        }else{
            if(!btAdapter.isEnabled()){
                turnOnBT();
            }

            getPairedDevices();
            startDiscovery();
        }

    }

    private void startDiscovery() {
        btAdapter.cancelDiscovery();
        btAdapter.startDiscovery();
    }

    private void getPairedDevices() {
        devicesArray = btAdapter.getBondedDevices();
        if(devicesArray.size() > 0){
            for(BluetoothDevice device:devicesArray){
                pairedDevices.add(device.getName());
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode == RESULT_CANCELED){
            Toast.makeText(getApplicationContext(),"Bluetooth must be enabled to continue",Toast.LENGTH_SHORT).show();
            finish();
        }
    }
    private void init() {
        listView = (ListView)findViewById(R.id.listView);
        listView.setOnItemClickListener(this);
        listAdapter = new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,0);
        btAdapter = BluetoothAdapter.getDefaultAdapter();
        listView.setAdapter(listAdapter);
        pairedDevices = new ArrayList<String>();
        filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        btDevices = new ArrayList<BluetoothDevice>();
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();

                if(BluetoothDevice.ACTION_FOUND.equals(action)){
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    btDevices.add(device);
                    String s = "";
                    for(int a=0 ; a < pairedDevices.size(); a++){
                        if(device.getName().equals(pairedDevices.get(a))){
                            //append
                            s = s+ "(Paired)";
                            break;
                        }
                    }

                    listAdapter.add(device.getName()+" "+s + " "+"\n"+device.getAddress());
                }
                else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)){

                }
                else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)){

                }
                else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)){
                    if(btAdapter.getState() == btAdapter.STATE_OFF){
                        turnOnBT();
                    }
                }
            }
        };
        registerReceiver(receiver,filter);
        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        registerReceiver(receiver,filter);
        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(receiver,filter);
        filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(receiver,filter);
    }

    @Override
    protected void onPause(){
        super.onPause();
        unregisterReceiver(receiver);
    }

    private void turnOnBT() {
        Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(intent, 1);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }
    public void onItemClick(AdapterView<?> arg0,View arg1,int arg2,long arg3) {
        if(btAdapter.isDiscovering()){
            btAdapter.cancelDiscovery();
        }
        if(listAdapter.getItem(arg2).contains("Paired")){
            BluetoothDevice selectedDevice = btDevices.get(arg2);
            ConnectThread connect = new ConnectThread(selectedDevice);
            connect.start();
        }else{
            Toast.makeText(getApplicationContext(),"device is not paired",Toast.LENGTH_SHORT).show();
        }
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        public ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket,
            // because mmSocket is final
            BluetoothSocket tmp = null;
            mmDevice = device;

            // Get a BluetoothSocket to connect with the given BluetoothDevice
            try {
                // MY_UUID is the app's UUID string, also used by the server code
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) { }
            mmSocket = tmp;
        }

        public void run() {
            // Cancel discovery because it will slow down the connection
            btAdapter.cancelDiscovery();

            try {
                // Connect the device through the socket. This will block
                // until it succeeds or throws an exception
                mmSocket.connect();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and get out
                try {
                    mmSocket.close();
                } catch (IOException closeException) { }
                return;
            }

            // Do work to manage the connection (in a separate thread)
            //manageConnectedSocket(mmSocket);
            mHandler.obtainMessage(SUCCESS_CONNECT,mmSocket).sendToTarget();
        }

        /*private void manageConnectedSocket(BluetoothSocket mmSocket) {

        }*/

        /** Will cancel an in-progress connection, and close the socket */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer;  // buffer store for the stream
            int bytes; // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    buffer = new byte[1024];
                    bytes = mmInStream.read(buffer);
                    // Send the obtained bytes to the UI activity
                    mHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer)
                            .sendToTarget();
                } catch (IOException e) {
                    break;
                }
            }
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) { }
        }

        /* Call this from the main activity to shutdown the connection */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }

}
