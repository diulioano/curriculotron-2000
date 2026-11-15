package dev.curriculotron.opportunities.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UrlIdentityJpaRepository extends JpaRepository<UrlIdentityEntity, String> {
  @Modifying
  @Query(
      "update UrlIdentityEntity i set i.opportunityId = :survivor where i.opportunityId = :merged")
  int moveToSurvivor(@Param("survivor") String survivor, @Param("merged") String merged);
}
