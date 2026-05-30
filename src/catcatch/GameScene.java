package catcatch;

import java.io.File;
import java.util.*;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.*;
import javafx.scene.text.*;
import javafx.util.Duration;

public class GameScene {

    // ── Static maps ───────────────────────────────────────────────────────────

    private static final Map<String, String> VARIANT_LABEL = Map.of(
        "GRAY", "灰色貓咪", "GOLD", "金色貓咪", "SNOW", "雪白貓咪");
    private static final Map<String, String> VARIANT_COLOR = Map.of(
        "GRAY", "#9E9E9E", "GOLD", "#FFC107", "SNOW", "#64B5F6");
    private static final Map<String, String> VARIANT_BG = Map.of(
        "GRAY", "#F5F5F5", "GOLD", "#FFFDE7", "SNOW", "#E3F2FD");

    // processed (transparent-bg) images, loaded once
    private static final Map<String, WritableImage> processedCache = new HashMap<>();

    // ── Scene builder ─────────────────────────────────────────────────────────

    public static Scene build(CatCatchApp app, GameClient client) {
        Theme t = CatCatchApp.theme;

        BorderPane root = new BorderPane();
        root.setStyle(t.rootStyle());

        // ── Pre-load & process all animal images in background ────────────────
        preloadImages();

        // ── Top bar (100 px tall) ─────────────────────────────────────────────
        HBox topBar = new HBox();
        topBar.setAlignment(Pos.CENTER);
        topBar.setPrefHeight(100);
        topBar.setStyle("-fx-background-color:" + t.panelBg + ";" +
                        "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.12),8,0,0,4);");

        // --- Left: target indicator -------------------------------------------
        // Large circular image  +  variant name
        StackPane targetImgPane = new StackPane();
        targetImgPane.setPrefSize(76, 76);

        ImageView targetIV = new ImageView();
        targetIV.setFitWidth(76); targetIV.setFitHeight(76);
        targetIV.setPreserveRatio(true);

        // Coloured ring behind image
        Circle targetRing = new Circle(42);
        targetRing.setFill(Color.TRANSPARENT);
        targetRing.setStroke(Color.web("#9E9E9E"));
        targetRing.setStrokeWidth(4);

        targetImgPane.getChildren().addAll(targetRing, targetIV);

        Label targetHintLbl = new Label("🎯 現在要抓這隻！");
        targetHintLbl.setStyle("-fx-font-size:12px;-fx-font-weight:bold;-fx-text-fill:" + t.muted + ";");

        Label targetNameLbl = new Label("灰色貓咪");
        targetNameLbl.setStyle("-fx-font-size:22px;-fx-font-weight:bold;-fx-text-fill:#9E9E9E;");

        VBox targetTextBox = new VBox(2, targetHintLbl, targetNameLbl);
        targetTextBox.setAlignment(Pos.CENTER_LEFT);

        HBox targetSection = new HBox(14, targetImgPane, targetTextBox);
        targetSection.setAlignment(Pos.CENTER);
        targetSection.setPrefWidth(380);
        targetSection.setPadding(new Insets(0, 20, 0, 20));
        targetSection.setStyle("-fx-background-color:" + VARIANT_BG.get("GRAY") + ";" +
                               "-fx-background-radius:12;-fx-padding:10 20;");

        // --- Centre: countdown timer ------------------------------------------
        Label timerLabel = new Label("45");
        timerLabel.setStyle("-fx-font-size:38px;-fx-font-weight:bold;-fx-text-fill:" + t.accent + ";" +
                            "-fx-min-width:70;-fx-alignment:center;");
        Label timerUnit = new Label("秒");
        timerUnit.setStyle("-fx-font-size:13px;-fx-text-fill:" + t.muted + ";");
        VBox timerBox = new VBox(0, timerLabel, timerUnit);
        timerBox.setAlignment(Pos.CENTER);
        timerBox.setPrefWidth(120);

        // --- Right: personal score --------------------------------------------
        Label scoreHead = new Label("我的分數");
        scoreHead.setStyle("-fx-font-size:11px;-fx-text-fill:" + t.muted + ";");
        Label scoreLabel = new Label("0");
        scoreLabel.setStyle("-fx-font-size:30px;-fx-font-weight:bold;-fx-text-fill:" + t.accent + ";");
        VBox scoreBox = new VBox(0, scoreHead, scoreLabel);
        scoreBox.setAlignment(Pos.CENTER);
        scoreBox.setPrefWidth(200);
        scoreBox.setPadding(new Insets(0, 20, 0, 0));

        Region sp1 = new Region(); HBox.setHgrow(sp1, Priority.ALWAYS);
        Region sp2 = new Region(); HBox.setHgrow(sp2, Priority.ALWAYS);
        topBar.getChildren().addAll(targetSection, sp1, timerBox, sp2, scoreBox);
        root.setTop(topBar);

        // ── Game canvas (left) ────────────────────────────────────────────────
        Pane canvas = new Pane();
        canvas.setStyle("-fx-background-color:" + t.bg + ";");
        canvas.setPrefWidth(760);
        canvas.setPrefHeight(CatCatchApp.HEIGHT - 100);

        // ── Leaderboard (right) ───────────────────────────────────────────────
        VBox leaderboard = new VBox(8);
        leaderboard.setPrefWidth(330);
        leaderboard.setPadding(new Insets(16));
        leaderboard.setStyle("-fx-background-color:" + t.panelBg + ";");

        Label lbTitle = new Label("📊 即時排行榜");
        lbTitle.setStyle("-fx-font-size:15px;-fx-font-weight:bold;-fx-text-fill:" + t.text + ";");
        VBox lbRows = new VBox(6);
        leaderboard.getChildren().addAll(lbTitle, new Separator(), lbRows);

        HBox content = new HBox(canvas, leaderboard);
        HBox.setHgrow(canvas, Priority.ALWAYS);
        root.setCenter(content);

        // ── Entity tracking ───────────────────────────────────────────────────
        Map<String, VBox> entityNodes = new HashMap<>();   // id → entity VBox
        final int[] prevScore = { 0 };
        final String[] prevTarget = { "GRAY" };

        // ── State listener ────────────────────────────────────────────────────
        client.setListener(new GameClient.Listener() {
            @Override public void onConnected(String id) {}

            @Override public void onState(GameClient.GameState state) {
                String tv = state.targetVariant() != null ? state.targetVariant() : "GRAY";

                // ── Update target indicator ───────────────────────────────────
                boolean targetChanged = !tv.equals(prevTarget[0]);
                if (targetChanged) {
                    prevTarget[0] = tv;
                    // Image
                    WritableImage proc = processedCache.get(tv.toLowerCase());
                    targetIV.setImage(proc);
                    // Ring colour
                    targetRing.setStroke(Color.web(VARIANT_COLOR.getOrDefault(tv, "#9E9E9E")));
                    // Glow on image
                    targetIV.setEffect(new DropShadow(18,
                        Color.web(VARIANT_COLOR.getOrDefault(tv, "#9E9E9E"))));
                    // Text
                    targetNameLbl.setText(VARIANT_LABEL.getOrDefault(tv, tv));
                    targetNameLbl.setStyle("-fx-font-size:22px;-fx-font-weight:bold;" +
                        "-fx-text-fill:" + VARIANT_COLOR.getOrDefault(tv, "#9E9E9E") + ";");
                    // Section background
                    targetSection.setStyle(
                        "-fx-background-color:" + VARIANT_BG.getOrDefault(tv, "#F5F5F5") + ";" +
                        "-fx-background-radius:12;-fx-padding:10 20;" +
                        "-fx-border-color:" + VARIANT_COLOR.getOrDefault(tv, "#9E9E9E") + ";" +
                        "-fx-border-radius:12;-fx-border-width:2;");

                    // Flash animation on target section
                    ScaleTransition flash = new ScaleTransition(Duration.millis(200), targetSection);
                    flash.setFromX(1.0); flash.setFromY(1.0);
                    flash.setToX(1.06); flash.setToY(1.06);
                    flash.setAutoReverse(true); flash.setCycleCount(2);
                    flash.play();

                    // Update existing entity glows
                    for (VBox vbox : entityNodes.values()) {
                        updateEntityGlow(vbox, tv);
                    }
                }

                // ── Timer ────────────────────────────────────────────────────
                int rem = state.remainingSeconds();
                timerLabel.setText(String.valueOf(rem));
                timerLabel.setStyle("-fx-font-size:38px;-fx-font-weight:bold;" +
                    "-fx-alignment:center;-fx-min-width:70;" +
                    "-fx-text-fill:" + (rem <= 10 ? "#e74c3c" : t.accent) + ";");

                // ── Score ─────────────────────────────────────────────────────
                GameClient.RemotePlayer self = state.self();
                if (self != null) {
                    int newScore = self.score();
                    if (newScore != prevScore[0]) {
                        showPopup(canvas, newScore - prevScore[0]);
                        prevScore[0] = newScore;
                    }
                    scoreLabel.setText(String.valueOf(newScore));
                }

                // ── Entities (diff) ───────────────────────────────────────────
                Set<String> activeIds = new HashSet<>();
                for (GameClient.RemoteEntity e : state.entities()) activeIds.add(e.id());

                entityNodes.entrySet().removeIf(entry -> {
                    if (!activeIds.contains(entry.getKey())) {
                        canvas.getChildren().remove(entry.getValue());
                        return true;
                    }
                    return false;
                });

                for (GameClient.RemoteEntity e : state.entities()) {
                    if (!entityNodes.containsKey(e.id())) {
                        VBox node = buildEntity(e, tv, client);
                        double size = e.size();
                        node.setLayoutX(e.x() * canvas.getPrefWidth()  - size * 0.45);
                        node.setLayoutY(e.y() * canvas.getPrefHeight() - size * 0.45);
                        canvas.getChildren().add(node);
                        entityNodes.put(e.id(), node);
                        spawnAnim(node);
                    }
                }

                // ── Leaderboard ───────────────────────────────────────────────
                lbRows.getChildren().clear();
                String[] medals = {"🥇","🥈","🥉"};
                for (int i = 0; i < state.players().size(); i++) {
                    GameClient.RemotePlayer p = state.players().get(i);
                    boolean isSelf = p.id().equals(state.selfId());
                    HBox row = new HBox(8);
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.setPadding(new Insets(5, 10, 5, 10));
                    if (isSelf) row.setStyle("-fx-background-color:" + t.bg + ";-fx-background-radius:8;");
                    Label rank = new Label(i < 3 ? medals[i] : (i+1) + ".");
                    rank.setStyle("-fx-font-size:14px;-fx-min-width:28;");
                    Label name = new Label(p.name());
                    name.setStyle("-fx-font-size:13px;-fx-text-fill:" + t.text + ";" +
                                  (isSelf ? "-fx-font-weight:bold;" : ""));
                    Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
                    Label sc = new Label(p.score() + "分");
                    sc.setStyle("-fx-font-size:14px;-fx-font-weight:bold;-fx-text-fill:" + t.accent + ";");
                    row.getChildren().addAll(rank, name, sp, sc);
                    lbRows.getChildren().add(row);
                }

                // ── Transition to result ──────────────────────────────────────
                if ("finished".equals(state.status())) {
                    CatCatchApp.audio.playFinish();
                    app.goResult(state, client, state.isHost());
                }
            }

            @Override public void onError(String msg) {}
            @Override public void onDisconnected(String reason) {
                Platform.runLater(() -> { client.close(); app.goMainMenu(); });
            }
        });

        // Initialise target indicator immediately (shows GRAY until first state arrives)
        WritableImage initImg = processedCache.get("gray");
        if (initImg != null) targetIV.setImage(initImg);

        // ── Countdown overlay (3 → 2 → 1 → GO!) ──────────────────────────────
        StackPane overlay = new StackPane();
        overlay.setStyle("-fx-background-color:rgba(0,0,0,0.55);");
        overlay.setPrefSize(CatCatchApp.WIDTH, CatCatchApp.HEIGHT);

        Label countLbl = new Label("3");
        countLbl.setStyle("-fx-font-size:160px;-fx-font-weight:bold;-fx-text-fill:white;" +
                          "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.6),20,0,0,4);");
        overlay.getChildren().add(countLbl);

        StackPane fullRoot = new StackPane(root, overlay);

        int[] countVal = { 3 };
        Timeline countdown = new Timeline();
        for (int i = 3; i >= 0; i--) {
            final int val = i;
            KeyFrame kf = new KeyFrame(Duration.seconds(3 - val), e2 -> {
                if (val == 0) {
                    // "GO!" flash then remove
                    countLbl.setText("GO!");
                    countLbl.setStyle("-fx-font-size:130px;-fx-font-weight:bold;" +
                                      "-fx-text-fill:#27AE60;" +
                                      "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.6),20,0,0,4);");
                    FadeTransition ft = new FadeTransition(Duration.millis(600), overlay);
                    ft.setFromValue(1.0); ft.setToValue(0.0);
                    ft.setOnFinished(ev -> fullRoot.getChildren().remove(overlay));
                    ft.play();
                } else {
                    countLbl.setText(String.valueOf(val));
                    countLbl.setStyle("-fx-font-size:160px;-fx-font-weight:bold;-fx-text-fill:white;" +
                                      "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.6),20,0,0,4);");
                    // scale pulse animation
                    ScaleTransition pulse = new ScaleTransition(Duration.millis(400), countLbl);
                    pulse.setFromX(1.4); pulse.setFromY(1.4);
                    pulse.setToX(1.0);   pulse.setToY(1.0);
                    pulse.setInterpolator(Interpolator.EASE_OUT);
                    pulse.play();
                }
            });
            countdown.getKeyFrames().add(kf);
        }
        countdown.play();

        return new Scene(fullRoot, CatCatchApp.WIDTH, CatCatchApp.HEIGHT);
    }

