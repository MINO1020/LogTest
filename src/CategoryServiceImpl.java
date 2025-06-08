package LogITBackend.LogIT.service;

import LogITBackend.LogIT.DTO.CategoryRequestDTO;
import LogITBackend.LogIT.DTO.CategoryResponseDTO;
import LogITBackend.LogIT.apiPayload.code.status.ErrorStatus;
import LogITBackend.LogIT.apiPayload.exception.GeneralException;
import LogITBackend.LogIT.config.security.SecurityUtil;
import LogITBackend.LogIT.domain.CodeCategories;
import LogITBackend.LogIT.domain.Users;
import LogITBackend.LogIT.repository.CategoryRepository;

import LogITBackend.LogIT.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.SQLOutput;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    @Override
    public List<String> getCategories() {
        Long userId = SecurityUtil.getCurrentUserId();

        List<CodeCategories> categories = categoryRepository.findAllByUsersId(userId);
        if (categories.isEmpty()) {
            throw new GeneralException(ErrorStatus.CATEGORY_NOT_FOUND);
        }
        return categories.stream()
                .map(CodeCategories::getName)
                .toList();
    }

    @Override
    public CategoryResponseDTO createCategory(CategoryRequestDTO request) {
        Long userId = SecurityUtil.getCurrentUserId();
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.USER_NOT_FOUND));

        CodeCategories category = request.ToEntity(user);
        return CategoryResponseDTO.ToDTO(categoryRepository.save(category));
    }
}
