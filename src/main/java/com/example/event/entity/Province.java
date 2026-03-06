package com.example.event.entity;

import com.example.event.config.jpa.UlidID;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
public class Province {
    @Id
    @UlidID
    private String id;
    private String name;
    private String codeName;
}
