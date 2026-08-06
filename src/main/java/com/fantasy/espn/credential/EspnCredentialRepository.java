package com.fantasy.espn.credential;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EspnCredentialRepository extends JpaRepository<EspnCredential, String> {
}
