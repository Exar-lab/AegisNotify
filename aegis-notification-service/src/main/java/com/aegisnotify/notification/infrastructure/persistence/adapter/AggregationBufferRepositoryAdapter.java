package com.aegisnotify.notification.infrastructure.persistence.adapter;

import com.aegisnotify.notification.application.port.out.AggregationBufferRepository;
import com.aegisnotify.notification.domain.enums.AggregationBufferStatus;
import com.aegisnotify.notification.domain.model.BufferedNotification;
import com.aegisnotify.notification.infrastructure.persistence.entity.AggregationBufferJpaEntity;
import com.aegisnotify.notification.infrastructure.persistence.mapper.AggregationBufferPersistenceMapper;
import com.aegisnotify.notification.infrastructure.persistence.repository.SpringDataAggregationBufferRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AggregationBufferRepositoryAdapter implements AggregationBufferRepository {

  private final SpringDataAggregationBufferRepository springDataRepository;
  private final AggregationBufferPersistenceMapper mapper;

  public AggregationBufferRepositoryAdapter(
      SpringDataAggregationBufferRepository springDataRepository,
      AggregationBufferPersistenceMapper mapper) {
    this.springDataRepository = springDataRepository;
    this.mapper = mapper;
  }

  @Override
  public BufferedNotification save(BufferedNotification bufferedNotification) {
    AggregationBufferJpaEntity entity = mapper.toJpa(bufferedNotification);
    AggregationBufferJpaEntity saved = springDataRepository.save(entity);
    return mapper.toDomain(saved);
  }

  @Override
  public List<BufferedNotification> findClaimable(Instant now, Instant leaseCutoff) {
    return springDataRepository.findClaimable(now, leaseCutoff)
        .stream()
        .map(mapper::toDomain)
        .toList();
  }

  @Override
  public Optional<BufferedNotification> claim(BufferedNotification bufferedNotification,
      Instant claimedAt) {
    int updated = springDataRepository.conditionalClaim(
        bufferedNotification.getId(),
        bufferedNotification.getStatus(),
        AggregationBufferStatus.CLAIMED,
        claimedAt,
        bufferedNotification.getAttempts() + 1);

    if (updated == 0) {
      return Optional.empty();
    }
    return Optional.of(bufferedNotification.claim(claimedAt));
  }

  @Override
  public void resolve(UUID id) {
    springDataRepository.markDone(id);
  }
}
