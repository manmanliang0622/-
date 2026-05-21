package catcatch;

public enum Theme {
    PINK("粉色",
        "#FFF0F5", "#FFE4EE", "#FF6B9D", "#C94F7D",
        "#3D2B3D", "#FFFFFF", "#B0A0B0", "#FF8EC0"),
    BLUE("藍色",
        "#EEF4FF", "#D6E8FF", "#3D7BE5", "#1A56C9",
        "#1B2A4A", "#FFFFFF", "#7090B0", "#5A9BFF"),
    GREEN("綠色",
        "#F0FFF6", "#D4F4E2", "#2ECC87", "#1A9E61",
        "#1B3A2D", "#FFFFFF", "#608070", "#5ADBA0"),
    DARK("深色",
        "#1A1A2E", "#252540", "#7C5CBF", "#E94560",
        "#E8E8F0", "#FFFFFF", "#6A6A8A", "#9C7CE0");

    public final String label, bg, panelBg, accent, accentDark, text, textOnAccent, muted, buttonHover;

    Theme(String label, String bg, String panelBg, String accent, String accentDark,
          String text, String textOnAccent, String muted, String buttonHover) {
        this.label = label; this.bg = bg; this.panelBg = panelBg;
        this.accent = accent; this.accentDark = accentDark;
        this.text = text; this.textOnAccent = textOnAccent;
        this.muted = muted; this.buttonHover = buttonHover;
    }

    public String rootStyle() {
        return "-fx-background-color:" + bg + ";-fx-font-family:'Microsoft JhengHei','Segoe UI',sans-serif;";
    }

    public String cardStyle() {
        return "-fx-background-color:" + panelBg + ";-fx-background-radius:12;" +
               "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.14),12,0,0,4);";
    }

    public String primaryBtn() {
        return "-fx-background-color:" + accent + ";-fx-text-fill:" + textOnAccent + ";" +
               "-fx-font-size:14px;-fx-font-weight:bold;-fx-background-radius:22;-fx-cursor:hand;" +
               "-fx-padding:10 30 10 30;";
    }

    public String primaryBtnHover() {
        return "-fx-background-color:" + accentDark + ";-fx-text-fill:" + textOnAccent + ";" +
               "-fx-font-size:14px;-fx-font-weight:bold;-fx-background-radius:22;-fx-cursor:hand;" +
               "-fx-padding:10 30 10 30;";
    }

    public String secondaryBtn() {
        return "-fx-background-color:transparent;-fx-text-fill:" + text + ";" +
               "-fx-font-size:13px;-fx-background-radius:22;-fx-cursor:hand;" +
               "-fx-border-color:" + muted + ";-fx-border-radius:22;-fx-padding:8 24 8 24;";
    }

    public String secondaryBtnHover() {
        return "-fx-background-color:" + panelBg + ";-fx-text-fill:" + accent + ";" +
               "-fx-font-size:13px;-fx-background-radius:22;-fx-cursor:hand;" +
               "-fx-border-color:" + accent + ";-fx-border-radius:22;-fx-padding:8 24 8 24;";
    }

    public String inputStyle() {
        boolean dark = this == DARK;
        String inputBg = dark ? "#2A2A4A" : "white";
        String inputText = dark ? "#E8E8F0" : "#333";
        return "-fx-background-color:" + inputBg + ";-fx-border-color:" + muted + ";-fx-border-radius:8;" +
               "-fx-background-radius:8;-fx-padding:8 12;-fx-font-size:14px;-fx-text-fill:" + inputText + ";";
    }

    public String labelStyle(int size, boolean bold) {
        return "-fx-font-size:" + size + "px;-fx-text-fill:" + text + (bold ? ";-fx-font-weight:bold;" : ";");
    }
}
