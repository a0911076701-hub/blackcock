package com.black.control;

import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private ServerSocket serverSocket;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean isRunning = true;
    private Map<String, Socket> devices = new HashMap<>(); // تخزين الأجهزة المتصلة

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl("file:///android_asset/control_panel.html");

        startServer();
    }

    private void startServer() {
        executor.execute(() -> {
            try {
                serverSocket = new ServerSocket(8080);
                while (isRunning) {
                    Socket clientSocket = serverSocket.accept();
                    handleClient(clientSocket);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void handleClient(Socket socket) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            String request = in.readLine();
            if (request == null) {
                socket.close();
                return;
            }

            // تسجيل جهاز جديد
            if (request.startsWith("REGISTER:")) {
                String data = request.substring(9);
                String deviceId = extractDeviceId(data);
                if (deviceId != null) {
                    devices.put(deviceId, socket);
                    runOnUiThread(() -> webView.loadUrl("javascript:addDevice('" + data + "')"));
                    out.println("OK");
                }
                return;
            }

            // استقبال بيانات من الجهاز
            if (request.startsWith("DATA:")) {
                String data = request.substring(5);
                runOnUiThread(() -> webView.loadUrl("javascript:showData('" + data + "')"));
                out.println("OK");
                return;
            }

            // أمر من لوحة التحكم إلى جهاز معين
            if (request.startsWith("CMD:")) {
                String cmdData = request.substring(4);
                String[] parts = cmdData.split("\\|");
                if (parts.length == 2) {
                    String command = parts[0];
                    String deviceId = parts[1];
                    Socket deviceSocket = devices.get(deviceId);
                    if (deviceSocket != null && !deviceSocket.isClosed()) {
                        PrintWriter deviceOut = new PrintWriter(deviceSocket.getOutputStream(), true);
                        deviceOut.println("CMD:" + command);
                        out.println("OK");
                    } else {
                        out.println("ERROR: Device not connected");
                    }
                }
                return;
            }

            out.println("ERROR: Unknown request");
            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String extractDeviceId(String data) {
        try {
            org.json.JSONObject json = new org.json.JSONObject(data);
            return json.getString("device_id");
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isRunning = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (Exception e) { e.printStackTrace(); }
    }
}
