package uz.sonic.githubbot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Commit(
        String id,
        String message,
        String timestamp,
        String url,
        Author author
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Author(String name, String email) {}
}
