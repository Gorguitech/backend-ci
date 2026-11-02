package com.daara.internatadmin.finance.categoriedepense;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "categories_depenses")
@Getter @Setter @NoArgsConstructor
public class CategorieDepense {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String name;

    private String description;

    private boolean active = true;
}