    // ── Entity node builder ───────────────────────────────────────────────────

    private static VBox buildEntity(GameClient.RemoteEntity e, String targetVariant,
                                     GameClient client) {
        boolean isDog = "DOG".equals(e.kind());
        String variant = e.variant();
        double size = e.size();
        double imgSize = size * 0.88;

        // Image container (StackPane for glow control)
        StackPane imgPane = new StackPane();
        imgPane.setPrefSize(imgSize, imgSize);
        imgPane.setMaxSize(imgSize, imgSize);

        String cacheKey = isDog ? "dog" : variant.toLowerCase();
        WritableImage proc = processedCache.get(cacheKey);

        if (proc != null) {
            ImageView iv = new ImageView(proc);
            iv.setFitWidth(imgSize);
            iv.setFitHeight(imgSize);
            iv.setPreserveRatio(true);
            imgPane.getChildren().add(iv);
        } else {
            // Fallback: coloured circle + emoji
            Circle circle = new Circle(imgSize / 2);
            circle.setFill(Color.web(isDog ? "#FFF3E0" : VARIANT_BG.getOrDefault(variant, "#F5F5F5")));
            circle.setStroke(Color.web(isDog ? "#FF7043" : VARIANT_COLOR.getOrDefault(variant, "#9E9E9E")));
            circle.setStrokeWidth(3);
            Label emoji = new Label(isDog ? "🐶" : "🐱");
            emoji.setStyle("-fx-font-size:" + (imgSize * 0.42) + "px;");
            imgPane.getChildren().addAll(circle, emoji);
        }

        // Label
        Label label = new Label(isDog ? "⚠ 地雷" : VARIANT_LABEL.getOrDefault(variant, variant));
        label.setStyle("-fx-font-size:11px;-fx-font-weight:bold;-fx-alignment:center;" +
            "-fx-text-fill:" + (isDog ? "#FF7043" : VARIANT_COLOR.getOrDefault(variant, "#777")) + ";");

        VBox vbox = new VBox(3, imgPane, label);
        vbox.setAlignment(Pos.CENTER);
        // Store variant in userData for glow updates
        vbox.setUserData(isDog ? "DOG" : variant);

        // Apply initial glow
        updateEntityGlow(vbox, targetVariant);

        // Click handler
        vbox.setOnMouseClicked(evt -> {
            vbox.setOnMouseClicked(null);
            client.sendClick(e.id());
            clickAnim(vbox);
            if (isDog) CatCatchApp.audio.playDog();
            else if (variant.equals(targetVariant)) CatCatchApp.audio.playMeow();
            else CatCatchApp.audio.playWrong();
        });
        vbox.setCursor(javafx.scene.Cursor.HAND);

        return vbox;
    }

