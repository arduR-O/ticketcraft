package com.ticketcraft.gateway.entity;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("users")
public class User implements Persistable<UUID> {
  @Id private UUID id;
  private String email;
  private String passwordHash;
  private String provider; // 'LOCAL' or 'GOOGLE'
  private String role;
  private LocalDateTime createdAt;
  
  @Transient
  @Builder.Default
  private boolean isNew = false;

  @Override
  public boolean isNew() {
      return isNew || id == null;
  }
}
