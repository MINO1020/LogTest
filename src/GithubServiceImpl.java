package LogITBackend.LogIT.service;

import LogITBackend.LogIT.DTO.*;
import LogITBackend.LogIT.apiPayload.code.status.ErrorStatus;
import LogITBackend.LogIT.apiPayload.exception.GeneralException;
import LogITBackend.LogIT.config.security.SecurityUtil;
import LogITBackend.LogIT.domain.*;
import LogITBackend.LogIT.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static LogITBackend.LogIT.apiPayload.code.status.ErrorStatus.COMMIT_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class GithubServiceImpl implements GithubService {

    private final OwnerRepository ownerRepository;
    private final RepoRepository repoRepository;
    private final UserRepository userRepository;
    private final CommitRepository commitRepository;
    private final CommitParentRepository commitParentRepository;
    private final FileRepository fileRepository;
    private final BranchRepository branchRepository;

    @Override
    @Transactional
    public List<CommitResponseDTO> getCommits(String ownerName, String repoName, String branchName) {
        String url = String.format("https://api.github.com/repos/%s/%s/commits?per_page=100&sha=%s", ownerName, repoName, branchName);

        Long userId = SecurityUtil.getCurrentUserId();

        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.USER_NOT_FOUND));

        String token = user.getGithubAccesstoken();
        if (token == null) {
            throw new GeneralException(ErrorStatus.GITHUB_NOT_ACCESS);
        }

        Owner owner = ownerRepository.findByUserIdAndOwnerName(userId, ownerName)
                .orElseThrow(() -> new GeneralException(ErrorStatus.OWNER_NOT_FOUND));

        Repo repo = repoRepository.findByOwnerIdAndRepoName(owner.getId(), repoName)
                .orElseThrow(() -> new GeneralException(ErrorStatus.REPO_NOT_FOUND));

        Branch branch = branchRepository.findByRepoIdAndName(repo.getId(), branchName)
                .orElseThrow(() -> new GeneralException(ErrorStatus.BRANCH_NOT_FOUND));

        System.out.println("branch.getId() = " + branch.getId());

        LocalDateTime latestDate = commitRepository.findLatestCommitDateByUserId(branch.getId())
                .orElse(LocalDateTime.MIN);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("Accept", "application/vnd.github+json");
        headers.set("X-GitHub-Api-Version", "2022-11-28");

        HttpEntity<String> entity = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();


        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}
        );

        List<Map<String, Object>> body = response.getBody();
        if (body == null) return Collections.emptyList();

        List<Map<String, Object>> newCommits = body.stream()
                .filter(item -> {
                    Map<String, Object> commit = (Map<String, Object>) item.get("commit");
                    Map<String, Object> author = (Map<String, Object>) commit.get("author");
                    String dateStr = (String) author.get("date");
                    LocalDateTime date = LocalDateTime.parse(dateStr.replace("Z", ""));
                    return date.isAfter(latestDate);
                })
                .toList();

        List<Commit> savedCommits = newCommits.stream()
                .map(item -> {
                    String sha = (String) item.get("sha");
                    Map<String, Object> commit = (Map<String, Object>) item.get("commit");
                    String message = (String) commit.get("message");
                    Map<String, Object> author = (Map<String, Object>) commit.get("author");
                    String dateStr = (String) author.get("date");
                    LocalDateTime date = LocalDateTime.parse(dateStr.replace("Z", ""));

                    return new Commit(
                            sha,
                            message,
                            null,  // stats 필드는 이후에 계산할 수 있음
                            date,
                            null,
                            branch
                    );
                }).collect(Collectors.toList());

        commitRepository.saveAll(savedCommits);

        List<Commit> allCommitList = commitRepository.findAllByBranchId(branch.getId());

        return allCommitList.stream()
                .map(c -> new CommitResponseDTO(c.getId(), c.getBranch().getId() ,c.getMessage(), c.getStats(), c.getDate()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional // 커밋 세부정보는 거의 바뀌지 않으므로, update x
    public CommitDetailResponseDTO getCommitDetails(String owner, String repo, String commitId) {
        Long userId = SecurityUtil.getCurrentUserId();

        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.USER_NOT_FOUND));

        String token = user.getGithubAccesstoken();

        Commit commit = commitRepository.findById(commitId)
                .orElseThrow(() -> new GeneralException(COMMIT_NOT_FOUND));

        // stats가 null이면 GitHub에서 정보 요청

        if (commit.getStats() == null) {
            RestTemplate restTemplate = new RestTemplate();

            // GitHub API 호출
            String url = String.format("https://api.github.com/repos/%s/%s/commits/%s", owner, repo, commitId);
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + token);
            headers.set("Accept", "application/vnd.github+json");

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, entity, JsonNode.class);
            JsonNode body = response.getBody();

            // stats 정보 세팅
            JsonNode statsNode = body.get("stats");
            if (statsNode != null) {
                int additions = statsNode.get("additions").asInt();
                int deletions = statsNode.get("deletions").asInt();
                int total = statsNode.get("total").asInt();
                String statsString = String.format("%d additions, %d deletions (total: %d)", additions, deletions, total);
                commit.setStats(statsString);
            }
            // files 저장
            List<File> fileList = new ArrayList<>();
            for (JsonNode fileNode : body.get("files")) {
                File file = new File();
                file.setCommit(commit);
                file.setFilename(fileNode.get("filename").asText());
                file.setAdditions(fileNode.get("additions").asLong());
                file.setDeletions(fileNode.get("deletions").asLong());
                file.setPatch(fileNode.has("patch") ? fileNode.get("patch").asText() : null);
                fileList.add(file);
            }
            fileRepository.saveAll(fileList);

            // 업데이트 저장
            commitRepository.save(commit);
        }

        List<File> files = fileRepository.findAllByCommitId(commitId);

        List<FileResponseDTO> fileResponses = files.stream()
                .map(FileResponseDTO::fromEntity)
                .collect(Collectors.toList());

        CommitResponseDTO commitResponseDTO = CommitResponseDTO.fromEntity(commit);
        return new CommitDetailResponseDTO(commitResponseDTO, fileResponses);
    }

    @Override
    @Transactional
    public GithubRepoResponse getUsersRepos() {
        Long userId = SecurityUtil.getCurrentUserId();
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.USER_NOT_FOUND));

        String githubAccesstoken = user.getGithubAccesstoken();
        String githubNickname = user.getGithubNickname();

        if (githubAccesstoken == null || githubNickname == null) {
            throw new GeneralException(ErrorStatus.GITHUB_NOT_ACCESS);
        }

        List<Owner> owners = ownerRepository.findAllByUserId(userId);

        Owner owner = getOrCreateOwner(user, owners);

        String url = "https://api.github.com/users/" + githubNickname + "/repos";
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(githubAccesstoken);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                request,
                JsonNode.class
        );

        LocalDateTime latestDate = repoRepository.findLatestRepoCreatedAtByOwnerId(owner.getId())
                .orElse(LocalDateTime.MIN);

        JsonNode body = response.getBody();
        List<Repo> repoList = new ArrayList<>();

        for (JsonNode item : body) {
            String name = item.get("name").asText();
            String createdAtStr = item.get("created_at").asText();
            String updatedAtStr = item.get("updated_at").asText();

            LocalDateTime createdAt = LocalDateTime.parse(createdAtStr, DateTimeFormatter.ISO_DATE_TIME);
            LocalDateTime updatedAt = LocalDateTime.parse(updatedAtStr, DateTimeFormatter.ISO_DATE_TIME);

            if (createdAt.isAfter(latestDate)) {
                Repo repo = Repo.builder()
                        .owner(owner)
                        .repoName(name)
                        .createdAt(createdAt)
                        .updatedAt(updatedAt)
                        .build();

                repoList.add(repo);
            }
        }
        repoRepository.saveAll(repoList);

        // db에 있는거 그대로 출력하는 로직
        List<RepositoryResponseDTO> repoDTOList = repoRepository.findAllByOwnerId((owner.getId()))
                .stream()
                .map(repo -> new RepositoryResponseDTO(
                        repo.getRepoName(),
                        repo.getCreatedAt(),
                        repo.getUpdatedAt()
                ))
                .collect(Collectors.toList());

        return new GithubRepoResponse(owner.getOwnerName(), repoDTOList);
    }

    @Override
    public List<OrgResponse> getUserOrgs() {
        Long userId = SecurityUtil.getCurrentUserId();

        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.USER_NOT_FOUND));

        String githubAccesstoken = user.getGithubAccesstoken();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(githubAccesstoken);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        HttpEntity<Void> request = new HttpEntity<>(headers);

        RestTemplate restTemplate = new RestTemplate();

        // Step 1: 조직 목록 조회
        ResponseEntity<JsonNode> orgResponse = restTemplate.exchange(
                "https://api.github.com/user/orgs",
                HttpMethod.GET,
                request,
                JsonNode.class
        );

        List<OrgResponse> orgResponses = new ArrayList<>();

        for (JsonNode org : orgResponse.getBody()) {
            String orgName = org.get("login").asText(); // Owner 이름

            ownerRepository.findByUserIdAndOwnerName(user.getId(), orgName)
                    .orElseGet(() -> ownerRepository.save(
                            Owner.builder()
                                    .user(user)
                                    .ownerName(orgName)
                                    .build()
                    ));

            orgResponses.add(new OrgResponse(orgName));
        }
        return orgResponses;
    }

    @Override
    public GithubRepoResponse getUserOrgsRepos(String owners) {
        Long userId = SecurityUtil.getCurrentUserId();

        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.USER_NOT_FOUND));

        String githubAccesstoken = user.getGithubAccesstoken();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(githubAccesstoken);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        HttpEntity<Void> request = new HttpEntity<>(headers);

        RestTemplate restTemplate = new RestTemplate();

        Owner owner = ownerRepository.findByUserIdAndOwnerName(userId, owners)
                .orElseGet(() -> ownerRepository.save(
                        Owner.builder()
                                .user(user)
                                .ownerName(owners)
                                .build()
                ));

        LocalDateTime latestDate = repoRepository.findLatestRepoCreatedAtByOwnerId(owner.getId())
                .orElse(LocalDateTime.MIN);

        String url = "https://api.github.com/orgs/" + owners + "/repos?per_page=100";
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                request,
                JsonNode.class
        );

        List<RepositoryResponseDTO> repoDTOList = new ArrayList<>();
        List<Repo> newRepos = new ArrayList<>();

        for (JsonNode repoNode : response.getBody()) {
            String repoName = repoNode.get("name").asText();
            LocalDateTime createdAt = LocalDateTime.parse(repoNode.get("created_at").asText(), DateTimeFormatter.ISO_DATE_TIME);
            LocalDateTime updatedAt = LocalDateTime.parse(repoNode.get("updated_at").asText(), DateTimeFormatter.ISO_DATE_TIME);

            if (createdAt.isAfter(latestDate)) {
                Repo repo = Repo.builder()
                        .owner(owner)
                        .repoName(repoName)
                        .createdAt(createdAt)
                        .updatedAt(updatedAt)
                        .build();
                newRepos.add(repo);
            }

            repoDTOList.add(new RepositoryResponseDTO(
                    repoName,
                    createdAt,
                    updatedAt
            ));
        }

        repoRepository.saveAll(newRepos);

        return new GithubRepoResponse(owners, repoDTOList);
    }

    private Owner getOrCreateOwner(Users user, List<Owner> owners) {
        return owners.stream()
                .filter(o -> o.getOwnerName().equals(user.getGithubNickname()))
                .findFirst()
                .orElseGet(() -> {
                    Owner newOwner = Owner.builder()
                            .user(user)
                            .ownerName(user.getGithubNickname())
                            .build();
                    return ownerRepository.save(newOwner);
                });
    }

    @Override
    public List<BranchResponseDTO> getUserBranches(String ownerName, String repoName) {
        Long userId = SecurityUtil.getCurrentUserId();

        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.USER_NOT_FOUND));

        Owner owner = ownerRepository.findByUserIdAndOwnerName(userId, ownerName)
                .orElseThrow(() -> new GeneralException(ErrorStatus.OWNER_NOT_FOUND));

        Repo repo = repoRepository.findByOwnerIdAndRepoName(owner.getId(), repoName)
                .orElseThrow(() -> new GeneralException(ErrorStatus.REPO_NOT_FOUND));

        String githubAccesstoken = user.getGithubAccesstoken();

        String url = "https://api.github.com/repos/" + ownerName + "/" + repoName + "/branches";

        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/vnd.github+json");
        headers.set("Authorization", "Bearer " + githubAccesstoken);
        headers.set("X-GitHub-Api-Version", "2022-11-28");

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ParameterizedTypeReference<List<Map<String, Object>>> responseType =
                new ParameterizedTypeReference<>() {};

        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                responseType
        );


        List<BranchResponseDTO> result = new ArrayList<>();
        List<Branch> branches = new ArrayList<>();

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            for (Map<String, Object> branch : response.getBody()) {
                String name = (String) branch.get("name");

                // 중복 저장 방지 (이미 DB에 있는 브랜치 필터링)
                boolean exists = branchRepository.findByRepoIdAndName(repo.getId(), name).isPresent();
                if (!exists) {
                    Branch branchEntity = new Branch(
                            null,              // id (auto-generated)
                            name,
                            repo,
                            new ArrayList<>()  // commits 비워두기
                    );
                    branches.add(branchEntity);

                }
                branchRepository.saveAll(branches);
                result.add(new BranchResponseDTO(name));

            }
        }

        return result;

    }
}
