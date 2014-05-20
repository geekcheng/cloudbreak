package com.sequenceiq.provisioning.domain;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;

@Entity
public abstract class Infra {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "infra_generator")
    @SequenceGenerator(name = "infra_generator", sequenceName = "sequence_table")
    private Long id;

    public Infra() {

    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public abstract CloudPlatform cloudPlatform();

}