package com.translator;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private TextToSpeech tts;
    private static final int PERMISSION_REQUEST_CODE = 1;
    private JSONObject zhToEnDict;
    private JSONObject enToZhDict;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Text-to-Speech
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.CHINESE);
            }
        });

        // Load dictionaries
        loadDictionaries();

        // Setup WebView
        webView = findViewById(R.id.webView);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new WebAppInterface(), "Android");

        // Check permissions
        checkPermissions();
    }

    private void checkPermissions() {
        String[] permissions = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET
        };

        boolean allGranted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (!allGranted) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
        } else {
            loadWebInterface();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                loadWebInterface();
            } else {
                Toast.makeText(this, "需要权限才能使用语音功能", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void loadDictionaries() {
        try {
            zhToEnDict = new JSONObject(loadJSONFromAsset("zh_to_en.json"));
            enToZhDict = new JSONObject(loadJSONFromAsset("en_to_zh.json"));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private String loadJSONFromAsset(String filename) {
        String json;
        try {
            InputStream is = getAssets().open(filename);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            json = new String(buffer, StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
            return "{}";
        }
        return json;
    }

    private void loadWebInterface() {
        webView.loadUrl("file:///android_asset/index.html");
    }

    public class WebAppInterface {
        @JavascriptInterface
        public void speakText(String text, String language) {
            if (tts != null) {
                Locale locale = language.equals("zh") ? Locale.CHINESE : Locale.ENGLISH;
                tts.setLanguage(locale);
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
            }
        }

        @JavascriptInterface
        public String translate(String text, String fromLang) {
            try {
                JSONObject dict = fromLang.equals("zh") ? zhToEnDict : enToZhDict;
                
                // Direct match
                if (dict.has(text)) {
                    return dict.getString(text);
                }
                
                // Try to find in phrases
                JSONArray phrases = dict.optJSONArray("_phrases");
                if (phrases != null) {
                    for (int i = 0; i < phrases.length(); i++) {
                        JSONObject phrase = phrases.getJSONObject(i);
                        if (phrase.getString("from").equals(text)) {
                            return phrase.getString("to");
                        }
                    }
                }
                
                // Word-by-word translation
                String[] words = text.split("\\s+");
                StringBuilder result = new StringBuilder();
                for (String word : words) {
                    if (dict.has(word)) {
                        result.append(dict.getString(word)).append(" ");
                    } else {
                        result.append(word).append(" ");
                    }
                }
                return result.toString().trim();
                
            } catch (JSONException e) {
                return text;
            }
        }

        @JavascriptInterface
        public void showToast(String message) {
            Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
