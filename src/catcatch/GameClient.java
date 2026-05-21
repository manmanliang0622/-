package catcatch;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import javafx.application.Platform;

final class GameClient {
    interface Listener {
        void onConnected(String playerId);
        void onState(GameState state);
        void onError(String message);
        void onDisconnected(String reason);
    }

    private volatile Listener listener;
    private final Object writerLock = new Object();

    private ExecutorService readExecutor, writeExecutor;
    private volatile boolean running, intentionalClose;
    private Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;
    private String playerId;

    GameClient(Listener listener) { this.listener = listener; }

    void setListener(Listener listener) { this.listener = listener; }

    void connectAndJoin(String rawHost, int port, String name, String roomCode, boolean create)
            throws IOException {
        close();
        intentionalClose = false;
        String host = normalizeHost(rawHost);

        Socket s = new Socket();
        s.connect(new InetSocketAddress(host, port), 5_000);
        s.setTcpNoDelay(true);
        BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
        BufferedWriter w = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
        socket = s; reader = r; writer = w;

        sendLine(Protocol.encode("JOIN",
            "name", name, "room", roomCode == null ? "" : roomCode, "create", create ? "1" : "0"));

        String resp = reader.readLine();
        if (resp == null) throw new IOException("伺服器沒有回應。");
        Protocol.Message msg = Protocol.parse(resp);
        if ("ERROR".equals(msg.type())) { closeSocket(); throw new IOException(msg.get("message")); }
        if (!"JOINED".equals(msg.type())) { closeSocket(); throw new IOException("伺服器回應格式不正確。"); }

        playerId = msg.get("playerId");
        running = true;
        readExecutor = executor("catcatch-reader");
        writeExecutor = executor("catcatch-writer");
        readExecutor.submit(this::readLoop);
        Platform.runLater(() -> listener.onConnected(playerId));
    }

    void sendReady(boolean ready)  { send(Protocol.encode("READY",      "playerId", playerId, "value", ready ? "1" : "0")); }
    void sendStart()               { send(Protocol.encode("START",       "playerId", playerId)); }
    void sendClick(String id)      { send(Protocol.encode("CLICK",       "playerId", playerId, "id", id)); }
    void sendPlayAgain()           { send(Protocol.encode("PLAY_AGAIN",  "playerId", playerId)); }
    void sendBackLobby()           { send(Protocol.encode("BACK_LOBBY",  "playerId", playerId)); }
    String getPlayerId()           { return playerId; }

    void close() {
        intentionalClose = true;
        running = false;
        if (socket != null && socket.isConnected() && !socket.isClosed() && playerId != null) {
            try { sendLine(Protocol.encode("LEAVE", "playerId", playerId)); } catch (IOException ignored) {}
        }
        closeSocket();
        shutdown(readExecutor); shutdown(writeExecutor);
        readExecutor = writeExecutor = null;
        playerId = null;
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private void readLoop() {
        try {
            String line;
            while (running && (line = reader.readLine()) != null) {
                Protocol.Message msg = Protocol.parse(line);
                switch (msg.type()) {
                    case "STATE" -> { GameState st = parseState(msg.fields(), playerId);
                                      Platform.runLater(() -> listener.onState(st)); }
                    case "ERROR" -> Platform.runLater(() -> listener.onError(msg.get("message")));
                    default -> {}
                }
            }
            if (running && !intentionalClose) notify("與伺服器連線中斷。");
        } catch (IOException e) {
            if (running && !intentionalClose) notify("連線中斷：" + e.getMessage());
        } finally {
            running = false;
            closeSocket();
        }
    }

    private void send(String payload) {
        if (!running || payload == null || payload.isBlank() || writeExecutor == null) return;
        writeExecutor.submit(() -> {
            try { sendLine(payload); }
            catch (IOException e) { if (!intentionalClose) notify("送出失敗：" + e.getMessage()); }
        });
    }

    private void sendLine(String payload) throws IOException {
        synchronized (writerLock) {
            if (writer == null) throw new IOException("尚未連線。");
            writer.write(payload); writer.newLine(); writer.flush();
        }
    }

    private void notify(String reason) {
        running = false;
        Platform.runLater(() -> listener.onDisconnected(reason));
    }

    private void closeSocket() {
        try { if (reader != null) reader.close(); } catch (IOException ignored) {}
        try { if (writer != null) writer.close(); } catch (IOException ignored) {}
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        reader = null; writer = null; socket = null;
    }

    private void shutdown(ExecutorService ex) {
        if (ex == null) return;
        ex.shutdownNow();
        try { ex.awaitTermination(200, TimeUnit.MILLISECONDS); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }

    private ExecutorService executor(String name) {
        return Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, name); t.setDaemon(true); return t; });
    }

    private String normalizeHost(String raw) {
        String h = raw == null ? "" : raw.trim();
        if (h.isBlank()) return "127.0.0.1";
        h = h.replace("http://", "").replace("https://", "");
        int slash = h.indexOf('/'); if (slash >= 0) h = h.substring(0, slash);
        int colon = h.indexOf(':'); if (colon >= 0) h = h.substring(0, colon);
        return h;
    }

    private GameState parseState(Map<String, String> fields, String selfId) {
        List<RemotePlayer> players = new ArrayList<>();
        for (String[] row : Protocol.decodeList(fields.get("players"))) {
            if (row.length < 5) continue;
            players.add(new RemotePlayer(row[0], row[1], Integer.parseInt(row[2]),
                "1".equals(row[3]), "1".equals(row[4])));
        }
        List<RemoteEntity> entities = new ArrayList<>();
        for (String[] row : Protocol.decodeList(fields.get("objects"))) {
            if (row.length < 6) continue;
            entities.add(new RemoteEntity(row[0], row[1], row[2],
                Double.parseDouble(row[3]), Double.parseDouble(row[4]), Double.parseDouble(row[5])));
        }
        return new GameState(
            selfId == null ? fields.get("selfId") : selfId,
            fields.get("room"), fields.get("status"), fields.get("hostId"),
            fields.get("target"),
            Integer.parseInt(fields.getOrDefault("remaining", "0")),
            Integer.parseInt(fields.getOrDefault("returnSeconds", "0")),
            fields.get("message"), players, entities);
    }

    // ── Records ───────────────────────────────────────────────────────────────

    record GameState(String selfId, String roomCode, String status, String hostId,
                     String targetVariant, int remainingSeconds, int returnSeconds,
                     String message, List<RemotePlayer> players, List<RemoteEntity> entities) {
        RemotePlayer self() {
            return players.stream().filter(p -> p.id().equals(selfId)).findFirst().orElse(null);
        }
        boolean isHost() { return hostId != null && hostId.equals(selfId); }
    }

    record RemotePlayer(String id, String name, int score, boolean ready, boolean connected) {}
    record RemoteEntity(String id, String kind, String variant, double x, double y, double size) {}
}
