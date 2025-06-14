package LogITBackend.LogIT.service;

import LogITBackend.LogIT.DTO.*;
import LogITBackend.LogIT.apiPayload.code.status.ErrorStatus;
import LogITBackend.LogIT.apiPayload.exception.GeneralException;
import LogITBackend.LogIT.config.security.SecurityUtil;
import LogITBackend.LogIT.domain.CodeCategories;
import LogITBackend.LogIT.domain.Codes;
import LogITBackend.LogIT.domain.Commit;
import LogITBackend.LogIT.domain.Users;
import LogITBackend.LogIT.domain.common.RedisCommon;
import LogITBackend.LogIT.repository.CategoryRepository;
import LogITBackend.LogIT.repository.CodesRepository;
import LogITBackend.LogIT.repository.CommitRepository;
import LogITBackend.LogIT.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CodeServiceImpl implements CodeService {

    private final CodesRepository codesRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final RedisCommon redisCommon;
    private final CommitRepository commitRepository;

    @Override
    public CodeResponseDTO addCodeBlock(CodeRequestDTO request) {
        Long userId = SecurityUtil.getCurrentUserId();
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.USER_NOT_FOUND));

        redisCommon.saveSnippet(String.valueOf(userId), request);

        return redisCommon.getSnippet(String.valueOf(userId), request.getId());

    }

    @Override
    public List<CodeResponseDTO> getCodeBlocks() {
        Long userId = SecurityUtil.getCurrentUserId();

        List<CodeResponseDTO> result = redisCommon.getAllSnippets(String.valueOf(userId));

        return result;
    }

    @Override
    public SnippetUpdateResponse setCodeBlock(String snippetId, SnippetUpdateRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();


        boolean success = redisCommon.updateSnippet(
                String.valueOf(userId),
                snippetId,
                request.getStartOffset(),
                request.getEndOffset(),
                request.getCode()
        );

        if (!success) {
            throw new GeneralException(ErrorStatus.SNIPPET_NOT_FOUND);
        }

        return SnippetUpdateResponse.builder()
                .snippetId(snippetId)
                .message("스니펫이 성공적으로 수정되었습니다.")
                .build();
    }

    @Override
    @Transactional
    public CommitCodeBlocksResponse commitCodeBlock(String commitId, Map<String, CodeRequestDTO> bookmarksMap) {
        Long userId = SecurityUtil.getCurrentUserId();

        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.USER_NOT_FOUND));

        List<CodeResponseDTO> redisSnippets = redisCommon.getAllSnippets(String.valueOf(userId));

        // 4) Redis에서 가져온 각 스니펫 DTO를 한 번만 순회
        for (CodeResponseDTO dto : redisSnippets) {

            if ("managed".equalsIgnoreCase(status)) {
                // ──────────── managed 상태 ────────────
                // Redis에서 온 dto를 그대로 DB 엔티티로 변환 저장

                CodeCategories category = categoryRepository
                        .findByUsersIdAndName(userId, dto.getCategory())
                        .orElseGet(() -> CodeCategories.builder()
                                .name(dto.getCategory())
                                .users(user)
                                .codesList(new ArrayList<>())
                                .build());

                if (category.getId() == null) {
                    category = categoryRepository.save(category);
                }

                Codes codeEntity = Codes.builder()
                        .id(dto.getId())                        // UUID 그대로
                        .title(dto.getTitle())
                        .content(dto.getContent())
                        .code(dto.getCode())
                        .startOffset(dto.getStartOffset())
                        .endOffset(dto.getEndOffset())
                        .fileName(dto.getFilePath())
                        .status(dto.getStatus())                // "managed"
                        .commitId(commitId)
                        .codeCategories(category)
                        .build();

                codesRepository.save(codeEntity);

            } else if ("deleted".equalsIgnoreCase(status)) {
                // ──────────── deleted 상태 ────────────
                // Redis에서는 “deleted”로 표시만 되어 있음.
                // 실제로는 클라이언트(Redis와 함께 전달된 bookmarksMap)에서
                // 예전 정보를 가져와서 저장.

                CodeRequestDTO requestDto = bookmarksMap.get(uuid);
                if (requestDto == null) {
                    throw new GeneralException(
                            ErrorStatus.SNIPPET_NOT_FOUND
                    );
                }

                CodeCategories category = categoryRepository
                        .findByUsersIdAndName(userId, requestDto.getCategory())
                        .orElseGet(() -> CodeCategories.builder()
                                .name(requestDto.getCategory())
                                .users(user)
                                .codesList(new ArrayList<>())
                                .build());

                if (category.getId() == null) {
                    category = categoryRepository.save(category);
                }
                System.out.println("-------카테고리 조회 완료-------");

                Codes codeEntity = Codes.builder()
                        .id(requestDto.getId())
                        .title(requestDto.getTitle())
                        .content(requestDto.getContent())
                        .code(requestDto.getCode())
                        .startOffset(requestDto.getStartOffset())
                        .endOffset(requestDto.getEndOffset())
                        .fileName(requestDto.getFilePath())
                        .status("deleted")        // "deleted"
                        .commitId(commitId)
                        .codeCategories(category)
                        .build();

                codesRepository.save(codeEntity);
            }
        }

        redisCommon.deleteSnippet(String.valueOf(userId), commitId);

        // 5) 성공 응답 반환
        return CommitCodeBlocksResponse.builder()
                .message("Commit processed: commitId=" + commitId)
                .build();
    }


    @Override
    public SnippetUpdateResponse setCodeBlockStatus(String snippetId) {
        Long userId = SecurityUtil.getCurrentUserId();

        boolean success = redisCommon.updateSnippetStatus(
                String.valueOf(userId),
                snippetId
        );

        if (!success) {
            throw new GeneralException(ErrorStatus.SNIPPET_NOT_FOUND);
        }

        return SnippetUpdateResponse.builder()
                .snippetId(snippetId)
                .message("스니펫 상태가 성공적으로 수정되었습니다.")
                .build();

    }

    @Override
    public CodeBlockListResponse getCodeBlockList(String commitId) {
        List<Codes> codesList = codesRepository.getAllByCommitId(commitId);

        List<CodeResponseDTO> result = codesList.stream()
                .map(code -> CodeResponseDTO.builder()
                        .id(code.getId())
                        .title(code.getTitle())
                        .filePath(code.getFileName()) // fileName을 filePath로 매핑
                        .startOffset(code.getStartOffset())
                        .endOffset(code.getEndOffset())
                        .content(code.getContent())
                        .code(code.getCode())
                        .category(code.getCodeCategories() != null ? code.getCodeCategories().getName() : null) // null 체크
                        .status(code.getStatus())
                        .date(code.getCreatedAt())
                        .build())
                .toList();

        return CodeBlockListResponse.builder()
                .commitId(commitId)
                .CodeBlocks(result)
                .build();
    }

    //    @Override
//    @Transactional
//    public CodeResponseDTO addCode(CodeRequestDTO request) {
//        Long userId = SecurityUtil.getCurrentUserId();
//        Users user = userRepository.findById(userId)
//                .orElseThrow(() -> new GeneralException(ErrorStatus.USER_NOT_FOUND));
//
//        CodeCategories category = categoryRepository.findByUsersIdAndName(userId, request.getCategory())
//                .orElseThrow(() -> new GeneralException(ErrorStatus.CATEGORY_NOT_FOUND));
//
//        Codes code = request.toEntity(user, category);
//
//        return CodeResponseDTO.toDTO(codeRepository.save(code));
//    }
}
