package com.custom.midisurface;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.midi.MidiDevice;
import android.media.midi.MidiDeviceInfo;
import android.media.midi.MidiInputPort;
import android.media.midi.MidiManager;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONObject;

public class MidiBridge {

    private final MainActivity activity;
    private final WebView web;
    private final MidiManager midiManager;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SharedPreferences prefs;

    private MidiDevice device;
    private MidiInputPort outPort;
    private int openInfoId = -1;
    private MidiManager.DeviceCallback deviceCallback;

    MidiBridge(MainActivity activity, WebView web) {
        this.activity = activity;
        this.web = web;
        this.prefs = activity.getSharedPreferences("midi_surface", Context.MODE_PRIVATE);
        this.midiManager = (MidiManager) activity.getSystemService(Context.MIDI_SERVICE);

        if (midiManager != null) {
            deviceCallback = new MidiManager.DeviceCallback() {
                @Override
                public void onDeviceAdded(MidiDeviceInfo info) {
                    notifyDevices();
                }

                @Override
                public void onDeviceRemoved(MidiDeviceInfo info) {
                    if (info.getId() == openInfoId) {
                        closePort();
                        activity.js("window.__midiDisconnected&&window.__midiDisconnected()");
                    }
                    notifyDevices();
                }
            };
            midiManager.registerDeviceCallback(deviceCallback, main);
        }
    }

    @JavascriptInterface
    public String listPorts() {
        JSONArray arr = new JSONArray();
        if (midiManager == null) return arr.toString();
        MidiDeviceInfo[] devices = midiManager.getDevices();
        if (devices == null) return arr.toString();
        for (MidiDeviceInfo info : devices) {
            try {
                JSONObject o = new JSONObject();
                o.put("id", info.getId());
                o.put("name", describe(info));
                o.put("inputs", info.getInputPortCount());
                o.put("outputs", info.getOutputPortCount());
                arr.put(o);
            } catch (Exception ignored) {
            }
        }
        return arr.toString();
    }

    @JavascriptInterface
    public void connect(final int id) {
        if (midiManager == null) {
            error("MIDI is not available on this device");
            return;
        }

        MidiDeviceInfo target = null;
        MidiDeviceInfo[] devices = midiManager.getDevices();
        if (devices != null) {
            for (MidiDeviceInfo info : devices) {
                if (info.getId() == id) {
                    target = info;
                    break;
                }
            }
        }
        if (target == null) {
            error("Device not found - replug the cable and refresh");
            return;
        }

        closePort();
        final MidiDeviceInfo chosen = target;

        midiManager.openDevice(chosen, device -> {
            if (device == null) {
                error("Could not open the MIDI device");
                return;
            }
            MidiBridge.this.device = device;

            if (chosen.getInputPortCount() < 1) {
                closePort();
                error("This device has no sendable port");
                return;
            }

            try {
                outPort = device.openInputPort(0);
                openInfoId = chosen.getId();
                connected(describe(chosen));
            } catch (Exception e) {
                closePort();
                error("Could not open port: " + e.getMessage());
            }
        }, main);
    }

    @JavascriptInterface
    public void disconnect() {
        closePort();
    }

    @JavascriptInterface
    public void sendCC(int channel, int cc, int value) {
        byte[] msg = {
                (byte) (0xB0 | ((channel - 1) & 0x0F)),
                (byte) (cc & 0x7F),
                (byte) (value & 0x7F)
        };
        send(msg);
    }

    @JavascriptInterface
    public void sendNote(int channel, int note, int velocity) {
        byte[] msg = {
                (byte) (0x90 | ((channel - 1) & 0x0F)),
                (byte) (note & 0x7F),
                (byte) (velocity & 0x7F)
        };
        send(msg);
    }

    @JavascriptInterface
    public void sendNoteOff(int channel, int note) {
        byte[] msg = {
                (byte) (0x80 | ((channel - 1) & 0x0F)),
                (byte) (note & 0x7F),
                (byte) 0
        };
        send(msg);
    }

    @JavascriptInterface
    public void sendPitchBend(int channel, int value) {
        int v = value < 0 ? 0 : (value > 16383 ? 16383 : value);
        byte[] msg = {
                (byte) (0xE0 | ((channel - 1) & 0x0F)),
                (byte) (v & 0x7F),
                (byte) ((v >> 7) & 0x7F)
        };
        send(msg);
    }

    @JavascriptInterface
    public void saveLayout(String json) {
        prefs.edit().putString("layout", json).apply();
    }

    @JavascriptInterface
    public String loadLayout() {
        return prefs.getString("layout", "");
    }

    private void send(byte[] msg) {
        MidiInputPort p = outPort;
        if (p == null) return;
        try {
            p.send(msg, 0, msg.length);
        } catch (Exception ignored) {
        }
    }

    private void closePort() {
        openInfoId = -1;
        try {
            if (outPort != null) outPort.close();
        } catch (Exception ignored) {
        }
        outPort = null;
        try {
            if (device != null) device.close();
        } catch (Exception ignored) {
        }
        device = null;
    }

    private void notifyDevices() {
        final WebView w = web;
        if (w == null) return;
        w.post(() -> w.evaluateJavascript(
                "window.__midiDevicesChanged&&window.__midiDevicesChanged()", null));
    }

    private void connected(String name) {
        activity.js("window.__midiConnected&&window.__midiConnected(" + JSONObject.quote(name) + ")");
    }

    private void error(String message) {
        String safe = message == null ? "MIDI error" : message;
        activity.js("window.__midiError&&window.__midiError(" + JSONObject.quote(safe) + ")");
    }

    private String describe(MidiDeviceInfo info) {
        String name = null;
        try {
            name = info.getProperties().getString(MidiDeviceInfo.PROPERTY_NAME);
        } catch (Exception ignored) {
        }
        if (name == null || name.trim().isEmpty()) {
            try {
                String m = info.getProperties().getString(MidiDeviceInfo.PROPERTY_MANUFACTURER);
                String p = info.getProperties().getString(MidiDeviceInfo.PROPERTY_PRODUCT);
                StringBuilder sb = new StringBuilder();
                if (m != null && !m.trim().isEmpty()) sb.append(m.trim()).append(' ');
                if (p != null && !p.trim().isEmpty()) sb.append(p.trim());
                if (sb.length() > 0) name = sb.toString();
            } catch (Exception ignored) {
            }
        }
        if (name == null || name.trim().isEmpty()) name = "MIDI device " + info.getId();
        return name.trim();
    }

    void close() {
        if (midiManager != null && deviceCallback != null) {
            try {
                midiManager.unregisterDeviceCallback(deviceCallback);
            } catch (Exception ignored) {
            }
        }
        closePort();
    }
}
