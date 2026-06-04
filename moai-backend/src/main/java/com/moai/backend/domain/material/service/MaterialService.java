package com.moai.backend.domain.material.service;

import com.moai.backend.domain.learningroom.entity.LearningRoom;
import com.moai.backend.domain.learningroom.repository.LearningRoomRepository;
import com.moai.backend.domain.material.dto.MaterialDetailResponseDto;
import com.moai.backend.domain.material.dto.MaterialListResponseDto;
import com.moai.backend.domain.material.entity.CustomMaterial;
import com.moai.backend.domain.material.repository.CustomMaterialRepository;
import com.moai.backend.domain.users.entity.User;
import com.moai.backend.domain.users.repository.UserRepository;
import com.moai.backend.global.exception.CustomException;
import com.moai.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MaterialService {

    private final CustomMaterialRepository customMaterialRepository;
    private final LearningRoomRepository learningRoomRepository;
    private final UserRepository userRepository;

    public MaterialListResponseDto getMaterials(String email, String roomId) {
        LearningRoom room = findRoomByOwner(email, roomId);

        List<MaterialListResponseDto.MaterialSummary> materials =
                customMaterialRepository.findByRoomIdOrderByCreatedAtDesc(room.getId()).stream()
                        .map(MaterialListResponseDto.MaterialSummary::from)
                        .toList();

        return new MaterialListResponseDto(materials);
    }

    public MaterialDetailResponseDto getMaterialDetail(String email, String roomId, String materialId) {
        LearningRoom room = findRoomByOwner(email, roomId);

        CustomMaterial material = customMaterialRepository.findById(materialId)
                .orElseThrow(() -> new CustomException(ErrorCode.MATERIAL_NOT_FOUND));

        // 요청된 학습실에 속하는 자료인지 검증
        if (!material.getRoom().getId().equals(room.getId())) {
            throw new CustomException(ErrorCode.MATERIAL_NOT_FOUND);
        }

        return MaterialDetailResponseDto.from(material);
    }

    /**
     * 학습실 소유권 검증 후 LearningRoom 반환
     */
    private LearningRoom findRoomByOwner(String email, String roomId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        return learningRoomRepository.findByIdAndUserId(roomId, user.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.LEARNING_ROOM_NOT_FOUND));
    }
}
