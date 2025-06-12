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



    @Override
    public SnippetUpdateResponse setCodeBlock(String snippetId, SnippetUpdateRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();


        boolean success = redisCommon.updateSnippet(
                String.valueOf(userId),
                snippetId,
                request.getStartOffset(),
                request.getEndOffset(),
                request.getCode()
        ); //code block

        if (!success) {
            throw new GeneralException(ErrorStatus.SNIPPET_NOT_FOUND); //dddddd
        }

        return SnippetUpdateResponse.builder()
                .snippetId(snippetId)
                .message("스니펫이 성공적으로 수정되었습니다.")
                .build();
    }


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


                codesRepository.save(codeEntity);

            } else if ("deleted".equalsIgnoreCase(status)) {
                // ──────────── deleted 상태 ────────────
                // Redis에서는 “deleted”로 표시만 되어 있음.
                // 실제로는 클라이언트(Redis와 함께 전달된 bookmarksMap)에서
                // 예전 정보를 가져와서 저장.

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
