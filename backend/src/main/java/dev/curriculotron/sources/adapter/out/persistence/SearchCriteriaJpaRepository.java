package dev.curriculotron.sources.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface SearchCriteriaJpaRepository extends JpaRepository<SearchCriteriaEntity, Integer> {}
