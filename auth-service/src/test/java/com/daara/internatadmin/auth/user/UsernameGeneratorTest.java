package com.daara.internatadmin.auth.user;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UsernameGeneratorTest {

    @Test
    void prenomPointNom_sansAccentsNiCaracteresSpeciaux() {
        assertThat(UsernameGenerator.generateBase("Awa", "Diop")).isEqualTo("awa.diop");
        assertThat(UsernameGenerator.generateBase("Ndèye Fatou", "N'Diaye")).isEqualTo("ndeyefatou.ndiaye");
        assertThat(UsernameGenerator.generateBase("Élhadji", "Gaye-Sène")).isEqualTo("elhadji.gayesene");
    }
}
