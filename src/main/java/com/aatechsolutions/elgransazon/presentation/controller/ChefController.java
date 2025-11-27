package com.aatechsolutions.elgransazon.presentation.controller;

import com.aatechsolutions.elgransazon.application.service.CategoryService;
import com.aatechsolutions.elgransazon.application.service.EmployeeService;
import com.aatechsolutions.elgransazon.application.service.ItemMenuService;
import com.aatechsolutions.elgransazon.application.service.OrderService;
import com.aatechsolutions.elgransazon.application.service.SystemConfigurationService;
import com.aatechsolutions.elgransazon.domain.entity.Category;
import com.aatechsolutions.elgransazon.domain.entity.Employee;
import com.aatechsolutions.elgransazon.domain.entity.ItemMenu;
import com.aatechsolutions.elgransazon.domain.entity.Order;
import com.aatechsolutions.elgransazon.domain.entity.OrderStatus;
import com.aatechsolutions.elgransazon.domain.entity.SystemConfiguration;
import com.aatechsolutions.elgransazon.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller for Chef role views
 * Handles chef-related pages for managing kitchen orders
 */
@Controller
@RequestMapping("/chef")
@RequiredArgsConstructor
@Slf4j
public class ChefController {

    @Qualifier("chefOrderService")
    private final OrderService chefOrderService;
    private final EmployeeService employeeService;
    private final OrderRepository orderRepository;
    private final ItemMenuService itemMenuService;
    private final CategoryService categoryService;
    private final SystemConfigurationService configurationService;

    /**
     * Display chef dashboard
     * 
     * @param authentication Spring Security authentication object
     * @param model Spring MVC model
     * @return chef dashboard view
     */
    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        String username = authentication.getName();
        log.info("Chef {} accessed dashboard", username);
        
        // Get system configuration
        SystemConfiguration config = configurationService.getConfiguration();
        
        model.addAttribute("config", config);
        model.addAttribute("username", username);
        model.addAttribute("role", "Chef");
        
