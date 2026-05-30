package catcatch;

import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.*;

public class SettingsScene {

    public static Scene build(CatCatchApp app) {
        Theme t = CatCatchApp.theme;

        BorderPane root = new BorderPane();
        root.setStyle(t.rootStyle());
        root.setPadding(new Insets(30));

        // ── Top bar ───────────────────────────────────────────────────────────
        HBox topBar = new HBox(14);
        topBar.setAlignment(Pos.CENTER_LEFT);

        Button backBtn = MainMenuScene.secondaryBtn("← 返回主選單", 140, t);
        backBtn.setOnAction(e -> app.goMainMenu());

        Label title = new Label("⚙  設定");
        title.setStyle("-fx-font-size:22px;-fx-font-weight:bold;-fx-text-fill:" + t.accent + ";");

        topBar.getChildren().addAll(backBtn, title);
        BorderPane.setMargin(topBar, new Insets(0, 0, 24, 0));
        root.setTop(topBar);

        // ── Settings card ─────────────────────────────────────────────────────
        VBox card = new VBox(28);
        card.setStyle(t.cardStyle());
        card.setPadding(new Insets(32, 40, 32, 40));
        card.setMaxWidth(560);

        // ── Section: theme ────────────────────────────────────────────────────
        Label themeTitle = sectionTitle("🎨  介面主題", t);

        HBox themeRow = new HBox(12);
        themeRow.setAlignment(Pos.CENTER_LEFT);

        for (Theme th : Theme.values()) {
            boolean selected = th == CatCatchApp.theme;
            Button tb = new Button(th.label);
            tb.setPrefWidth(105);
            tb.setPrefHeight(44);
            applyThemeBtn(tb, th, selected);
            tb.setOnAction(e -> {
                CatCatchApp.theme = th;
                // Rebuild scene with new theme
                app.goSettings();
            });
            themeRow.getChildren().add(tb);
        }

        // ── Section: sound ────────────────────────────────────────────────────
        Label soundTitle = sectionTitle("🔊  音效設定", t);

        HBox soundRow = new HBox(16);
        soundRow.setAlignment(Pos.CENTER_LEFT);

        Label soundLbl = new Label("音效開關");
        soundLbl.setStyle(t.labelStyle(14, false));

        final boolean[] soundOn = { CatCatchApp.soundEnabled };
        Button soundToggle = new Button(soundOn[0] ? "🔊  開啟" : "🔇  關閉");
        soundToggle.setPrefWidth(110);
        soundToggle.setStyle(soundOn[0] ? t.primaryBtn() : t.secondaryBtn());
        soundToggle.setOnMouseEntered(e -> soundToggle.setStyle(soundOn[0] ? t.primaryBtnHover() : t.secondaryBtnHover()));
        soundToggle.setOnMouseExited(e ->  soundToggle.setStyle(soundOn[0] ? t.primaryBtn() : t.secondaryBtn()));
        soundToggle.setOnAction(e -> {
            soundOn[0] = !soundOn[0];
            CatCatchApp.soundEnabled = soundOn[0];
            CatCatchApp.audio.setEnabled(soundOn[0]);
            soundToggle.setText(soundOn[0] ? "🔊  開啟" : "🔇  關閉");
            soundToggle.setStyle(soundOn[0] ? t.primaryBtn() : t.secondaryBtn());
        });
        soundRow.getChildren().addAll(soundLbl, soundToggle);

        // Volume slider
        HBox volRow = new HBox(16);
        volRow.setAlignment(Pos.CENTER_LEFT);

        Label volLbl = new Label("音量");
        volLbl.setStyle(t.labelStyle(14, false));
        volLbl.setMinWidth(60);

        Slider volSlider = new Slider(0, 100, CatCatchApp.volume * 100);
        volSlider.setPrefWidth(280);
        volSlider.setShowTickLabels(true);
        volSlider.setShowTickMarks(true);
        volSlider.setMajorTickUnit(25);
        volSlider.setStyle("-fx-control-inner-background:" + t.bg + ";");

        Label volValueLbl = new Label((int)(CatCatchApp.volume * 100) + "%");
        volValueLbl.setStyle("-fx-font-size:14px;-fx-font-weight:bold;-fx-text-fill:" + t.accent + ";-fx-min-width:40;");

        volSlider.valueProperty().addListener((obs, ov, nv) -> {
            float v = nv.floatValue() / 100f;
            CatCatchApp.volume = v;
            CatCatchApp.audio.setVolume(v);
            volValueLbl.setText((int)(v * 100) + "%");
        });

        volRow.getChildren().addAll(volLbl, volSlider, volValueLbl);

        // ── Section: info ─────────────────────────────────────────────────────
        Label infoTitle = sectionTitle("ℹ  遊戲說明", t);

        VBox rules = new VBox(6);
        String[] ruleTexts = {
            "• 點擊「目標色」小貓：+10 分",
            "• 點擊其他顏色小貓：-5 分",
            "• 點擊地雷（狗）：-15 分",
            "• 每 5 秒切換一次目標顏色",
            "• 45 秒倒數，時間到計算排名"
        };
        for (String r : ruleTexts) {
            Label rl = new Label(r);
            rl.setStyle(t.labelStyle(13, false));
            rules.getChildren().add(rl);
        }

        card.getChildren().addAll(
            themeTitle, themeRow,
            new Separator(),
            soundTitle, soundRow, volRow,
            new Separator(),
            infoTitle, rules
        );

        StackPane centre = new StackPane(card);
        centre.setStyle(t.rootStyle());
        root.setCenter(centre);

        return new Scene(root, CatCatchApp.WIDTH, CatCatchApp.HEIGHT);
    }

    private static Label sectionTitle(String text, Theme t) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:" + t.text + ";");
        return l;
    }

    private static void applyThemeBtn(Button b, Theme th, boolean selected) {
        String base = "-fx-font-size:13px;-fx-font-weight:bold;-fx-background-radius:10;-fx-cursor:hand;" +
                      "-fx-padding:6 0;";
        if (selected) {
            b.setStyle(base + "-fx-background-color:" + th.accent + ";-fx-text-fill:" + th.textOnAccent + ";" +
                       "-fx-border-color:" + th.accentDark + ";-fx-border-radius:10;-fx-border-width:3;");
        } else {
            b.setStyle(base + "-fx-background-color:" + th.panelBg + ";-fx-text-fill:" + th.text + ";" +
                       "-fx-border-color:" + th.muted + ";-fx-border-radius:10;-fx-border-width:1;");
        }
        b.setOnMouseEntered(e -> {
            if (th != CatCatchApp.theme)
                b.setStyle(base + "-fx-background-color:" + th.accent + ";-fx-text-fill:" + th.textOnAccent + ";" +
                           "-fx-border-color:" + th.accent + ";-fx-border-radius:10;-fx-border-width:2;");
        });
        b.setOnMouseExited(e -> applyThemeBtn(b, th, th == CatCatchApp.theme));
    }
}
