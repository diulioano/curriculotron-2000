package dev.curriculotron.sources;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface OpportunityPublicationJpaRepository
    extends JpaRepository<OpportunityPublicationEntity, String> {
  @Modifying
  @Query("update OpportunityPublicationEntity o set o.publishedAt = :date where o.id = :id")
  int updatePublishedAt(@Param("id") String id, @Param("date") String date);
}
