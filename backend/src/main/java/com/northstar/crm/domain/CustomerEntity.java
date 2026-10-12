package com.northstar.crm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "customer")
public class CustomerEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "customer_id")
  private Long id;

  @Column(name = "public_id", nullable = false, unique = true)
  private String publicId;

  @Column(name = "full_name", nullable = false)
  private String fullName;

  @Column(nullable = false)
  private String status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Version
  @Column(nullable = false)
  private long version;

  public long getVersion() {
    return version;
  }

  public void setPublicId(String publicId) {
    this.publicId = publicId;
  }

  public void setFullName(String fullName) {
    this.fullName = fullName;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Long getId() {
    return id;
  }

  public String getPublicId() {
    return publicId;
  }

  public String getFullName() {
    return fullName;
  }

  public String getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
