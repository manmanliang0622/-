package catcatch;

import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.*;

public class MainMenuScene {

    public static Scene build(CatCatchApp app) {
        Theme t = CatCatchApp.theme;

        // ── Root ──────────────────────────────────────────────────────────────
        StackPane root = new StackPane();
        root.setStyle(t.rootStyle());

        VBox card = new VBox(16);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(44, 60, 44, 60));
        card.setStyle(t.cardStyle());
        card.setMaxWidth(430);

        // ── Title ─────────────────────────────────────────────────────────────
        Label icon = new Label("🐱");
        icon.setStyle("-fx-font-size:52px;");

        Label title = new Label("抓小貓");
        title.setStyle("-fx-font-size:36px;-fx-font-weight:bold;-fx-text-fill:" + t.accent + ";");

        Label sub = new Label("多人連線版 ─ 進階程式設計期末專案");
        sub.setStyle("-fx-font-size:11px;-fx-text-fill:" + t.muted + ";");

        // ── Name input ────────────────────────────────────────────────────────
        Label nameLabel = new Label("你的暱稱");
        nameLabel.setStyle(t.labelStyle(13, true));

        TextField nameField = new TextField();
        nameField.setPromptText("輸入暱稱…");
        nameField.setStyle(t.inputStyle());
        nameField.setMaxWidth(300);

        VBox nameBox = new VBox(6, nameLabel, nameField);
        nameBox.setAlignment(Pos.CENTER_LEFT);
        nameBox.setMaxWidth(300);

        Label errorLabel = new Label(" ");
        errorLabel.setStyle("-fx-text-fill:#e74c3c;-fx-font-size:12px;");
        errorLabel.setMinHeight(16);

        // ── Main buttons ──────────────────────────────────────────────────────
        Button hostBtn = primaryBtn("🏠  建立房間", 300, t);
        Button joinToggle = primaryBtn("🔗  加入房間", 300, t);
        Button settingsBtn = secondaryBtn("⚙  設定", 300, t);
        Button quitBtn = secondaryBtn("✕  離開", 300, t);

        // ── Inline join panel ─────────────────────────────────────────────────
        VBox joinPanel = buildJoinPanel(app, t, nameField, errorLabel);
        joinPanel.setVisible(false);
        joinPanel.setManaged(false);

        joinToggle.setOnAction(e -> {
            boolean show = !joinPanel.isVisible();
            joinPanel.setVisible(show);
            joinPanel.setManaged(show);
            joinToggle.setText(show ? "✕  取消加入" : "🔗  加入房間");
        });

