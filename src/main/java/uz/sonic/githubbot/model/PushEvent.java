package uz.sonic.githubbot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PushEvent(
        String ref,
        String compare,
        List<Commit> commits,
        Repository repository,
        Pusher pusher
) {}
