package com.lastsafe.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.lastsafe.entity.FavoriteDestination;
import com.lastsafe.entity.User;

public interface FavoriteDestinationRepository extends JpaRepository<FavoriteDestination, Long> {

    List<FavoriteDestination> findAllByUser(User user);
}
