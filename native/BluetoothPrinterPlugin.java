package com.jewelrypos.app;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.PermissionState;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@CapacitorPlugin(
    name = "BluetoothPrinter",
    permissions = {
        @Permission(
            alias = "bluetooth",
            strings = { Manifest.permission.BLUETOOTH_CONNECT }
        )
    }
)
public class BluetoothPrinterPlugin extends Plugin {

    private static final UUID SPP_UUID = UUID.fromString(
        "00001101-0000-1000-8000-00805F9B34FB"
    );

    private final Map<String, BluetoothSocket> connections =
        new ConcurrentHashMap<>();

    @PluginMethod
    public void getPairedDevices(PluginCall call) {
        if (getPermissionState("bluetooth") != PermissionState.GRANTED) {
            requestPermissionForAlias(
                "bluetooth", call, "pairedDevicesPermissionCallback"
            );
            return;
        }

        loadPairedDevices(call);
    }

    @PermissionCallback
    private void pairedDevicesPermissionCallback(PluginCall call) {
        if (getPermissionState("bluetooth") == PermissionState.GRANTED) {
            loadPairedDevices(call);
        } else {
            call.reject("Bluetooth permission was not granted.");
        }
    }

    private void loadPairedDevices(PluginCall call) {
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();

            if (adapter == null) {
                call.reject("This device does not support Bluetooth.");
                return;
            }

            Set<BluetoothDevice> devices = adapter.getBondedDevices();
            JSArray result = new JSArray();

            for (BluetoothDevice device : devices) {
                JSObject item = new JSObject();
                item.put("name", device.getName());
                item.put("address", device.getAddress());
                result.put(item);
            }

            JSObject response = new JSObject();
            response.put("devices", result);
            call.resolve(response);

        } catch (Exception e) {
            call.reject("Unable to get paired devices: " + e.getMessage());
        }
    }

    @PluginMethod
    public void connect(PluginCall call) {
        if (getPermissionState("bluetooth") != PermissionState.GRANTED) {
            requestPermissionForAlias(
                "bluetooth", call, "connectPermissionCallback"
            );
            return;
        }

        connectPrinter(call);
    }

    @PermissionCallback
    private void connectPermissionCallback(PluginCall call) {
        if (getPermissionState("bluetooth") == PermissionState.GRANTED) {
            connectPrinter(call);
        } else {
            call.reject("Bluetooth permission was not granted.");
        }
    }

    private void connectPrinter(PluginCall call) {
        String printerId = call.getString("printerId");
        String address = call.getString("address");

        if (printerId == null || address == null) {
            call.reject("Printer ID and Bluetooth address are required.");
            return;
        }

        new Thread(() -> {
            BluetoothSocket socket = null;

            try {
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();

                if (adapter == null) {
                    call.reject("Bluetooth is not supported.");
                    return;
                }

                BluetoothDevice device = adapter.getRemoteDevice(address);

                socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                socket.connect();

                BluetoothSocket oldSocket = connections.put(printerId, socket);

                if (oldSocket != null) {
                    try {
                        oldSocket.close();
                    } catch (Exception ignored) {}
                }

                JSObject result = new JSObject();
                result.put("printerId", printerId);
                result.put("name", device.getName());
                result.put("address", address);
                result.put("connected", true);

                call.resolve(result);

            } catch (Exception e) {
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (Exception ignored) {}
                }

                call.reject("Unable to connect printer: " + e.getMessage());
            }
        }).start();
    }

    @PluginMethod
    public void disconnect(PluginCall call) {
        String printerId = call.getString("printerId");

        if (printerId == null) {
            call.reject("Printer ID is required.");
            return;
        }

        BluetoothSocket socket = connections.remove(printerId);

        try {
            if (socket != null) {
                socket.close();
            }

            JSObject result = new JSObject();
            result.put("printerId", printerId);
            result.put("connected", false);
            call.resolve(result);

        } catch (Exception e) {
            call.reject("Unable to disconnect printer: " + e.getMessage());
        }
    }

    @PluginMethod
    public void print(PluginCall call) {
        String printerId = call.getString("printerId");
        String content = call.getString("text");

        if (printerId == null || content == null) {
            call.reject("Printer ID and receipt text are required.");
            return;
        }

        new Thread(() -> {
            try {
                BluetoothSocket socket = connections.get(printerId);

                if (socket == null || !socket.isConnected()) {
                    call.reject(printerId + " is not connected.");
                    return;
                }

                OutputStream output = socket.getOutputStream();

                // ESC/POS: initialize printer
                output.write(new byte[] { 0x1B, 0x40 });

                // Print receipt text
                output.write(content.getBytes(StandardCharsets.UTF_8));

                // Feed paper
                output.write(new byte[] { 0x0A, 0x0A, 0x0A });

                output.flush();

                JSObject result = new JSObject();
                result.put("printerId", printerId);
                result.put("printed", true);
                call.resolve(result);

            } catch (Exception e) {
                call.reject("Printing failed: " + e.getMessage());
            }
        }).start();
    }

    @Override
    protected void handleOnDestroy() {
        for (BluetoothSocket socket : connections.values()) {
            try {
                socket.close();
            } catch (Exception ignored) {}
        }

        connections.clear();
        super.handleOnDestroy();
    }
}