    // ── Glow helper ───────────────────────────────────────────────────────────

    private static void updateEntityGlow(VBox vbox, String currentTarget) {
        if (vbox.getChildren().isEmpty()) return;
        javafx.scene.Node imgPane = vbox.getChildren().get(0);
        String variant = vbox.getUserData() != null ? vbox.getUserData().toString() : "";
        boolean isDog = "DOG".equals(variant);
        boolean isTarget = !isDog && variant.equals(currentTarget);

        if (isDog) {
            imgPane.setEffect(new DropShadow(8, Color.web("#FF7043")));
        } else if (isTarget) {
            DropShadow glow = new DropShadow(22,
                Color.web(VARIANT_COLOR.getOrDefault(variant, "#9E9E9E")));
            glow.setSpread(0.35);
            imgPane.setEffect(glow);
        } else {
            imgPane.setEffect(new DropShadow(6, Color.rgb(0, 0, 0, 0.25)));
        }
    }

    // ── Animations ────────────────────────────────────────────────────────────

    private static void spawnAnim(javafx.scene.Node node) {
        ScaleTransition st = new ScaleTransition(Duration.millis(200), node);
        st.setFromX(0.1); st.setFromY(0.1);
        st.setToX(1.0);   st.setToY(1.0);
        st.setInterpolator(Interpolator.EASE_OUT);
        st.play();
    }

