package com.iby.invoice_reconciliation.repository;

import com.iby.invoice_reconciliation.enitity.InputInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InputInvoiceRepository extends JpaRepository<InputInvoice, String> {
}
