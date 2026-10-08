package com.northstar.crm.mapper;

import com.northstar.crm.domain.CustomerEntity;
import com.northstar.crm.api.dto.CustomerDTO;

public class CustomerMapper {
    public static CustomerDTO toCustomerDTO(CustomerEntity customerEntity) {
        return new CustomerDTO(
                customerEntity.getPublicId(),
                customerEntity.getFullName(),
                customerEntity.getStatus(),
                customerEntity.getCreatedAt().toString()
        );
    }
}