        hostBtn.setOnAction(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) { errorLabel.setText("請先輸入暱稱！"); return; }
            errorLabel.setText(" ");
            hostBtn.setDisable(true);
            hostBtn.setText("啟動中…");
            doHost(app, name, hostBtn, errorLabel);
        });

        settingsBtn.setOnAction(e -> app.goSettings());
        quitBtn.setOnAction(e -> { CatCatchApp.audio.shutdown(); System.exit(0); });

        Separator sep1 = new Separator(); sep1.setMaxWidth(300);
        Separator sep2 = new Separator(); sep2.setMaxWidth(300);

        card.getChildren().addAll(
            icon, title, sub, sep1,
            nameBox, errorLabel,
            hostBtn, joinToggle, joinPanel, sep2,
            settingsBtn, quitBtn
        );

        root.getChildren().add(card);
        return new Scene(root, CatCatchApp.WIDTH, CatCatchApp.HEIGHT);
    }

    // ── Join panel ────────────────────────────────────────────────────────────

    private static VBox buildJoinPanel(CatCatchApp app, Theme t,
                                        TextField nameField, Label errorLabel) {
        VBox panel = new VBox(10);
        panel.setStyle("-fx-background-color:" + t.bg +
                       ";-fx-background-radius:8;-fx-padding:16;-fx-border-radius:8;" +
                       "-fx-border-color:" + t.muted + ";-fx-border-width:1;");
        panel.setMaxWidth(300);
        panel.setAlignment(Pos.CENTER_LEFT);

        Label ipLbl = new Label("主機 IP");
        ipLbl.setStyle(t.labelStyle(12, true));
        TextField ipField = new TextField();
        ipField.setPromptText("例：192.168.1.5");
        ipField.setStyle(t.inputStyle());
        ipField.setMaxWidth(280);

        Label codeLbl = new Label("房間號碼");
        codeLbl.setStyle(t.labelStyle(12, true));
        TextField codeField = new TextField();
        codeField.setPromptText("4 位房號（例：ABCD）");
        codeField.setStyle(t.inputStyle());
        codeField.setMaxWidth(280);
        codeField.setTextFormatter(new TextFormatter<>(c -> {
            String s = c.getControlNewText().toUpperCase();
            if (s.length() <= 4) { c.setText(c.getText().toUpperCase()); return c; }
            return null;
        }));

        Button connectBtn = primaryBtn("🔌  連線加入", 280, t);

        connectBtn.setOnAction(e -> {
            String name = nameField.getText().trim();
            String ip   = ipField.getText().trim();
            String code = codeField.getText().trim().toUpperCase();
            if (name.isEmpty())        { errorLabel.setText("請先輸入暱稱！");        return; }
            if (ip.isEmpty())          { errorLabel.setText("請輸入主機 IP！");       return; }
            if (code.length() != 4)    { errorLabel.setText("請輸入 4 位房間號碼！"); return; }
            errorLabel.setText(" ");
            connectBtn.setDisable(true);
            connectBtn.setText("連線中…");
            doJoin(app, name, ip, code, connectBtn, errorLabel);
        });

        panel.getChildren().addAll(ipLbl, ipField, codeLbl, codeField, connectBtn);
        return panel;
    }

    // ── Hosting ───────────────────────────────────────────────────────────────

    private static void doHost(CatCatchApp app, String name, Button btn, Label errorLabel) {
        Thread t = new Thread(() -> {
            GameServer.EmbeddedServer server = null;
            try {
                server = GameServer.startEmbedded(CatCatchApp.SERVER_PORT);
                CatCatchApp.embeddedServer = server;
                Thread.sleep(200);
            } catch (Exception ex) {
                String msg = "伺服器無法啟動（埠 " + CatCatchApp.SERVER_PORT + " 已被使用？）";
                resetBtn(btn, "🏠  建立房間", errorLabel, msg);
                return;
            }

            final GameClient[] ref = { null };
            GameClient.Listener listener = new GameClient.Listener() {
                @Override public void onConnected(String id) {
                    Platform.runLater(() -> app.goLobby(ref[0], true));
                }
                @Override public void onState(GameClient.GameState s) {}
                @Override public void onError(String msg) {
                    CatCatchApp.stopServer();
                    resetBtn(btn, "🏠  建立房間", errorLabel, "建立失敗：" + msg);
                }
                @Override public void onDisconnected(String r) {}
            };
            ref[0] = new GameClient(listener);
            try {
                ref[0].connectAndJoin("localhost", CatCatchApp.SERVER_PORT, name, "", true);
            } catch (Exception ex) {
                CatCatchApp.stopServer();
                resetBtn(btn, "🏠  建立房間", errorLabel, "連線失敗：" + ex.getMessage());
            }
        }, "catcatch-host");
        t.setDaemon(true);
        t.start();
    }

    // ── Joining ───────────────────────────────────────────────────────────────

    private static void doJoin(CatCatchApp app, String name, String ip, String code,
                                Button btn, Label errorLabel) {
        Thread t = new Thread(() -> {
            final GameClient[] ref = { null };
            GameClient.Listener listener = new GameClient.Listener() {
                @Override public void onConnected(String id) {
                    Platform.runLater(() -> app.goLobby(ref[0], false));
                }
                @Override public void onState(GameClient.GameState s) {}
                @Override public void onError(String msg) {
                    resetBtn(btn, "🔌  連線加入", errorLabel, "連線失敗：" + msg);
                }
                @Override public void onDisconnected(String r) {}
            };
            ref[0] = new GameClient(listener);
            try {
                ref[0].connectAndJoin(ip, CatCatchApp.SERVER_PORT, name, code, false);
            } catch (Exception ex) {
                resetBtn(btn, "🔌  連線加入", errorLabel, "連線失敗：" + ex.getMessage());
            }
        }, "catcatch-join");
        t.setDaemon(true);
        t.start();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void resetBtn(Button btn, String text, Label errorLabel, String error) {
        Platform.runLater(() -> {
            btn.setDisable(false);
            btn.setText(text);
            errorLabel.setText(error);
        });
    }

    static Button primaryBtn(String text, double width, Theme t) {
        Button b = new Button(text);
        b.setPrefWidth(width);
        b.setStyle(t.primaryBtn());
        b.setOnMouseEntered(e -> b.setStyle(t.primaryBtnHover()));
        b.setOnMouseExited(e ->  b.setStyle(t.primaryBtn()));
        return b;
    }

    static Button secondaryBtn(String text, double width, Theme t) {
        Button b = new Button(text);
        b.setPrefWidth(width);
        b.setStyle(t.secondaryBtn());
        b.setOnMouseEntered(e -> b.setStyle(t.secondaryBtnHover()));
        b.setOnMouseExited(e ->  b.setStyle(t.secondaryBtn()));
        return b;
    }
}
