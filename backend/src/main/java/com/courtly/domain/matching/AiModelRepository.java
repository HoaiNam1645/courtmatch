package com.courtly.domain.matching;

import com.courtly.common.enums.ModelStatus;
import com.courtly.common.enums.ModelType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiModelRepository extends JpaRepository<AiModel, UUID> {

    Optional<AiModel> findFirstByModelTypeAndStatus(ModelType modelType, ModelStatus status);
}
