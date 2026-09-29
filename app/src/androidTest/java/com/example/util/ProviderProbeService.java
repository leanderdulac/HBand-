package com.example.util;

import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import java.io.InputStream;
import java.security.MessageDigest;

/** Android/JDK only: the test APK must run independently of the target classpath/UID. */
public final class ProviderProbeService extends Service {
    private static final String TARGET = "com.aistudio.hbandhealthtech.pxq97m.storagelab";
    private int targetUid;
    private Messenger messenger;

    @Override public void onCreate() {
        super.onCreate();
        if (!getPackageName().equals(TARGET + ".test") ||
                !(Build.HARDWARE.equals("ranchu") || Build.HARDWARE.equals("goldfish")) ||
                checkSelfPermission(android.Manifest.permission.INTERNET) != PackageManager.PERMISSION_DENIED) {
            throw new SecurityException("Synthetic offline emulator only");
        }
        try {
            targetUid = getPackageManager().getApplicationInfo(TARGET, 0).uid;
        } catch (PackageManager.NameNotFoundException e) { throw new IllegalStateException(e); }
        if (targetUid == android.os.Process.myUid()) throw new SecurityException("Separate UID required");
        messenger = new Messenger(new Handler(getMainLooper()) {
            @Override public void handleMessage(Message request) {
                if (request.sendingUid != targetUid || request.replyTo == null) return;
                Bundle result = new Bundle();
                result.putInt("uid", android.os.Process.myUid());
                result.putInt("pid", android.os.Process.myPid());
                try {
                    if (request.what == 3) {
                        result.putString("receipt", getSharedPreferences("share-chooser-lab", MODE_PRIVATE)
                                .getString("receipt", ""));
                        Message reply = Message.obtain(null, request.what);
                        reply.setData(result);
                        request.replyTo.send(reply);
                        return;
                    }
                    Uri uri = Uri.parse(request.getData().getString("uri", ""));
                    if (!"content".equals(uri.getScheme()) || ! (TARGET + ".fileprovider").equals(uri.getAuthority())) {
                        throw new IllegalArgumentException("Only the synthetic target provider is allowed");
                    }
                    if (request.what == 1) {
                        MessageDigest digest = MessageDigest.getInstance("SHA-256");
                        long size = 0;
                        try (InputStream input = getContentResolver().openInputStream(uri)) {
                            if (input == null) throw new IllegalStateException("No input stream");
                            byte[] buffer = new byte[8192];
                            int count;
                            while ((count = input.read(buffer)) != -1) {
                                digest.update(buffer, 0, count);
                                size += count;
                            }
                        }
                        StringBuilder hex = new StringBuilder();
                        for (byte b : digest.digest()) hex.append(String.format(java.util.Locale.ROOT, "%02x", b));
                        result.putString("sha256", hex.toString());
                        result.putLong("bytes", size);
                    } else if (request.what == 2) {
                        // 'rw' does not truncate; never write or change the retained artifact.
                        try (ParcelFileDescriptor descriptor = getContentResolver().openFileDescriptor(uri, "rw")) {
                            if (descriptor == null) throw new IllegalStateException("No descriptor");
                        }
                    } else { throw new IllegalArgumentException("Unknown probe operation"); }
                    result.putString("outcome", "allowed");
                } catch (SecurityException denied) {
                    result.putString("outcome", "denied");
                    result.putString("exception", denied.getClass().getName());
                } catch (Exception unexpected) {
                    result.putString("outcome", "error");
                    result.putString("exception", unexpected.getClass().getName());
                }
                Message reply = Message.obtain(null, request.what);
                reply.setData(result);
                try { request.replyTo.send(reply); }
                catch (RemoteException e) { throw new IllegalStateException(e); }
            }
        });
    }

    @Override public IBinder onBind(Intent intent) { return messenger.getBinder(); }
}
