package com.aatechsolutions.elgransazon.domain.repository;

import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository interface for SystemConfiguration entity
 * Only one configuration should exist (Singleton pattern)
 */
@Repository
public interface SystemConfigurationRepository extends JpaRepository<SystemConfiguration, Long> {

    /**
     * Find the first (and should be only) configuration
     */
    @Query("SELECT sc FROM SystemConfiguration sc ORDER BY sc.id ASC")
    Optional<SystemConfiguration> findFirstConfiguration();

    /**
     * Check if any configuration exists
     */
    @Query("SELECT COUNT(sc) > 0 FROM SystemConfiguration sc")
    boolean existsConfiguration();

    /**
     * Count total configurations (should always be 0 or 1)
     */
    @Query("SELECT COUNT(sc) FROM SystemConfiguration sc")
    long countConfigurations();
}
