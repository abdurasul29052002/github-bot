package uz.sonic.githubbot.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "repo_topic_mapping")
public class RepoTopicMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String repoFullName;

    @Column(nullable = false)
    private Integer topicId;

    public RepoTopicMapping() {
    }

    public RepoTopicMapping(String repoFullName, Integer topicId) {
        this.repoFullName = repoFullName;
        this.topicId = topicId;
    }

    public Long getId() {
        return id;
    }

    public String getRepoFullName() {
        return repoFullName;
    }

    public void setRepoFullName(String repoFullName) {
        this.repoFullName = repoFullName;
    }

    public Integer getTopicId() {
        return topicId;
    }

    public void setTopicId(Integer topicId) {
        this.topicId = topicId;
    }
}
