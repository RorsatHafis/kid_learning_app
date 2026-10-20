package com.platform.family.entity;

import org.springframework.util.Assert;

import com.platform.common.entity.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;

/**
 * A family: the private unit that owns children and their learning. Parents belong to it through memberships;
 * nothing in it is visible to a school unless a parent grants access.
 */
@Entity
@Table(name = "families")
public class Family extends AuditableEntity {

    @NotBlank
    @Column(name = "name", nullable = false, length = 120)
    private String name;
 
    protected Family() {
        // JPA
    }
 
    private Family(String name) {
        this.name = name;
    }
 
    public static Family create(String name) {
        Assert.hasText(name, "name must not be blank");
        return new Family(name);
    }
 
    public void rename(String newName) {
        Assert.hasText(newName, "newName must not be blank");
        this.name = newName;
    }
 
    public String getName() {
        return name;
    }
    
}
