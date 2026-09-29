package com.example.util;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.InputStream;
import java.security.MessageDigest;

/** Synthetic ACTION_SEND receiver, in the test APK's own UID, Android/JDK only. */
public final class ShareReceiverLabActivity extends Activity {
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        String target = "com.aistudio.hbandhealthtech.pxq97m.storagelab";
        if (!getPackageName().equals(target + ".test") ||
                !(Build.HARDWARE.equals("ranchu") || Build.HARDWARE.equals("goldfish")) ||
                checkSelfPermission(android.Manifest.permission.INTERNET) != PackageManager.PERMISSION_DENIED) {
            throw new SecurityException("Synthetic offline emulator only");
        }
        JSONObject result = new JSONObject();
        try {
            if (getSharedPreferences("share-chooser-lab", MODE_PRIVATE).contains("receipt")) {
                throw new IllegalStateException("Preserve prior receiver evidence; use a fresh AVD");
            }
            Intent incoming = getIntent();
            Uri uri = incoming.getParcelableExtra(Intent.EXTRA_STREAM);
            if (!Intent.ACTION_SEND.equals(incoming.getAction()) || !"image/png".equals(incoming.getType()) ||
                    uri == null || !"content".equals(uri.getScheme()) || !(target + ".fileprovider").equals(uri.getAuthority())) {
                throw new SecurityException("Unexpected synthetic share");
            }
            int senderUid = getPackageManager().getApplicationInfo(target, 0).uid;
            if (senderUid == android.os.Process.myUid()) throw new SecurityException("Distinct UID required");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long bytes = 0;
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IllegalStateException("No stream");
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) { digest.update(buffer, 0, count); bytes += count; }
            }
            StringBuilder hash = new StringBuilder();
            for (byte b : digest.digest()) hash.append(String.format(java.util.Locale.ROOT, "%02x", b));
            boolean writeDenied = false;
            try (ParcelFileDescriptor descriptor = getContentResolver().openFileDescriptor(uri, "rw")) {
                if (descriptor == null) throw new IllegalStateException("No descriptor");
            } catch (SecurityException expected) { writeDenied = true; }
            result.put("action", incoming.getAction()).put("mime", incoming.getType())
                    .put("uri", uri.toString()).put("summary", incoming.getStringExtra(Intent.EXTRA_TEXT))
                    .put("flags", incoming.getFlags()).put("sha256", hash.toString()).put("bytes", bytes)
                    .put("uid", android.os.Process.myUid()).put("pid", android.os.Process.myPid())
                    .put("writeDenied", writeDenied).put("outcome", "received");
            if (!getSharedPreferences("share-chooser-lab", MODE_PRIVATE).edit()
                    .putString("receipt", result.toString()).commit()) throw new IllegalStateException("Cannot persist receipt");
            TextView view = new TextView(this);
            view.setText("Next2U Lab Receiver\nSynthetic card received");
            setContentView(view);
        } catch (Exception e) { throw new IllegalStateException("Synthetic receiver failed", e); }
    }
}
