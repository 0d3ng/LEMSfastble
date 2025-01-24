package com.sinaungoding.lems_fastble;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.clj.fastble.BleManager;
import com.clj.fastble.callback.BleGattCallback;
import com.clj.fastble.callback.BleNotifyCallback;
import com.clj.fastble.callback.BleScanCallback;
import com.clj.fastble.data.BleDevice;
import com.clj.fastble.exception.BleException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int REQUEST_CODE_PERMISSION_LOCATION = 2;
    private static final int REQUEST_CODE_OPEN_GPS = 1;
    private static final String MAC_RS_BTEVS1 = "E0:37:8B:3C:4E:7B";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        BleManager.getInstance().init(getApplication());
        BleManager.getInstance()
                .enableLog(true)
                .setReConnectCount(1, 5000)
                .setConnectOverTime(20000)
                .setOperateTimeout(5000);

        checkPermissions();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        BleManager.getInstance().disconnectAllDevice();
        BleManager.getInstance().destroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults, int deviceId) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults, deviceId);
        switch (requestCode) {
            case REQUEST_CODE_PERMISSION_LOCATION:
                if (grantResults.length > 0) {
                    for (int i = 0; i < grantResults.length; i++) {
                        if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                            onPermissionGranted(permissions[i]);
                        }
                    }
                }
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_OPEN_GPS) {
            boolean check = checkGPSIsOpen();
            Log.i(TAG, String.format("onActivityResult: %s %b", requestCode, check));
            if (check) {
//                setScanRule();
//                startScan();
                startConnect();
            }
        }
    }

    private void checkPermissions() {
        Log.d(TAG, "checkPermissions: ");
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, getString(R.string.please_open_blue), Toast.LENGTH_LONG).show();
            return;
        }

        String[] permissions = {android.Manifest.permission.ACCESS_FINE_LOCATION};
        List<String> permissionDeniedList = new ArrayList<>();
        for (String permission : permissions) {
            int permissionCheck = ContextCompat.checkSelfPermission(this, permission);
            if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                onPermissionGranted(permission);
            } else {
                permissionDeniedList.add(permission);
            }
        }
        if (!permissionDeniedList.isEmpty()) {
            String[] deniedPermissions = permissionDeniedList.toArray(new String[permissionDeniedList.size()]);
            ActivityCompat.requestPermissions(this, deniedPermissions, REQUEST_CODE_PERMISSION_LOCATION);
        }
    }

    private void onPermissionGranted(String permission) {
        switch (permission) {
            case android.Manifest.permission.ACCESS_FINE_LOCATION:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !checkGPSIsOpen()) {
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.notifyTitle)
                            .setMessage(R.string.gpsNotifyMsg)
                            .setNegativeButton(R.string.cancel,
                                    (dialog, which) -> finish())
                            .setPositiveButton(R.string.setting,
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            Intent intent = new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                                            startActivityForResult(intent, REQUEST_CODE_OPEN_GPS);
                                        }
                                    })

                            .setCancelable(false)
                            .show();
                } else {
//                    setScanRule();
//                    startScan();
                    startConnect();
                }
                break;
        }
    }

    private void startScan() {
        Log.d(TAG, "startScan: ...");
        BleManager.getInstance().scan(new BleScanCallback() {
            @Override
            public void onScanFinished(List<BleDevice> scanResultList) {
                for (BleDevice device : scanResultList) {
                    Log.i(TAG, String.format("Name: %s Mac: %s RSSI: %s", device.getName(), device.getMac(), device.getRssi()));
                }
                BleManager.getInstance().cancelScan();
            }

            @Override
            public void onScanStarted(boolean success) {
                Log.i(TAG, "Start scanning..." + success);
            }

            @Override
            public void onScanning(BleDevice bleDevice) {
                Log.i(TAG, String.format("onScanning: %s %s", bleDevice.getName(), bleDevice.getMac()));
            }
        });
    }

    private void startConnect() {
        BleManager.getInstance().connect(MAC_RS_BTEVS1, new BleGattCallback() {
            @Override
            public void onStartConnect() {
                Log.i(TAG, "onStartConnect: connect to " + MAC_RS_BTEVS1);
            }

            @Override
            public void onConnectFail(BleDevice bleDevice, BleException exception) {
                Log.w(TAG, "onConnectFail: cannot connect to " + bleDevice.getMac(), new Throwable(exception.getDescription()));
            }

            @Override
            public void onConnectSuccess(BleDevice bleDevice, BluetoothGatt gatt, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.i(TAG, "onConnectSuccess: " + bleDevice.getMac());
                    for (BluetoothGattService service : gatt.getServices()) {
                        for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                            if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) != 0
                                    && ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY)) != 0) {
                                try {
                                    boolean read = gatt.readCharacteristic(characteristic);
                                    Log.i(TAG, "onConnectSuccess: " + read);
                                    Log.i(TAG, "onConnectSuccess: " + service.getUuid().toString());
                                    Log.i(TAG, "onConnectSuccess: " + characteristic.getUuid().toString());
                                    BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                                    if (descriptor != null) {
                                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                        boolean b = gatt.writeDescriptor(descriptor);
                                        Log.i(TAG, "onConnectSuccess: " + b);
                                        if (b) {
                                            doNotify(bleDevice, service.getUuid().toString(), characteristic.getUuid().toString());
                                        }
                                    }
                                } catch (SecurityException e) {
                                    Log.e(TAG, "onConnectSuccess: ", e);
                                }
                            }
                        }
                    }
                } else {
                    Log.d(TAG, "onConnectSuccess: failed");
                }

            }

            @Override
            public void onDisConnected(boolean isActiveDisConnected, BleDevice device, BluetoothGatt gatt, int status) {
                Log.i(TAG, "onDisConnected: " + device.getMac());
                if (isActiveDisConnected) {
                    Toast.makeText(MainActivity.this, "Disconnected " + device.getMac(), Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private boolean checkGPSIsOpen() {
        Log.d(TAG, "checkGPSIsOpen: ");
        LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null)
            return false;
        return locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER);
    }

    private void connect(final String mac) {
        BleManager.getInstance().connect(mac, new BleGattCallback() {
            @Override
            public void onStartConnect() {
                Log.d(TAG, "onStartConnect: ");
            }

            @Override
            public void onConnectFail(BleDevice bleDevice, BleException exception) {
                Log.w(TAG, String.format("onConnectFail: %s %s -> %s", bleDevice.getMac(), bleDevice.getName(), exception.getDescription()));
            }

            @Override
            public void onConnectSuccess(BleDevice bleDevice, BluetoothGatt gatt, int status) {
            }

            @Override
            public void onDisConnected(boolean isActiveDisConnected, BleDevice device, BluetoothGatt gatt, int status) {
                if (isActiveDisConnected) {
                    Toast.makeText(MainActivity.this, String.format("Disconnected %s", device.getMac()), Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void doNotify(final BleDevice bleDevice, String uuid_service, String uuid_notify) {
        BleManager.getInstance().notify(bleDevice, uuid_service, uuid_notify, new BleNotifyCallback() {
            @Override
            public void onNotifySuccess() {
                Log.i(TAG, "onNotifySuccess: ");
            }

            @Override
            public void onNotifyFailure(BleException exception) {
                Log.d(TAG, "onNotifyFailure: " + exception.getDescription());
            }

            @Override
            public void onCharacteristicChanged(byte[] data) {
                Log.i(TAG, "onCharacteristicChanged: " + bytearray2Hex(data));
            }
        });
    }

    private static String bytearray2Hex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            // Konversi setiap byte ke hex dan tambahkan spasi
            hexString.append(String.format("%02X ", b));
        }
        // Hapus spasi terakhir
        return hexString.toString().trim();
    }
}