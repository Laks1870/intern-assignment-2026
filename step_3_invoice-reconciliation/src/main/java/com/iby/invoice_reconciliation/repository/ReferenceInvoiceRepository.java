package com.iby.invoice_reconciliation.repository;

import com.iby.invoice_reconciliation.enitity.ReferenceInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReferenceInvoiceRepository extends JpaRepository<ReferenceInvoice, String> {
}