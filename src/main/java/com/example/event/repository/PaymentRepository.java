package com.example.event.repository;

import com.example.event.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, String> {
    Payment findPaymentByReservation_Id(String reservationId);
    Payment findPaymentById(String id);
}
