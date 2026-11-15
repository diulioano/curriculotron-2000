package dev.curriculotron.opportunities.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface CompatibilityRefreshJpaRepository
    extends JpaRepository<CompatibilityRefreshEntity, Integer> {}
