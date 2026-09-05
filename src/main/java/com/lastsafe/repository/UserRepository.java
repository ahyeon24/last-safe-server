package com.lastsafe.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.lastsafe.entity.OauthProvider;
import com.lastsafe.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByOauthProviderAndOauthId(OauthProvider oauthProvider, String oauthId);
}
