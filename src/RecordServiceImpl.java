package LogITBackend.LogIT.service;

import LogITBackend.LogIT.DTO.RecordRequestDTO;
import LogITBackend.LogIT.apiPayload.code.status.ErrorStatus;
import LogITBackend.LogIT.apiPayload.exception.handler.ExceptionHandler;
import LogITBackend.LogIT.config.security.SecurityUtil;
import LogITBackend.LogIT.converter.RecordConverter;
import LogITBackend.LogIT.domain.Records;
import LogITBackend.LogIT.domain.Users;
import LogITBackend.LogIT.repository.RecordRepository;
import LogITBackend.LogIT.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RecordCommandServiceImpl implements RecordCommandService{
    private final RecordRepository recordRepository;
    private final UserRepository userRepository;

    @Override
    public Records createRecord(RecordRequestDTO.CreateRecordRequestDTO request) {
        Records newRecord = RecordConverter.toRecords(request);
        Long userId = SecurityUtil.getCurrentUserId();
        Users user = userRepository.findById(userId).orElseThrow(() -> new ExceptionHandler(ErrorStatus.USER_NOT_FOUND));

        newRecord.setUsers(user);

        return recordRepository.save(newRecord);
    }

    @Override
    @Transactional
    public Records editRecord(Long recordId, RecordRequestDTO.EditRecordRequestDTO request) {
        // 해당유저의 기록이 맞는지 확인하기 <- 추후에
        Records getRecord = recordRepository.findById(recordId).orElseThrow(() -> new ExceptionHandler(ErrorStatus.RECORD_NOT_FOUND));
        request.getTitle().ifPresent(getRecord::updateTitle);
        request.getContent().ifPresent(getRecord::updateContent);
        return getRecord;
    }

    @Override
    public void deleteRecord(Long recordId) {
        // 해당유저의 기록이 맞는지 확인하기 <- 추후에
        Records getRecord = recordRepository.findById(recordId).orElseThrow(() -> new ExceptionHandler(ErrorStatus.RECORD_NOT_FOUND));
        recordRepository.delete(getRecord);
    }
}
