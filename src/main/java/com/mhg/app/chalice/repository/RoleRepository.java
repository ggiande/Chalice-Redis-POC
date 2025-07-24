package com.mhg.app.chalice.repository;

import com.mhg.app.chalice.model.Role;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoleRepository extends CrudRepository<Role, String> {
    Role findFirstByName(String role);
}