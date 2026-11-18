package usecases;

import datatransferobjects.ProcessBoutiqueOrderRequest;
import datatransferobjects.ProcessBoutiqueOrderResponse;
import entities.*;
import gateways.BoutiqueEmployeeRepository;
import gateways.BoutiqueOrderRepository;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProcessBoutiqueOrderInteractorTest {

    @Test
    void shouldConfirmPendingOrder() {

        UUID orderId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();

        OrderItem item = new OrderItem(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                new BigDecimal("50.00")
        );

        Order order = new Order(
                orderId,
                "ORD-3001",
                UUID.randomUUID(),
                List.of(item),
                new BigDecimal("50.00"),
                BigDecimal.ZERO,
                new BigDecimal("50.00"),
                0,
                OrderStatus.PENDING_CONFIRMATION,
                FulfillmentType.IN_STORE_PICKUP,
                null,
                null,
                null,
                null,
                0
        );

        Employee employee = new Employee(
                employeeId,
                "Emma",
                EmployeeRole.SALES_ASSOCIATE
        );

        // Fake repository: returns the test order
        BoutiqueOrderRepository orderRepository =
                new BoutiqueOrderRepository() {

            @Override
            public Order findById(UUID id) {
                return order;
            }

            @Override
            public boolean updateIfVersionMatches(
                    Order updatedOrder,
                    long expectedVersion) {

                return expectedVersion == 0
                        && updatedOrder.getStatus()
                        == OrderStatus.CONFIRMED;
            }
        };

        // Fake repository: returns the test employee
        BoutiqueEmployeeRepository employeeRepository =
                id -> employee;

        ProcessBoutiqueOrderInteractor interactor =
                new ProcessBoutiqueOrderInteractor(
                        orderRepository,
                        employeeRepository
                );

        ProcessBoutiqueOrderRequest request =
                new ProcessBoutiqueOrderRequest(
                        orderId,
                        employeeId,
                        OrderStatus.CONFIRMED,
                        null,
                        null
                );

        ProcessBoutiqueOrderResponse response =
                interactor.handle(request);

        assertTrue(response.isSuccess());
        assertEquals(orderId, order.getOrderId());
        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
    }

    @Test
    void shouldRejectCancellationBySalesAssociate() {

        UUID orderId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();

        Employee employee = new Employee(
                employeeId,
                "Emma",
                EmployeeRole.SALES_ASSOCIATE
        );

        OrderItem item = new OrderItem(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                new BigDecimal("50.00")
        );

        Order order = new Order(
                orderId,
                "ORD-3002",
                UUID.randomUUID(),
                List.of(item),
                new BigDecimal("50.00"),
                BigDecimal.ZERO,
                new BigDecimal("50.00"),
                0,
                OrderStatus.CONFIRMED,
                FulfillmentType.IN_STORE_PICKUP,
                null,
                null,
                null,
                null,
                0
        );

        BoutiqueOrderRepository orderRepository =
                new BoutiqueOrderRepository() {

                    @Override
                    public Order findById(UUID id) {
                        return order;
                    }

                    @Override
                    public boolean updateIfVersionMatches(
                            Order order,
                            long expectedVersion) {
                        fail("Order must not be updated");
                        return false;
                    }
                };

        BoutiqueEmployeeRepository employeeRepository =
                id -> employee;

        ProcessBoutiqueOrderInteractor interactor =
                new ProcessBoutiqueOrderInteractor(
                        orderRepository,
                        employeeRepository
                );

        ProcessBoutiqueOrderRequest request =
                new ProcessBoutiqueOrderRequest(
                        orderId,
                        employeeId,
                        OrderStatus.CANCELLED,
                        null,
                        "Customer requested cancellation"
                );

        ProcessBoutiqueOrderResponse response =
                interactor.handle(request);

        assertFalse(response.isSuccess());
        assertEquals("Employee is not authorized for this operation.",response.getMessage());
        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
    }
}

