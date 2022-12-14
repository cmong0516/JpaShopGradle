package jpabook.jpashop.api;

import jpabook.jpashop.domain.*;
import jpabook.jpashop.repository.OrderRepository;
import jpabook.jpashop.repository.query.OrderFlatDto;
import jpabook.jpashop.repository.query.OrderItemQueryDto;
import jpabook.jpashop.repository.query.OrderQueryDto;
import jpabook.jpashop.repository.query.OrderQueryRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.*;

@RestController
@RequiredArgsConstructor
public class OrderApiController {

    private final OrderRepository orderRepository;
    private final OrderQueryRepository orderQueryRepository;

    // Entity 반환
//    public List<Order> orderV1() {
//        List<Order> all = orderRepository.findAllByString(new OrderSearch());
//        for (Order order : all) {
//            order.getMember().getName();
//            order.getDelivery().getAddress();
//            List<OrderItem> orderItems = order.getOrderItems();
//            orderItems.stream().forEach(o -> o.getItem().getName());
//        }
//        return all;
//        // 지연로딩을 강제로 초기화 하고 Entity 를 반환하는 좋지않은 방법.
//    }

// Entity 조회후 Dto 로 반환
    @GetMapping("/api/v2/orders")
    public List<OrderDto> ordersV2() {
        return orderRepository.findAllByString(new OrderSearch()).stream().map(OrderDto::new).collect(toList());
        // Order , OrderItem 모두 Dto 로 변환하였다 . 하지만 쿼리가 너무 많이날아가는 N+1 문제가 발생중.
        // join fetch 로 성능최적화를 하자.
    }

    // Entity 조회후 Dto 반환
    // join fetch 로 쿼리 최적화
    @GetMapping("/api/v3/orders")
    public List<OrderDto> ordersV3() {
        return orderRepository.findAllWithItem().stream().map(OrderDto::new).collect(toList());
        // 상품은 2개 인데 order 와 조인하면서 같은 order 가 두개씩 총 네개의 데이터가 나온다.
        // "select distinct o from Order o join fetch o.member m join fetch o.delivery d join fetch o.orderItems oi join fetch oi.item i"
        // distinct 는 DB 에서는 모든 항복이 같아야 중복 제거가 되지만 jpa 에선 id 값으로 판별해서 중복을 제거해준다.
        // 단점 : 컬렉션은 일대다 조인이 발생하므로 데이터가 예측할수 없게 증가하여 페이징 처리가 힘들다.
        // 해결
        // toOne 관계는 fetch join 한다. -> 데이터가 증가하지 않기때문에 사용해도 무방함.
        // 컬렉션은 모두 지연로딩으로 조회한다.
    }

    // 컬렉션 페이징
    @GetMapping("/api/v3.1/orders")
    public List<OrderDto> orderV3_page(
            @RequestParam(value = "offset", defaultValue = "0") int offset,
            @RequestParam(value = "limit", defaultValue = "100") int limit
    ) {
        //return orderRepository.findAllWithItem().stream().map(OrderDto::new).collect(Collectors.toList());
        return orderRepository.findAllWithMemberDelivery(offset, limit).stream().map(OrderDto::new).collect(toList());
        // n+1 발생.
        // spring.jpa.properties.hibernate.default_batch_fetch_size=100 옵션을 넣어주면
        // n+1 을 1+1 로 최적화 해준다.
    }

    // jpa 에서 dto 를 직접 조회
    @GetMapping("/api/v4/orders")
    public List<OrderQueryDto> ordersV4() {
        return orderQueryRepository.findOrderQueryDto();
    }

    // map 과 in 을 사용하여 최적화
    @GetMapping("/api/v5/orders")
    public List<OrderQueryDto> ordersV5() {
        return orderQueryRepository.findAllByDto_optimization();
    }

    // join 결과를 그대로 조회후 OrderFlatDto >> OrderQueryDto 로 변환.
    @GetMapping("/api/v6/orders")
    public List<OrderQueryDto> ordersV6() {
        List<OrderFlatDto> flats = orderQueryRepository.findAllByDto_flat();

        return flats.stream()
                .collect(groupingBy(o -> new OrderQueryDto(o.getOrderId(), o.getName(), o.getOrderDate(), o.getOrderStatus(), o.getAddress()), mapping(o -> new OrderItemQueryDto(o.getOrderId(), o.getItemName(), o.getOrderPrice(), o.getCount()), toList()))).entrySet().stream()
                .map(e -> new OrderQueryDto(e.getKey().getOrderId(), e.getKey().getName(), e.getKey().getOrderDate(), e.getKey().getOrderStatus(), e.getKey().getAddress(), e.getValue())).collect(toList());

    }

    // 권장 순서
    // 1. join fetch 로 최적화 V3
    // 2. 컬렉션이 있을시 페이징 :  default batch fetch size 로 최적화    논페이징 : fetch join
    // 3. entity 조회로 해결이 불가하면 DTO 를 직접 조회.     V4, V5, V6
    // 4. DTO 조회로 해결이 불가하면 NativeSQL , jdbcTemplate
   @Data
    static class OrderDto {

        private Long orderId;
        private String name;
        private LocalDateTime orderDate;
        private OrderStatus orderStatus;
        private Address address;
        //        private List<OrderItem> orderItems;
        private List<OrderItemDto> orderItems;

        public OrderDto(Order order) {
            orderId = order.getId();
            name = order.getMember().getName();
            orderDate = order.getOrderDate();
            orderStatus = order.getStatus();
            address = order.getDelivery().getAddress();
//            order.getOrderItems().stream().forEach(o -> o.getItem().getName());
//            orderItems = order.getOrderItems();
            orderItems = order.getOrderItems().stream().map(OrderItemDto::new).collect(toList());
        }
    }

    @Data
    static class OrderItemDto {

        private String itemName;
        private int orderPrice;
        private int count;

        public OrderItemDto(OrderItem orderItem) {
            itemName = orderItem.getItem().getName();
            orderPrice = orderItem.getOrderPrice();
            count = orderItem.getCount();
        }
    }

}
