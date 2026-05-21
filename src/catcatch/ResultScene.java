package catcatch;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.*;
import javafx.util.Duration;

public class ResultScene {

    public static Scene build(CatCatchApp app, GameClient.GameState finalState,
                               GameClient client, boolean isHost) {
        Theme t = CatCatchApp.theme;

        BorderPane root = new BorderPane();
        root.setStyle(t.rootStyle());
        root.setPadding(new Insets(30));

        // ── Title ─────────────────────────────────────────────────────────────
        Label trophy = new Label("🏆");
        trophy.setStyle("-fx-font-size:52px;");

        Label title = new Label("遊戲結束！");
        title.setStyle("-fx-font-size:32px;-fx-font-weight:bold;-fx-text-fill:" + t.accent + ";");

        VBox titleBox = new VBox(6, trophy, title);
        titleBox.setAlignment(Pos.CENTER);
        BorderPane.setMargin(titleBox, new Insets(0, 0, 20, 0));
        root.setTop(titleBox);

        // ── Rankings ──────────────────────────────────────────────────────────
        VBox rankList = new VBox(10);
        rankList.setAlignment(Pos.CENTER);
        rankList.setMaxWidth(560);

        String[] medals = { "🥇", "🥈", "🥉" };
        String[] goldColors = { "#FFD700", "#C0C0C0", "#CD7F32" };
        String[] bgColors   = { "#FFFDE7", "#F5F5F5", "#FFF3E0" };

        GameClient.RemotePlayer selfPlayer = finalState.self();
        int selfRank = -1;

        for (int i = 0; i < finalState.players().size(); i++) {
            GameClient.RemotePlayer p = finalState.players().get(i);
            boolean isSelf = p.id().equals(finalState.selfId());
            if (isSelf) selfRank = i + 1;

            HBox row = new HBox(14);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(12, 20, 12, 20));

            String rowStyle;
            if (i < 3) {
                rowStyle = "-fx-background-color:" + bgColors[i] + ";" +
                           "-fx-background-radius:12;" +
                           "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.08),6,0,0,2);" +
                           "-fx-border-color:" + goldColors[i] + ";-fx-border-radius:12;-fx-border-width:2;";
            } else {
                rowStyle = "-fx-background-color:" + t.panelBg + ";-fx-background-radius:10;";
            }
            if (isSelf) rowStyle += "-fx-border-color:" + t.accent + ";-fx-border-radius:12;-fx-border-width:3;";
            row.setStyle(rowStyle);

            String rankText = i < 3 ? medals[i] : String.valueOf(i + 1) + ".";
            Label rankLbl = new Label(rankText);
            rankLbl.setStyle("-fx-font-size:" + (i < 3 ? 22 : 16) + "px;-fx-min-width:36;");

            Label nameLbl = new Label(p.name() + (isSelf ? "  ← 你" : ""));
            nameLbl.setStyle("-fx-font-size:16px;-fx-text-fill:" + t.text + ";" +
                             (isSelf ? "-fx-font-weight:bold;" : ""));

            Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);

            Label scoreLbl = new Label(p.score() + " 分");
            scoreLbl.setStyle("-fx-font-size:18px;-fx-font-weight:bold;-fx-text-fill:" + t.accent + ";");

            row.getChildren().addAll(rankLbl, nameLbl, sp, scoreLbl);
            rankList.getChildren().add(row);
        }

        ScrollPane scroll = new ScrollPane(rankList);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background:transparent;-fx-background-color:transparent;");
        scroll.setMaxHeight(360);

        // Self summary
        String summaryText = "你的排名：第 " + selfRank + " 名";
        if (selfPlayer != null) summaryText += "  |  最終分數：" + selfPlayer.score() + " 分";
        Label summaryLabel = new Label(summaryText);
        summaryLabel.setStyle("-fx-font-size:14px;-fx-text-fill:" + t.muted + ";");

        VBox centre = new VBox(14, scroll, summaryLabel);
        centre.setAlignment(Pos.CENTER);
        root.setCenter(centre);

        // ── Bottom buttons ────────────────────────────────────────────────────
        Label countdownLabel = new Label("10 秒後自動返回大廳");
        countdownLabel.setStyle("-fx-font-size:12px;-fx-text-fill:" + t.muted + ";");

        Button playAgainBtn = MainMenuScene.primaryBtn("🔄  再來一局", 200, t);
        Button backLobbyBtn = MainMenuScene.secondaryBtn("🏠  返回大廳", 200, t);

        playAgainBtn.setOnAction(e -> {
            playAgainBtn.setDisable(true);
            playAgainBtn.setText("等待其他玩家…");
            client.sendPlayAgain();
        });
        backLobbyBtn.setOnAction(e -> {
            client.sendBackLobby();
        });

        HBox btnRow = new HBox(16, playAgainBtn, backLobbyBtn);
        btnRow.setAlignment(Pos.CENTER);

        VBox bottom = new VBox(10, btnRow, countdownLabel);
        bottom.setAlignment(Pos.CENTER);
        bottom.setPadding(new Insets(16, 0, 0, 0));
        BorderPane.setMargin(bottom, new Insets(10, 0, 0, 0));
        root.setBottom(bottom);

        // ── 10-second countdown ───────────────────────────────────────────────
        int[] countdown = { finalState.returnSeconds() > 0 ? finalState.returnSeconds() : 10 };
        Timeline timer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            countdown[0]--;
            countdownLabel.setText(countdown[0] + " 秒後自動返回大廳");
            if (countdown[0] <= 0) {
                client.sendBackLobby();
            }
        }));
        timer.setCycleCount(Timeline.INDEFINITE);
        timer.play();

        // ── Result listener ───────────────────────────────────────────────────
        client.setListener(new GameClient.Listener() {
            @Override public void onConnected(String id) {}

            @Override public void onState(GameClient.GameState state) {
                // Track replay votes
                if (state.message() != null && state.message().contains("想再玩")) {
                    countdownLabel.setText(state.message());
                }
                if ("lobby".equals(state.status())) {
                    timer.stop();
                    boolean host = state.isHost();
                    app.goLobby(client, host);
                }
            }

            @Override public void onError(String msg) { countdownLabel.setText("⚠ " + msg); }

            @Override public void onDisconnected(String reason) {
                timer.stop();
                Platform.runLater(() -> { client.close(); app.goMainMenu(); });
            }
        });

        return new Scene(root, CatCatchApp.WIDTH, CatCatchApp.HEIGHT);
    }
}
