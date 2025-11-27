package com.aatechsolutions.elgransazon.application.service;

import com.aatechsolutions.elgransazon.domain.entity.Promotion;
import com.aatechsolutions.elgransazon.domain.entity.PromotionType;
import com.aatechsolutions.elgransazon.domain.repository.PromotionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of PromotionService
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PromotionServiceImpl implements PromotionService {

    private final PromotionRepository promotionRepository;

    @Override
    public List<Promotion> findAll() {
        log.debug("Finding all promotions");
        return promotionRepository.findAll();
    }

    @Override
    public Optional<Promotion> findById(Long id) {
        log.debug("Finding promotion by ID: {}", id);
        return promotionRepository.findById(id);
    }

    @Override
    @Transactional
    public Promotion save(Promotion promotion) {
        log.info("Saving promotion: {}", promotion.getName());
        
        // Validate configuration before saving
        if (!isValidConfiguration(promotion)) {
            throw new IllegalArgumentException("Configuración de promoción inválida para el tipo seleccionado");
        }
        
        // Validate date range
        if (promotion.getStartDate().isAfter(promotion.getEndDate())) {
            throw new IllegalArgumentException("La fecha de inicio debe ser anterior o igual a la fecha de fin");
        }
        
        // Check for duplicate name (excluding current promotion if updating)
        Long excludeId = promotion.getIdPromotion();
        if (existsByName(promotion.getName(), excludeId)) {
            throw new IllegalArgumentException("Ya existe una promoción con ese nombre");
        }
        
        return promotionRepository.save(promotion);
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        log.info("Deleting promotion with ID: {}", id);
        
        if (!promotionRepository.existsById(id)) {
            throw new IllegalArgumentException("Promoción no encontrada con ID: " + id);
        }
        
        promotionRepository.deleteById(id);
    }

    @Override
    public List<Promotion> findActivePromotions() {
        log.debug("Finding active promotions for today");
        LocalDate today = LocalDate.now();
        
        // Get promotions that are within date range and active
        List<Promotion> promotions = promotionRepository.findActivePromotionsForDate(today);
        
        // Filter by valid day of week (validDays field)
        return promotions.stream()
            .filter(promotion -> promotion.isValidForDay(today.getDayOfWeek()))
            .toList();
    }

    @Override
    public List<Promotion> findByType(PromotionType type) {
        log.debug("Finding promotions by type: {}", type);
        return promotionRepository.findByPromotionType(type);
    }

    @Override
    public List<Promotion> findActiveByType(PromotionType type) {
        log.debug("Finding active promotions by type: {}", type);
        return promotionRepository.findByPromotionType(type).stream()
            .filter(promotion -> promotion.isValidNow())
            .toList();
    }

    @Override
    public List<Promotion> findPromotionsByItemId(Long itemId) {
        log.debug("Finding promotions for item ID: {}", itemId);
        return promotionRepository.findPromotionsByItemId(itemId);
    }

    @Override
    public List<Promotion> findActivePromotionsByItemId(Long itemId) {
        log.debug("Finding active promotions for item ID: {}", itemId);
        LocalDate today = LocalDate.now();
        
        // Get promotions for item that are within date range and active
        List<Promotion> promotions = promotionRepository.findActivePromotionsByItemId(itemId, today);
        
        // Filter by valid day of week (validDays field)
        return promotions.stream()
            .filter(promotion -> promotion.isValidForDay(today.getDayOfWeek()))
            .toList();
    }

    @Override
    public boolean isPromotionValidNow(Long promotionId) {
        log.debug("Checking if promotion is valid now: {}", promotionId);
        
        return promotionRepository.findById(promotionId)
                .map(Promotion::isValidNow)
                .orElse(false);
    }

    @Override
    public boolean isPromotionValidForDate(Promotion promotion, LocalDate date) {
        if (promotion == null || date == null) {
            return false;
        }
        
        boolean withinDateRange = !date.isBefore(promotion.getStartDate()) 
                                  && !date.isAfter(promotion.getEndDate());
        boolean validForDay = promotion.isValidForDay(date.getDayOfWeek());
        boolean isActive = Boolean.TRUE.equals(promotion.getActive());
        
        return isActive && withinDateRange && validForDay;
    }

    @Override
    public BigDecimal calculateDiscountedPrice(BigDecimal originalPrice, Promotion promotion, int quantity) {
        if (promotion == null || originalPrice == null || quantity <= 0) {
            return originalPrice != null ? originalPrice.multiply(BigDecimal.valueOf(quantity)) : BigDecimal.ZERO;
        }
        
        // CRITICAL: Validate promotion is valid NOW (date range + day of week)
        if (!promotion.isValidNow()) {
            log.warn("Attempted to apply invalid promotion: {} (not valid for today)", promotion.getName());
            return originalPrice.multiply(BigDecimal.valueOf(quantity)); // Return full price without discount
        }
        
        return promotion.calculateDiscountedPrice(originalPrice, quantity);
    }

    @Override
    public BigDecimal calculateSavings(BigDecimal originalPrice, Promotion promotion, int quantity) {
        if (promotion == null || originalPrice == null || quantity <= 0) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal originalTotal = originalPrice.multiply(BigDecimal.valueOf(quantity));
        BigDecimal discountedTotal = calculateDiscountedPrice(originalPrice, promotion, quantity);
        
        return originalTotal.subtract(discountedTotal);
    }

    @Override
    public Optional<Promotion> getBestPromotionForItem(Long itemId) {
        log.debug("Finding best promotion for item ID: {}", itemId);
        
        List<Promotion> activePromotions = findActivePromotionsByItemId(itemId);
        
        if (activePromotions.isEmpty()) {
            return Optional.empty();
        }
        
        // For simplicity, we'll use priority as the main criterion
        // In a real scenario, you might want to calculate actual savings
        return activePromotions.stream()
                .max(Comparator.comparing(Promotion::getPriority));
    }

    @Override
    @Transactional
    public Promotion activate(Long id) {
        log.info("Activating promotion with ID: {}", id);
        
        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Promoción no encontrada con ID: " + id));
        
        promotion.setActive(true);
        return promotionRepository.save(promotion);
    }

    @Override
    @Transactional
    public Promotion deactivate(Long id) {
        log.info("Deactivating promotion with ID: {}", id);
        
        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Promoción no encontrada con ID: " + id));
        
        promotion.setActive(false);
        return promotionRepository.save(promotion);
    }

    @Override
    public List<Promotion> findPromotionsEndingSoon(int days) {
        log.debug("Finding promotions ending in next {} days", days);
        
        LocalDate today = LocalDate.now();
        LocalDate endDate = today.plusDays(days);
        
        return promotionRepository.findPromotionsEndingSoon(today, endDate);
    }

    @Override
    public long countActivePromotions() {
        return promotionRepository.countActivePromotions(LocalDate.now());
    }

    @Override
    public boolean isValidConfiguration(Promotion promotion) {
        if (promotion == null || promotion.getPromotionType() == null) {
            return false;
        }
        
        return promotion.isValidConfiguration();
    }

    @Override
    public boolean existsByName(String name, Long excludeId) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        
        return promotionRepository.existsByNameIgnoreCaseExcludingId(name.trim(), excludeId);
    }

    @Override
    public List<Promotion> findAllOrderedByPriority() {
        log.debug("Finding all promotions ordered by priority");
        return promotionRepository.findAllByOrderByPriorityDescNameAsc();
    }
}
