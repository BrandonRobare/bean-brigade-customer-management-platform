package com.northstar.crm.repo;

import com.northstar.crm.domain.CustomerEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CustomerRepository extends JpaRepository<CustomerEntity, Long> {
  Optional<CustomerEntity> findByPublicId(String publicId);

  List<CustomerEntity> findByFullNameContainingIgnoreCaseOrPublicIdIgnoreCaseOrderByPublicId(
      String fullName, String publicId);

  boolean existsByPublicId(String publicId);

  @Query(value = "SELECT nextval('customer_public_id_seq')", nativeQuery = true)
  long nextPublicNumber();
}