        return "chef/dashboard";
    }

    /**
     * Display working orders (PENDING, IN_PREPARATION only)
     * Shows orders that chef is currently working on
     * 
     * IMPORTANT FILTERING LOGIC:
     * - PENDING orders WITHOUT preparedBy (never accepted): Shown to ALL chefs
     * - PENDING orders WITH preparedBy (previously accepted): Shown ONLY to the chef who accepted it originally
     * - IN_PREPARATION orders: Only shown to the chef who accepted them (preparedBy matches current chef)
     * 
     * This prevents order "stealing" when new items are added to previously accepted orders
     * 
     * @param authentication Spring Security authentication object
     * @param model Spring MVC model
     * @return pending orders view
     */
    @GetMapping("/orders/pending")
    public String pendingOrders(Authentication authentication, Model model) {
        String username = authentication.getName();
        log.info("Chef {} viewing working orders", username);
        
        // Obtener órdenes en trabajo con filtrado inteligente
        List<Order> workingOrders = chefOrderService.findAll().stream()
            .filter(order -> {
                // CASO 1: Orden PENDING que NUNCA fue aceptada (preparedBy = null)
                // Estas órdenes son visibles para TODOS los chefs (disponibles para aceptar)
                if (order.getStatus() == OrderStatus.PENDING && order.getPreparedBy() == null) {
                    log.debug("Order {} is PENDING and available for all chefs", order.getOrderNumber());
                    return true;
                }
                
                // CASO 2: Orden PENDING que YA fue aceptada antes (preparedBy != null)
                // Esta orden SOLO es visible para el chef que la aceptó originalmente
                // Esto ocurre cuando se agregan nuevos items a una orden que ya fue entregada
                if (order.getStatus() == OrderStatus.PENDING && order.getPreparedBy() != null) {
                    boolean belongsToThisChef = order.getPreparedBy().getUsername().equalsIgnoreCase(username);
                    if (belongsToThisChef) {
                        log.debug("Order {} returned to PENDING but belongs to chef {}", 
                            order.getOrderNumber(), username);
                    }
                    return belongsToThisChef;
                }
                
                // CASO 3: Orden IN_PREPARATION
                // Solo visible para el chef que la aceptó
                if (order.getStatus() == OrderStatus.IN_PREPARATION) {
                    boolean belongsToThisChef = order.getPreparedBy() != null && 
                           order.getPreparedBy().getUsername().equalsIgnoreCase(username);
                    return belongsToThisChef;
                }
                
                return false;
            })
            .sorted((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt())) // Más reciente primero
            .toList();
        
        log.info("Chef {} has {} working orders ({} pending, {} in preparation)", 
                 username, workingOrders.size(),
                 workingOrders.stream().filter(o -> o.getStatus() == OrderStatus.PENDING).count(),
                 workingOrders.stream().filter(o -> o.getStatus() == OrderStatus.IN_PREPARATION).count());
        
        // Contar por estados
        long pendingCount = workingOrders.stream()
            .filter(o -> o.getStatus() == OrderStatus.PENDING)
            .count();
        long inPreparationCount = workingOrders.stream()
            .filter(o -> o.getStatus() == OrderStatus.IN_PREPARATION)
            .count();
        
        model.addAttribute("orders", workingOrders);
        model.addAttribute("pendingCount", pendingCount);
        model.addAttribute("inPreparationCount", inPreparationCount);
        model.addAttribute("username", username);
        model.addAttribute("role", "Chef");
        model.addAttribute("currentChef", username);
        
        return "chef/orders/pending";
    }

    /**
     * Display completed orders history
     * Shows orders prepared by the current chef that are no longer PENDING or IN_PREPARATION
     * (READY, DELIVERED, PAID, CANCELLED, etc.)
     * 
     * @param authentication Spring Security authentication object
     * @param model Spring MVC model
     * @return my orders view (history)
     */
    @GetMapping("/orders/my-orders")
    public String myOrders(Authentication authentication, Model model) {
        String username = authentication.getName();
        log.info("Chef {} viewing completed orders history", username);
        
        // Obtener todos los pedidos preparados por este chef
        // que ya no están en trabajo (diferentes de PENDING e IN_PREPARATION)
        List<Order> completedOrders = chefOrderService.findAll().stream()
            .filter(order -> 
                order.getStatus() != OrderStatus.PENDING &&
                order.getStatus() != OrderStatus.IN_PREPARATION &&
                order.getPreparedBy() != null &&
                order.getPreparedBy().getUsername().equalsIgnoreCase(username)
            )
            .sorted((o1, o2) -> o2.getUpdatedAt().compareTo(o1.getUpdatedAt())) // Más reciente primero
            .toList();
        
        log.info("Found {} completed orders prepared by chef {}", completedOrders.size(), username);
        
        // Contar por estados
        long readyCount = completedOrders.stream()
            .filter(o -> o.getStatus() == OrderStatus.READY)
            .count();
        long deliveredCount = completedOrders.stream()
            .filter(o -> o.getStatus() == OrderStatus.DELIVERED)
            .count();
        long paidCount = completedOrders.stream()
            .filter(o -> o.getStatus() == OrderStatus.PAID)
            .count();
        
        model.addAttribute("orders", completedOrders);
        model.addAttribute("readyCount", readyCount);
        model.addAttribute("deliveredCount", deliveredCount);
        model.addAttribute("paidCount", paidCount);
        model.addAttribute("username", username);
        model.addAttribute("role", "Chef");
        
        return "chef/orders/my-orders";
    }

    /**
     * Display user profile
     *
     * @param authentication Spring Security authentication object
     * @param model Spring MVC model
     * @param redirectAttributes Redirect attributes for error messages
     * @return profile view
     */
    @GetMapping("/profile")
    public String profile(Authentication authentication, Model model, RedirectAttributes redirectAttributes) {
        String username = authentication.getName();
        log.info("Chef {} accessed profile", username);
        
        try {
            Employee employee = employeeService.findByUsername(username)
                    .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
            
            model.addAttribute("employee", employee);
            return "chef/profile/view";
            
        } catch (Exception e) {
            log.error("Error loading profile for chef {}: {}", username, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Error al cargar el perfil");
            return "redirect:/chef/dashboard";
        }
    }

    /**
     * Display user reports with charts
     *
     * @param authentication Spring Security authentication object
     * @param model Spring MVC model
     * @param redirectAttributes Redirect attributes for error messages
     * @return reports view
     */
    @GetMapping("/reports/view")
    public String viewReports(Authentication authentication, Model model, RedirectAttributes redirectAttributes) {
        String username = authentication.getName();
        log.info("Chef {} accessed reports view", username);
        
        try {
            Employee employee = employeeService.findByUsername(username)
                    .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
            
            // Get all orders prepared by this chef
            List<Order> allOrders = orderRepository.findAll().stream()
                    .filter(order -> order.getPreparedBy() != null && 
                                   order.getPreparedBy().getUsername().equalsIgnoreCase(username))
                    .toList();
            
            // Get today's date
            LocalDate today = LocalDate.now();
            LocalDateTime startOfDay = today.atStartOfDay();
            LocalDateTime endOfDay = today.atTime(LocalTime.MAX);
            
            // Today's orders prepared by this chef
            List<Order> todaysOrders = allOrders.stream()
                    .filter(order -> {
                        LocalDateTime updatedAt = order.getUpdatedAt();
                        return updatedAt != null && 
                               !updatedAt.isBefore(startOfDay) && 
                               !updatedAt.isAfter(endOfDay);
                    })
                    .toList();
            
            // Count by status (All time)
            long totalPending = allOrders.stream().filter(o -> o.getStatus() == OrderStatus.PENDING).count();
            long totalInPreparation = allOrders.stream().filter(o -> o.getStatus() == OrderStatus.IN_PREPARATION).count();
            long totalReady = allOrders.stream().filter(o -> o.getStatus() == OrderStatus.READY).count();
            long totalDelivered = allOrders.stream().filter(o -> o.getStatus() == OrderStatus.DELIVERED).count();
            long totalPaid = allOrders.stream().filter(o -> o.getStatus() == OrderStatus.PAID).count();
            long totalCancelled = allOrders.stream().filter(o -> o.getStatus() == OrderStatus.CANCELLED).count();
            
            // Count by status (Today)
            long todayPending = todaysOrders.stream().filter(o -> o.getStatus() == OrderStatus.PENDING).count();
            long todayInPreparation = todaysOrders.stream().filter(o -> o.getStatus() == OrderStatus.IN_PREPARATION).count();
            long todayReady = todaysOrders.stream().filter(o -> o.getStatus() == OrderStatus.READY).count();
            long todayDelivered = todaysOrders.stream().filter(o -> o.getStatus() == OrderStatus.DELIVERED).count();
            long todayPaid = todaysOrders.stream().filter(o -> o.getStatus() == OrderStatus.PAID).count();
            long todayCancelled = todaysOrders.stream().filter(o -> o.getStatus() == OrderStatus.CANCELLED).count();
            
            // Last 7 days data
            List<String> last7DaysLabels = new ArrayList<>();
            List<Long> last7DaysOrdersData = new ArrayList<>();
            
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM");
            
            for (int i = 6; i >= 0; i--) {
                LocalDate date = today.minusDays(i);
                LocalDateTime dayStart = date.atStartOfDay();
                LocalDateTime dayEnd = date.atTime(LocalTime.MAX);
                
                long dayOrders = allOrders.stream()
                        .filter(order -> {
                            LocalDateTime updatedAt = order.getUpdatedAt();
                            return updatedAt != null && 
                                   !updatedAt.isBefore(dayStart) && 
                                   !updatedAt.isAfter(dayEnd);
                        })
                        .count();
                
                last7DaysLabels.add(date.format(formatter));
                last7DaysOrdersData.add(dayOrders);
            }
            
            model.addAttribute("employee", employee);
            model.addAttribute("totalOrders", allOrders.size());
            model.addAttribute("todayOrders", todaysOrders.size());
            
            // All time counts
            model.addAttribute("totalPending", totalPending);
            model.addAttribute("totalInPreparation", totalInPreparation);
            model.addAttribute("totalReady", totalReady);
            model.addAttribute("totalDelivered", totalDelivered);
            model.addAttribute("totalPaid", totalPaid);
            model.addAttribute("totalCancelled", totalCancelled);
            
            // Today counts
            model.addAttribute("todayPending", todayPending);
            model.addAttribute("todayInPreparation", todayInPreparation);
            model.addAttribute("todayReady", todayReady);
            model.addAttribute("todayDelivered", todayDelivered);
            model.addAttribute("todayPaid", todayPaid);
            model.addAttribute("todayCancelled", todayCancelled);
            
            // Last 7 days
            model.addAttribute("last7DaysLabels", last7DaysLabels);
            model.addAttribute("last7DaysOrdersData", last7DaysOrdersData);
            
            return "chef/reports/view";
            
        } catch (Exception e) {
            log.error("Error loading reports for chef {}: {}", username, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Error al cargar los reportes");
            return "redirect:/chef/dashboard";
        }
    }

    /**
     * Display menu items (visual only)
     *
     * @param model Spring MVC model
     * @param redirectAttributes Redirect attributes for error messages
     * @return menu view
     */
    @GetMapping("/menu/view")
    public String viewMenu(Model model, RedirectAttributes redirectAttributes) {
        log.info("Chef accessed visual menu view");
        
        try {
            // Get system configuration
            SystemConfiguration config = configurationService.getConfiguration();
            
            // Get all active categories
            List<Category> categories = categoryService.getAllActiveCategories();
            
            // Get all available items
            List<ItemMenu> availableItems = itemMenuService.findAvailableItems();
            
            // Group items by category
            Map<Long, List<ItemMenu>> itemsByCategory = availableItems.stream()
                    .collect(Collectors.groupingBy(item -> item.getCategory().getIdCategory()));
            
            model.addAttribute("config", config);
            model.addAttribute("categories", categories);
            model.addAttribute("itemsByCategory", itemsByCategory);
            
            return "chef/menu/view";
            
        } catch (Exception e) {
            log.error("Error loading menu: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Error al cargar el menú");
            return "redirect:/chef/dashboard";
        }
    }

    /**
     * Display chefs ranking by prepared orders
     *
     * @param model Spring MVC model
     * @param redirectAttributes Redirect attributes for error messages
     * @return ranking view
     */
    @GetMapping("/ranking/view")
    public String viewRanking(Model model, RedirectAttributes redirectAttributes) {
        log.info("Accessed chefs ranking view");
        
        try {
            // Get system configuration
            SystemConfiguration config = configurationService.getConfiguration();
            
            LocalDate today = LocalDate.now();
            LocalDateTime startOfDay = today.atStartOfDay();
            LocalDateTime endOfDay = today.atTime(LocalTime.MAX);
            
            // Get all chefs (employees with CHEF role)
            List<Employee> chefs = employeeService.findAll().stream()
                    .filter(emp -> emp.hasRole("ROLE_CHEF"))
                    .toList();
            
            // Count today's orders prepared by each chef
            List<Map<String, Object>> chefRanking = chefs.stream()
                    .map(chef -> {
                        // Count orders prepared by this chef today
                        long ordersCount = orderRepository.findAll().stream()
                                .filter(order -> order.getPreparedBy() != null && 
                                               order.getPreparedBy().getIdEmpleado().equals(chef.getIdEmpleado()))
                                .filter(order -> {
                                    LocalDateTime updatedAt = order.getUpdatedAt();
                                    return updatedAt != null && 
                                           !updatedAt.isBefore(startOfDay) && 
                                           !updatedAt.isAfter(endOfDay);
                                })
                                .count();
                        
                        // Get initials
                        String firstName = chef.getNombre() != null ? chef.getNombre() : "";
                        String lastName = chef.getApellido() != null ? chef.getApellido() : "";
                        String initials = "";
                        if (!firstName.isEmpty()) {
                            initials += firstName.charAt(0);
                        }
                        if (!lastName.isEmpty()) {
                            initials += lastName.charAt(0);
                        }
                        initials = initials.toUpperCase();
                        
                        Map<String, Object> chefData = new HashMap<>();
                        chefData.put("employee", chef);
                        chefData.put("orderCount", ordersCount);
                        chefData.put("initials", initials);
                        
                        return chefData;
                    })
                    .filter(chefData -> {
                        // Only include chefs with orders TODAY
                        Long count = (Long) chefData.get("orderCount");
                        return count > 0;
                    })
                    .sorted((c1, c2) -> {
                        Long count1 = (Long) c1.get("orderCount");
                        Long count2 = (Long) c2.get("orderCount");
                        return count2.compareTo(count1); // Descending order
                    })
                    .limit(5) // Top 5 chefs
                    .toList();
            
            model.addAttribute("config", config);
            model.addAttribute("waiterRanking", chefRanking); // Using same attribute name for template compatibility
            model.addAttribute("rankingDate", today);
            
            return "chef/ranking/view";
            
        } catch (Exception e) {
            log.error("Error loading ranking: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Error al cargar el ranking");
            return "redirect:/chef/dashboard";
        }
    }
}
