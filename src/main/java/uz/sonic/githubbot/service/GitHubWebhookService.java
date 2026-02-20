package uz.sonic.githubbot.service;

import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import uz.sonic.githubbot.config.GitHubWebhookProperties;
import uz.sonic.githubbot.model.Commit;
import uz.sonic.githubbot.model.PushEvent;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class GitHubWebhookService {

    private final GitHubWebhookProperties properties;

    public GitHubWebhookService(GitHubWebhookProperties properties) {
        this.properties = properties;
    }

    public boolean isValidSignature(byte[] payload, String signature) {
        if (signature == null || !signature.startsWith("sha256=")) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                    properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hash = mac.doFinal(payload);
            String expected = "sha256=" + HexFormat.of().formatHex(hash);
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    public String formatPushMessage(PushEvent event) {
        String branch = event.ref().replace("refs/heads/", "");
        String repoFullName = event.repository().fullName();
        String pusherName = event.pusher().name();
        int commitCount = event.commits().size();

        var sb = new StringBuilder();
        sb.append("\uD83D\uDD14 <b>New Push to ").append(HtmlUtils.htmlEscape(repoFullName)).append("</b>\n\n");
        sb.append("\uD83C\uDF3F Branch: <code>").append(HtmlUtils.htmlEscape(branch)).append("</code>\n");
        sb.append("\uD83D\uDC64 Pushed by: <b>").append(HtmlUtils.htmlEscape(pusherName)).append("</b>\n");
        sb.append("\uD83D\uDCE6 Commits: ").append(commitCount).append("\n\n");

        for (Commit commit : event.commits()) {
            String shortId = commit.id().substring(0, Math.min(7, commit.id().length()));
            String firstLine = commit.message().lines().findFirst().orElse("");
            sb.append("• <code>").append(shortId).append("</code> - ")
                    .append(HtmlUtils.htmlEscape(firstLine)).append("\n");
        }

        sb.append("\n\uD83D\uDD17 <a href=\"").append(HtmlUtils.htmlEscape(event.compare())).append("\">View Changes</a>");
        return sb.toString();
    }
}
