package catcatch;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.Enumeration;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class CatCatchApp extends Application {

    static final int WIDTH = 1100;
    static final int HEIGHT = 700;
    static final int SERVER_PORT = 5050;

    static Theme theme = Theme.PINK;
    static boolean soundEnabled = true;
    static float volume = 0.7f;
    static final SynthAudio audio = new SynthAudio();
    static GameServer.EmbeddedServer embeddedServer = null;

    static CatCatchApp instance;
    private Stage stage;

    @Override
    public void init() { instance = this; }

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        stage.setTitle("抓小貓 ─ 多人連線版");
        stage.setResizable(false);
        stage.setOnCloseRequest(e -> { audio.shutdown(); stopServer(); Platform.exit(); System.exit(0); });
        goMainMenu();
        stage.show();
    }

    void goMainMenu() {
        stopServer();
        setScene(MainMenuScene.build(this));
    }

    void goSettings() { setScene(SettingsScene.build(this)); }

    void goLobby(GameClient client, boolean isHost) {
        setScene(LobbyScene.build(this, client, isHost));
    }

    void goGame(GameClient client) { setScene(GameScene.build(this, client)); }

    void goResult(GameClient.GameState state, GameClient client, boolean isHost) {
        setScene(ResultScene.build(this, state, client, isHost));
    }

    private void setScene(Scene s) { stage.setScene(s); }

    static void stopServer() {
        if (embeddedServer != null) { embeddedServer.stop(); embeddedServer = null; }
    }

    /** Returns the first non-loopback IPv4 address of this machine, for the host to share. */
    static String getLocalIP() {
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface ni = ifaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;
                Enumeration<java.net.InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    java.net.InetAddress a = addrs.nextElement();
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) return a.getHostAddress();
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }

    public static void main(String[] args) { launch(args); }
}
