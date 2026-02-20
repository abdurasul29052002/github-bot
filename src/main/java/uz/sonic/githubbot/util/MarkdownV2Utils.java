package uz.sonic.githubbot.util;

public final class MarkdownV2Utils {

    private MarkdownV2Utils() {
    }

    public static String escape(String text) {
        return text.replaceAll("([_*\\[\\]()~`>#+\\-=|{}.!\\\\])", "\\\\$1");
    }

    public static String bold(String text) {
        return "*" + escape(text) + "*";
    }

    public static String italic(String text) {
        return "_" + escape(text) + "_";
    }

    public static String code(String text) {
        return "`" + text.replace("\\", "\\\\").replace("`", "\\`") + "`";
    }

    public static String link(String text, String url) {
        String escapedUrl = url.replace("\\", "\\\\").replace(")", "\\)");
        return "[" + escape(text) + "](" + escapedUrl + ")";
    }
}
