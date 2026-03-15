package com.pricingtool.test.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "test_data")
public class TestData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "test_name")
    private String name;
    
    @Column(name = "test_value")
    private String value;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}
