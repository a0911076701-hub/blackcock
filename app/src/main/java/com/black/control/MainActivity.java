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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private ServerSocket serverSocket;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean isRunning = true;

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
            if (request != null) {
                String response = processRequest(request);
                out.println(response);
            }
            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String processRequest(String request) {
        if (request.startsWith("REGISTER:")) {
            String data = request.substring(9);
            runOnUiThread(() -> webView.loadUrl("javascript:addDevice('" + data + "')"));
            return "OK";
        } else if (request.startsWith("DATA:")) {
            String data = request.substring(5);
            runOnUiThread(() -> webView.loadUrl("javascript:showData('" + data + "')"));
            return "OK";
        }
        return "ERROR";
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
