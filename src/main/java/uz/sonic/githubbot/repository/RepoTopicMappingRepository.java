package uz.sonic.githubbot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import uz.sonic.githubbot.entity.RepoTopicMapping;

import java.util.Optional;

public interface RepoTopicMappingRepository extends JpaRepository<RepoTopicMapping, Long> {

    Optional<RepoTopicMapping> findByRepoFullName(String repoFullName); //comment

    boolean existsByRepoFullName(String repoFullName);

    @Transactional
    void deleteByRepoFullName(String repoFullName);
}
