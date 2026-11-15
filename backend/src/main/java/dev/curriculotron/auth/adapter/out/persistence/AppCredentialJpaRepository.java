package dev.curriculotron.auth.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface AppCredentialJpaRepository extends JpaRepository<AppCredentialEntity, Integer> {}
