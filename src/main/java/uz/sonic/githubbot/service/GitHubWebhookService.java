package uz.sonic.githubbot.service;

import org.springframework.stereotype.Service;
import uz.sonic.githubbot.config.GitHubWebhookProperties;
import uz.sonic.githubbot.model.Commit;
import uz.sonic.githubbot.model.PushEvent;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static uz.sonic.githubbot.util.MarkdownV2Utils.*;

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

        var sb = new StringBuilder();

        // Find merge commit (if PR was merged)
        Commit mergeCommit = event.commits().stream()
                .filter(c -> c.message().startsWith("Merge pull request #"))
                .findFirst()
                .orElse(null);

        if (mergeCommit != null) {
            // PR merge format
            String firstLine = mergeCommit.message().lines().findFirst().orElse("");
            String extendedDescription = extractExtendedDescription(mergeCommit.message());

            sb.append("\uD83D\uDD00 ").append(bold("PR Merged in " + repoFullName)).append("\n\n");
            sb.append("\uD83C\uDF3F Branch: ").append(code(branch)).append("\n");
            sb.append("\uD83D\uDC64 Merged by: ").append(bold(pusherName)).append("\n");
            sb.append("\uD83D\uDD17 ").append(escape(firstLine)).append("\n");

            if (!extendedDescription.isEmpty()) {
                sb.append("\n\uD83D\uDCDD ").append(italic(extendedDescription)).append("\n");
            }

            // List non-merge commits as changes
            var changes = event.commits().stream()
                    .filter(c -> c != mergeCommit)
                    .toList();

            if (!changes.isEmpty()) {
                sb.append("\n\uD83D\uDCE6 ").append(escape("Changes (" + changes.size() + " commits):")).append("\n");
                appendCommitList(sb, changes);
            }
        } else {
            // Regular push format
            int commitCount = event.commits().size();
            sb.append("\uD83D\uDD14 ").append(bold("New Push to " + repoFullName)).append("\n\n");
            sb.append("\uD83C\uDF3F Branch: ").append(code(branch)).append("\n");
            sb.append("\uD83D\uDC64 Pushed by: ").append(bold(pusherName)).append("\n");
            sb.append("\uD83D\uDCE6 Commits: ").append(commitCount).append("\n\n");

            appendCommitList(sb, event.commits());
        }

        sb.append("\n\uD83D\uDD17 ").append(link("View Changes", event.compare()));
        return sb.toString();
    }

    private static final int MAX_COMMITS = 10;

    private void appendCommitList(StringBuilder sb, List<Commit> commits) {
        int shown = Math.min(commits.size(), MAX_COMMITS);
        for (int i = 0; i < shown; i++) {
            Commit commit = commits.get(i);
            String shortId = commit.id().substring(0, Math.min(7, commit.id().length()));
            String firstLine = commit.message().lines().findFirst().orElse("");
            sb.append("\u2022 ").append(code(shortId)).append(" \\- ")
                    .append(escape(firstLine)).append("\n");
        }
        int remaining = commits.size() - shown;
        if (remaining > 0) {
            sb.append(escape("... va yana " + remaining + " ta commit")).append("\n");
        }
    }

    private String extractExtendedDescription(String message) {
        var lines = message.lines().toList();
        if (lines.size() <= 1) {
            return "";
        }
        // Skip first line and any blank lines after it
        int start = 1;
        while (start < lines.size() && lines.get(start).isBlank()) {
            start++;
        }
        if (start >= lines.size()) {
            return "";
        }
        return String.join("\n", lines.subList(start, lines.size())).strip();
    }
}