    private static void clickAnim(javafx.scene.Node node) {
        ScaleTransition st = new ScaleTransition(Duration.millis(120), node);
        st.setFromX(1.0); st.setFromY(1.0);
        st.setToX(1.3);   st.setToY(1.3);
        st.setAutoReverse(true); st.setCycleCount(2);
        st.play();
    }

    private static void showPopup(Pane canvas, int delta) {
        String sign = delta > 0 ? "+" : "";
        Label popup = new Label(sign + delta);
        popup.setStyle("-fx-font-size:26px;-fx-font-weight:bold;" +
            "-fx-text-fill:" + (delta > 0 ? "#27AE60" : "#e74c3c") + ";" +
            "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.35),5,0,0,2);");

        double cx = canvas.getPrefWidth() / 2 + (Math.random() - 0.5) * 200;
        popup.setLayoutX(cx);
        popup.setLayoutY(canvas.getPrefHeight() / 2 - 60);
        canvas.getChildren().add(popup);

        ParallelTransition pt = new ParallelTransition(popup,
            translate(popup, -70), fade(popup));
        pt.setOnFinished(e -> canvas.getChildren().remove(popup));
        pt.play();
    }

    private static TranslateTransition translate(javafx.scene.Node n, double byY) {
        TranslateTransition tt = new TranslateTransition(Duration.millis(750), n);
        tt.setByY(byY);
        return tt;
    }

    private static FadeTransition fade(javafx.scene.Node n) {
        FadeTransition ft = new FadeTransition(Duration.millis(750), n);
        ft.setFromValue(1.0); ft.setToValue(0.0);
        return ft;
    }

    // ── Image loading & processing ────────────────────────────────────────────

    private static void preloadImages() {
        if (!processedCache.isEmpty()) return; // already loaded
        String[][] specs = {
            {"gray",  "assets/cat-gray.jpg"},
            {"gold",  "assets/cat-gold.jpg"},
            {"snow",  "assets/cat-snow.jpg"},
            {"dog",   "assets/dog.jpg"}
        };
        for (String[] spec : specs) {
            String key  = spec[0];
            String path = spec[1];
            File f = new File(path);
            if (!f.exists()) continue;
            try (java.io.FileInputStream fis = new java.io.FileInputStream(f)) {
                javafx.scene.image.Image raw = new javafx.scene.image.Image(fis);
                if (!raw.isError()) {
                    int w = (int) raw.getWidth(), h = (int) raw.getHeight();
                    WritableImage wi = new WritableImage(raw.getPixelReader(), 0, 0, w, h);
                    processedCache.put(key, wi);
                }
            } catch (Exception ignored) {}
        }
    }
}
