package com.daara.internatadmin.parametres;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "parametres_etablissement")
@Getter @Setter @NoArgsConstructor
public class ParametresEtablissement {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String address;
    private String phone;
    private String email;

    @Column(name = "director_name")
    private String directorName;

    @Column(name = "logo_path")
    private String logoPath;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}