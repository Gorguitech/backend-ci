package com.daara.internatadmin.auth.parametres;

import com.daara.internatadmin.auth.parametres.dto.ParametresResponse;
import com.daara.internatadmin.auth.parametres.dto.UpdateParametresRequest;
import com.daara.internatadmin.common.audit.Audited;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDateTime;

/** Ligne unique (id 1). Le logo est stocke sur disque, dans un volume Docker en production. */
@Service
public class ParametresService {

    static final long MAX_LOGO_SIZE = 2 * 1024 * 1024;

    private final ParametresEtablissementRepository repository;
    private final String logoDir;
    private final Clock clock;

    public ParametresService(ParametresEtablissementRepository repository,
                             @Value("${internatadmin.parametres.logo-dir}") String logoDir,
                             Clock clock) {
        this.repository = repository;
        this.logoDir = logoDir;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ParametresResponse get() {
        return toResponse(getEntity());
    }

    @Audited(action = "UPDATE", entityType = "Parametres")
    @Transactional
    public ParametresResponse update(UpdateParametresRequest dto) {
        ParametresEtablissement p = getEntity();
        p.setName(dto.name());
        p.setAddress(dto.address());
        p.setPhone(dto.phone());
        p.setEmail(dto.email());
        p.setDirectorName(dto.directorName());
        p.setUpdatedAt(LocalDateTime.now(clock));
        return toResponse(p);
    }

    @Audited(action = "UPLOAD_LOGO", entityType = "Parametres")
    @Transactional
    public ParametresResponse uploadLogo(MultipartFile file) {
        validateLogo(file);
        ParametresEtablissement p = getEntity();

        try {
            Path directory = Paths.get(logoDir);
            Files.createDirectories(directory);

            String extension = "image/png".equals(file.getContentType()) ? ".png" : ".jpg";
            Path destination = directory.resolve("logo" + extension);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
            }

            p.setLogoPath(destination.toString());
            p.setUpdatedAt(LocalDateTime.now(clock));
        } catch (IOException e) {
            throw new IllegalArgumentException("Erreur lors de l'enregistrement du logo");
        }
        return toResponse(p);
    }

    @Transactional(readOnly = true)
    public byte[] getLogoBytes() {
        ParametresEtablissement p = getEntity();
        if (p.getLogoPath() == null) {
            throw new IllegalArgumentException("Aucun logo n'a été configuré");
        }
        try {
            return Files.readAllBytes(Paths.get(p.getLogoPath()));
        } catch (IOException e) {
            throw new IllegalArgumentException("Impossible de lire le fichier logo");
        }
    }

    @Transactional(readOnly = true)
    public String getLogoContentType() {
        String path = getEntity().getLogoPath();
        return (path != null && path.endsWith(".png")) ? "image/png" : "image/jpeg";
    }

    private static void validateLogo(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Le fichier est vide");
        }
        if (file.getSize() > MAX_LOGO_SIZE) {
            throw new IllegalArgumentException("Le logo dépasse la taille maximale de 2 Mo");
        }
        String contentType = file.getContentType();
        if (!"image/jpeg".equals(contentType) && !"image/png".equals(contentType)) {
            throw new IllegalArgumentException("Seuls les formats JPEG et PNG sont acceptés");
        }
    }

    private ParametresEtablissement getEntity() {
        return repository.findById(1L)
                .orElseThrow(() -> new IllegalStateException(
                        "Paramètres établissement introuvables : la migration V2 a-t-elle été appliquée ?"));
    }

    private static ParametresResponse toResponse(ParametresEtablissement p) {
        return new ParametresResponse(p.getId(), p.getName(), p.getAddress(), p.getPhone(),
                p.getEmail(), p.getDirectorName(), p.getLogoPath() != null);
    }
}
