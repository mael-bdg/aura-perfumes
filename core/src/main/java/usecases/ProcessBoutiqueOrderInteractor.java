package usecases;

import datatransferobjects.ProcessBoutiqueOrderRequest;
import datatransferobjects.ProcessBoutiqueOrderResponse;
import entities.Employee;
import entities.EmployeeRole;
import entities.Order;
import entities.OrderStatus;
import gateways.BoutiqueEmployeeRepository;
import gateways.BoutiqueOrderRepository;

public class ProcessBoutiqueOrderInteractor
        implements ProcessBoutiqueOrderRequestHandler {

    private final BoutiqueOrderRepository orderRepository;
    private final BoutiqueEmployeeRepository employeeRepository;

    public ProcessBoutiqueOrderInteractor(
            BoutiqueOrderRepository orderRepository,
            BoutiqueEmployeeRepository employeeRepository) {

        this.orderRepository = orderRepository;
        this.employeeRepository = employeeRepository;
    }

    @Override
    public ProcessBoutiqueOrderResponse handle(
            ProcessBoutiqueOrderRequest request) {

        if (request == null
                || request.getOrderId() == null
                || request.getEmployeeId() == null
                || request.getTargetStatus() == null) {

            return new ProcessBoutiqueOrderResponse(
                    false, null, null, "Invalid request."
            );
        }

        Order order = orderRepository.findById(request.getOrderId());

        if (order == null) {
            return failure(request, "Order not found.");
        }

        Employee employee =
                employeeRepository.findById(request.getEmployeeId());

        if (employee == null) {
            return failure(request, "Employee not found.");
        }

        OrderStatus targetStatus = request.getTargetStatus();

        // Basic authorization rules for UC4
        if (!isAuthorized(employee.getRole(), targetStatus)) {
            return failure(
                    request,
                    "Employee is not authorized for this operation."
            );
        }

        long expectedVersion = order.getVersion();

        try {
            switch (targetStatus) {

                case CONFIRMED:
                    order.confirm();
                    break;

                case IN_PREPARATION:
                    order.markInPreparation();
                    break;

                case READY_FOR_PICKUP:
                    order.markReadyForPickup(request.getPickupPin());
                    break;

                case CANCELLED:
                    order.cancel(request.getCancellationReason());
                    break;

                default:
                    return failure(
                            request,
                            "Unsupported order status transition."
                    );
            }

            boolean saved = orderRepository.updateIfVersionMatches(
                    order,
                    expectedVersion
            );

            if (!saved) {
                return failure(
                        request,
                        "Order was modified by another employee."
                );
            }

            return new ProcessBoutiqueOrderResponse(
                    true,
                    order.getOrderId(),
                    order.getStatus(),
                    "Order updated successfully."
            );

        } catch (IllegalArgumentException | IllegalStateException e) {

            return failure(request, e.getMessage());
        }
    }

    private boolean isAuthorized(
            EmployeeRole role,
            OrderStatus targetStatus) {

        if (role == EmployeeRole.STORE_MANAGER
                || role == EmployeeRole.OPERATIONS_MANAGER) {
            return true;
        }

        if (role == EmployeeRole.SALES_ASSOCIATE) {
            return targetStatus == OrderStatus.CONFIRMED
                    || targetStatus == OrderStatus.IN_PREPARATION
                    || targetStatus == OrderStatus.READY_FOR_PICKUP;
        }

        return false;
    }

    private ProcessBoutiqueOrderResponse failure(
            ProcessBoutiqueOrderRequest request,
            String message) {

        return new ProcessBoutiqueOrderResponse(
                false,
                request.getOrderId(),
                null,
                message
        );
    }
}
