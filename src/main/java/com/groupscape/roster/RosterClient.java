package com.groupscape.roster;

import com.google.gson.Gson;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * Real-time roster feed for the party overlay. Connects to the server's
 * `/api/characters/{accountHash}/ws` endpoint using the same API-key
 * `Authorization` header as the plugin's existing HTTP telemetry POSTs, gets
 * one `roster_snapshot` handshake, then applies `vitals_update` deltas as
 * they arrive. Auto-reconnects with a fixed delay on any close/failure.
 */
@Slf4j
public class RosterClient {
    private static final int RECONNECT_DELAY_SECONDS = 5;
    private static final String ROSTER_SNAPSHOT = "roster_snapshot";
    private static final String VITALS_UPDATE = "vitals_update";

    private final OkHttpClient okHttpClient;
    private final Gson gson;
    private final RosterState rosterState;
    private final ScheduledExecutorService reconnectExecutor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "groupscape-roster-reconnect");
                t.setDaemon(true);
                return t;
            });

    private WebSocket webSocket;
    private volatile boolean intentionallyClosed = true;
    private String connectedAccountHash;
    private String connectedApiKey;

    public RosterClient(OkHttpClient okHttpClient, Gson gson, RosterState rosterState) {
        this.okHttpClient = okHttpClient;
        this.gson = gson;
        this.rosterState = rosterState;
    }

    /** No-op if already connected to this exact accountHash+apiKey. */
    public synchronized void connect(String baseUrl, String accountHash, String apiKey) {
        if (baseUrl == null || accountHash == null || apiKey == null || apiKey.trim().isEmpty()) {
            disconnect();
            return;
        }
        if (webSocket != null && accountHash.equals(connectedAccountHash) && apiKey.equals(connectedApiKey)) {
            return;
        }

        disconnect();
        intentionallyClosed = false;
        connectedAccountHash = accountHash;
        connectedApiKey = apiKey;
        openSocket(baseUrl, accountHash, apiKey);
    }

    public synchronized void disconnect() {
        intentionallyClosed = true;
        connectedAccountHash = null;
        connectedApiKey = null;
        if (webSocket != null) {
            webSocket.close(1000, "client disconnect");
            webSocket = null;
        }
        rosterState.clear();
    }

    public void shutdown() {
        disconnect();
        reconnectExecutor.shutdownNow();
    }

    private void openSocket(String baseUrl, String accountHash, String apiKey) {
        String wsUrl = toWebSocketUrl(baseUrl) + "/api/characters/" + accountHash + "/ws";
        Request request = new Request.Builder().url(wsUrl).header("Authorization", apiKey).build();

        webSocket = okHttpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onMessage(WebSocket webSocket, String text) {
                handleMessage(text);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                scheduleReconnect(baseUrl, accountHash, apiKey);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                log.debug("Party overlay WebSocket failure: {}", t.toString());
                scheduleReconnect(baseUrl, accountHash, apiKey);
            }
        });
    }

    private static String toWebSocketUrl(String baseUrl) {
        if (baseUrl.startsWith("https://")) {
            return "wss://" + baseUrl.substring("https://".length());
        }
        if (baseUrl.startsWith("http://")) {
            return "ws://" + baseUrl.substring("http://".length());
        }
        return baseUrl;
    }

    private void handleMessage(String text) {
        try {
            RosterWireTypes.Envelope envelope = gson.fromJson(text, RosterWireTypes.Envelope.class);
            if (envelope == null || envelope.type == null || envelope.payload == null) {
                return;
            }

            if (ROSTER_SNAPSHOT.equals(envelope.type)) {
                RosterWireTypes.RosterSnapshotPayload payload =
                        gson.fromJson(envelope.payload, RosterWireTypes.RosterSnapshotPayload.class);
                rosterState.replaceAll(payload.roster);
            } else if (VITALS_UPDATE.equals(envelope.type)) {
                RosterWireTypes.VitalsUpdatePayload payload =
                        gson.fromJson(envelope.payload, RosterWireTypes.VitalsUpdatePayload.class);
                if (payload.name != null) {
                    rosterState.getOrCreate(payload.name).applyVitals(payload.vitals);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse party overlay message: {}", e.toString());
        }
    }

    private synchronized void scheduleReconnect(String baseUrl, String accountHash, String apiKey) {
        if (intentionallyClosed) return;
        reconnectExecutor.schedule(() -> {
            synchronized (this) {
                if (!intentionallyClosed
                        && accountHash.equals(connectedAccountHash)
                        && apiKey.equals(connectedApiKey)) {
                    openSocket(baseUrl, accountHash, apiKey);
                }
            }
        }, RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS);
    }
}
