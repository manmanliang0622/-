package catcatch;

import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.text.*;

public class LobbyScene {

    public static Scene build(CatCatchApp app, GameClient client, boolean isHost) {
        Theme t = CatCatchApp.theme;

        // ── Root layout ───────────────────────────────────────────────────────
        BorderPane root = new BorderPane();
        root.setStyle(t.rootStyle());
        root.setPadding(new Insets(30));

        // ── Top bar ───────────────────────────────────────────────────────────
        HBox topBar = new HBox(12);
        topBar.setAlignment(Pos.CENTER_LEFT);

        Button backBtn = MainMenuScene.secondaryBtn("← 返回主選單", 140, t);
        backBtn.setOnAction(e -> {
            client.close();
            app.goMainMenu();
        });

        Label roomTitle = new Label("房間大廳");
        roomTitle.setStyle("-fx-font-size:22px;-fx-font-weight:bold;-fx-text-fill:" + t.accent + ";");

        Label msgLabel = new Label("等待玩家…");
        msgLabel.setStyle("-fx-font-size:12px;-fx-text-fill:" + t.muted + ";");

        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        topBar.getChildren().addAll(backBtn, roomTitle, spacer, msgLabel);

        BorderPane.setMargin(topBar, new Insets(0, 0, 20, 0));
        root.setTop(topBar);

        // ── Centre: left (room info) + right (players) ────────────────────────
        HBox centre = new HBox(20);
        centre.setAlignment(Pos.TOP_CENTER);

        // Left: room info card
        VBox infoCard = new VBox(16);
        infoCard.setStyle(t.cardStyle());
        infoCard.setPadding(new Insets(24));
        infoCard.setPrefWidth(320);

        Label infoTitle = new Label("🔑 房間資訊");
        infoTitle.setStyle("-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:" + t.text + ";");

        Label codeHeadLabel = new Label("房間號碼");
        codeHeadLabel.setStyle(t.labelStyle(12, true));

        Label codeLabel = new Label("……");
        codeLabel.setStyle("-fx-font-size:36px;-fx-font-weight:bold;-fx-letter-spacing:6;-fx-text-fill:" + t.accent + ";");

        Button copyCodeBtn = MainMenuScene.secondaryBtn("📋 複製房號", 220, t);
        copyCodeBtn.setOnAction(e -> {
            ClipboardContent cc = new ClipboardContent();
            cc.putString(codeLabel.getText());
            Clipboard.getSystemClipboard().setContent(cc);
            copyCodeBtn.setText("✓ 已複製");
            Platform.runLater(() -> copyCodeBtn.setText("📋 複製房號"));
        });

        Label ipHeadLabel = new Label("你的 IP 位址（分享給其他玩家）");
        ipHeadLabel.setStyle(t.labelStyle(12, true));
        ipHeadLabel.setWrapText(true);

        Label ipLabel = new Label(isHost ? CatCatchApp.getLocalIP() : "（加入者不需提供 IP）");
        ipLabel.setStyle("-fx-font-size:18px;-fx-font-weight:bold;-fx-text-fill:" + t.accent + ";");

        Label portLabel = new Label("連接埠固定：" + CatCatchApp.SERVER_PORT);
        portLabel.setStyle(t.labelStyle(11, false));

        Separator sep = new Separator();

        // Ready button
        Button readyBtn = new Button("○ 準備完成");
        readyBtn.setPrefWidth(220);
        readyBtn.setStyle(t.primaryBtn());
        readyBtn.setOnMouseEntered(e -> readyBtn.setStyle(t.primaryBtnHover()));
        readyBtn.setOnMouseExited(e ->  readyBtn.setStyle(t.primaryBtn()));

        final boolean[] ready = { false };
        readyBtn.setOnAction(e -> {
            ready[0] = !ready[0];
            client.sendReady(ready[0]);
            readyBtn.setText(ready[0] ? "✓ 已準備（點擊取消）" : "○ 準備完成");
        });

        // Start button (host only)
        Button startBtn = new Button("▶ 開始遊戲");
        startBtn.setPrefWidth(220);
        startBtn.setDisable(true);
        startBtn.setStyle("-fx-background-color:" + t.accentDark + ";-fx-text-fill:white;-fx-font-size:14px;" +
                          "-fx-font-weight:bold;-fx-background-radius:22;-fx-cursor:hand;-fx-padding:10 30;");
        startBtn.setOnAction(e -> client.sendStart());
        startBtn.setVisible(isHost);
        startBtn.setManaged(isHost);

        infoCard.getChildren().addAll(infoTitle, codeHeadLabel, codeLabel, copyCodeBtn);
        if (isHost) infoCard.getChildren().addAll(ipHeadLabel, ipLabel, portLabel);
        infoCard.getChildren().addAll(sep, readyBtn, startBtn);

        // Right: player list card
        VBox playersCard = new VBox(12);
        playersCard.setStyle(t.cardStyle());
        playersCard.setPadding(new Insets(24));
        playersCard.setPrefWidth(380);

        Label playersTitle = new Label("👥 玩家列表");
        playersTitle.setStyle("-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:" + t.text + ";");

        VBox playerRows = new VBox(8);
        playersCard.getChildren().addAll(playersTitle, new Separator(), playerRows);

        centre.getChildren().addAll(infoCard, playersCard);
        HBox.setHgrow(playersCard, Priority.ALWAYS);
        root.setCenter(centre);

        // ── State listener ────────────────────────────────────────────────────
        client.setListener(new GameClient.Listener() {
            @Override public void onConnected(String id) {}

            @Override public void onState(GameClient.GameState state) {
                codeLabel.setText(state.roomCode() != null ? state.roomCode() : "……");
                msgLabel.setText(state.message() != null ? state.message() : "");

                // Update player rows
                playerRows.getChildren().clear();
                for (GameClient.RemotePlayer p : state.players()) {
                    HBox row = new HBox(10);
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.setPadding(new Insets(8, 12, 8, 12));
                    row.setStyle("-fx-background-color:" + t.bg + ";-fx-background-radius:8;");

                    boolean isSelf = p.id().equals(state.selfId());
                    String nameText = p.name() + (isSelf ? "  （你）" : "") +
                                      (p.id().equals(state.hostId()) ? "  👑" : "");

                    Label nameLbl = new Label(nameText);
                    nameLbl.setStyle("-fx-font-size:14px;-fx-text-fill:" + t.text + ";" +
                                     (isSelf ? "-fx-font-weight:bold;" : ""));

                    Region sp2 = new Region(); HBox.setHgrow(sp2, Priority.ALWAYS);

                    Label badge = new Label(p.ready() ? "✓  已準備" : "○  等待中");
                    badge.setStyle("-fx-font-size:12px;-fx-font-weight:bold;" +
                                   "-fx-background-radius:12;-fx-padding:3 10;" +
                                   (p.ready()
                                    ? "-fx-background-color:#27AE60;-fx-text-fill:white;"
                                    : "-fx-background-color:" + t.muted + ";-fx-text-fill:white;"));

                    row.getChildren().addAll(nameLbl, sp2, badge);
                    playerRows.getChildren().add(row);
                }

                // Enable start only when all ready (host view)
                if (isHost) {
                    boolean allReady = !state.players().isEmpty() &&
                        state.players().stream().allMatch(GameClient.RemotePlayer::ready);
                    startBtn.setDisable(!allReady);
                }

                // Navigate when game starts
                if ("playing".equals(state.status())) {
                    CatCatchApp.audio.playStart();
                    app.goGame(client);
                }
            }

            @Override public void onError(String msg) { msgLabel.setText("⚠ " + msg); }

            @Override public void onDisconnected(String reason) {
                Platform.runLater(() -> {
                    client.close();
                    app.goMainMenu();
                });
            }
        });

        return new Scene(root, CatCatchApp.WIDTH, CatCatchApp.HEIGHT);
    }
}
