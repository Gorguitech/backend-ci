package com.daara.internatadmin.auth.parametres;

import com.daara.internatadmin.auth.parametres.dto.ParametresResponse;
import com.daara.internatadmin.auth.parametres.dto.UpdateParametresRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ParametresServiceTest {

    @TempDir
    Path logoDir;

    private final ParametresEtablissementRepository repository = mock(ParametresEtablissementRepository.class);
    private final ParametresEtablissement entity = new ParametresEtablissement();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-27T10:00:00Z"), ZoneOffset.UTC);

    private ParametresService service() {
        entity.setName("Daara (à configurer)");
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        return new ParametresService(repository, logoDir.toString(), clock);
    }

    @Test
    void modification() {
        ParametresResponse response = service().update(
                new UpdateParametresRequest("Daara Serigne Touba", "Touba", "771234567", "daara@exemple.sn", "Serigne Mbaye"));

        assertThat(response.name()).isEqualTo("Daara Serigne Touba");
        assertThat(response.directorName()).isEqualTo("Serigne Mbaye");
        assertThat(response.hasLogo()).isFalse();
        assertThat(entity.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 10, 27, 10, 0));
    }

    @Test
    void logoPng_enregistreSurDisqueEtRelu() throws Exception {
        ParametresService service = service();
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};

        ParametresResponse response = service.uploadLogo(new MockMultipartFile("file", "logo.png", "image/png", png));

        assertThat(response.hasLogo()).isTrue();
        assertThat(Files.readAllBytes(logoDir.resolve("logo.png"))).isEqualTo(png);
        assertThat(service.getLogoBytes()).isEqualTo(png);
        assertThat(service.getLogoContentType()).isEqualTo("image/png");
    }

    @Test
    void logoJpeg_extensionJpg() {
        ParametresService service = service();
        service.uploadLogo(new MockMultipartFile("file", "logo.jpeg", "image/jpeg", new byte[]{1}));
        assertThat(entity.getLogoPath()).endsWith("logo.jpg");
        assertThat(service.getLogoContentType()).isEqualTo("image/jpeg");
    }

    @Test
    void logoRefuse_vide_tropGros_mauvaisFormat() {
        ParametresService service = service();
        assertThatThrownBy(() -> service.uploadLogo(new MockMultipartFile("file", "a.png", "image/png", new byte[0])))
                .hasMessage("Le fichier est vide");
        byte[] big = new byte[(int) ParametresService.MAX_LOGO_SIZE + 1];
        assertThatThrownBy(() -> service.uploadLogo(new MockMultipartFile("file", "a.png", "image/png", big)))
                .hasMessage("Le logo dépasse la taille maximale de 2 Mo");
        assertThatThrownBy(() -> service.uploadLogo(new MockMultipartFile("file", "a.gif", "image/gif", new byte[]{1})))
                .hasMessage("Seuls les formats JPEG et PNG sont acceptés");
    }

    @Test
    void logoDeExactement2Mo_accepte() {
        ParametresService service = service();
        byte[] limit = new byte[(int) ParametresService.MAX_LOGO_SIZE];
        assertThat(service.uploadLogo(new MockMultipartFile("file", "a.png", "image/png", limit)).hasLogo()).isTrue();
    }

    @Test
    void aucunLogo_ouFichierDisparu() {
        ParametresService service = service();
        assertThatThrownBy(service::getLogoBytes).hasMessage("Aucun logo n'a été configuré");
        entity.setLogoPath(logoDir.resolve("absent.png").toString());
        assertThatThrownBy(service::getLogoBytes).hasMessage("Impossible de lire le fichier logo");
    }

    @Test
    void ligneDeParametresAbsente_erreurTechnique() {
        when(repository.findById(1L)).thenReturn(Optional.empty());
        ParametresService service = new ParametresService(repository, logoDir.toString(), clock);
        assertThatThrownBy(service::get).isInstanceOf(IllegalStateException.class);
    }
}
