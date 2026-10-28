package com.aegisnotify.notification.infrastructure.persistence.repository;

import com.aegisnotify.notification.application.dto.NotificationListQuery;
import com.aegisnotify.notification.infrastructure.persistence.entity.NotificationJpaEntity;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;
import org.hibernate.query.criteria.JpaExpression;
import org.springframework.data.jpa.domain.Specification;

public final class NotificationSpecifications {
  private NotificationSpecifications() {}

  public static Specification<NotificationJpaEntity> matching(NotificationListQuery filter) {
    return (root, query, cb) -> {
      var predicates = new ArrayList<Predicate>();
      if (filter.channel() != null) {
        predicates.add(cb.equal(root.get("channel"), filter.channel()));
      }
      if (filter.priority() != null) {
        predicates.add(cb.equal(root.get("priority"), filter.priority()));
      }
      if (filter.status() != null) {
        predicates.add(cb.equal(root.get("status"), filter.status()));
      }
      if (filter.from() != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.from()));
      }
      if (filter.to() != null) {
        predicates.add(cb.lessThan(root.get("createdAt"), filter.to()));
      }
      if (filter.recipient() != null && !filter.recipient().isBlank()) {
        predicates.add(cb.like(cb.lower(root.get("recipient")), pattern(filter.recipient()), '\\'));
      }
      if (filter.search() != null && !filter.search().isBlank()) {
        String pattern = pattern(filter.search());
        predicates.add(cb.or(
            cb.like(cb.lower(((JpaExpression<UUID>) root.<UUID>get("id"))
                .cast(String.class)), pattern, '\\'),
            cb.like(cb.lower(root.get("recipient")), pattern, '\\'),
            cb.like(cb.lower(root.get("templateName")), pattern, '\\')));
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }

  private static String pattern(String value) {
    return "%" + value.trim().toLowerCase(Locale.ROOT)
        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
  }
}
