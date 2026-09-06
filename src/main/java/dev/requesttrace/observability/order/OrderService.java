package dev.requesttrace.observability.order;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    public Order create(String productName, int quantity) {
        return orderRepository.save(new Order(productName, quantity));
    }

    public Order get(long id) {
        return findOrder(id);
    }

    @Transactional
    public Order update(long id, String productName, int quantity) {
        Order order = findOrder(id);
        order.update(productName, quantity);
        return order;
    }

    @Transactional
    public void delete(long id) {
        Order order = findOrder(id);
        orderRepository.delete(order);
    }

    private Order findOrder(long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }
}

